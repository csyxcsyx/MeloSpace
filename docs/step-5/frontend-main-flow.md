# 步骤五前端主流程与播放器说明

## 1. 完成内容

- 新增 `music-web-frontend` 前端工程，采用 Vue 3、Vite、TypeScript、Pinia、Vue Router、Axios 和 lucide-vue-next。
- 落地 Apple Music 风格启发的极简界面：浅灰侧栏、白底内容区、横向推荐卡、四列歌曲榜单和底部胶囊播放器。
- 完成前端路由：`/discover`、`/search`、`/songs/:id`、`/playlists/:id`、`/me`、`/admin`、`/login`。
- 完成 API 客户端封装，统一处理 `ApiResponse<T>`、分页结果、Token 注入、401 清理登录态和 403 权限提示。
- 完成登录/注册、发现页、搜索、歌曲详情、歌单详情、个人中心、后台管理和全局播放器。
- 全局播放器使用单个隐藏 `<audio>`，支持播放、暂停、上一首、下一首、进度跳转、音量调整、错误提示和本地状态保存。
- 开发期通过 Vite 代理 `/api` 和 `/media` 到 `http://localhost:8080`，无需改动后端 CORS。

## 2. 本地启动

后端：

```powershell
cd music-web-backend
.\mvnw.cmd spring-boot:run
```

前端：

```powershell
cd music-web-frontend
npm install
npm run dev
```

前端默认地址：`http://127.0.0.1:5173/`。

## 3. 当前功能门禁

- [x] 匿名用户可进入 `/discover` 浏览歌曲和公开歌单。
- [x] 点击歌曲可通过底部播放器播放，音频加载失败时显示错误提示。
- [x] 用户可登录/注册，登录态写入 `localStorage`。
- [x] 登录用户可收藏歌曲/歌单、评论歌曲、创建歌单、向歌单添加歌曲并查看个人中心。
- [x] 管理员可进入 `/admin` 查看统计、上传资源、创建歌手、创建歌曲和切换歌曲上下架状态。
- [x] 页面在宽屏、窄屏和移动端有响应式布局，侧栏和播放器不会遮挡主体操作。

## 4. 已验证命令

```powershell
cd music-web-frontend
npm run type-check
npm run build
```

以上命令已通过。后端接口级测试在步骤四已覆盖用户核心业务；本步骤未新增后端接口。

## 5. 后续步骤六建议

- 歌词 LRC 解析与当前行高亮。
- 歌单歌曲拖拽排序。
- 场景推荐或沉浸播放页。
- 更完整的用户收藏详情聚合展示。
