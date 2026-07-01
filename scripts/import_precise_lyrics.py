#!/usr/bin/env python
"""Fetch word-timed lyrics with LDDC and save them for MeloSpace.

The script intentionally uses LDDC as an external tool instead of vendoring its
GPL-licensed source into this repository.
"""

from __future__ import annotations

import argparse
import re
import sys
from pathlib import Path
from urllib.parse import quote


REPO_ROOT = Path(__file__).resolve().parents[1]
DEFAULT_OUTPUT_DIR = REPO_ROOT / "src" / "main" / "resources" / "static" / "media" / "lyrics"


def safe_filename(value: str) -> str:
    cleaned = re.sub(r'[<>:"/\\|?*\x00-\x1f]', "_", value).strip()
    cleaned = re.sub(r"\s+", " ", cleaned)
    return cleaned or "lyrics"


def add_lddc_to_path(lddc_path: str | None) -> None:
    if not lddc_path:
        return
    root = Path(lddc_path).expanduser().resolve()
    if not (root / "LDDC").is_dir():
        raise SystemExit(f"--lddc-path 应指向 LDDC 仓库根目录，未找到: {root / 'LDDC'}")
    sys.path.insert(0, str(root))


def ensure_qt_app() -> None:
    from PySide6.QtCore import QCoreApplication

    if QCoreApplication.instance() is None:
        QCoreApplication([])


def parse_sources(values: list[str]):
    from LDDC.common.models import Source

    sources = []
    for value in values:
        normalized = value.upper()
        try:
            sources.append(Source[normalized])
        except KeyError as exc:
            supported = ", ".join(source.name for source in (Source.QM, Source.KG, Source.NE, Source.LRCLIB))
            raise SystemExit(f"不支持的歌词源: {value}，可选: {supported}") from exc
    return tuple(sources)


def read_audio_duration_ms(audio_path: str | None) -> int | None:
    if not audio_path:
        return None
    try:
        from mutagen import File
    except ImportError:
        return None

    audio = File(audio_path)
    if not audio or not audio.info or not getattr(audio.info, "length", None):
        return None
    return int(audio.info.length * 1000)


def write_lyrics(args: argparse.Namespace) -> Path:
    add_lddc_to_path(args.lddc_path)

    try:
        from LDDC.common.models import Artist, LyricsFormat, SongInfo, Source
        from LDDC.core.auto_fetch import auto_fetch
    except ModuleNotFoundError as exc:
        if exc.name == "LDDC":
            raise SystemExit(
                "未找到 LDDC。请先安装或提供源码路径，例如:\n"
                "  python -m pip install git+https://github.com/chenmozhijin/LDDC.git\n"
                "或使用:\n"
                "  python scripts/import_precise_lyrics.py --lddc-path C:\\path\\to\\LDDC ..."
            ) from exc
        raise SystemExit(
            f"LDDC 运行依赖缺失: {exc.name}。请在当前 Python 环境安装 LDDC 依赖，例如:\n"
            "  python -m pip install git+https://github.com/chenmozhijin/LDDC.git\n"
            "或在 LDDC 源码目录执行:\n"
            "  python -m pip install -r requirements.txt"
        ) from exc
    except ImportError as exc:
        raise SystemExit(f"LDDC 导入失败: {exc}") from exc

    ensure_qt_app()

    duration_ms = args.duration_ms or read_audio_duration_ms(args.audio)
    song_info = SongInfo(
        source=Source.Local,
        title=args.title,
        artist=Artist(args.artist),
        album=args.album,
        duration=duration_ms,
        path=Path(args.audio).resolve() if args.audio else None,
    )
    lyrics = auto_fetch(song_info, min_score=args.min_score, sources=parse_sources(args.source))
    lyrics_text = lyrics.to(LyricsFormat[args.format], ["orig"], 0)

    target = Path(args.output).expanduser() if args.output else Path(args.output_dir).expanduser() / f"{safe_filename(args.artist)} - {safe_filename(args.title)}.lrc"
    target = target.resolve()
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text(lyrics_text, encoding="utf-8")
    return target


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description="使用 LDDC 为 MeloSpace 导入逐字歌词。")
    parser.add_argument("--title", required=True, help="歌曲名")
    parser.add_argument("--artist", required=True, help="歌手名")
    parser.add_argument("--album", help="专辑名，可选")
    parser.add_argument("--audio", help="音频文件路径，可选；用于读取时长并提升匹配准确率")
    parser.add_argument("--duration-ms", type=int, help="歌曲时长，单位毫秒；优先级高于从音频读取")
    parser.add_argument("--lddc-path", help="LDDC 仓库根目录；若已 pip 安装 LDDC 可省略")
    parser.add_argument("--output", help="明确指定输出文件")
    parser.add_argument("--output-dir", default=str(DEFAULT_OUTPUT_DIR), help="输出目录，默认写入项目歌词静态目录")
    parser.add_argument(
        "--format",
        default="ENHANCEDLRC",
        choices=["VERBATIMLRC", "ENHANCEDLRC", "LINEBYLINELRC"],
        help="歌词格式；逐字推荐 ENHANCEDLRC 或 VERBATIMLRC",
    )
    parser.add_argument(
        "--source",
        action="append",
        default=None,
        help="歌词源，可重复传入；可选 QM/KG/NE/LRCLIB",
    )
    parser.add_argument("--min-score", type=float, default=55, help="LDDC 自动匹配最低分")
    return parser


def main() -> None:
    parser = build_parser()
    args = parser.parse_args()
    if args.source is None:
        args.source = ["QM", "KG", "NE", "LRCLIB"]
    target = write_lyrics(args)
    print(f"歌词已保存: {target}")

    try:
        relative = target.relative_to(DEFAULT_OUTPUT_DIR.resolve())
    except ValueError:
        return

    print(f"媒体 URL: /media/lyrics/{quote(relative.as_posix())}")


if __name__ == "__main__":
    main()
