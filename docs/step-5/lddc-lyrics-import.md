# LDDC 精准歌词导入说明

本项目使用用户本机下载的 `chenmozhijin/LDDC` 源码辅助获取逐字歌词。导入脚本只加载本地 LDDC Python 模块；搜索、匹配、下载与格式转换都委托给 LDDC 内部实现，不在 MusicWeb 中单独实现外部歌词接口调用。

## 1. 依赖准备

LDDC 运行依赖需要安装到当前 Python 环境：

```powershell
python -m pip install "PySide6-Essentials>=6.8.0" "httpx[brotli,http2]" mutagen diskcache charset-normalizer pyaes psutil
```

已下载的源码压缩包默认路径：

```text
C:\Users\YUXIANde\Downloads\LDDC-0.9.2.zip
```

也可以先手动解压，然后用 `--lddc-src` 指向解压目录。

## 2. 导入单首歌曲

从项目根目录运行：

```powershell
python .\scripts\import_lddc_lyrics.py `
  --title "I Do" `
  --artist "周杰伦" `
  --album "太阳之子" `
  --audio-file ".\src\main\resources\static\media\audio\I Do - 周杰伦.flac" `
  --lddc-zip "C:\Users\YUXIANde\Downloads\LDDC-0.9.2.zip" `
  --force
```

默认输出位置：

```text
src/main/resources/static/media/lyrics/<歌手> - <歌名>.lrc
```

如果目标文件已经存在，脚本会拒绝覆盖；确认匹配正确后使用 `--force` 覆盖。也可以使用 `--output` 写到临时文件，人工确认后再替换。

## 3. 输出格式

默认格式为 `enhanced-lrc`，即保留行级时间戳，并在行内使用 `<mm:ss.xxx>` 标记逐字/逐词时间。前端 `LyricPanel` 已兼容：

- 普通逐句 LRC：按行滚动高亮。
- LDDC Enhanced LRC：按行滚动，并在当前行内逐字高亮。
- LDDC Verbatim LRC：兼容解析，但推荐继续使用默认 `enhanced-lrc`。

可选格式：

```powershell
--format enhanced-lrc
--format verbatim-lrc
--format line-lrc
--format json
```

## 4. 匹配来源与约束

默认使用 LDDC 的 `QM,KG,NE` 来源：

```powershell
--sources "QM,KG,NE"
```

可按需加入 `LRCLIB`。网络请求由 LDDC 内部完成，MusicWeb 脚本不直接请求任何歌词平台 API。

公开提交或部署前，需要确认歌曲和歌词素材授权范围；不明确授权的歌词建议只用于本地课程演示。
