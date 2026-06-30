# 步骤四用户核心业务闭环说明

## 1. 完成内容

- 新增歌单用户接口：公开歌单分页、歌单详情、我的歌单、创建、编辑、删除、添加歌曲、移除歌曲和排序。
- 新增用户行为接口：收藏/取消收藏、评论/删除评论、播放记录/最近播放。
- 扩展综合搜索接口：`/api/search` 现在返回歌曲、歌手、专辑和公开歌单。
- 扩展权限控制：用户写操作、个人中心数据、播放记录、收藏和评论发布删除均要求登录。
- 扩展集成测试，覆盖歌单归属、私有歌单访问、下架歌曲拦截、收藏幂等、评论删除权限、播放量递增和公开歌单搜索。

## 2. 当前接口

### 歌单接口

| 方法 | 路径 | 说明 |
|-|-|-|
| GET | `/api/playlists?page=&size=&keyword=` | 公开歌单分页，只返回 `visibility=PUBLIC` 的歌单。 |
| GET | `/api/playlists/{id}` | 歌单详情；公开歌单可匿名访问，私有歌单仅创建者可访问。 |
| GET | `/api/users/me/playlists?page=&size=` | 当前登录用户自己的歌单。 |
| POST | `/api/playlists` | 创建歌单，需要登录。 |
| PUT | `/api/playlists/{id}` | 编辑自己的歌单。 |
| DELETE | `/api/playlists/{id}` | 删除自己的歌单，并清理该歌单的歌曲关联、收藏和评论。 |
| POST | `/api/playlists/{id}/songs` | 向自己的歌单添加已上架歌曲；重复添加按幂等成功处理。 |
| DELETE | `/api/playlists/{id}/songs/{songId}` | 从自己的歌单移除歌曲。 |
| PUT | `/api/playlists/{id}/songs/order` | 调整歌单歌曲顺序，请求体为 `{ "songIds": [2, 1] }`。 |

### 收藏、评论与播放记录接口

| 方法 | 路径 | 说明 |
|-|-|-|
| POST | `/api/favorites` | 收藏歌曲或歌单，请求体为 `{ "targetType": "SONG|PLAYLIST", "targetId": 1 }`。 |
| DELETE | `/api/favorites?targetType=&targetId=` | 取消收藏；不存在时仍返回成功。 |
| GET | `/api/users/me/favorites?page=&size=` | 当前用户收藏分页。 |
| GET | `/api/comments?targetType=&targetId=&page=&size=` | 评论分页，公开读取。 |
| POST | `/api/comments` | 发布评论，需要登录。 |
| DELETE | `/api/comments/{id}` | 删除自己的评论。 |
| POST | `/api/songs/{id}/play-record` | 记录播放，需要登录，并递增歌曲播放量。 |
| GET | `/api/users/me/recent-plays?page=&size=` | 当前用户最近播放记录；返回项包含 `song` 摘要，便于前端展示歌曲名、歌手和封面。 |

### 搜索接口

| 方法 | 路径 | 说明 |
|-|-|-|
| GET | `/api/search?keyword=` | 返回 `songs`、`artists`、`albums`、`playlists`；歌单结果仅包含公开歌单。 |

## 3. 业务规则

- `targetType` 仅支持 `SONG` 和 `PLAYLIST`。
- 歌单 `visibility` 仅支持 `PUBLIC` 和 `PRIVATE`，为空时默认 `PUBLIC`。
- 只有已上架歌曲可加入歌单、收藏、评论和记录播放。
- 普通用户只能编辑、删除自己的歌单和评论。
- 私有歌单不会出现在公开列表或综合搜索中。
- 播放记录按事件新增，不做同一歌曲去重。

## 4. 本地验证

当前验证命令：

```powershell
$env:JAVA_HOME='C:\Program Files\Java\jdk-21.0.10'
cd music-web-backend
.\mvnw.cmd test
```

测试结果：9 个集成测试全部通过，覆盖以下场景：

- 未登录访问个人歌单、收藏、播放记录和写接口会返回 401。
- 用户可创建、编辑、删除自己的歌单，其他用户无法修改。
- 歌单可添加已上架歌曲，添加下架歌曲返回 404。
- 歌单排序保存后按新顺序返回。
- 收藏歌曲和歌单、重复收藏、取消不存在收藏均符合预期。
- 评论可发布和公开读取，用户只能删除自己的评论。
- 播放记录可查询，记录播放会递增歌曲播放量。
- 综合搜索返回公开歌单，不返回私有歌单。

## 5. 步骤四门禁状态

- [x] 用户能创建歌单并添加歌曲。
- [x] 收藏和评论数据能持久化。
- [x] 最近播放记录能按用户查询。
- [x] 普通用户只能修改自己的歌单、收藏和评论。
- [x] 综合搜索已覆盖公开歌单。
