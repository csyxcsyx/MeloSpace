#!/usr/bin/env python3
"""Backfill MeloSpace catalog lyrics, artwork, and album dates.

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
        help="Comma separated tasks: audit-catalog,real-art,identity-art,release-dates,lyrics.",
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
        "--real-art-mode",
        choices=("missing", "all"),
        default="missing",
        help="Which artists/albums receive downloaded real artwork.",
    )
    parser.add_argument(
        "--real-art-sources",
        default="deezer,itunes,musicbrainz",
        help="Comma separated real artwork sources: deezer,itunes,musicbrainz.",
    )
    parser.add_argument("--real-art-limit", type=int, default=0, help="Maximum artists and albums to check; 0 means all.")
    parser.add_argument("--real-art-sleep", type=float, default=0.35, help="Delay between real artwork lookups.")
    parser.add_argument("--real-art-min-score", type=float, default=0.84, help="Minimum match score for downloaded artwork.")
    parser.add_argument("--audit-output", type=Path, help="Write catalog audit report JSON to this path.")
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


GBK_PINYIN_RANGES = [
    (1601, 1636, "A"),
    (1637, 1832, "B"),
    (1833, 2077, "C"),
    (2078, 2273, "D"),
    (2274, 2301, "E"),
    (2302, 2432, "F"),
    (2433, 2593, "G"),
    (2594, 2786, "H"),
    (2787, 3105, "J"),
    (3106, 3211, "K"),
    (3212, 3471, "L"),
    (3472, 3634, "M"),
    (3635, 3721, "N"),
    (3722, 3729, "O"),
    (3730, 3857, "P"),
    (3858, 4026, "Q"),
    (4027, 4085, "R"),
    (4086, 4390, "S"),
    (4391, 4557, "T"),
    (4558, 4683, "W"),
    (4684, 4924, "X"),
    (4925, 5248, "Y"),
    (5249, 5589, "Z"),
]


def hash_letters(value: str, length: int = 2) -> str:
    digest = hashlib.sha1(value.encode("utf-8")).digest()
    return "".join(chr(ord("A") + byte % 26) for byte in digest[:length])


def cjk_latin_initial(char: str) -> str:
    try:
        encoded = char.encode("gbk")
    except UnicodeEncodeError:
        return hash_letters(char, 1)
    if len(encoded) != 2:
        return hash_letters(char, 1)
    section_position = (encoded[0] - 160) * 100 + encoded[1] - 160
    for start, end, initial in GBK_PINYIN_RANGES:
        if start <= section_position <= end:
            return initial
    return hash_letters(char, 1)


def initials(value: str, fallback: str = "MS") -> str:
    matches = re.findall(r"[A-Za-z]+|[\u3400-\u4DBF\u4E00-\u9FFF\uF900-\uFAFF]", value)
    if len(matches) == 1 and re.fullmatch(r"[A-Za-z]+", matches[0]):
        return matches[0].upper()[:3]

    letters: list[str] = []
    for token in matches:
        if re.fullmatch(r"[A-Za-z]+", token):
            letters.append(token[0].upper())
        elif is_cjk(token):
            letters.append(cjk_latin_initial(token))

    result = re.sub(r"[^A-Z]", "", "".join(letters).upper())[:3]
    if result:
        return result
    fallback_result = re.sub(r"[^A-Z]", "", fallback.upper())[:3]
    return fallback_result or hash_letters(value or fallback, 2)


PALETTES = [
    ("#F5F8FF", "#2D5D83"),
    ("#FFF4F6", "#8E3E4C"),
    ("#F4FBF7", "#3F7151"),
    ("#FFFAEF", "#806034"),
    ("#F7F5FF", "#5D4B8A"),
    ("#F6F7F9", "#3B4657"),
]


def palette_for(value: str) -> tuple[str, str]:
    index = int(hashlib.sha1(value.encode("utf-8")).hexdigest()[:8], 16) % len(PALETTES)
    return PALETTES[index]


def svg_text(value: str) -> str:
    return html.escape(value, quote=False)


def is_generated_album_art_url(value: Any) -> bool:
    if not value:
        return False
    path = unquote(str(value).split("?", 1)[0])
    return path.startswith("/media/cover/album-") and path.endswith(".svg")


def is_generated_artist_art_url(value: Any) -> bool:
    if not value:
        return False
    path = unquote(str(value).split("?", 1)[0])
    return path.startswith("/media/artist/artist-") and path.endswith(".svg")


def has_real_url(value: Any, generated_checker) -> bool:
    return bool(value) and not generated_checker(value)


def solid_font_size(mark: str) -> int:
    length = max(len(mark), 1)
    if length <= 1:
        return 310
    if length == 2:
        return 262
    return 220


def album_svg(title: str, artist: str) -> str:
    background, foreground = palette_for(f"album:{artist}:{title}")
    mark = svg_text(initials(title))
    font_size = solid_font_size(mark)
    return f"""<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 640 640" role="img" aria-label="Album initials {mark}">
  <rect width="640" height="640" rx="58" fill="{background}"/>
  <text x="320" y="390" text-anchor="middle" font-family="-apple-system,BlinkMacSystemFont,'SF Pro Display','Segoe UI',Arial,sans-serif" font-size="{font_size}" font-weight="820" letter-spacing="0" fill="{foreground}">{mark}</text>
</svg>
"""


def artist_svg(name: str) -> str:
    background, foreground = palette_for(f"artist:{name}")
    mark = svg_text(initials(name))
    font_size = solid_font_size(mark)
    return f"""<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 640 640" role="img" aria-label="Artist initials {mark}">
  <rect width="640" height="640" fill="{background}"/>
  <text x="320" y="390" text-anchor="middle" font-family="-apple-system,BlinkMacSystemFont,'SF Pro Display','Segoe UI',Arial,sans-serif" font-size="{font_size}" font-weight="820" letter-spacing="0" fill="{foreground}">{mark}</text>
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


def query_all_songs(config: dict[str, str], database: str) -> list[dict[str, Any]]:
    return fetch_json_rows(
        config,
        database,
        """
        SELECT JSON_OBJECT(
          'id', s.id,
          'title', s.title,
          'artist_id', s.artist_id,
          'artist_name', ar.name,
          'album_id', s.album_id,
          'album_title', al.title,
          'album_artist_id', al.artist_id,
          'album_artist_name', album_ar.name,
          'cover_url', s.cover_url,
          'album_cover_url', al.cover_url,
          'audio_url', s.audio_url,
          'lyric_url', s.lyric_url,
          'duration_seconds', s.duration_seconds,
          'language', s.language,
          'genre', s.genre,
          'mood', s.mood,
          'play_count', s.play_count,
          'status', s.status
        )
        FROM song s
        LEFT JOIN artist ar ON ar.id = s.artist_id
        LEFT JOIN album al ON al.id = s.album_id
        LEFT JOIN artist album_ar ON album_ar.id = al.artist_id
        ORDER BY s.id;
        """,
    )


def query_catalog_counts(config: dict[str, str], database: str) -> dict[str, Any]:
    rows = fetch_json_rows(
        config,
        database,
        """
        SELECT JSON_OBJECT(
          'users', (SELECT COUNT(*) FROM `user`),
          'artists', (SELECT COUNT(*) FROM artist),
          'albums', (SELECT COUNT(*) FROM album),
          'songs', (SELECT COUNT(*) FROM song),
          'playlists', (SELECT COUNT(*) FROM playlist),
          'comments', (SELECT COUNT(*) FROM comment)
        );
        """,
    )
    return rows[0] if rows else {}


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
            or is_generated_album_art_url(album.get("cover_url"))
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


def http_bytes(url: str, headers: dict[str, str] | None = None) -> tuple[bytes, str]:
    request = Request(url, headers=headers or {"User-Agent": MUSICBRAINZ_USER_AGENT})
    with urlopen(request, timeout=HTTP_TIMEOUT_SECONDS) as response:
        content_type = response.headers.get("Content-Type", "").split(";", 1)[0].strip().lower()
        return response.read(), content_type


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


def image_extension(content_type: str, url: str) -> str:
    if content_type == "image/png":
        return ".png"
    if content_type == "image/webp":
        return ".webp"
    if content_type in {"image/jpeg", "image/jpg"}:
        return ".jpg"
    suffix = Path(unquote(url.split("?", 1)[0])).suffix.lower()
    if suffix in {".jpg", ".jpeg", ".png", ".webp"}:
        return ".jpg" if suffix == ".jpeg" else suffix
    return ".jpg"


def upscale_itunes_artwork(url: str) -> str:
    return re.sub(r"/\d+x\d+bb\.(jpg|png|webp)$", r"/1200x1200bb.\1", url)


def deezer_is_default_image(url: str | None) -> bool:
    if not url:
        return True
    return "5639395138885805" in url or "/images/artist//" in url or "/images/cover//" in url


def lookup_deezer_artist_image(artist: dict[str, Any]) -> dict[str, Any] | None:
    name = str(artist["name"] or "")
    params = urlencode({"q": name, "limit": "8"})
    data = http_json(f"https://api.deezer.com/search/artist?{params}", {"User-Agent": MUSICBRAINZ_USER_AGENT})
    best: dict[str, Any] | None = None
    for item in data.get("data", []):
        if not isinstance(item, dict):
            continue
        image_url = item.get("picture_xl") or item.get("picture_big") or item.get("picture_medium")
        if deezer_is_default_image(image_url):
            continue
        score = similarity(name, str(item.get("name", "")))
        if best is None or score > best["score"]:
            best = {
                "artwork_url": image_url,
                "source": "deezer:artist",
                "score": round(score, 4),
                "matched_artist": item.get("name"),
                "source_url": item.get("link"),
            }
    if best and best["score"] >= 0.84:
        return best
    return None


def lookup_deezer_album_art(album: dict[str, Any]) -> dict[str, Any] | None:
    artist = str(album["artist_name"] or "")
    title = str(album["title"] or "")
    queries = [
        f'artist:"{artist}" album:"{title}"',
        f"{artist} {title}",
    ]
    best: dict[str, Any] | None = None
    for query in queries:
        params = urlencode({"q": query, "limit": "10"})
        data = http_json(f"https://api.deezer.com/search/album?{params}", {"User-Agent": MUSICBRAINZ_USER_AGENT})
        for item in data.get("data", []):
            if not isinstance(item, dict):
                continue
            image_url = item.get("cover_xl") or item.get("cover_big") or item.get("cover_medium")
            if deezer_is_default_image(image_url):
                continue
            item_artist = ""
            if isinstance(item.get("artist"), dict):
                item_artist = str(item["artist"].get("name") or "")
            score, title_score, artist_score = album_match_score(title, artist, str(item.get("title", "")), item_artist)
            if title_score < 0.72 or artist_score < 0.60:
                continue
            if best is None or score > best["score"]:
                best = {
                    "artwork_url": image_url,
                    "source": "deezer:album",
                    "score": round(score, 4),
                    "title_score": round(title_score, 4),
                    "artist_score": round(artist_score, 4),
                    "matched_album": item.get("title"),
                    "matched_artist": item_artist,
                    "source_url": item.get("link"),
                }
        if best and best["score"] >= 0.88:
            return best
    if best and best["score"] >= 0.84:
        return best
    return None


def lookup_itunes_album_art(album: dict[str, Any]) -> dict[str, Any] | None:
    artist = str(album["artist_name"] or "")
    title = str(album["title"] or "")
    best: dict[str, Any] | None = None
    for country in ("CN", "TW", "HK", "SG", "US", "JP"):
        params = urlencode(
            {
                "term": f"{artist} {title}",
                "media": "music",
                "entity": "album",
                "limit": "16",
                "country": country,
            }
        )
        data = http_json(f"https://itunes.apple.com/search?{params}", {"User-Agent": MUSICBRAINZ_USER_AGENT})
        for item in data.get("results", []):
            artwork_url = item.get("artworkUrl100")
            if not artwork_url:
                continue
            score, title_score, artist_score = album_match_score(
                title,
                artist,
                str(item.get("collectionName", "")),
                str(item.get("artistName", "")),
            )
            if title_score < 0.72 or artist_score < 0.60:
                continue
            if best is None or score > best["score"]:
                best = {
                    "artwork_url": upscale_itunes_artwork(str(artwork_url)),
                    "source": f"itunes:{country}:album",
                    "score": round(score, 4),
                    "title_score": round(title_score, 4),
                    "artist_score": round(artist_score, 4),
                    "matched_album": item.get("collectionName"),
                    "matched_artist": item.get("artistName"),
                    "source_url": item.get("collectionViewUrl"),
                }
        if best and best["score"] >= 0.88:
            return best
    if best and best["score"] >= 0.84:
        return best
    return None


def album_match_score(title: str, artist: str, matched_title: str, matched_artist: str) -> tuple[float, float, float]:
    title_score = similarity(title, matched_title)
    artist_score = similarity(artist, matched_artist)
    score = title_score * 0.72 + artist_score * 0.28
    return score, title_score, artist_score


def lookup_musicbrainz_album_art(album: dict[str, Any]) -> dict[str, Any] | None:
    artist = str(album["artist_name"] or "")
    title = str(album["title"] or "")
    params = urlencode({"query": f'releasegroup:"{title}" AND artist:"{artist}"', "fmt": "json", "limit": "10"})
    data = http_json(
        f"https://musicbrainz.org/ws/2/release-group/?{params}",
        {"User-Agent": MUSICBRAINZ_USER_AGENT},
    )
    best: dict[str, Any] | None = None
    for item in data.get("release-groups", []):
        mbid = item.get("id")
        if not mbid:
            continue
        artist_credit = " ".join(part.get("name", "") for part in item.get("artist-credit", []) if isinstance(part, dict))
        mb_score = float(item.get("score") or 0.0) / 100.0
        combined_score, title_score, artist_score = album_match_score(title, artist, str(item.get("title", "")), artist_credit)
        if title_score < 0.72 or artist_score < 0.60:
            continue
        score = max(mb_score, combined_score)
        if best is None or score > best["score"]:
            best = {
                "artwork_url": f"https://coverartarchive.org/release-group/{mbid}/front-500",
                "source": "musicbrainz:cover-art-archive",
                "score": round(score, 4),
                "title_score": round(title_score, 4),
                "artist_score": round(artist_score, 4),
                "matched_album": item.get("title"),
                "matched_artist": artist_credit,
                "source_url": f"https://musicbrainz.org/release-group/{mbid}",
            }
    if not best or best["score"] < 0.84:
        return None
    try:
        image, content_type = http_bytes(str(best["artwork_url"]), {"User-Agent": MUSICBRAINZ_USER_AGENT})
    except Exception:
        return None
    if not content_type.startswith("image/") or len(image) < 1024:
        return None
    best["prefetched_bytes"] = image
    best["prefetched_content_type"] = content_type
    return best


def download_artwork(url: str, output_dir: Path, file_stem: str, execute: bool, prefetched: bytes | None = None, content_type: str = "") -> tuple[str, Path | None]:
    image = prefetched
    if image is None:
        image, content_type = http_bytes(url, {"User-Agent": MUSICBRAINZ_USER_AGENT})
    if not content_type.startswith("image/") or len(image) < 1024:
        raise ValueError(f"not an image response: {content_type or 'unknown'}")
    extension = image_extension(content_type, url)
    file_name = f"{file_stem}-{hashlib.sha1(image).hexdigest()[:10]}{extension}"
    output_path = output_dir / file_name
    if execute:
        output_dir.mkdir(parents=True, exist_ok=True)
        output_path.write_bytes(image)
    return file_name, output_path if execute else None


def backfill_real_art(args: argparse.Namespace, config: dict[str, str], database: str) -> dict[str, int]:
    artists = query_artists(config, database)
    albums = query_all_albums(config, database)
    if args.real_art_mode == "missing":
        artists = [artist for artist in artists if not has_real_url(artist.get("avatar_url"), is_generated_artist_art_url)]
        albums = [album for album in albums if not has_real_url(album.get("cover_url"), is_generated_album_art_url)]
    if args.real_art_limit > 0:
        artists = artists[: args.real_art_limit]
        albums = albums[: args.real_art_limit]

    sources = split_csv(args.real_art_sources)
    artist_dir = args.media_root / "artist"
    cover_dir = args.media_root / "cover"
    state_path = args.state_dir / "real_art_results.json"
    state = load_state(state_path)
    statements: list[str] = []
    checked_artists = 0
    updated_artists = 0
    checked_albums = 0
    updated_albums = 0
    failed = 0
    album_ids: list[int] = []

    for artist in artists:
        artist_id = int(artist["id"])
        name = str(artist["name"] or "")
        key = f"artist:{name}"
        result = None
        checked_artists += 1
        if "deezer" in sources:
            try:
                result = lookup_deezer_artist_image(artist)
            except Exception as exc:
                state[f"{key}:deezer:error"] = str(exc)
        if result and result["score"] >= args.real_art_min_score:
            try:
                file_name, _ = download_artwork(
                    str(result["artwork_url"]),
                    artist_dir,
                    f"artist-{artist_id}-{safe_ascii_name(name, short_hash(name))}",
                    args.execute,
                )
                url = f"/media/artist/{quote(file_name)}"
                statements.append(f"UPDATE artist SET avatar_url = {sql_string(url)} WHERE id = {artist_id};")
                result["local_url"] = url
                result["status"] = "ok"
                updated_artists += 1
            except Exception as exc:
                result["status"] = "failed"
                result["error"] = str(exc)
                failed += 1
        else:
            result = result or {"status": "not-found"}
            result.setdefault("status", "below-threshold")
        state[key] = result
        if args.execute:
            save_state(state_path, state)
        if args.real_art_sleep > 0:
            time.sleep(args.real_art_sleep)

    album_lookups = {
        "itunes": lookup_itunes_album_art,
        "deezer": lookup_deezer_album_art,
        "musicbrainz": lookup_musicbrainz_album_art,
    }
    for album in albums:
        album_id = int(album["id"])
        title = str(album["title"] or "")
        artist_name = str(album["artist_name"] or "")
        key = f"album:{artist_name}\u241f{title}"
        result = None
        checked_albums += 1
        for source in sources:
            lookup = album_lookups.get(source)
            if lookup is None:
                continue
            try:
                result = lookup(album)
            except Exception as exc:
                state[f"{key}:{source}:error"] = str(exc)
                result = None
            if result and result["score"] >= args.real_art_min_score:
                break
            if args.real_art_sleep > 0:
                time.sleep(args.real_art_sleep)
        if result and result["score"] >= args.real_art_min_score:
            try:
                file_name, _ = download_artwork(
                    str(result["artwork_url"]),
                    cover_dir,
                    f"album-{album_id}-{safe_ascii_name(title, short_hash(key))}",
                    args.execute,
                    prefetched=result.get("prefetched_bytes"),
                    content_type=str(result.get("prefetched_content_type") or ""),
                )
                url = f"/media/cover/{quote(file_name)}"
                statements.append(f"UPDATE album SET cover_url = {sql_string(url)} WHERE id = {album_id};")
                album_ids.append(album_id)
                result["local_url"] = url
                result["status"] = "ok"
                updated_albums += 1
            except Exception as exc:
                result["status"] = "failed"
                result["error"] = str(exc)
                failed += 1
        else:
            result = result or {"status": "not-found"}
            result.setdefault("status", "below-threshold")
        result.pop("prefetched_bytes", None)
        state[key] = result
        if args.execute:
            save_state(state_path, state)
        if args.real_art_sleep > 0:
            time.sleep(args.real_art_sleep)

    if album_ids:
        ids = ",".join(str(item) for item in album_ids)
        statements.append(
            "UPDATE song s JOIN album a ON a.id = s.album_id "
            f"SET s.cover_url = a.cover_url WHERE s.album_id IN ({ids});"
        )
    execute_statements(config, database, statements, args.execute)
    if args.execute:
        save_state(state_path, state)
    else:
        print(json.dumps(state, ensure_ascii=False, indent=2, sort_keys=True))
    return {
        "artists_checked": checked_artists,
        "artists_updated": updated_artists,
        "albums_checked": checked_albums,
        "albums_updated": updated_albums,
        "failed_downloads": failed,
    }


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


def artist_relation_ok(song_artist: str | None, album_artist: str | None) -> bool:
    song_name = normalize(song_artist or "")
    album_name = normalize(album_artist or "")
    if not song_name or not album_name:
        return False
    return song_name == album_name or song_name in album_name or album_name in song_name


def media_issue(media_root: Path, owner: str, item_id: Any, field: str, value: Any) -> dict[str, Any] | None:
    if not value:
        return {"owner": owner, "id": item_id, "field": field, "status": "empty"}
    url = str(value)
    path = media_path_from_url(media_root, url)
    if path is None:
        return None
    if not path.is_file():
        return {"owner": owner, "id": item_id, "field": field, "status": "missing-file", "url": url}
    if path.stat().st_size <= 0:
        return {"owner": owner, "id": item_id, "field": field, "status": "empty-file", "url": url}
    return None


def backfill_audit_catalog(args: argparse.Namespace, config: dict[str, str], database: str) -> dict[str, Any]:
    artists = query_artists(config, database)
    albums = query_all_albums(config, database)
    songs = query_all_songs(config, database)
    counts = query_catalog_counts(config, database)

    missing_artist_images = [artist for artist in artists if not artist.get("avatar_url")]
    generated_artist_images = [artist for artist in artists if is_generated_artist_art_url(artist.get("avatar_url"))]
    missing_real_artist_images = [
        artist for artist in artists if not has_real_url(artist.get("avatar_url"), is_generated_artist_art_url)
    ]
    missing_album_covers = [album for album in albums if not album.get("cover_url")]
    generated_album_covers = [album for album in albums if is_generated_album_art_url(album.get("cover_url"))]
    missing_real_album_covers = [
        album for album in albums if not has_real_url(album.get("cover_url"), is_generated_album_art_url)
    ]

    relation_mismatches: list[dict[str, Any]] = []
    relation_reviews: list[dict[str, Any]] = []
    missing_media: list[dict[str, Any]] = []
    song_cover_drift: list[dict[str, Any]] = []
    duplicate_keys: dict[str, list[dict[str, Any]]] = {}

    for artist in artists:
        issue = media_issue(args.media_root, "artist", artist.get("id"), "avatar_url", artist.get("avatar_url"))
        if issue:
            missing_media.append(issue)
    for album in albums:
        issue = media_issue(args.media_root, "album", album.get("id"), "cover_url", album.get("cover_url"))
        if issue:
            missing_media.append(issue)
    for song in songs:
        song_id = song.get("id")
        if song.get("artist_id") != song.get("album_artist_id"):
            record = {
                "song_id": song_id,
                "title": song.get("title"),
                "song_artist": song.get("artist_name"),
                "album": song.get("album_title"),
                "album_artist": song.get("album_artist_name"),
                "accepted_as_credit_variant": artist_relation_ok(song.get("artist_name"), song.get("album_artist_name")),
            }
            relation_mismatches.append(record)
            if not record["accepted_as_credit_variant"]:
                relation_reviews.append(record)

        song_cover = song.get("cover_url")
        album_cover = song.get("album_cover_url")
        if song_cover and album_cover and song_cover != album_cover:
            song_cover_drift.append(
                {
                    "song_id": song_id,
                    "title": song.get("title"),
                    "album": song.get("album_title"),
                    "song_cover_url": song_cover,
                    "album_cover_url": album_cover,
                }
            )

        key = f"{normalize(str(song.get('artist_name') or ''))}\u241f{normalize(str(song.get('title') or ''))}"
        duplicate_keys.setdefault(key, []).append(
            {
                "id": song_id,
                "title": song.get("title"),
                "artist": song.get("artist_name"),
                "album": song.get("album_title"),
            }
        )
        for field in ("audio_url", "cover_url", "lyric_url"):
            issue = media_issue(args.media_root, "song", song_id, field, song.get(field))
            if issue:
                missing_media.append(issue)

    duplicate_songs = [items for key, items in duplicate_keys.items() if key.strip("\u241f") and len(items) > 1]
    songs_without_lyrics = [song for song in songs if not song.get("lyric_url")]
    songs_without_cover = [song for song in songs if not song.get("cover_url")]
    songs_without_audio = [song for song in songs if not song.get("audio_url")]
    songs_without_album = [song for song in songs if not song.get("album_id")]
    songs_without_artist = [song for song in songs if not song.get("artist_id")]

    report = {
        "generated_at": dt.datetime.now(dt.timezone.utc).isoformat(),
        "database": database,
        "media_root": str(args.media_root),
        "counts": counts,
        "summary": {
            "missing_artist_images": len(missing_artist_images),
            "generated_artist_images": len(generated_artist_images),
            "missing_real_artist_images": len(missing_real_artist_images),
            "missing_album_covers": len(missing_album_covers),
            "generated_album_covers": len(generated_album_covers),
            "missing_real_album_covers": len(missing_real_album_covers),
            "songs_without_lyrics": len(songs_without_lyrics),
            "songs_without_cover": len(songs_without_cover),
            "songs_without_audio": len(songs_without_audio),
            "songs_without_album": len(songs_without_album),
            "songs_without_artist": len(songs_without_artist),
            "song_album_artist_mismatches": len(relation_mismatches),
            "song_album_artist_needs_review": len(relation_reviews),
            "song_cover_differs_from_album": len(song_cover_drift),
            "duplicate_song_title_artist_groups": len(duplicate_songs),
            "local_media_issues": len(missing_media),
        },
        "issues": {
            "song_album_artist_mismatches": relation_mismatches,
            "song_album_artist_needs_review": relation_reviews,
            "song_cover_differs_from_album": song_cover_drift,
            "duplicate_song_title_artist_groups": duplicate_songs,
            "local_media_issues": missing_media,
        },
        "samples": {
            "missing_real_artist_images": [
                {"id": item.get("id"), "name": item.get("name"), "avatar_url": item.get("avatar_url")}
                for item in missing_real_artist_images[:40]
            ],
            "missing_real_album_covers": [
                {
                    "id": item.get("id"),
                    "title": item.get("title"),
                    "artist": item.get("artist_name"),
                    "cover_url": item.get("cover_url"),
                }
                for item in missing_real_album_covers[:80]
            ],
            "songs_without_lyrics": [
                {"id": item.get("id"), "title": item.get("title"), "artist": item.get("artist_name")}
                for item in songs_without_lyrics[:80]
            ],
        },
    }

    output_path = args.audit_output or args.state_dir / "catalog_audit.json"
    output_path.parent.mkdir(parents=True, exist_ok=True)
    output_path.write_text(json.dumps(report, ensure_ascii=False, indent=2, sort_keys=True), encoding="utf-8")
    return {**report["summary"], "report": str(output_path)}


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
    summaries: dict[str, dict[str, Any]] = {}

    if "audit-catalog" in tasks:
        summaries["audit-catalog"] = backfill_audit_catalog(args, config, database)
    if "real-art" in tasks:
        summaries["real-art"] = backfill_real_art(args, config, database)
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
