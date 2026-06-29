# 步骤一技术路线与范围基线

## 1. 目标

步骤一只完成范围确认与设计准备，不创建 Spring Boot 或 Vue 工程骨架，不写业务代码。完成后，后续实现可以直接进入“步骤二：后端基础架构”。

## 2. 技术路线决策

项目采用展示增强型路线：

```text
Vue 3 / Vite 8
        |
        | REST API
        v
Spring Boot 3.5.x / Java 17 / Spring Security
        |
        v
MySQL 8.4 LTS

Nginx 负责前端静态资源、/api 反向代理、/media 静态媒体访问。
Docker Compose 负责本地和服务器部署编排。
```

选择原因：

- Vue 单页应用更适合全局播放器、播放队列、歌词高亮、拖拽歌单和沉浸播放页。
- Spring Boot REST API 能清楚展示 Java Web、权限控制、接口设计、异常处理和数据库能力。
- MySQL 8.4 LTS 更适合课程项目的稳定交付，避免跟随创新版本频繁变动。
- 本地媒体目录先满足课程演示，后续可平滑替换为 MinIO 或云对象存储。

## 3. 版本基线

|组件|基线|说明|
|-|-|-|
|JDK|Java 17|Spring Boot 3.5.x 的最低要求，课程环境兼容性较好。|
|后端框架|Spring Boot 3.5.x|以 3.5 稳定线为准；虽然官方已有 4.x 稳定版，但本课程项目优先降低生态迁移风险。|
|构建工具|Maven 3.6.3+|与 Spring Boot 3.5.x 官方要求一致。|
|前端框架|Vue 3|用于单页应用、组件化页面和全局播放器状态管理。|
|前端构建|Vite 8|Vite 8 已稳定发布；Node.js 使用 20.19+ 或 22.12+。|
|数据库|MySQL 8.4 LTS|长期支持版本，适合稳定部署与答辩演示。|
|部署|Docker Compose + Nginx|固化 MySQL、后端、前端、媒体目录访问。|
|缓存|Redis 可选|只在热门榜单、验证码、推荐缓存需要时引入。|

参考依据：

- Spring Boot 3.5 系统要求：<https://docs.spring.io/spring-boot/3.5/system-requirements.html>
- Vite 8 发布与 Node.js 要求：<https://vite.dev/blog/announcing-vite8>
- MySQL 8.4 LTS 发布模型：<https://dev.mysql.com/doc/refman/8.4/en/mysql-releases.html>

## 4. 计划目录

后续实现阶段按以下目录组织：

```text
music-web-backend/        Spring Boot REST API
music-web-frontend/       Vue 3 / Vite 前端
deploy/                   Docker Compose、Nginx、部署脚本
docs/                     项目设计、接口、测试、部署文档
src/main/resources/static/media/
  audio/                  本地演示音频清单和本地音频目录
  cover/                  本地封面目录
  lyrics/                 本地歌词目录
```

当前步骤一不创建 `music-web-backend/` 或 `music-web-frontend/`，避免在技术设计审阅前生成工程代码。

## 5. MVP 边界

|优先级|范围|验收口径|
|-|-|-|
|P0 必须|注册登录、角色权限、歌曲列表/详情/搜索、基础播放器、歌单创建与添加歌曲、管理员上传与上下架、Docker 本地部署|普通用户和管理员两条主流程可以完整演示。|
|P1 应该|收藏、评论、最近播放、歌词显示、后台用户/评论管理、统一错误提示|提升完整度和稳定性，作为课程验收加分项。|
|P2 可以|歌词高亮、场景推荐、拖拽歌单、沉浸播放页、封面主色背景|只在 P0/P1 稳定后实现，作为答辩亮点。|
|暂不做|商业平台音乐抓取、复杂机器学习推荐、多机高可用、支付会员、原生 App|避免偏离课程交付和合规边界。|

## 6. 媒体与版权策略

- 当前 13 首 FLAC 只作为本地开发和课程演示素材。
- Git 仓库不提交 `.flac` 大文件，只提交 `catalog.json`、音频 README 和素材来源说明。
- 数据库只保存媒体 URL，不保存音频二进制。
- 若需要公开部署或公开仓库展示，必须先确认音乐素材授权范围；必要时替换为自有、授权或 CC 素材。

## 7. 步骤一完成门禁

- 技术路线、版本基线、目录策略已确认。
- 页面清单、普通用户流程、管理员流程和演示主线已确认。
- ER 初稿和 API 初稿已覆盖 P0/P1 核心能力。
- `git status` 中不出现待提交的 FLAC 文件。
- 步骤一文档、媒体清单和忽略规则已提交并推送。
