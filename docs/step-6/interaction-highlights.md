# 步骤六交互亮点增强说明

> 状态：进行中。步骤六聚焦在已有页面与已有业务基础上的交互优化，不新增场景电台等独立新模块。

## 1. 当前完成内容

- 歌单详情页对歌单创建者开放拖拽排序，松手后调用已有 `PUT /api/playlists/{id}/songs/order` 保存顺序。
- 歌单排序提供上移/下移按钮作为补充，保证移动端或拖拽不便时仍可稳定调整顺序。
- 排序保存失败时回滚到拖拽前顺序，并用 Toast 提示“排序保存失败，已恢复原顺序”。
- 非歌单创建者仍显示原普通歌曲列表，不能拖拽修改他人歌单。
- 发现页推荐区、歌曲行、按钮和歌单排序行补充轻量 hover、进入动画和保存态反馈。
- 新增 `prefers-reduced-motion` 兜底，系统减少动态效果时会关闭位移动画和唱片旋转。
- 歌曲栏中的歌手名可点击跳转到对应歌手主页。
- 歌手主页展示该歌手全部专辑和歌曲，并支持分页、筛选和排序。
- 歌曲库不再高亮当前播放歌曲。
- 从歌曲库播放任意歌曲时，将当前歌曲库筛选结果中的全部歌曲加入播放列表。

## 2. 已验证命令

本机默认 Node.js 为 14.21.3，无法运行当前 `vue-tsc` 依赖中的新语法；验证时使用 Codex 工作区捆绑 Node.js 24.14.0 临时加入 PATH。

```powershell
cd music-web-frontend
$env:Path='C:\Users\YUXIANde\.cache\codex-runtimes\codex-primary-runtime\dependencies\node\bin;' + $env:Path
npm run type-check
npm run build
```

## 3. 相关提交

- `feat: add playlist drag sorting`
- `style: polish step six interactions`
- `revert: remove scene radio module`
- `feat: link song artists to profiles`
- `feat: enhance artist detail catalog`
- `feat: improve song library playback queue`
