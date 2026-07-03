#!/usr/bin/env python3
"""Backfill MeloSpace catalog lyrics, identity art, and album dates.

The script is designed to run on the deployed server. It reads the same
environment file as the backend, calls the existing LDDC import script for
lyrics, writes generated SVG media into the configured media root, and updates
MySQL through the mysql CLI.
"""

from __future__ import annotations

import argparse
import datetime as dt
import difflib
import hashlib
import html
import json
import os
import re
import subprocess
import sys
import time
from pathlib import Path
from typing import Any
from urllib.parse import quote, unquote, urlencode
from urllib.request import Request, urlopen


REPO_ROOT = Path(__file__).resolve().parents[1]
DEFAULT_ENV_FILE = Path("/opt/melospace/env/backend.env")
DEFAULT_MEDIA_ROOT = Path("/opt/melospace/media")
DEFAULT_STATE_DIR = Path("/opt/melospace/backfill-state")
DEFAULT_LDDC_SCRIPT = REPO_ROOT / "scripts" / "import_lddc_lyrics.py"

HTTP_TIMEOUT_SECONDS = 15
MUSICBRAINZ_USER_AGENT = "MeloSpaceCourseProject/1.0 (metadata-backfill)"


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Backfill lyrics, generated catalog art, and album release dates for MeloSpace.",
    )
    parser.add_argument(
        "--tasks",
        default="identity-art,release-dates,lyrics",
        help="Comma separated tasks: identity-art,release-dates,lyrics.",
    )
    parser.add_argument("--execute", action="store_true", help="Write files and update the database.")
    parser.add_argument("--env-file", type=Path, default=DEFAULT_ENV_FILE)
    parser.add_argument("--media-root", type=Path, default=DEFAULT_MEDIA_ROOT)
    parser.add_argument("--state-dir", type=Path, default=DEFAULT_STATE_DIR)
    parser.add_argument("--database", help="Override the database name from backend.env.")
    parser.add_argument("--cover-results", type=Path, help="Previous cover_results.json; source-less entries get initials art.")
    parser.add_argument(
        "--album-art-mode",
        choices=("fallback", "missing", "all"),
        default="fallback",
        help="Which album covers receive generated initials art.",
    )
    parser.add_argument(
        "--release-sources",
        default="itunes,musicbrainz",
        help="Comma separated release date sources: itunes,musicbrainz,netease.",
    )
    parser.add_argument("--release-limit", type=int, default=0, help="Maximum albums to check; 0 means all.")
    parser.add_argument("--release-sleep", type=float, default=1.0, help="Delay between release date lookups.")
    parser.add_argument("--force-release", action="store_true", help="Re-check albums that already have dates.")
    parser.add_argument("--lyrics-limit", type=int, default=0, help="Maximum songs to process; 0 means all.")
    parser.add_argument("--lyrics-timeout", type=int, default=240)
    parser.add_argument("--lyrics-sleep", type=float, default=0.5)
    parser.add_argument("--lyrics-min-score", type=float, default=55.0)
    parser.add_argument("--lyrics-sources", default="QM,KG,NE,LRCLIB")
    parser.add_argument("--lddc-python", help="Override MUSIC_WEB_LDDC_PYTHON.")
    parser.add_argument("--lddc-src", type=Path, help="Override MUSIC_WEB_LDDC_SRC_PATH.")
    parser.add_argument("--lddc-script", type=Path, help="Override MUSIC_WEB_LDDC_SCRIPT_PATH.")
    parser.add_argument("--retry-failed-lyrics", action="store_true")
    return parser.parse_args()


def split_csv(value: str) -> list[str]:
    return [part.strip() for part in value.split(",") if part.strip()]


def read_env_file(path: Path) -> dict[str, str]:
    values: dict[str, str] = {}
    if not path.is_file():
        return values
    for raw_line in path.read_text(encoding="utf-8").splitlines():
        line = raw_line.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        key, value = line.split("=", 1)
        value = value.strip()
        if len(value) >= 2 and value[0] == value[-1] and value[0] in {"'", '"'}:
            value = value[1:-1]
        values[key.strip()] = value
    return values


def mysql_command(config: dict[str, str], database: str) -> tuple[list[str], dict[str, str]]:
    host = config.get("MYSQL_HOST", "localhost")
    port = config.get("MYSQL_PORT", "3306")
    user = config.get("MYSQL_USERNAME") or config.get("MYSQL_USER") or "root"
    password = config.get("MYSQL_PASSWORD", "")
    command = [
        "mysql",
        "--default-character-set=utf8mb4",
        "--batch",
        "--raw",
        "--skip-column-names",
        "-h",
        host,
        "-P",
        str(port),
        "-u",
        user,
        database,
    ]
    env = os.environ.copy()
    if password:
        env["MYSQL_PWD"] = password
    return command, env


def run_mysql(config: dict[str, str], database: str, sql: str) -> str:
    command, env = mysql_command(config, database)
    completed = subprocess.run(
        command,
        input=sql,
        text=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        env=env,
        check=False,
    )
    if completed.returncode != 0:
        raise RuntimeError(f"mysql failed: {completed.stderr.strip()}")
    return completed.stdout


def fetch_json_rows(config: dict[str, str], database: str, sql: str) -> list[dict[str, Any]]:
    rows: list[dict[str, Any]] = []
    for line in run_mysql(config, database, sql).splitlines():
        if line.strip():
            rows.append(json.loads(line))
    return rows


def execute_statements(config: dict[str, str], database: str, statements: list[str], execute: bool) -> None:
    if not statements:
        return
    sql = "SET NAMES utf8mb4;\nSTART TRANSACTION;\n" + "\n".join(statements) + "\nCOMMIT;\n"
    if execute:
        run_mysql(config, database, sql)
    else:
        print(sql)


def sql_string(value: str | None) -> str:
    if value is None:
        return "NULL"
    escaped = value.replace("\\", "\\\\").replace("'", "''")
    return f"'{escaped}'"


def safe_ascii_name(value: str, fallback: str) -> str:
    cleaned = re.sub(r"[^A-Za-z0-9]+", "-", value).strip("-").lower()
    cleaned = cleaned[:42].strip("-")
    return cleaned or fallback


def short_hash(value: str) -> str:
    return hashlib.sha1(value.encode("utf-8")).hexdigest()[:10]


def is_cjk(char: str) -> bool:
    code = ord(char)
    return (
        0x3400 <= code <= 0x4DBF
        or 0x4E00 <= code <= 0x9FFF
        or 0xF900 <= code <= 0xFAFF
        or 0x3040 <= code <= 0x30FF
        or 0xAC00 <= code <= 0xD7AF
    )


def initials(value: str, fallback: str = "MS") -> str:
    cjk = [char for char in value if is_cjk(char)]
    if cjk:
        return "".join(cjk[:2])
    words = re.findall(r"[A-Za-z0-9]+", value)
    if words:
        if len(words) == 1:
            return words[0][:3].upper()
        return "".join(word[0] for word in words[:3]).upper()
    visible = re.sub(r"\s+", "", value)
    return visible[:2] or fallback


PALETTES = [
    ("#8dc7ee", "#f3a6ad", "#b9d9c6", "#f7fbff", "#fbf7f4"),
    ("#9fc4f3", "#efb2c0", "#c9dfbb", "#f9fbff", "#fff7f8"),
    ("#a9d9d2", "#f0b5aa", "#a9c8ec", "#f8fcfb", "#fff8f4"),
    ("#c3d7a5", "#efb0b4", "#9fc8e7", "#fbfdf8", "#fff8f8"),
    ("#bad5ee", "#f1c0a7", "#b8dccb", "#f8fbfe", "#fffaf6"),
]


def palette_for(value: str) -> tuple[str, str, str, str, str]:
    index = int(hashlib.sha1(value.encode("utf-8")).hexdigest()[:8], 16) % len(PALETTES)
    return PALETTES[index]


def svg_text(value: str) -> str:
    return html.escape(value, quote=False)


def glass_font_size(mark: str, base: int) -> int:
    length = max(len(mark), 1)
    if any(is_cjk(char) for char in mark):
        if length <= 1:
            return base + 20
        if length == 2:
            return base - 40
        return base - 72
    if length <= 1:
        return base + 64
    if length == 2:
        return base
    return base - 44


def album_svg(title: str, artist: str) -> str:
    cool, warm, herb, paper, mist = palette_for(f"album:{artist}:{title}")
    mark = svg_text(initials(title))
    label = svg_text(title[:42])
    font_size = glass_font_size(mark, 242)
    return f"""<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 640 640" role="img" aria-label="{label}">
  <defs>
    <linearGradient id="paper" x1="64" y1="40" x2="596" y2="612" gradientUnits="userSpaceOnUse">
      <stop offset="0" stop-color="#ffffff"/>
      <stop offset="0.55" stop-color="{paper}"/>
      <stop offset="1" stop-color="{mist}"/>
    </linearGradient>
    <linearGradient id="coolWash" x1="0" y1="0" x2="1" y2="1">
      <stop offset="0" stop-color="{cool}" stop-opacity="0.42"/>
      <stop offset="0.54" stop-color="{cool}" stop-opacity="0.08"/>
      <stop offset="1" stop-color="{cool}" stop-opacity="0"/>
    </linearGradient>
    <linearGradient id="warmWash" x1="1" y1="0" x2="0" y2="1">
      <stop offset="0" stop-color="{warm}" stop-opacity="0.34"/>
      <stop offset="0.58" stop-color="{warm}" stop-opacity="0.08"/>
      <stop offset="1" stop-color="{warm}" stop-opacity="0"/>
    </linearGradient>
    <linearGradient id="letterFill" x1="122" y1="120" x2="520" y2="506" gradientUnits="userSpaceOnUse">
      <stop offset="0" stop-color="#ffffff" stop-opacity="0.76"/>
      <stop offset="0.38" stop-color="{cool}" stop-opacity="0.34"/>
      <stop offset="0.68" stop-color="{warm}" stop-opacity="0.28"/>
      <stop offset="1" stop-color="{herb}" stop-opacity="0.32"/>
    </linearGradient>
    <filter id="softShadow" x="-25%" y="-25%" width="150%" height="150%">
      <feDropShadow dx="0" dy="30" stdDeviation="24" flood-color="#9aa8b5" flood-opacity="0.24"/>
    </filter>
    <filter id="glassBlur" x="-18%" y="-18%" width="136%" height="136%">
      <feGaussianBlur stdDeviation="1.4"/>
    </filter>
  </defs>
  <rect width="640" height="640" rx="58" fill="url(#paper)"/>
  <rect x="22" y="22" width="596" height="596" rx="54" fill="#ffffff" opacity="0.48"/>
  <rect x="22" y="22" width="596" height="596" rx="54" fill="url(#coolWash)"/>
  <rect x="22" y="22" width="596" height="596" rx="54" fill="url(#warmWash)"/>
  <rect x="62" y="64" width="516" height="512" rx="46" fill="#ffffff" opacity="0.28" stroke="#ffffff" stroke-opacity="0.82" stroke-width="2"/>
  <text x="320" y="388" text-anchor="middle" font-family="-apple-system,BlinkMacSystemFont,'SF Pro Display','PingFang SC','Microsoft YaHei',Arial,sans-serif" font-size="{font_size}" font-weight="820" letter-spacing="0" fill="url(#letterFill)" stroke="#ffffff" stroke-opacity="0.72" stroke-width="18" paint-order="stroke fill" filter="url(#softShadow)">{mark}</text>
  <text x="320" y="388" text-anchor="middle" font-family="-apple-system,BlinkMacSystemFont,'SF Pro Display','PingFang SC','Microsoft YaHei',Arial,sans-serif" font-size="{font_size}" font-weight="820" letter-spacing="0" fill="none" stroke="{cool}" stroke-opacity="0.45" stroke-width="7" filter="url(#glassBlur)">{mark}</text>
  <text x="316" y="379" text-anchor="middle" font-family="-apple-system,BlinkMacSystemFont,'SF Pro Display','PingFang SC','Microsoft YaHei',Arial,sans-serif" font-size="{font_size}" font-weight="820" letter-spacing="0" fill="none" stroke="#ffffff" stroke-opacity="0.72" stroke-width="5">{mark}</text>
  <path d="M126 128C188 92 268 82 352 98" fill="none" stroke="#ffffff" stroke-opacity="0.64" stroke-width="5" stroke-linecap="round"/>
  <path d="M130 510C214 548 354 556 486 508" fill="none" stroke="{warm}" stroke-opacity="0.18" stroke-width="10" stroke-linecap="round"/>
</svg>
"""


def artist_svg(name: str) -> str:
    cool, warm, herb, paper, mist = palette_for(f"artist:{name}")
    mark = svg_text(initials(name))
    label = svg_text(name[:38])
    font_size = glass_font_size(mark, 220)
    return f"""<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 640 640" role="img" aria-label="{label}">
  <defs>
    <linearGradient id="paper" x1="82" y1="48" x2="560" y2="594" gradientUnits="userSpaceOnUse">
      <stop offset="0" stop-color="#ffffff"/>
      <stop offset="0.55" stop-color="{paper}"/>
      <stop offset="1" stop-color="{mist}"/>
    </linearGradient>
    <linearGradient id="coolWash" x1="0" y1="0" x2="1" y2="1">
      <stop offset="0" stop-color="{cool}" stop-opacity="0.4"/>
      <stop offset="0.56" stop-color="{cool}" stop-opacity="0.08"/>
      <stop offset="1" stop-color="{cool}" stop-opacity="0"/>
    </linearGradient>
    <linearGradient id="herbWash" x1="1" y1="0" x2="0" y2="1">
      <stop offset="0" stop-color="{herb}" stop-opacity="0.34"/>
      <stop offset="0.58" stop-color="{herb}" stop-opacity="0.08"/>
      <stop offset="1" stop-color="{herb}" stop-opacity="0"/>
    </linearGradient>
    <linearGradient id="letterFill" x1="136" y1="122" x2="506" y2="512" gradientUnits="userSpaceOnUse">
      <stop offset="0" stop-color="#ffffff" stop-opacity="0.76"/>
      <stop offset="0.42" stop-color="{cool}" stop-opacity="0.34"/>
      <stop offset="0.72" stop-color="{warm}" stop-opacity="0.24"/>
      <stop offset="1" stop-color="{herb}" stop-opacity="0.3"/>
    </linearGradient>
    <filter id="softShadow" x="-25%" y="-25%" width="150%" height="150%">
      <feDropShadow dx="0" dy="30" stdDeviation="24" flood-color="#9aa8b5" flood-opacity="0.24"/>
    </filter>
    <filter id="glassBlur" x="-18%" y="-18%" width="136%" height="136%">
      <feGaussianBlur stdDeviation="1.4"/>
    </filter>
  </defs>
  <rect width="640" height="640" fill="url(#paper)"/>
  <circle cx="320" cy="320" r="298" fill="#ffffff" opacity="0.5"/>
  <circle cx="320" cy="320" r="298" fill="url(#coolWash)"/>
  <circle cx="320" cy="320" r="298" fill="url(#herbWash)"/>
  <circle cx="320" cy="320" r="240" fill="#ffffff" opacity="0.28" stroke="#ffffff" stroke-opacity="0.84" stroke-width="2"/>
  <text x="320" y="386" text-anchor="middle" font-family="-apple-system,BlinkMacSystemFont,'SF Pro Display','PingFang SC','Microsoft YaHei',Arial,sans-serif" font-size="{font_size}" font-weight="820" letter-spacing="0" fill="url(#letterFill)" stroke="#ffffff" stroke-opacity="0.72" stroke-width="17" paint-order="stroke fill" filter="url(#softShadow)">{mark}</text>
  <text x="320" y="386" text-anchor="middle" font-family="-apple-system,BlinkMacSystemFont,'SF Pro Display','PingFang SC','Microsoft YaHei',Arial,sans-serif" font-size="{font_size}" font-weight="820" letter-spacing="0" fill="none" stroke="{cool}" stroke-opacity="0.44" stroke-width="7" filter="url(#glassBlur)">{mark}</text>
  <text x="316" y="377" text-anchor="middle" font-family="-apple-system,BlinkMacSystemFont,'SF Pro Display','PingFang SC','Microsoft YaHei',Arial,sans-serif" font-size="{font_size}" font-weight="820" letter-spacing="0" fill="none" stroke="#ffffff" stroke-opacity="0.72" stroke-width="5">{mark}</text>
  <path d="M142 168C196 118 286 96 378 112" fill="none" stroke="#ffffff" stroke-opacity="0.62" stroke-width="5" stroke-linecap="round"/>
  <path d="M158 494C238 540 386 550 494 486" fill="none" stroke="{warm}" stroke-opacity="0.18" stroke-width="10" stroke-linecap="round"/>
</svg>
"""


def load_cover_results(path: Path | None) -> set[str]:
    if not path or not path.is_file():
        return set()
    data = json.loads(path.read_text(encoding="utf-8"))
    missing: set[str] = set()
    for key, value in data.items():
        if not isinstance(value, dict):
            continue
        if not value.get("source") or not value.get("artwork_url"):
            missing.add(key)
    return missing


def query_artists(config: dict[str, str], database: str) -> list[dict[str, Any]]:
    return fetch_json_rows(
        config,
        database,
        """
        SELECT JSON_OBJECT(
          'id', id,
          'name', name,
          'bio', bio,
          'avatar_url', avatar_url
        )
        FROM artist
        ORDER BY id;
        """,
    )


def query_albums(config: dict[str, str], database: str, force_release: bool = False) -> list[dict[str, Any]]:
    where = "1=1" if force_release else "a.release_date IS NULL"
    return fetch_json_rows(
        config,
        database,
        f"""
        SELECT JSON_OBJECT(
          'id', a.id,
          'title', a.title,
          'artist_id', a.artist_id,
          'artist_name', ar.name,
          'cover_url', a.cover_url,
          'release_date', DATE_FORMAT(a.release_date, '%Y-%m-%d')
        )
        FROM album a
        JOIN artist ar ON ar.id = a.artist_id
        WHERE {where}
        ORDER BY a.id;
        """,
    )


def query_all_albums(config: dict[str, str], database: str) -> list[dict[str, Any]]:
    return fetch_json_rows(
        config,
        database,
        """
        SELECT JSON_OBJECT(
          'id', a.id,
          'title', a.title,
          'artist_id', a.artist_id,
          'artist_name', ar.name,
          'cover_url', a.cover_url,
          'release_date', DATE_FORMAT(a.release_date, '%Y-%m-%d')
        )
        FROM album a
        JOIN artist ar ON ar.id = a.artist_id
        ORDER BY a.id;
        """,
    )


def backfill_identity_art(args: argparse.Namespace, config: dict[str, str], database: str) -> dict[str, int]:
    artists = query_artists(config, database)
    albums = query_all_albums(config, database)
    missing_real_cover_keys = load_cover_results(args.cover_results)
    artist_dir = args.media_root / "artist"
    cover_dir = args.media_root / "cover"
    statements: list[str] = []
    artist_count = 0
    album_count = 0

    if args.execute:
        artist_dir.mkdir(parents=True, exist_ok=True)
        cover_dir.mkdir(parents=True, exist_ok=True)

    for artist in artists:
        artist_id = int(artist["id"])
        name = str(artist["name"] or "")
        file_name = f"artist-{artist_id}-{safe_ascii_name(name, short_hash(name))}.svg"
        path = artist_dir / file_name
        if args.execute:
            path.write_text(artist_svg(name), encoding="utf-8", newline="\n")
        url = f"/media/artist/{quote(file_name)}"
        statements.append(f"UPDATE artist SET avatar_url = {sql_string(url)} WHERE id = {artist_id};")
        artist_count += 1

    album_ids: list[int] = []
    for album in albums:
        album_id = int(album["id"])
        title = str(album["title"] or "")
        artist_name = str(album["artist_name"] or "")
        key = f"{artist_name}\u241f{title}"
        has_cover = bool(album.get("cover_url"))
        should_generate = (
            args.album_art_mode == "all"
            or (args.album_art_mode == "missing" and not has_cover)
            or (args.album_art_mode == "fallback" and (not has_cover or key in missing_real_cover_keys))
        )
        if not should_generate:
            continue

        file_name = f"album-{album_id}-{safe_ascii_name(title, short_hash(key))}.svg"
        path = cover_dir / file_name
        if args.execute:
            path.write_text(album_svg(title, artist_name), encoding="utf-8", newline="\n")
        url = f"/media/cover/{quote(file_name)}"
        statements.append(f"UPDATE album SET cover_url = {sql_string(url)} WHERE id = {album_id};")
        album_ids.append(album_id)
        album_count += 1

    if album_ids:
        ids = ",".join(str(item) for item in album_ids)
        statements.append(
            "UPDATE song s JOIN album a ON a.id = s.album_id "
            f"SET s.cover_url = a.cover_url WHERE s.album_id IN ({ids});"
        )

    execute_statements(config, database, statements, args.execute)
    return {"artists": artist_count, "albums": album_count}


def normalize(value: str) -> str:
    value = value.lower()
    value = re.sub(r"[\[\(（【].*?[\]\)）】]", " ", value)
    value = re.sub(r"\b(deluxe|explicit|single|ep|version|edition|remaster(?:ed)?)\b", " ", value)
    value = re.sub(r"[^0-9a-z\u3400-\u9fff\u3040-\u30ff\uac00-\ud7af]+", " ", value)
    return re.sub(r"\s+", " ", value).strip()


def similarity(left: str, right: str) -> float:
    left_norm = normalize(left)
    right_norm = normalize(right)
    if not left_norm or not right_norm:
        return 0.0
    if left_norm == right_norm:
        return 1.0
    if left_norm in right_norm or right_norm in left_norm:
        return 0.88
    return difflib.SequenceMatcher(None, left_norm, right_norm).ratio()


def http_json(url: str, headers: dict[str, str] | None = None) -> dict[str, Any]:
    request = Request(url, headers=headers or {"User-Agent": MUSICBRAINZ_USER_AGENT})
    with urlopen(request, timeout=HTTP_TIMEOUT_SECONDS) as response:
        return json.loads(response.read().decode("utf-8"))


def parse_date(value: str | None) -> str | None:
    if not value:
        return None
    match = re.match(r"^(\d{4})(?:-(\d{2})(?:-(\d{2}))?)?", value)
    if not match:
        return None
    year = int(match.group(1))
    if year < 1900 or year > dt.date.today().year + 1:
        return None
    month = int(match.group(2) or "1")
    day = int(match.group(3) or "1")
    try:
        return dt.date(year, month, day).isoformat()
    except ValueError:
        return None


def lookup_itunes(album: dict[str, Any]) -> dict[str, Any] | None:
    artist = str(album["artist_name"])
    title = str(album["title"])
    best: dict[str, Any] | None = None
    for country in ("CN", "TW", "HK", "US", "JP"):
        params = urlencode(
            {
                "term": f"{artist} {title}",
                "media": "music",
                "entity": "album",
                "limit": "12",
                "country": country,
            }
        )
        data = http_json(f"https://itunes.apple.com/search?{params}", {"User-Agent": MUSICBRAINZ_USER_AGENT})
        for item in data.get("results", []):
            date = parse_date(item.get("releaseDate"))
            if not date:
                continue
            score = similarity(title, str(item.get("collectionName", ""))) * 0.72
            score += similarity(artist, str(item.get("artistName", ""))) * 0.28
            if best is None or score > best["score"]:
                best = {
                    "date": date,
                    "source": f"itunes:{country}",
                    "score": round(score, 4),
                    "matched_album": item.get("collectionName"),
                    "matched_artist": item.get("artistName"),
                }
        if best and best["score"] >= 0.86:
            return best
    if best and best["score"] >= 0.72:
        return best
    return None


def lookup_musicbrainz(album: dict[str, Any]) -> dict[str, Any] | None:
    artist = str(album["artist_name"])
    title = str(album["title"])
    query = f'releasegroup:"{title}" AND artist:"{artist}"'
    params = urlencode({"query": query, "fmt": "json", "limit": "10"})
    data = http_json(
        f"https://musicbrainz.org/ws/2/release-group/?{params}",
        {"User-Agent": MUSICBRAINZ_USER_AGENT},
    )
    best: dict[str, Any] | None = None
    for item in data.get("release-groups", []):
        date = parse_date(item.get("first-release-date"))
        if not date:
            continue
        mb_score = float(item.get("score") or 0.0) / 100.0
        title_score = similarity(title, str(item.get("title", "")))
        artist_credit = " ".join(part.get("name", "") for part in item.get("artist-credit", []) if isinstance(part, dict))
        artist_score = similarity(artist, artist_credit)
        score = max(mb_score, title_score * 0.72 + artist_score * 0.28)
        if best is None or score > best["score"]:
            best = {
                "date": date,
                "source": "musicbrainz",
                "score": round(score, 4),
                "matched_album": item.get("title"),
                "matched_artist": artist_credit,
            }
    if best and best["score"] >= 0.70:
        return best
    return None


def lookup_netease(album: dict[str, Any]) -> dict[str, Any] | None:
    artist = str(album["artist_name"])
    title = str(album["title"])
    params = urlencode({"s": f"{artist} {title}", "type": "10", "offset": "0", "limit": "12"})
    data = http_json(
        f"https://music.163.com/api/search/get/web?{params}",
        {
            "User-Agent": MUSICBRAINZ_USER_AGENT,
            "Referer": "https://music.163.com/",
        },
    )
    result = data.get("result", {})
    if not isinstance(result, dict):
        return None
    albums = result.get("albums", [])
    if not isinstance(albums, list):
        return None

    best: dict[str, Any] | None = None
    for item in albums:
        if not isinstance(item, dict):
            continue
        publish_time = item.get("publishTime")
        if not publish_time:
            continue
        date = dt.datetime.utcfromtimestamp(int(publish_time) / 1000).date().isoformat()
        raw_artist = item.get("artist")
        if isinstance(raw_artist, dict):
            item_artist = str(raw_artist.get("name") or "")
        elif isinstance(raw_artist, list):
            item_artist = "/".join(str(part.get("name") or "") for part in raw_artist if isinstance(part, dict))
        else:
            item_artist = str(raw_artist or "")
        score = similarity(title, str(item.get("name", ""))) * 0.72 + similarity(artist, item_artist) * 0.28
        if best is None or score > best["score"]:
            best = {
                "date": date,
                "source": "netease",
                "score": round(score, 4),
                "matched_album": item.get("name"),
                "matched_artist": item_artist,
            }
    if best and best["score"] >= 0.72:
        return best
    return None


def title_year_fallback(album: dict[str, Any]) -> dict[str, Any] | None:
    title = str(album["title"])
    matches = re.findall(r"(?<!\d)(19\d{2}|20\d{2})(?!\d)", title)
    for raw_year in matches:
        year = int(raw_year)
        if 1900 <= year <= dt.date.today().year + 1:
            return {
                "date": f"{year:04d}-01-01",
                "source": "title-year",
                "score": 0.50,
                "matched_album": title,
                "matched_artist": album["artist_name"],
            }
    return None


def load_state(path: Path) -> dict[str, Any]:
    if not path.is_file():
        return {}
    return json.loads(path.read_text(encoding="utf-8"))


def save_state(path: Path, data: dict[str, Any]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, ensure_ascii=False, indent=2, sort_keys=True), encoding="utf-8")


def backfill_release_dates(args: argparse.Namespace, config: dict[str, str], database: str) -> dict[str, int]:
    albums = query_albums(config, database, force_release=args.force_release)
    if args.release_limit > 0:
        albums = albums[: args.release_limit]
    sources = split_csv(args.release_sources)
    state_path = args.state_dir / "release_dates.json"
    state = load_state(state_path)
    statements: list[str] = []
    checked = 0
    updated = 0
    missing = 0

    lookups = {
        "itunes": lookup_itunes,
        "musicbrainz": lookup_musicbrainz,
        "netease": lookup_netease,
    }

    for album in albums:
        album_id = int(album["id"])
        cache_key = f"{album['artist_name']}\u241f{album['title']}"
        cached = state.get(cache_key)
        result = cached if cached and cached.get("date") else None
        if result is None:
            for source in sources:
                lookup = lookups.get(source)
                if lookup is None:
                    continue
                try:
                    result = lookup(album)
                except Exception as exc:
                    state[f"{cache_key}\u241f{source}:error"] = str(exc)
                    result = None
                checked += 1
                if result:
                    break
                if args.release_sleep > 0:
                    time.sleep(args.release_sleep)
            if result is None:
                result = title_year_fallback(album)
            state[cache_key] = result or {"date": None, "source": "not-found"}
            if args.execute:
                save_state(state_path, state)

        if result and result.get("date"):
            statements.append(
                "UPDATE album "
                f"SET release_date = {sql_string(str(result['date']))} "
                f"WHERE id = {album_id};"
            )
            updated += 1
        else:
            missing += 1

    execute_statements(config, database, statements, args.execute)
    if args.execute:
        save_state(state_path, state)
    return {"albums_checked": checked, "albums_updated": updated, "albums_missing": missing}


def query_songs_without_lyrics(config: dict[str, str], database: str) -> list[dict[str, Any]]:
    return fetch_json_rows(
        config,
        database,
        """
        SELECT JSON_OBJECT(
          'id', s.id,
          'title', s.title,
          'artist_name', ar.name,
          'album_title', al.title,
          'audio_url', s.audio_url,
          'duration_seconds', s.duration_seconds
        )
        FROM song s
        JOIN artist ar ON ar.id = s.artist_id
        LEFT JOIN album al ON al.id = s.album_id
        WHERE s.lyric_url IS NULL OR s.lyric_url = ''
        ORDER BY s.id;
        """,
    )


def media_path_from_url(media_root: Path, url: str | None) -> Path | None:
    if not url:
        return None
    marker = "/media/"
    if marker not in url:
        return None
    relative = unquote(url.split(marker, 1)[1].lstrip("/"))
    path = (media_root / relative).resolve()
    root = media_root.resolve()
    try:
        path.relative_to(root)
    except ValueError:
        return None
    return path


def extract_json_object(stdout: str) -> dict[str, Any]:
    start = stdout.find("{")
    end = stdout.rfind("}")
    if start < 0 or end < start:
        raise ValueError("No JSON object in LDDC stdout")
    return json.loads(stdout[start : end + 1])


def backfill_lyrics(args: argparse.Namespace, config: dict[str, str], database: str) -> dict[str, int]:
    songs = query_songs_without_lyrics(config, database)
    if args.lyrics_limit > 0:
        songs = songs[: args.lyrics_limit]
    state_path = args.state_dir / "lyrics.json"
    state = load_state(state_path)
    lyrics_dir = args.media_root / "lyrics"
    lddc_python = args.lddc_python or config.get("MUSIC_WEB_LDDC_PYTHON") or sys.executable
    lddc_script = args.lddc_script or Path(config.get("MUSIC_WEB_LDDC_SCRIPT_PATH") or DEFAULT_LDDC_SCRIPT)
    lddc_src = args.lddc_src or Path(config.get("MUSIC_WEB_LDDC_SRC_PATH") or "")
    statements: list[str] = []
    attempted = 0
    succeeded = 0
    failed = 0
    skipped = 0

    if args.execute:
        lyrics_dir.mkdir(parents=True, exist_ok=True)

    for song in songs:
        song_id = int(song["id"])
        key = str(song_id)
        prior = state.get(key)
        if prior and prior.get("status") == "ok":
            skipped += 1
            continue
        if prior and prior.get("status") == "failed" and not args.retry_failed_lyrics:
            skipped += 1
            continue

        title = str(song["title"] or "")
        artist = str(song["artist_name"] or "")
        album = str(song.get("album_title") or "")
        file_name = f"song-{song_id}-{safe_ascii_name(f'{artist}-{title}', short_hash(f'{artist}:{title}'))}.lrc"
        output = lyrics_dir / file_name
        lyric_url = f"/media/lyrics/{quote(file_name)}"
        audio_path = media_path_from_url(args.media_root, song.get("audio_url"))
        duration = song.get("duration_seconds")
        command = [
            str(lddc_python),
            str(lddc_script),
            "--title",
            title,
            "--artist",
            artist,
            "--album",
            album,
            "--lyrics-dir",
            str(lyrics_dir),
            "--output",
            str(output),
            "--force",
            "--format",
            "enhanced-lrc",
            "--sources",
            args.lyrics_sources,
            "--min-score",
            str(args.lyrics_min_score),
        ]
        if duration is not None:
            command.extend(["--duration-ms", str(int(duration) * 1000)])
        if audio_path and audio_path.is_file():
            command.extend(["--audio-file", str(audio_path)])
        if str(lddc_src):
            command.extend(["--lddc-src", str(lddc_src)])

        if not args.execute:
            print(" ".join(quote(part) for part in command))
            attempted += 1
            continue

        env = os.environ.copy()
        env.setdefault("QT_QPA_PLATFORM", "offscreen")
        env.setdefault("HOME", "/opt/melospace")
        attempted += 1
        try:
            completed = subprocess.run(
                command,
                text=True,
                stdout=subprocess.PIPE,
                stderr=subprocess.PIPE,
                timeout=args.lyrics_timeout,
                env=env,
                check=False,
            )
        except subprocess.TimeoutExpired as exc:
            state[key] = {
                "status": "failed",
                "title": title,
                "artist": artist,
                "stderr": f"timeout after {args.lyrics_timeout}s",
                "stdout": (exc.stdout or "")[-1200:] if isinstance(exc.stdout, str) else "",
                "returncode": "timeout",
            }
            failed += 1
            save_state(state_path, state)
            if args.lyrics_sleep > 0:
                time.sleep(args.lyrics_sleep)
            continue
        if completed.returncode == 0 and output.is_file() and output.stat().st_size > 0:
            try:
                result = extract_json_object(completed.stdout)
            except Exception:
                result = {}
            statements.append(f"UPDATE song SET lyric_url = {sql_string(lyric_url)} WHERE id = {song_id};")
            execute_statements(config, database, statements, True)
            statements.clear()
            state[key] = {
                "status": "ok",
                "url": lyric_url,
                "source": result.get("source"),
                "matched_title": result.get("matched_title"),
                "matched_artist": result.get("matched_artist"),
            }
            succeeded += 1
        else:
            state[key] = {
                "status": "failed",
                "title": title,
                "artist": artist,
                "stderr": completed.stderr[-1200:],
                "stdout": completed.stdout[-1200:],
                "returncode": completed.returncode,
            }
            failed += 1
        save_state(state_path, state)
        if args.lyrics_sleep > 0:
            time.sleep(args.lyrics_sleep)

    return {"songs_attempted": attempted, "songs_succeeded": succeeded, "songs_failed": failed, "songs_skipped": skipped}


def main() -> int:
    args = parse_args()
    config = read_env_file(args.env_file)
    database = args.database or config.get("MYSQL_DATABASE") or "music_web"
    tasks = set(split_csv(args.tasks))
    summaries: dict[str, dict[str, int]] = {}

    if "identity-art" in tasks:
        summaries["identity-art"] = backfill_identity_art(args, config, database)
    if "release-dates" in tasks:
        summaries["release-dates"] = backfill_release_dates(args, config, database)
    if "lyrics" in tasks:
        summaries["lyrics"] = backfill_lyrics(args, config, database)

    print(json.dumps({"execute": args.execute, "summaries": summaries}, ensure_ascii=False, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
