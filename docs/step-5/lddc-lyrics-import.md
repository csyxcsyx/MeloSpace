# LDDC 逐字歌词导入说明

本项目使用 [chenmozhijin/LDDC](https://github.com/chenmozhijin/LDDC) 作为外部辅助工具，按歌曲名、歌手和可选音频文件自动检索逐字歌词，并保存为 MeloSpace 可访问的 `.lrc` 文件。

## 1. 环境准备

推荐二选一：

```powershell
python -m pip install git+https://github.com/chenmozhijin/LDDC.git
```

或克隆 LDDC 后安装依赖：

```powershell
git clone https://github.com/chenmozhijin/LDDC.git C:\Tools\LDDC
cd C:\Tools\LDDC
python -m pip install -r requirements.txt
```

## 2. 导入命令

已安装 LDDC 时：

```powershell
python scripts\import_precise_lyrics.py --title "歌曲名" --artist "歌手名" --audio "歌曲文件路径"
```

使用源码目录时：

```powershell
python scripts\import_precise_lyrics.py --lddc-path C:\Tools\LDDC --title "歌曲名" --artist "歌手名" --audio "歌曲文件路径"
```

默认输出到：

```text
src/main/resources/static/media/lyrics/歌手名 - 歌曲名.lrc
```

脚本会同时打印可填入后台歌曲表单的媒体 URL：

```text
/media/lyrics/歌手名%20-%20歌曲名.lrc
```

## 3. 格式说明

默认导出 `ENHANCEDLRC`，包含字级时间戳；前端 `LyricPanel` 已支持增强 LRC 与逐字 LRC，并会在播放时按字高亮。

也可以指定格式：

```powershell
python scripts\import_precise_lyrics.py --title "歌曲名" --artist "歌手名" --format VERBATIMLRC
```

## 4. 使用边界

- LDDC 的匹配依赖第三方歌词源，结果需要人工检查一次。
- 若歌曲名、歌手名、时长不准确，建议提供 `--audio` 或 `--duration-ms` 提升匹配准确率。
- 歌词来源和使用权限需要在答辩或公开部署前确认，避免版权风险。
