# 本地演示音频说明

本目录用于存放音乐网本地演示播放文件。当前音频文件来自用户提供的本地路径：

```text
C:\Users\YUXIANde\iCloudDrive\杂项\太阳之子
```

在 Spring Boot 默认静态资源配置下，本目录文件可通过以下形式访问：

```text
/media/audio/文件名.flac
```

前端给 `<audio>` 设置 `src` 时，建议对文件名做 URL 编码，避免中文、空格和括号在不同浏览器或服务器配置中出现路径解析问题。

本目录同时提供静态曲目清单：

```text
/media/audio/catalog.json
```

在数据库接口尚未完成前，前端可先读取该 JSON 文件测试歌曲列表和播放器。

## 曲目清单

|序号|文件名|建议歌曲名|歌手|建议播放地址|
|-|-|-|-|-|
|1|I Do - 周杰伦.flac|I Do|周杰伦|/media/audio/I%20Do%20-%20%E5%91%A8%E6%9D%B0%E4%BC%A6.flac|
|2|爱琴海 - 周杰伦.flac|爱琴海|周杰伦|/media/audio/%E7%88%B1%E7%90%B4%E6%B5%B7%20-%20%E5%91%A8%E6%9D%B0%E4%BC%A6.flac|
|3|那天下雨了 - 周杰伦.flac|那天下雨了|周杰伦|/media/audio/%E9%82%A3%E5%A4%A9%E4%B8%8B%E9%9B%A8%E4%BA%86%20-%20%E5%91%A8%E6%9D%B0%E4%BC%A6.flac|
|4|女儿殿下 - 周杰伦.flac|女儿殿下|周杰伦|/media/audio/%E5%A5%B3%E5%84%BF%E6%AE%BF%E4%B8%8B%20-%20%E5%91%A8%E6%9D%B0%E4%BC%A6.flac|
|5|七月的极光 - 周杰伦.flac|七月的极光|周杰伦|/media/audio/%E4%B8%83%E6%9C%88%E7%9A%84%E6%9E%81%E5%85%89%20-%20%E5%91%A8%E6%9D%B0%E4%BC%A6.flac|
|6|谁稀罕 - 周杰伦.flac|谁稀罕|周杰伦|/media/audio/%E8%B0%81%E7%A8%80%E7%BD%95%20-%20%E5%91%A8%E6%9D%B0%E4%BC%A6.flac|
|7|圣诞星 (feat. 杨瑞代) - 周杰伦.flac|圣诞星|周杰伦 feat. 杨瑞代|/media/audio/%E5%9C%A3%E8%AF%9E%E6%98%9F%20(feat.%20%E6%9D%A8%E7%91%9E%E4%BB%A3)%20-%20%E5%91%A8%E6%9D%B0%E4%BC%A6.flac|
|8|圣徒 - 周杰伦.flac|圣徒|周杰伦|/media/audio/%E5%9C%A3%E5%BE%92%20-%20%E5%91%A8%E6%9D%B0%E4%BC%A6.flac|
|9|太阳之子 - 周杰伦.flac|太阳之子|周杰伦|/media/audio/%E5%A4%AA%E9%98%B3%E4%B9%8B%E5%AD%90%20-%20%E5%91%A8%E6%9D%B0%E4%BC%A6.flac|
|10|淘金小镇 - 周杰伦.flac|淘金小镇|周杰伦|/media/audio/%E6%B7%98%E9%87%91%E5%B0%8F%E9%95%87%20-%20%E5%91%A8%E6%9D%B0%E4%BC%A6.flac|
|11|西西里 - 周杰伦.flac|西西里|周杰伦|/media/audio/%E8%A5%BF%E8%A5%BF%E9%87%8C%20-%20%E5%91%A8%E6%9D%B0%E4%BC%A6.flac|
|12|乡间的路 - 周杰伦.flac|乡间的路|周杰伦|/media/audio/%E4%B9%A1%E9%97%B4%E7%9A%84%E8%B7%AF%20-%20%E5%91%A8%E6%9D%B0%E4%BC%A6.flac|
|13|湘女多情 - 周杰伦.flac|湘女多情|周杰伦|/media/audio/%E6%B9%98%E5%A5%B3%E5%A4%9A%E6%83%85%20-%20%E5%91%A8%E6%9D%B0%E4%BC%A6.flac|

## 使用注意

* 这些文件用于本地开发、课程演示和播放功能测试。
* 若项目公开部署或提交到公开仓库，需要确认音乐素材授权范围。
* FLAC 文件体积较大，适合演示音质，但正式部署可额外准备 MP3/AAC 转码版本，提升加载速度和浏览器兼容性。
* 数据库中建议只保存音频 URL，不把音频二进制写入 MySQL。
