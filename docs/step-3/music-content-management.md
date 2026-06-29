# 步骤三音乐内容管理闭环说明

## 1. 完成内容

- 新增公开音乐查询接口：歌曲分页、歌曲详情、综合搜索、歌手列表、专辑列表。
- 新增后台内容管理接口：管理员可创建和编辑歌曲、歌手、专辑，并通过状态字段控制歌曲上架/下架。
- 新增本地媒体上传接口：管理员可上传音频、封面、歌词文件，文件落盘后写入 `upload_file` 表并返回 `/media/...` 访问 URL。
- 新增 `/media/**` 静态资源映射，默认读取本地 `./media` 目录。
- 扩展集成测试，覆盖公开查询、后台鉴权、内容管理、上传成功和上传错误场景。

## 2. 当前接口

### 公开接口

| 方法 | 路径 | 说明 |
|-|-|-|
| GET | `/api/songs?page=&size=&keyword=&artistId=&albumId=` | 歌曲分页列表，只返回 `status=1` 的已上架歌曲。 |
| GET | `/api/songs/{id}` | 歌曲详情，只允许查看已上架歌曲；不存在或已下架返回 404。 |
| GET | `/api/search?keyword=` | 综合搜索歌曲、歌手、专辑；歌单搜索留到步骤四。 |
| GET | `/api/artists?keyword=` | 歌手列表，可按名称模糊过滤。 |
| GET | `/api/albums?keyword=&artistId=` | 专辑列表，可按标题和歌手过滤。 |

### 管理员接口

| 方法 | 路径 | 说明 |
|-|-|-|
| GET | `/api/admin/songs?page=&size=&keyword=&status=` | 管理员歌曲分页，可查看全部状态。 |
| POST | `/api/admin/songs` | 新增歌曲。 |
| PUT | `/api/admin/songs/{id}` | 编辑歌曲。 |
| PATCH | `/api/admin/songs/{id}/status` | 上架或下架歌曲，请求体为 `{ "status": 0|1 }`。 |
| POST | `/api/admin/artists` | 新增歌手。 |
| PUT | `/api/admin/artists/{id}` | 编辑歌手。 |
| POST | `/api/admin/albums` | 新增专辑。 |
| PUT | `/api/admin/albums/{id}` | 编辑专辑。 |
| POST | `/api/admin/upload` | 上传媒体文件，使用 `multipart/form-data` 字段 `file` 和 `fileType`。 |

## 3. 上传规则

`fileType` 支持：

| fileType | 保存目录 | 扩展名白名单 | 默认大小限制 |
|-|-|-|-|
| `AUDIO` | `media/audio/` | `mp3`、`aac`、`m4a`、`flac`、`wav` | 100MB |
| `COVER` | `media/cover/` | `jpg`、`jpeg`、`png`、`webp` | 5MB |
| `LYRIC` | `media/lyrics/` | `lrc`、`txt` | 1MB |

上传成功返回：

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "id": 1,
    "fileType": "AUDIO",
    "originalName": "demo.mp3",
    "url": "/media/audio/uuid.mp3",
    "mimeType": "audio/mpeg",
    "sizeBytes": 12345,
    "createdAt": "2026-06-30T00:00:00"
  },
  "timestamp": "2026-06-30T00:00:00+08:00"
}
```

相关配置：

```yaml
music-web:
  media:
    base-url: /media
    storage-root: ./media
    max-audio-size: 100MB
    max-cover-size: 5MB
    max-lyric-size: 1MB
```

## 4. 本地验证

当前验证命令：

```powershell
$env:JAVA_HOME='C:\Program Files\Java\jdk-21.0.10'
cd music-web-backend
.\mvnw.cmd test
```

测试结果：7 个集成测试全部通过，覆盖以下场景：

- 公开歌曲列表只展示已上架歌曲。
- 下架歌曲公开详情返回 404，后台仍可查询。
- 普通用户访问 `/api/admin/**` 返回 403，未登录访问返回 401。
- 管理员可新增、编辑歌手和专辑。
- 管理员可新增、编辑歌曲并切换上下架状态。
- 管理员可上传合法音频，返回 URL 并可通过 `/media/...` 访问。
- 非法扩展名、非法 `fileType`、缺少文件均返回统一错误结构。

## 5. 步骤三门禁状态

- [x] 管理员上传音频后，可用返回的 URL 创建歌曲。
- [x] 普通用户可通过公开接口查到该歌曲并拿到可播放 `audioUrl`。
- [x] 下架后普通用户列表和详情不可见。
- [x] 歌手、专辑、歌曲管理接口受管理员权限保护。
- [x] 本地上传目录已加入忽略规则，避免误提交用户上传文件。

步骤三已满足进入步骤四“用户核心业务闭环”的条件。
