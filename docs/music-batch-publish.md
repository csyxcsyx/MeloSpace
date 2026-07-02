# MeloSpace 批量音乐上架

批量上架使用 `scripts/publish_music_batch.py`，通过 CSV 清单描述歌手、专辑、歌曲和本地媒体文件。脚本会把媒体文件复制到服务器 `/opt/melospace/media`，并向 MySQL 幂等写入歌手、专辑和歌曲记录。

## CSV 字段

必填字段：

|字段|说明|
|-|-|
|`title`|歌曲名|
|`artist`|歌手名；同名歌手会复用|
|`album`|专辑名；同一歌手下同名专辑会复用|
|`audio_path`|本地音频文件路径，支持 `mp3`、`aac`、`m4a`、`flac`、`wav`|

可选字段：

|字段|说明|
|-|-|
|`cover_path`|本地封面路径，支持 `jpg`、`jpeg`、`png`、`webp`|
|`lyric_path`|本地歌词路径，支持 `lrc`、`txt`|
|`duration_seconds`|歌曲时长，秒|
|`language`|语言，默认中文|
|`genre`|风格|
|`mood`|情绪标签|
|`release_date`|专辑发行日期，格式 `YYYY-MM-DD`|
|`artist_bio`|歌手简介|

示例见 `scripts/music_batch_manifest.example.csv`。

## 预演

```bash
python scripts/publish_music_batch.py path/to/manifest.csv --output-sql tmp/publish.sql
```

预演只生成 SQL，不会改服务器。确认 SQL 和文件路径无误后再执行。

## 执行上架

```bash
python scripts/publish_music_batch.py path/to/manifest.csv --execute
```

默认服务器参数：

```text
SSH 私钥：C:\Users\YUXIANde\Downloads\REX.pem
SSH 用户：root
服务器：47.89.235.138
数据库：music_web
媒体目录：/opt/melospace/media
```

同一首歌按“歌名 + 歌手 + 专辑”重复执行时会更新音频、封面、歌词、时长和上架状态，不会重复插入歌曲。
