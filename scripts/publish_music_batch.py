#!/usr/bin/env python3
"""Batch publish MeloSpace music assets from a CSV manifest.

The script copies media files to the server and upserts artist, album, and song
metadata into MySQL. It is intended for trusted course/demo assets only.
"""

from __future__ import annotations

import argparse
import csv
import hashlib
import os
import shlex
import subprocess
import sys
import tempfile
from dataclasses import dataclass
from pathlib import Path
from urllib.parse import quote


MEDIA_KINDS = {
    "audio": ("audio_path", "audio", {".mp3", ".aac", ".m4a", ".flac", ".wav"}),
    "cover": ("cover_path", "cover", {".jpg", ".jpeg", ".png", ".webp"}),
    "lyric": ("lyric_path", "lyrics", {".lrc", ".txt"}),
}


@dataclass(frozen=True)
class PublishedFile:
    local_path: Path
    remote_relative_path: str
    url: str


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Copy music media to MeloSpace and upsert song metadata from CSV."
    )
    parser.add_argument("manifest", type=Path, help="CSV file with song metadata and local media paths.")
    parser.add_argument("--ssh-key", type=Path, default=Path(r"C:\Users\YUXIANde\Downloads\REX.pem"))
    parser.add_argument("--ssh-user", default="root")
    parser.add_argument("--ssh-host", default="47.89.235.138")
    parser.add_argument("--ssh-port", default="22")
    parser.add_argument("--remote-media-root", default="/opt/melospace/media")
    parser.add_argument("--database", default="music_web")
    parser.add_argument("--output-sql", type=Path, help="Write generated SQL to this file.")
    parser.add_argument("--execute", action="store_true", help="Upload files and execute SQL on the server.")
    parser.add_argument("--skip-upload", action="store_true", help="Only execute/write SQL; do not copy media files.")
    return parser.parse_args()


def read_manifest(path: Path) -> list[dict[str, str]]:
    with path.open("r", encoding="utf-8-sig", newline="") as handle:
        rows = list(csv.DictReader(handle))
    if not rows:
        raise SystemExit(f"Manifest is empty: {path}")
    required = {"title", "artist", "album", "audio_path"}
    missing = required.difference(rows[0].keys())
    if missing:
        raise SystemExit(f"Manifest is missing required columns: {', '.join(sorted(missing))}")
    return rows


def sql_string(value: str | None) -> str:
    if value is None or value == "":
        return "NULL"
    escaped = value.replace("\\", "\\\\").replace("'", "''")
    return f"'{escaped}'"


def sql_int(value: str | None, default: int = 0) -> str:
    if value is None or value.strip() == "":
        return str(default)
    return str(int(value))


def resolve_local_path(manifest_dir: Path, raw_path: str) -> Path | None:
    if not raw_path:
        return None
    path = Path(raw_path)
    if not path.is_absolute():
        path = manifest_dir / path
    path = path.resolve()
    if not path.is_file():
        raise SystemExit(f"Media file does not exist: {path}")
    return path


def hashed_name(path: Path) -> str:
    digest = hashlib.sha1()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(chunk)
    return f"{digest.hexdigest()[:32]}{path.suffix.lower()}"


def publish_file(manifest_dir: Path, row: dict[str, str], kind: str) -> PublishedFile | None:
    column, remote_folder, allowed_extensions = MEDIA_KINDS[kind]
    local_path = resolve_local_path(manifest_dir, row.get(column, "").strip())
    if local_path is None:
        return None
    if local_path.suffix.lower() not in allowed_extensions:
        raise SystemExit(f"Unsupported {kind} extension for {local_path}")
    remote_relative_path = f"{remote_folder}/{hashed_name(local_path)}"
    url = "/media/" + quote(remote_relative_path, safe="/")
    return PublishedFile(local_path, remote_relative_path, url)


def build_sql(rows: list[dict[str, str]], manifest_dir: Path) -> tuple[str, list[PublishedFile]]:
    lines = [
        "SET NAMES utf8mb4;",
        "START TRANSACTION;",
    ]
    files: list[PublishedFile] = []
    seen_files: set[Path] = set()

    for index, row in enumerate(rows, start=1):
        title = row.get("title", "").strip()
        artist = row.get("artist", "").strip()
        album = row.get("album", "").strip()
        if not title or not artist or not album:
            raise SystemExit(f"Row {index} must include title, artist, and album.")

        audio = publish_file(manifest_dir, row, "audio")
        if audio is None:
            raise SystemExit(f"Row {index} must include audio_path.")
        cover = publish_file(manifest_dir, row, "cover")
        lyric = publish_file(manifest_dir, row, "lyric")

        for item in (audio, cover, lyric):
            if item and item.local_path not in seen_files:
                files.append(item)
                seen_files.add(item.local_path)

        artist_bio = row.get("artist_bio", "").strip()
        release_date = row.get("release_date", "").strip()
        language = row.get("language", "中文").strip() or "中文"
        genre = row.get("genre", "").strip()
        mood = row.get("mood", "").strip()

        lines.extend(
            [
                "",
                f"-- {title} / {artist} / {album}",
                f"SET @artist_name := {sql_string(artist)};",
                f"SET @artist_bio := {sql_string(artist_bio)};",
                "SET @artist_id := (SELECT id FROM artist WHERE name = @artist_name LIMIT 1);",
                "INSERT INTO artist (name, bio, avatar_url)",
                "SELECT @artist_name, NULLIF(@artist_bio, ''), NULL WHERE @artist_id IS NULL;",
                "SET @artist_id := (SELECT id FROM artist WHERE name = @artist_name LIMIT 1);",
                "UPDATE artist SET bio = COALESCE(NULLIF(@artist_bio, ''), bio) WHERE id = @artist_id;",
                f"SET @album_title := {sql_string(album)};",
                f"SET @album_cover := {sql_string(cover.url if cover else '')};",
                f"SET @release_date := {sql_string(release_date)};",
                "SET @album_id := (SELECT id FROM album WHERE title = @album_title AND artist_id = @artist_id LIMIT 1);",
                "INSERT INTO album (title, artist_id, cover_url, release_date)",
                "SELECT @album_title, @artist_id, NULLIF(@album_cover, ''), NULLIF(@release_date, '')",
                "WHERE @album_id IS NULL;",
                "SET @album_id := (SELECT id FROM album WHERE title = @album_title AND artist_id = @artist_id LIMIT 1);",
                "UPDATE album SET",
                "  cover_url = COALESCE(NULLIF(@album_cover, ''), cover_url),",
                "  release_date = COALESCE(NULLIF(@release_date, ''), release_date)",
                "WHERE id = @album_id;",
                f"SET @song_title := {sql_string(title)};",
                f"SET @audio_url := {sql_string(audio.url)};",
                f"SET @cover_url := {sql_string(cover.url if cover else '')};",
                f"SET @lyric_url := {sql_string(lyric.url if lyric else '')};",
                f"SET @duration_seconds := {sql_int(row.get('duration_seconds'), 0)};",
                f"SET @language := {sql_string(language)};",
                f"SET @genre := {sql_string(genre)};",
                f"SET @mood := {sql_string(mood)};",
                "SET @song_id := (SELECT id FROM song WHERE title = @song_title AND artist_id = @artist_id AND album_id = @album_id LIMIT 1);",
                "INSERT INTO song (title, artist_id, album_id, cover_url, audio_url, lyric_url, duration_seconds, language, genre, mood, status)",
                "SELECT @song_title, @artist_id, @album_id, NULLIF(@cover_url, ''), @audio_url, NULLIF(@lyric_url, ''),",
                "       @duration_seconds, @language, NULLIF(@genre, ''), NULLIF(@mood, ''), 1",
                "WHERE @song_id IS NULL;",
                "SET @song_id := (SELECT id FROM song WHERE title = @song_title AND artist_id = @artist_id AND album_id = @album_id LIMIT 1);",
                "UPDATE song SET",
                "  cover_url = COALESCE(NULLIF(@cover_url, ''), cover_url),",
                "  audio_url = @audio_url,",
                "  lyric_url = COALESCE(NULLIF(@lyric_url, ''), lyric_url),",
                "  duration_seconds = @duration_seconds,",
                "  language = @language,",
                "  genre = NULLIF(@genre, ''),",
                "  mood = NULLIF(@mood, ''),",
                "  status = 1",
                "WHERE id = @song_id;",
            ]
        )

    lines.extend(["", "COMMIT;", ""])
    return "\n".join(lines), files


def run(command: list[str]) -> None:
    printable = " ".join(shlex.quote(part) for part in command)
    print(f"+ {printable}")
    subprocess.run(command, check=True)


def remote_target(args: argparse.Namespace, path: str) -> str:
    return f"{args.ssh_user}@{args.ssh_host}:{path}"


def upload_files(args: argparse.Namespace, files: list[PublishedFile]) -> None:
    ssh_base = [
        "ssh",
        "-i",
        str(args.ssh_key),
        "-p",
        str(args.ssh_port),
        "-o",
        "StrictHostKeyChecking=no",
        f"{args.ssh_user}@{args.ssh_host}",
    ]
    run(ssh_base + [f"mkdir -p {shlex.quote(args.remote_media_root)}/audio {shlex.quote(args.remote_media_root)}/cover {shlex.quote(args.remote_media_root)}/lyrics"])

    for item in files:
        remote_path = f"{args.remote_media_root}/{item.remote_relative_path}"
        remote_dir = os.path.dirname(remote_path)
        run(ssh_base + [f"mkdir -p {shlex.quote(remote_dir)}"])
        run(
            [
                "scp",
                "-i",
                str(args.ssh_key),
                "-P",
                str(args.ssh_port),
                "-o",
                "StrictHostKeyChecking=no",
                str(item.local_path),
                remote_target(args, remote_path),
            ]
        )


def execute_sql(args: argparse.Namespace, sql: str) -> None:
    with tempfile.TemporaryDirectory() as tmp:
        local_sql = Path(tmp) / "publish_music_batch.sql"
        local_sql.write_text(sql, encoding="utf-8")
        remote_sql = "/tmp/publish_music_batch.sql"
        run(
            [
                "scp",
                "-i",
                str(args.ssh_key),
                "-P",
                str(args.ssh_port),
                "-o",
                "StrictHostKeyChecking=no",
                str(local_sql),
                remote_target(args, remote_sql),
            ]
        )
        run(
            [
                "ssh",
                "-i",
                str(args.ssh_key),
                "-p",
                str(args.ssh_port),
                "-o",
                "StrictHostKeyChecking=no",
                f"{args.ssh_user}@{args.ssh_host}",
                f"mysql {shlex.quote(args.database)} < {remote_sql} && rm -f {remote_sql}",
            ]
        )


def main() -> int:
    args = parse_args()
    rows = read_manifest(args.manifest)
    sql, files = build_sql(rows, args.manifest.resolve().parent)

    if args.output_sql:
        args.output_sql.write_text(sql, encoding="utf-8")
        print(f"Wrote SQL: {args.output_sql}")
    else:
        print(sql)

    print(f"Prepared {len(rows)} songs and {len(files)} unique media files.")

    if args.execute:
        if not args.skip_upload:
            upload_files(args, files)
        execute_sql(args, sql)
        print("Batch publish complete.")
    else:
        print("Dry run only. Add --execute to upload files and update the server database.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
