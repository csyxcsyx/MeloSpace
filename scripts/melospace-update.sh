#!/usr/bin/env bash
set -Eeuo pipefail
umask 077

readonly BASE_DIR="/opt/melospace"
readonly REPO_DIR="${BASE_DIR}/repo"
readonly BACKEND_SOURCE_DIR="${REPO_DIR}/music-web-backend"
readonly FRONTEND_SOURCE_DIR="${REPO_DIR}/music-web-frontend"
readonly BACKEND_BUILD_JAR="${BACKEND_SOURCE_DIR}/target/music-web-backend-0.0.1-SNAPSHOT.jar"
readonly APP_DIR="${BASE_DIR}/app"
readonly BACKEND_JAR="${APP_DIR}/backend.jar"
readonly FRONTEND_DIR="${BASE_DIR}/frontend"
readonly FRONTEND_DIST="${FRONTEND_DIR}/dist"
readonly BACKUP_ROOT="${BASE_DIR}/backups/deployments"
readonly MYSQL_BACKUP_DEFAULTS_FILE="${BASE_DIR}/env/mysql-backup.cnf"
readonly MYSQL_DATABASE_NAME="music_web"
readonly SERVICE_NAME="melospace-backend"
readonly HEALTH_URL="http://127.0.0.1:8080/actuator/health"
readonly LOCK_FILE="/run/lock/melospace-update.lock"
readonly BACKUP_KEEP_COUNT=10
readonly HEALTH_ATTEMPTS=30
readonly HEALTH_RETRY_SECONDS=2

STAGING_DIR=""
INCOMPLETE_BACKUP_DIR=""
BACKUP_DIR=""
BACKEND_NEW=""
BACKEND_OLD=""
FRONTEND_NEW=""
FRONTEND_OLD=""
DEPLOYMENT_STARTED=0
ROLLBACK_COMPLETED=0

log() {
  printf '[melospace-update] %s\n' "$*"
}

warn() {
  printf '[melospace-update] WARNING: %s\n' "$*" >&2
}

die() {
  printf '[melospace-update] ERROR: %s\n' "$*" >&2
  exit 1
}

safe_remove_tree() {
  local target="${1:?A directory path is required}"

  case "$target" in
    "${BASE_DIR}"/.deploy-staging.* \
      | "${FRONTEND_DIR}"/dist.new.* \
      | "${FRONTEND_DIR}"/dist.old.* \
      | "${FRONTEND_DIST}" \
      | "${BACKUP_ROOT}"/.incomplete-* \
      | "${BACKUP_ROOT}"/20*)
      rm -rf --one-file-system -- "$target"
      ;;
    *)
      die "Refusing to recursively remove an unexpected path: ${target}"
      ;;
  esac
}

safe_remove_file() {
  local target="${1:?A file path is required}"

  case "$target" in
    "${BACKEND_JAR}" \
      | "${APP_DIR}"/backend.jar.new.* \
      | "${APP_DIR}"/backend.jar.old.*)
      rm -f -- "$target"
      ;;
    *)
      die "Refusing to remove an unexpected file: ${target}"
      ;;
  esac
}

cleanup() {
  set +e

  if [[ -n "$STAGING_DIR" && -e "$STAGING_DIR" ]]; then
    safe_remove_tree "$STAGING_DIR"
  fi
  if [[ -n "$INCOMPLETE_BACKUP_DIR" && -e "$INCOMPLETE_BACKUP_DIR" ]]; then
    safe_remove_tree "$INCOMPLETE_BACKUP_DIR"
  fi
  if [[ -n "$BACKEND_NEW" && -e "$BACKEND_NEW" ]]; then
    safe_remove_file "$BACKEND_NEW"
  fi
  if [[ -n "$FRONTEND_NEW" && -e "$FRONTEND_NEW" ]]; then
    safe_remove_tree "$FRONTEND_NEW"
  fi
}

require_command() {
  local command_name="${1:?A command name is required}"
  command -v "$command_name" >/dev/null 2>&1 \
    || die "Required command is not installed: ${command_name}"
}

wait_for_health() {
  local attempt
  local response

  for ((attempt = 1; attempt <= HEALTH_ATTEMPTS; attempt++)); do
    if response="$(curl --fail --silent --show-error --max-time 3 "$HEALTH_URL" 2>/dev/null)"; then
      if grep -Eq '"status"[[:space:]]*:[[:space:]]*"UP"' <<<"$response"; then
        return 0
      fi
    fi

    if ((attempt < HEALTH_ATTEMPTS)); then
      sleep "$HEALTH_RETRY_SECONDS"
    fi
  done

  return 1
}

set_frontend_permissions() {
  local directory="${1:?A frontend directory is required}"

  find "$directory" -type d -exec chmod 0755 {} + || return 1
  find "$directory" -type f -exec chmod 0644 {} + || return 1
}

rollback() {
  local reason="${1:-deployment failure}"
  local rollback_status=0

  warn "Rolling back application artifacts after ${reason}."

  systemctl stop "$SERVICE_NAME" || rollback_status=1

  if [[ -n "$BACKEND_OLD" && -f "$BACKEND_OLD" ]]; then
    if [[ -e "$BACKEND_JAR" ]]; then
      safe_remove_file "$BACKEND_JAR" || rollback_status=1
    fi
    mv -- "$BACKEND_OLD" "$BACKEND_JAR" || rollback_status=1
  elif [[ -n "$BACKUP_DIR" && -f "$BACKUP_DIR/backend.jar" ]]; then
    install -o melospace -g melospace -m 0644 \
      "$BACKUP_DIR/backend.jar" "$BACKEND_JAR" || rollback_status=1
  else
    warn "No previous backend artifact is available for rollback."
    rollback_status=1
  fi

  if [[ -n "$FRONTEND_OLD" && -d "$FRONTEND_OLD" ]]; then
    if [[ -e "$FRONTEND_DIST" ]]; then
      safe_remove_tree "$FRONTEND_DIST" || rollback_status=1
    fi
    mv -- "$FRONTEND_OLD" "$FRONTEND_DIST" || rollback_status=1
  elif [[ -n "$BACKUP_DIR" && -d "$BACKUP_DIR/frontend-dist" ]]; then
    if [[ -e "$FRONTEND_DIST" ]]; then
      safe_remove_tree "$FRONTEND_DIST" || rollback_status=1
    fi
    cp -a -- "$BACKUP_DIR/frontend-dist" "$FRONTEND_DIST" || rollback_status=1
  else
    warn "No previous frontend artifact is available for rollback."
    rollback_status=1
  fi

  chown melospace:melospace "$BACKEND_JAR" 2>/dev/null || rollback_status=1
  chmod 0644 "$BACKEND_JAR" 2>/dev/null || rollback_status=1
  if [[ -d "$FRONTEND_DIST" ]]; then
    set_frontend_permissions "$FRONTEND_DIST" || rollback_status=1
  fi

  if nginx -t; then
    systemctl reload nginx || rollback_status=1
  else
    rollback_status=1
  fi
  systemctl restart "$SERVICE_NAME" || rollback_status=1

  if wait_for_health; then
    warn "Previous application artifacts were restored and are healthy."
  else
    warn "Rollback completed, but the backend health check is still failing."
    rollback_status=1
  fi

  ROLLBACK_COMPLETED=1
  return "$rollback_status"
}

on_error() {
  local exit_code=$?
  local line_number="${1:-unknown}"

  trap - ERR
  set +e
  warn "Command failed at line ${line_number} with exit code ${exit_code}."

  if ((DEPLOYMENT_STARTED == 1 && ROLLBACK_COMPLETED == 0)); then
    rollback "a deployment command failure" || true
    journalctl -u "$SERVICE_NAME" -n 80 --no-pager >&2 || true
  fi

  exit "$exit_code"
}

prune_backups() {
  local -a backup_directories=()
  local remove_count
  local index

  mapfile -d '' -t backup_directories < <(
    find "$BACKUP_ROOT" \
      -mindepth 1 \
      -maxdepth 1 \
      -type d \
      -name '20*' \
      -print0 \
      | sort -z
  )

  remove_count=$((${#backup_directories[@]} - BACKUP_KEEP_COUNT))
  if ((remove_count <= 0)); then
    return 0
  fi

  for ((index = 0; index < remove_count; index++)); do
    log "Removing expired deployment backup: ${backup_directories[$index]}"
    safe_remove_tree "${backup_directories[$index]}" || return 1
  done
}

trap 'on_error "$LINENO"' ERR
trap cleanup EXIT

if ((EUID != 0)); then
  die "This deployment script must be run as root."
fi

for required_command in \
  chmod chown cp curl date find flock git grep gzip install journalctl mktemp mv \
  mvn mysqldump nginx npm rm sed sha256sum sleep sort systemctl; do
  require_command "$required_command"
done

for required_directory in \
  "$BASE_DIR" "$REPO_DIR" "$BACKEND_SOURCE_DIR" "$FRONTEND_SOURCE_DIR" \
  "$APP_DIR" "$FRONTEND_DIR"; do
  [[ -d "$required_directory" ]] \
    || die "Required directory does not exist: ${required_directory}"
  [[ ! -L "$required_directory" ]] \
    || die "Required directory must not be a symbolic link: ${required_directory}"
done

[[ -f "$BACKEND_JAR" ]] || die "Current backend artifact does not exist: ${BACKEND_JAR}"
[[ ! -L "$BACKEND_JAR" ]] \
  || die "Current backend artifact must not be a symbolic link: ${BACKEND_JAR}"
[[ -d "$FRONTEND_DIST" ]] || die "Current frontend artifact does not exist: ${FRONTEND_DIST}"
[[ ! -L "$FRONTEND_DIST" ]] \
  || die "Current frontend artifact must not be a symbolic link: ${FRONTEND_DIST}"
[[ -f "$FRONTEND_DIST/index.html" ]] \
  || die "Current frontend artifact is incomplete: ${FRONTEND_DIST}/index.html"

exec 9>"$LOCK_FILE"
flock -n 9 || die "Another MeloSpace deployment is already running."

PREVIOUS_REPO_REVISION="$(git -C "$REPO_DIR" rev-parse HEAD)"
readonly PREVIOUS_REPO_REVISION
CURRENT_BRANCH="$(git -C "$REPO_DIR" branch --show-current)"
readonly CURRENT_BRANCH
[[ "$CURRENT_BRANCH" == "master" ]] \
  || die "Expected ${REPO_DIR} to be on master, found: ${CURRENT_BRANCH:-detached HEAD}"

REPO_STATUS="$(git -C "$REPO_DIR" status --porcelain --untracked-files=no)"
if [[ -n "$REPO_STATUS" ]]; then
  die "Tracked changes exist in ${REPO_DIR}; refusing to deploy over them."
fi

log "Pulling origin/master with fast-forward-only policy."
git -C "$REPO_DIR" pull --ff-only origin master
DEPLOY_REVISION="$(git -C "$REPO_DIR" rev-parse HEAD)"
readonly DEPLOY_REVISION
DEPLOY_REVISION_SHORT="$(git -C "$REPO_DIR" rev-parse --short=12 HEAD)"
readonly DEPLOY_REVISION_SHORT
DEPLOY_TIMESTAMP="$(date -u +%Y%m%dT%H%M%SZ)"
readonly DEPLOY_TIMESTAMP
readonly RELEASE_ID="${DEPLOY_TIMESTAMP}-${DEPLOY_REVISION_SHORT}-$$"

log "Building backend revision ${DEPLOY_REVISION_SHORT}."
(
  cd "$BACKEND_SOURCE_DIR"
  mvn -DskipTests clean package
)
[[ -s "$BACKEND_BUILD_JAR" ]] \
  || die "Backend build did not produce the expected jar: ${BACKEND_BUILD_JAR}"

log "Installing frontend dependencies and building production assets."
(
  cd "$FRONTEND_SOURCE_DIR"
  npm ci --no-audit --no-fund
  npm run build
)
[[ -f "$FRONTEND_SOURCE_DIR/dist/index.html" ]] \
  || die "Frontend build did not produce dist/index.html."

STAGING_DIR="$(mktemp -d "${BASE_DIR}/.deploy-staging.XXXXXX")"
install -m 0644 "$BACKEND_BUILD_JAR" "$STAGING_DIR/backend.jar"
cp -a -- "$FRONTEND_SOURCE_DIR/dist" "$STAGING_DIR/frontend-dist"
set_frontend_permissions "$STAGING_DIR/frontend-dist"

nginx -t
install -d -m 0700 "$BACKUP_ROOT"
[[ ! -L "$BACKUP_ROOT" ]] \
  || die "Backup root must not be a symbolic link: ${BACKUP_ROOT}"
INCOMPLETE_BACKUP_DIR="${BACKUP_ROOT}/.incomplete-${RELEASE_ID}"
BACKUP_DIR="${BACKUP_ROOT}/${RELEASE_ID}"
[[ ! -e "$INCOMPLETE_BACKUP_DIR" && ! -e "$BACKUP_DIR" ]] \
  || die "Deployment backup already exists for release ${RELEASE_ID}."
install -d -m 0700 "$INCOMPLETE_BACKUP_DIR"

log "Backing up database ${MYSQL_DATABASE_NAME} and current application artifacts."
mysql_dump_auth_args=()
if [[ -f "$MYSQL_BACKUP_DEFAULTS_FILE" ]]; then
  [[ -r "$MYSQL_BACKUP_DEFAULTS_FILE" ]] \
    || die "MySQL backup defaults file is not readable: ${MYSQL_BACKUP_DEFAULTS_FILE}"
  mysql_dump_auth_args+=(--defaults-extra-file="$MYSQL_BACKUP_DEFAULTS_FILE")
fi

mysqldump \
  "${mysql_dump_auth_args[@]}" \
  --single-transaction \
  --quick \
  --routines \
  --events \
  --triggers \
  --hex-blob \
  --no-tablespaces \
  --set-gtid-purged=OFF \
  --default-character-set=utf8mb4 \
  --databases "$MYSQL_DATABASE_NAME" \
  | gzip -9 >"$INCOMPLETE_BACKUP_DIR/${MYSQL_DATABASE_NAME}.sql.gz"
gzip -t "$INCOMPLETE_BACKUP_DIR/${MYSQL_DATABASE_NAME}.sql.gz"

install -m 0600 "$BACKEND_JAR" "$INCOMPLETE_BACKUP_DIR/backend.jar"
cp -a -- "$FRONTEND_DIST" "$INCOMPLETE_BACKUP_DIR/frontend-dist"
[[ -f "$INCOMPLETE_BACKUP_DIR/frontend-dist/index.html" ]] \
  || die "Frontend backup is incomplete."

{
  printf 'created_at_utc=%s\n' "$DEPLOY_TIMESTAMP"
  printf 'previous_repo_revision=%s\n' "$PREVIOUS_REPO_REVISION"
  printf 'deploy_revision=%s\n' "$DEPLOY_REVISION"
  printf 'database=%s\n' "$MYSQL_DATABASE_NAME"
} >"$INCOMPLETE_BACKUP_DIR/deployment.env"
chmod 0600 "$INCOMPLETE_BACKUP_DIR/deployment.env"
(
  cd "$INCOMPLETE_BACKUP_DIR"
  sha256sum \
    backend.jar \
    "${MYSQL_DATABASE_NAME}.sql.gz" \
    >SHA256SUMS
)
chmod 0600 "$INCOMPLETE_BACKUP_DIR/SHA256SUMS"

mv -- "$INCOMPLETE_BACKUP_DIR" "$BACKUP_DIR"
INCOMPLETE_BACKUP_DIR=""
log "Backup completed: ${BACKUP_DIR}"

BACKEND_NEW="${APP_DIR}/backend.jar.new.${RELEASE_ID}"
BACKEND_OLD="${APP_DIR}/backend.jar.old.${RELEASE_ID}"
FRONTEND_NEW="${FRONTEND_DIR}/dist.new.${RELEASE_ID}"
FRONTEND_OLD="${FRONTEND_DIR}/dist.old.${RELEASE_ID}"

install -o melospace -g melospace -m 0644 \
  "$STAGING_DIR/backend.jar" "$BACKEND_NEW"
cp -a -- "$STAGING_DIR/frontend-dist" "$FRONTEND_NEW"
set_frontend_permissions "$FRONTEND_NEW"

DEPLOYMENT_STARTED=1
mv -- "$BACKEND_JAR" "$BACKEND_OLD"
mv -- "$BACKEND_NEW" "$BACKEND_JAR"
BACKEND_NEW=""
mv -- "$FRONTEND_DIST" "$FRONTEND_OLD"
mv -- "$FRONTEND_NEW" "$FRONTEND_DIST"
FRONTEND_NEW=""

systemctl restart "$SERVICE_NAME"
systemctl reload nginx

log "Waiting for backend health at ${HEALTH_URL}."
if ! wait_for_health; then
  trap - ERR
  set +e
  warn "New backend did not become healthy within $((HEALTH_ATTEMPTS * HEALTH_RETRY_SECONDS)) seconds."
  rollback "a failed health check"
  rollback_status=$?
  journalctl -u "$SERVICE_NAME" -n 80 --no-pager >&2 || true
  if ((rollback_status != 0)); then
    warn "Rollback encountered errors; manual intervention is required."
  fi
  exit 1
fi

DEPLOYMENT_STARTED=0
log "Backend health is UP."

if [[ -f "$BACKEND_OLD" ]]; then
  safe_remove_file "$BACKEND_OLD" || warn "Could not remove superseded backend artifact."
fi
if [[ -d "$FRONTEND_OLD" ]]; then
  safe_remove_tree "$FRONTEND_OLD" || warn "Could not remove superseded frontend artifact."
fi

prune_backups || warn "Deployment succeeded, but old backup pruning failed."

systemctl --no-pager --full status "$SERVICE_NAME" \
  | sed -n '1,12p' \
  || warn "Deployment succeeded, but the final service status could not be displayed."
log "Deployment completed successfully at revision ${DEPLOY_REVISION_SHORT}."
