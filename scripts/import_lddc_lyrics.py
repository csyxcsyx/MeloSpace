#!/usr/bin/env python3
"""Import word-level lyrics with a local LDDC source checkout or zip.

This script intentionally does not implement provider API calls itself. It loads
the user-provided LDDC Python source locally and delegates search/fetch/convert
work to LDDC modules.
"""

from __future__ import annotations

import argparse
import json
import re
import shutil
import sys
import tempfile
import zipfile
from pathlib import Path
from typing import Iterable


REPO_ROOT = Path(__file__).resolve().parents[1]
DEFAULT_LDDC_ZIP = Path(r"C:\Users\YUXIANde\Downloads\LDDC-0.9.2.zip")
DEFAULT_LYRICS_DIR = REPO_ROOT / "src" / "main" / "resources" / "static" / "media" / "lyrics"
DEPENDENCY_HINT = (
    'python -m pip install "PySide6-Essentials>=6.8.0" '
    '"httpx[brotli,http2]" mutagen diskcache charset-normalizer pyaes psutil'
)


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Fetch precise lyrics through local LDDC and write an LRC file for MusicWeb.",
    )
    parser.add_argument("--title", required=True, help="Song title used for LDDC matching.")
    parser.add_argument("--artist", required=True, help="Song artist used for LDDC matching.")
    parser.add_argument("--album", default="", help="Optional album name used to improve matching.")
    parser.add_argument("--audio-file", type=Path, help="Optional local audio file; duration is read with mutagen.")
    parser.add_argument("--duration-ms", type=int, help="Optional duration in milliseconds; overrides audio metadata.")
    parser.add_argument("--lddc-zip", type=Path, default=DEFAULT_LDDC_ZIP, help="Path to the downloaded LDDC source zip.")
    parser.add_argument("--lddc-src", type=Path, help="Path to an extracted LDDC source directory.")
    parser.add_argument("--lyrics-dir", type=Path, default=DEFAULT_LYRICS_DIR, help="MusicWeb lyrics output directory.")
    parser.add_argument("--output", type=Path, help="Explicit output file. Defaults to '<artist> - <title>.lrc'.")
    parser.add_argument("--format", choices=("enhanced-lrc", "verbatim-lrc", "line-lrc", "json"), default="enhanced-lrc")
    parser.add_argument("--langs", default="orig", help="Comma separated LDDC lyric channels, for example 'orig' or 'orig,ts'.")
    parser.add_argument("--sources", default="QM,KG,NE", help="Comma separated LDDC sources: QM,KG,NE,LRCLIB.")
    parser.add_argument("--min-score", type=float, default=55.0, help="Minimum LDDC auto match score.")
    parser.add_argument("--offset-ms", type=int, default=0, help="Offset applied when exporting lyrics.")
    parser.add_argument("--force", action="store_true", help="Overwrite output if it already exists.")
    parser.add_argument("--dry-run", action="store_true", help="Fetch and report metadata without writing a file.")
    return parser.parse_args()


def safe_filename(value: str) -> str:
    cleaned = re.sub(r'[<>:"/\\|?*\x00-\x1f]', "_", value).strip().strip(".")
    cleaned = re.sub(r"\s+", " ", cleaned)
    return cleaned or "lyrics"


def resolve_lddc_root(src: Path | None, archive: Path | None) -> tuple[Path, Path | None]:
    if src:
        root = src.resolve()
        if (root / "LDDC").is_dir():
            return root, None
        nested = next((child for child in root.iterdir() if (child / "LDDC").is_dir()), None)
        if nested:
            return nested.resolve(), None
        raise FileNotFoundError(f"Cannot find LDDC package under {root}")

    if not archive or not archive.is_file():
        raise FileNotFoundError(f"LDDC zip not found: {archive}")

    temp_root = Path(tempfile.mkdtemp(prefix="musicweb-lddc-"))
    with zipfile.ZipFile(archive) as zip_file:
        zip_file.extractall(temp_root)

    root = next((child for child in temp_root.iterdir() if (child / "LDDC").is_dir()), None)
    if not root:
        shutil.rmtree(temp_root, ignore_errors=True)
        raise FileNotFoundError(f"Cannot find LDDC package inside {archive}")
    return root.resolve(), temp_root


def read_duration_ms(audio_file: Path | None) -> int | None:
    if not audio_file:
        return None
    if not audio_file.is_file():
        raise FileNotFoundError(f"Audio file not found: {audio_file}")

    try:
        import mutagen  # type: ignore[import-not-found]
    except ModuleNotFoundError:
        return None

    audio = mutagen.File(audio_file)
    length = getattr(getattr(audio, "info", None), "length", None)
    if length is None:
        return None
    return int(float(length) * 1000)


def split_csv(value: str) -> list[str]:
    return [part.strip() for part in value.split(",") if part.strip()]


def format_name_to_enum(name: str):
    from LDDC.common.models import LyricsFormat

    return {
        "enhanced-lrc": LyricsFormat.ENHANCEDLRC,
        "verbatim-lrc": LyricsFormat.VERBATIMLRC,
        "line-lrc": LyricsFormat.LINEBYLINELRC,
        "json": LyricsFormat.JSON,
    }[name]


def source_names_to_enums(names: Iterable[str]):
    from LDDC.common.models import Source

    sources = []
    for name in names:
        key = name.upper()
        if key not in Source.__members__:
            raise ValueError(f"Unsupported LDDC source: {name}")
        sources.append(Source[key])
    return tuple(sources)


def import_lyrics(args: argparse.Namespace) -> dict[str, object]:
    cleanup_root: Path | None = None
    try:
        lddc_root, cleanup_root = resolve_lddc_root(args.lddc_src, args.lddc_zip)
        sys.path.insert(0, str(lddc_root))

        try:
            from PySide6.QtCore import QCoreApplication

            from LDDC.common.models import Artist, LyricsType, SongInfo, Source
            from LDDC.core.auto_fetch import auto_fetch
        except ModuleNotFoundError as exc:
            missing = exc.name or "unknown"
            raise RuntimeError(
                f"Missing Python dependency '{missing}'. Install LDDC runtime dependencies with:\n{DEPENDENCY_HINT}",
            ) from exc

        app = QCoreApplication.instance() or QCoreApplication([])
        _ = app

        duration_ms = args.duration_ms if args.duration_ms is not None else read_duration_ms(args.audio_file)
        song_info = SongInfo(
            source=Source.Local,
            title=args.title,
            artist=Artist(args.artist),
            album=args.album or None,
            duration=duration_ms,
            path=args.audio_file.resolve() if args.audio_file else None,
        )

        lyrics = auto_fetch(
            song_info,
            min_score=args.min_score,
            sources=source_names_to_enums(split_csv(args.sources)),
            return_search_results=False,
        )
        lyrics_format = format_name_to_enum(args.format)
        langs = None if args.format == "json" else split_csv(args.langs)
        rendered = lyrics.to(lyrics_format, langs=langs, offset=args.offset_ms)

        output = args.output
        if output is None:
            output = args.lyrics_dir / f"{safe_filename(args.artist)} - {safe_filename(args.title)}.lrc"
        output = output.resolve()

        if output.exists() and not args.force and not args.dry_run:
            raise FileExistsError(f"Output already exists: {output}. Use --force to replace it.")

        if not args.dry_run:
            output.parent.mkdir(parents=True, exist_ok=True)
            output.write_text(rendered, encoding="utf-8", newline="\n")

        lyric_type = lyrics.types.get("orig")
        return {
            "output": str(output),
            "written": not args.dry_run,
            "source": lyrics.info.source.name,
            "matched_title": lyrics.title,
            "matched_artist": str(lyrics.artist),
            "matched_album": lyrics.album,
            "duration_ms": duration_ms,
            "lyrics_type": lyric_type.name if isinstance(lyric_type, LyricsType) else str(lyric_type),
            "candidate_count": None,
            "format": args.format,
        }
    finally:
        if cleanup_root:
            shutil.rmtree(cleanup_root, ignore_errors=True)


def main() -> int:
    args = parse_args()
    try:
        result = import_lyrics(args)
    except Exception as exc:
        print(f"ERROR: {exc}", file=sys.stderr)
        return 1

    print(json.dumps(result, ensure_ascii=False, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
