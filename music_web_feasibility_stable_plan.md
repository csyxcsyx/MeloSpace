# Java Web 端音乐网项目：可行性与稳定方案计划

> 项目参考：用户提供截图中的《Java Web项目开发全程实录》（明日科技编，清华大学出版社，2019，ISBN 9787302498797）中“甜橙音乐网”板块。  
> 视觉参考：Apple Music 的大卡片、侧边导航、沉浸式封面、底部播放条、浅色留白与圆角风格；本项目仅做“风格启发”，不复刻其版权素材、图标、文案或商业界面。

## 0. 高可行性执行总纲（优化版）

### 0.1 推荐落地路线

推荐主路线为 **Spring Boot REST API + Vue 3/Vite + MySQL + 本地媒体目录 + Nginx + Docker Compose**。

理由：

* 全局播放器、播放队列、歌词高亮、拖拽歌单更适合用 Vue 单页应用实现。
* Spring Boot REST API 能清楚展示 Java Web 后端能力、权限控制、接口设计和数据库能力。
* 本地媒体目录 + Nginx 静态访问足够支撑课程演示，后续可平滑替换为 MinIO 或云对象存储。
* Docker Compose 能降低部署环境差异，便于答辩时证明“可复现运行”。

若课程周期较短，或老师要求传统 Java Web 页面，则降级为 **Spring Boot + Thymeleaf/JSP + MySQL + 本地媒体目录**，但仍保留统一接口、权限、上传、播放器和部署脚本。

### 0.2 多步骤执行顺序

项目不必严格绑定“第几天”推进，更适合按依赖关系逐步完成。每一步通过门禁后再进入下一步；若时间紧，优先砍 P2 创新功能，不砍 P0 主流程。

|步骤| 状态  |核心目标|必须产出|进入下一步的门禁|
|-|-----|-|-|-|
|步骤 1：范围确认与设计准备| 已完成 |锁定技术栈、MVP 边界、页面范围、素材来源、ER 图|技术选型说明、页面清单、ER 初稿、测试音乐素材|已能说清楚数据表、页面、接口和演示主线；产出见 `docs/step-1/`|
|步骤 2：后端基础架构| 已完成 |搭好 Spring Boot、数据库连接、统一返回、异常处理、参数校验、登录权限|后端项目、基础表、Auth API、Admin 权限；产出见 `docs/step-2/`|注册登录可用，管理员接口能鉴权，MySQL 8.4 脚本已通过 Docker 验证|
|步骤 3：音乐内容管理闭环| 已完成 |完成歌曲、歌手、专辑、上传、上架/下架等后台能力|歌曲管理 API、上传接口、资源 URL 访问；产出见 `docs/step-3/`|管理员上传歌曲后，前台接口能查到并返回播放地址|
|步骤 4：用户核心业务闭环| 已完成 |完成搜索、播放记录、歌单、收藏、评论等用户能力|歌单 API、收藏 API、评论 API、最近播放 API；产出见 `docs/step-4/`|普通用户能完成搜索、建歌单、收藏、评论|
|步骤 5：前端主流程与播放器| 已完成 |完成主页面、全局播放器、接口联调、基础响应式|Vue 页面、播放器状态管理、核心页面联调|用户能完成“搜索 → 播放 → 收藏 → 建歌单 → 评论”|
|步骤 5.5：阿里云服务器提前部署| 已完成 |在亮点增强前先完成公网部署演练|Ubuntu 24.04 ECS、Nginx、后端 systemd、MySQL、部署记录；产出见 `docs/deployment/aliyun-ecs.md`|公网 IP 可访问前端，`/api` 健康检查可用，服务支持开机自启|
|步骤 6：演示亮点增强| 已完成 |只做已有页面与已有业务基础上的稳定交互优化|歌单拖拽排序、歌曲栏跳转、歌曲库播放队列优化、发现页单屏与推荐封面预加载、底部播放器固定、字体图标、固定玻璃标题栏和个人中心交互优化；产出见 `docs/step-6/`|亮点能在无报错情况下连续演示|
|步骤 7：测试、部署与答辩材料| 已完成 |修 Bug、补文档、部署复测、准备演示脚本|测试清单、项目文档、演示材料、部署复测记录；产出见 `docs/step-7/`|公网部署可访问，答辩流程可连续跑完|

### 0.3 推荐任务顺序

1. **先建数据库模型**：先完成 user、artist、album、song、playlist、playlist_song、favorite、comment、play_history、upload_file，避免后续返工。
2. **先跑通登录和权限**：普通用户与管理员权限是后台管理、上传、评论删除的前置条件。
3. **先完成歌曲上传到播放闭环**：管理员上传音频/封面/歌词，歌曲入库，前台查询，播放器播放。
4. **再做歌单和用户行为**：歌单、收藏、评论、最近播放是用户侧完整体验的核心。
5. **再统一 UI 和播放器体验**：先能用，再好看；播放器状态要集中管理。
6. **最后做创新亮点**：优先选择歌词高亮、场景推荐、拖拽排序，这三项演示效果明显且难度可控。
7. **提前部署，不要最后才部署**：后端和前端主流程跑通后，先完成一次公网服务器部署演练；正式答辩前再做部署复测与数据备份演练。

### 0.4 进度推进模板

每完成一轮开发或每次同步进度时，记录四件事：

```text
今日完成：
今日问题：
明日任务：
是否影响 MVP：是/否，若是则写处理方案
```

每次代码更新后建议同步更新：

* 相关接口文档或 README。
* 数据库变更脚本。
* 测试记录。
* Git 提交说明。

### 0.5 立即可执行的前 10 步

1. 建立后端、前端、部署、文档四个目录。
2. 确定 Java 17、Spring Boot、MyBatis-Plus/MyBatis、MySQL、Vue 3、Vite 版本。
3. 编写 `schema.sql` 初稿，先覆盖核心 10 张表。
4. 准备 5-10 首可合法演示的测试音频、封面和歌词。
5. 搭建 Spring Boot 项目，接入 MySQL 和统一返回结构。
6. 完成注册、登录、JWT/Session、管理员角色校验。
7. 完成歌曲、歌手、专辑基础 CRUD 和分页查询。
8. 完成媒体上传接口，限制文件大小、类型和扩展名。
9. 搭建 Vue 主框架，先做侧边栏、首页、歌曲列表和底部播放器骨架。
10. 联调第一条主流程：管理员上传歌曲 → 用户搜索歌曲 → 点击播放。

### 0.6 项目控制原则

* **MVP 先于美化**：任何 UI 优化都不能阻塞登录、播放、上传、歌单、部署。
* **规则推荐先于算法推荐**：课程项目用标签和热度权重即可，不引入机器学习复杂度。
* **授权素材先于真实曲库**：宁可少量合法素材，也不要使用来源不明的商业音乐。
* **单机稳定先于高可用**：先保证一台服务器可稳定启动、可备份、可恢复。
* **演示链路先于功能数量**：答辩时一条稳定主线比十个半成品功能更有说服力。

\---

## 1\. 项目定位

本项目不是简单复刻教材案例，而是在“音乐网基础功能”之上完成一个更适合课程展示与上线部署的 Java Web 项目：

* **基础层**：用户、歌手、专辑、歌曲、歌单、收藏、评论、搜索、后台管理、数据库持久化、播放组件。
* **工程层**：分层架构、REST API、权限控制、日志、异常处理、Docker 化部署、服务器上线、备份与监控。
* **体验层**：Apple Music 风格启发的现代 UI、响应式布局、底部全局播放器、播放队列、歌词高亮、个性化推荐入口。
* **创新层**：情绪/场景听歌、封面主色氛围、拖拽式歌单、听歌时间线、协同歌单、智能搜索与轻量推荐。

\---

## 2\. 可行性结论

|维度|结论|原因|建议|
|-|-|-|-|
|技术可行性|高|Java Web、数据库、音频播放、文件上传、部署链路均成熟|采用 Spring Boot + MySQL + 对象存储/本地文件存储 + Nginx|
|课程实现可行性|高|可按 MVP → 增强 → 部署分阶段完成|先做核心功能，再做交互创新|
|UI 实现可行性|高|Apple Music 风格可通过卡片、侧边栏、底部播放器、封面渐变实现|不直接复制 Apple 素材，保持自定义品牌|
|音乐源可行性|中|音乐版权是主要风险；商业平台音乐不可直接抓取或热链|使用自有/授权/CC 音乐、短试听样例或 MusicKit 授权接入|
|部署稳定性|高|Docker Compose、Nginx 反向代理、MySQL 持久卷、日志监控可覆盖课程上线需求|采用单机稳定部署，保留扩展接口|
|创新交互可行性|中高|交互创新主要在前端与业务模型层，难度可控|将推荐算法做成“规则推荐”优先，避免过度复杂|

**总体结论：可行。** 推荐先完成一个可上线的 MVP，再逐步加入视觉与交互亮点。项目难点不在播放本身，而在**音乐源合规、播放器状态管理、数据库模型设计、部署稳定性与 UI 一致性**。

\---

## 3\. 推荐技术路线

### 3.1 稳定优先技术栈

|层级|推荐方案|说明|
|-|-|-|
|后端|Java 17+、Spring Boot、Spring MVC、Spring Security、Spring Validation|现代 Java Web 主流路线，便于部署与维护|
|ORM|MyBatis-Plus 或 MyBatis|对课程项目友好，SQL 可控，便于展示数据库能力|
|数据库|MySQL 8.4 LTS 或 MySQL 8.x|歌曲、用户、歌单、收藏、评论等结构化数据|
|缓存|Redis，可选|播放榜单、验证码、登录态、热门搜索缓存|
|文件/音乐存储|开发期本地磁盘；进阶期 MinIO/S3 兼容对象存储；生产期云对象存储|避免把大音频直接塞进数据库|
|前端|方案 A：Thymeleaf + 原生 JS；方案 B：Vue 3 + Vite + REST API|课程展示推荐 Vue；若老师要求传统 Java Web，可用 Thymeleaf/JSP|
|播放器|HTML5 `<audio>` + 自定义播放控制条|支持播放、暂停、进度、音量、上一首/下一首、播放队列|
|部署|Docker Compose + Nginx + MySQL + 后端服务|易复现、易迁移、适合云服务器|
|监控|Spring Boot Actuator + 日志文件 + 健康检查|能展示线上稳定性意识|

### 3.2 两种实现路径

#### 路径 A：课程稳妥型

适合时间较紧、需要确保交付：

```text
Spring Boot + Thymeleaf + MyBatis-Plus + MySQL + 本地文件存储 + Nginx
```

优点：

* 后端与页面在一个项目中，部署简单。
* 学校验收更容易看到 Java Web 技术点。
* 不需要处理前后端跨域、前端工程构建等额外问题。

缺点：

* 交互体验不如前后端分离灵活。
* 全局播放器跨页面保持状态较麻烦，建议使用局部页面刷新或单页播放区域。

#### 路径 B：展示增强型（推荐）

适合希望 UI 与交互更出彩：

```text
Spring Boot REST API + Vue 3/Vite + MyBatis-Plus + MySQL + MinIO/本地存储 + Nginx
```

优点：

* 更适合 Apple Music 风格的单页应用体验。
* 全局播放器、播放队列、歌词同步、拖拽歌单更容易实现。
* 前后端职责清晰，更适合后续部署与扩展。

缺点：

* 初期工程量更大。
* 需要处理 Token 登录、跨域、打包、Nginx 路由转发。

**建议选择：路径 B。** 若时间不足，可先完成传统 Java Web 或后端核心 CRUD，再补充 Vue 前端展示与播放器体验。

\---

## 4\. 系统总体架构

```text
用户浏览器
  │
  ├── Vue/Thymeleaf 页面
  │     ├── 首页/发现页
  │     ├── 歌曲详情页
  │     ├── 歌单页
  │     ├── 搜索页
  │     ├── 个人中心
  │     └── 后台管理页
  │
  ├── HTML5 Audio 全局播放器
  │     ├── 当前播放歌曲
  │     ├── 播放队列
  │     ├── 进度/音量/循环模式
  │     └── 歌词同步
  │
Nginx
  ├── 静态资源服务
  ├── /api 转发到 Spring Boot
  └── /media 转发到对象存储或后端媒体接口
  │
Spring Boot 后端
  ├── Auth 模块
  ├── User 模块
  ├── Music 模块
  ├── Album/Artist 模块
  ├── Playlist 模块
  ├── Search 模块
  ├── Comment 模块
  ├── Admin 模块
  ├── Recommend 模块
  └── Upload/Media 模块
  │
数据与存储
  ├── MySQL：结构化数据
  ├── Redis：缓存，可选
  └── 本地磁盘/MinIO/云对象存储：音频、封面、歌词文件
```

\---

## 5\. 功能规划

### 5.1 MVP 必做功能

|模块|功能|说明|
|-|-|-|
|用户模块|注册、登录、退出、个人资料、头像|支持普通用户与管理员|
|音乐模块|歌曲列表、歌曲详情、按歌手/专辑查看|每首歌包含标题、歌手、封面、时长、音频地址|
|播放模块|播放、暂停、上一首、下一首、进度条、音量、播放队列|使用 HTML5 audio，自定义 UI|
|歌单模块|创建歌单、编辑歌单、添加/移除歌曲、公开/私有|项目核心业务之一|
|收藏模块|收藏歌曲、收藏歌单、取消收藏|与个人中心联动|
|搜索模块|按歌曲、歌手、专辑、歌单搜索|支持模糊搜索|
|评论模块|歌曲评论、歌单评论、删除自己的评论|注意敏感词与后台删除能力|
|后台模块|歌曲管理、歌手管理、专辑管理、用户管理、评论管理|展示完整管理闭环|
|部署模块|Docker Compose、Nginx、数据库初始化脚本|保证可以服务器上线|

### 5.2 增强功能

|功能|价值|难度|
|-|-|-|
|歌词滚动高亮|增强播放体验|中|
|每日推荐|增强个性化|中|
|热门榜单|首页更像真实音乐平台|低|
|最近播放|提升用户粘性|低|
|播放历史时间线|新颖且容易展示|中|
|封面主色渐变背景|提升 Apple Music 风格质感|中|
|拖拽调整歌单顺序|交互亮点明显|中|
|情绪/场景电台|新颖功能，可用标签规则实现|中|
|音乐卡片悬浮预览|类似“发现”体验|中|
|多端响应式布局|手机端展示更完整|中|

### 5.3 创新交互建议

#### 1）情绪/场景听歌

用户选择当前状态：

```text
学习 / 通勤 / 深夜 / 运动 / 放松 / 雨天 / 失眠 / 快乐 / 治愈
```

系统根据歌曲标签、节奏、语言、收藏记录做规则推荐：

```text
场景标签 + 用户收藏风格 + 最近播放去重 + 热度权重
```

课程项目无需复杂机器学习，可先做“规则推荐”，展示效果足够明显。

#### 2）封面氛围背景

播放歌曲时读取封面主色，生成播放器背景：

```text
封面主色 → 渐变背景 → 毛玻璃播放条 → 歌词区域高亮
```

实现方式：前端使用 canvas 或后端预处理封面主色。若时间紧，可在歌曲表中存 `dominant\_color` 字段，由管理员录入或上传时自动生成。

#### 3）沉浸播放页

点击底部播放器后进入全屏播放页：

* 左侧：大封面与动态背景。
* 中间：同步歌词。
* 右侧：播放队列与相关推荐。
* 底部：控制条与播放模式。

#### 4）歌单故事模式

为歌单增加“简介卡片”：

* 歌单封面。
* 创建者一句话描述。
* 标签。
* 推荐理由。
* 歌曲情绪曲线。

这能把普通歌单 CRUD 变成更有产品感的功能。

#### 5）拖拽式歌单编辑

用户在歌单详情页拖拽歌曲顺序，后端保存 `sort\_order`。这是很适合课程演示的交互点。

\---

## 6\. 数据库设计建议

### 6.1 核心实体

```text
User 用户
Role 角色
Artist 歌手
Album 专辑
Song 歌曲
SongTag 歌曲标签
Playlist 歌单
PlaylistSong 歌单-歌曲关系
Favorite 收藏
Comment 评论
PlayHistory 播放历史
Lyric 歌词
UploadFile 文件资源
```

### 6.2 建议表结构

#### user

|字段|类型|说明|
|-|-|-|
|id|bigint|主键|
|username|varchar(50)|用户名，唯一|
|password\_hash|varchar(255)|密码哈希|
|nickname|varchar(50)|昵称|
|avatar\_url|varchar(500)|头像|
|role|varchar(20)|USER / ADMIN|
|status|tinyint|1 正常，0 禁用|
|created\_at|datetime|创建时间|
|updated\_at|datetime|更新时间|

#### song

|字段|类型|说明|
|-|-|-|
|id|bigint|主键|
|title|varchar(100)|歌名|
|artist\_id|bigint|歌手 ID|
|album\_id|bigint|专辑 ID，可为空|
|cover\_url|varchar(500)|封面地址|
|audio\_url|varchar(500)|音频地址|
|lyric\_url|varchar(500)|歌词文件地址|
|duration|int|秒|
|language|varchar(20)|语言|
|genre|varchar(50)|风格|
|mood|varchar(50)|情绪标签|
|play\_count|bigint|播放量|
|status|tinyint|1 上架，0 下架|
|created\_at|datetime|创建时间|
|updated\_at|datetime|更新时间|

#### playlist

|字段|类型|说明|
|-|-|-|
|id|bigint|主键|
|user\_id|bigint|创建者|
|title|varchar(100)|歌单名|
|description|varchar(500)|描述|
|cover\_url|varchar(500)|封面|
|visibility|varchar(20)|PUBLIC / PRIVATE|
|play\_count|bigint|播放量|
|favorite\_count|bigint|收藏量|
|created\_at|datetime|创建时间|
|updated\_at|datetime|更新时间|

#### playlist\_song

|字段|类型|说明|
|-|-|-|
|id|bigint|主键|
|playlist\_id|bigint|歌单 ID|
|song\_id|bigint|歌曲 ID|
|sort\_order|int|歌曲顺序|
|created\_at|datetime|添加时间|

#### play\_history

|字段|类型|说明|
|-|-|-|
|id|bigint|主键|
|user\_id|bigint|用户 ID|
|song\_id|bigint|歌曲 ID|
|progress\_seconds|int|最近播放进度|
|played\_at|datetime|播放时间|
|source\_type|varchar(30)|首页/歌单/搜索/推荐|

### 6.3 索引建议

|表|索引|用途|
|-|-|-|
|user|unique(username)|登录查询|
|song|index(title), index(artist\_id), index(album\_id), index(status)|搜索与列表|
|playlist|index(user\_id), index(visibility), index(play\_count)|用户歌单与热门歌单|
|playlist\_song|unique(playlist\_id, song\_id), index(playlist\_id, sort\_order)|歌单详情排序|
|favorite|unique(user\_id, target\_type, target\_id)|防止重复收藏|
|play\_history|index(user\_id, played\_at)|最近播放与推荐|
|comment|index(target\_type, target\_id, created\_at)|评论列表|

\---

## 7\. 音乐源与版权方案

### 7.1 推荐策略

|场景|推荐做法|风险|
|-|-|-|
|课程开发|使用自己上传的测试音频、无版权争议素材、CC 授权音乐|低|
|展示上线|仅提供短试听样例或已授权音乐|低|
|商业平台音乐|不抓取、不盗链、不下载、不绕过 DRM|高风险，避免|
|Apple Music 风格|可参考交互与视觉逻辑，但不要复制资源|中低|
|第三方授权接入|使用 MusicKit / Apple Music API 等官方授权方式|需要账号、Token 与用户授权|

### 7.2 音频存储方案

#### 开发期

```text
/media/audio/xxx.flac
/media/cover/xxx.jpg
/media/lyrics/xxx.lrc
```

当前项目已按 Spring Boot 静态资源目录创建本地媒体路径：

```text
src/main/resources/static/media/
  ├── audio/
  ├── cover/
  └── lyrics/
```

其中 `audio/` 已放入 13 首本地 FLAC 演示音频，曲目索引见：

```text
src/main/resources/static/media/audio/README.md
```

静态曲目 JSON 清单见：

```text
src/main/resources/static/media/audio/catalog.json
```

在后端数据库接口完成前，前端可临时请求 `/media/audio/catalog.json` 测试歌曲列表、播放队列和 `<audio>` 播放能力。

后端保存 URL，前端通过 `<audio src="...">` 播放。由于文件名包含中文、空格和括号，前端生成播放地址时建议对文件名做 URL 编码；例如：

```text
/media/audio/%E5%A4%AA%E9%98%B3%E4%B9%8B%E5%AD%90%20-%20%E5%91%A8%E6%9D%B0%E4%BC%A6.flac
```

FLAC 适合作为高音质演示文件，但文件体积较大。若后续需要公网演示或移动端访问，建议补充 MP3/AAC 转码版本，并在数据库中保留 `audio_url` 与 `audio_format` 字段。

#### 部署期

```text
Nginx 静态目录 / 对象存储 bucket / 云 OSS
```

建议：

* 小规模课程演示：服务器本地目录 + Nginx 静态资源。
* 更稳定方案：MinIO 或云对象存储，数据库只保存资源 URL。
* 避免把 MP3、封面二进制直接存 MySQL。

### 7.3 音频播放注意事项

* 支持常见格式：开发演示可直接使用当前 FLAC 文件；公网部署或移动端访问建议 MP3/AAC 优先。
* 处理加载失败、音频不存在、网络中断。
* 播放量不要在每次 `timeupdate` 时更新，可在播放超过 30 秒后更新一次。
* 进度条拖动使用 `currentTime`。
* 歌曲结束事件触发下一首。
* 移动端需要用户主动点击后才能开始播放，避免自动播放限制。

\---

## 8\. UI 风格方案

### 8.1 设计关键词

```text
现代、干净、音乐感、留白、圆角、大封面、沉浸感、轻毛玻璃、柔和阴影、响应式
```

### 8.2 页面布局建议

#### 首页 / 新发现

* 左侧固定侧边栏：Logo、搜索、首页、发现、歌单、我的音乐、后台入口。
* 顶部区域：欢迎语、搜索框、用户头像。
* 主视觉卡片：推荐歌单、今日热门、场景电台。
* 榜单区域：热门歌曲、新歌、收藏趋势。
* 底部全局播放器：始终可见。

#### 歌曲详情页

* 大封面 + 歌曲信息。
* 播放/收藏/加入歌单按钮。
* 歌词滚动。
* 相似歌曲推荐。
* 评论区。

#### 歌单详情页

* 歌单头图：封面、标题、创建者、标签、播放按钮。
* 歌曲列表：序号、封面、歌名、歌手、时长、操作。
* 支持拖拽排序。

#### 沉浸播放页

* 动态渐变背景。
* 中心封面。
* 歌词高亮。
* 播放队列。
* 推荐下一首。

### 8.3 视觉实现建议

* 主色：可用红色、粉色、紫色、蓝色渐变，避免直接使用 Apple Music 完全相同的品牌风格。
* 圆角：卡片 16px–24px，播放器 24px。
* 阴影：浅阴影，突出卡片层次。
* 字体：中文可用系统字体栈：`-apple-system, BlinkMacSystemFont, "PingFang SC", "Microsoft YaHei", sans-serif`。
* 播放条：固定底部，半透明背景，backdrop-filter 毛玻璃效果。
* 封面：统一 1:1 比例，缺省封面使用渐变占位图。

\---

## 9\. 后端接口规划

### 9.1 用户与认证

|方法|路径|功能|
|-|-|-|
|POST|/api/auth/register|注册|
|POST|/api/auth/login|登录|
|POST|/api/auth/logout|退出|
|GET|/api/users/me|当前用户信息|
|PUT|/api/users/me|修改资料|

### 9.2 音乐接口

|方法|路径|功能|
|-|-|-|
|GET|/api/songs|歌曲分页|
|GET|/api/songs/{id}|歌曲详情|
|GET|/api/songs/{id}/stream|音频播放地址或流式输出|
|POST|/api/songs/{id}/play-record|记录播放|
|GET|/api/artists|歌手列表|
|GET|/api/albums|专辑列表|
|GET|/api/search|综合搜索|

### 9.3 歌单接口

|方法|路径|功能|
|-|-|-|
|GET|/api/playlists|歌单分页|
|POST|/api/playlists|创建歌单|
|GET|/api/playlists/{id}|歌单详情|
|PUT|/api/playlists/{id}|修改歌单|
|DELETE|/api/playlists/{id}|删除歌单|
|POST|/api/playlists/{id}/songs|添加歌曲|
|DELETE|/api/playlists/{id}/songs/{songId}|移除歌曲|
|PUT|/api/playlists/{id}/songs/order|调整排序|

### 9.4 评论与收藏

|方法|路径|功能|
|-|-|-|
|POST|/api/favorites|收藏|
|DELETE|/api/favorites|取消收藏|
|GET|/api/comments|评论列表|
|POST|/api/comments|发布评论|
|DELETE|/api/comments/{id}|删除评论|

### 9.5 后台接口

|方法|路径|功能|
|-|-|-|
|POST|/api/admin/songs|新增歌曲|
|PUT|/api/admin/songs/{id}|修改歌曲|
|DELETE|/api/admin/songs/{id}|下架歌曲|
|POST|/api/admin/upload|上传音频/封面/歌词|
|GET|/api/admin/dashboard|统计面板|

\---

## 10\. 稳定性设计

### 10.1 后端稳定性

* 所有接口统一返回结构：`code / message / data / timestamp`。
* 全局异常处理：参数错误、权限错误、资源不存在、文件上传失败、数据库异常。
* 参数校验：注册、登录、评论、上传、歌单编辑必须校验。
* 权限控制：管理员接口必须限制角色。
* 密码安全：只保存哈希，不保存明文。
* 文件上传限制：大小、类型、扩展名、MIME 校验。
* 音频资源不存在时返回友好错误，不让播放器卡死。
* 日志分级：INFO 记录业务关键行为，ERROR 记录异常堆栈。
* 接口分页：歌曲、歌单、评论、后台列表必须分页。

### 10.2 前端稳定性

* 播放器状态集中管理：当前歌曲、队列、播放模式、音量、进度。
* 音频事件完整处理：`loadedmetadata`、`timeupdate`、`ended`、`error`、`waiting`、`canplay`。
* 加载态与空状态：搜索无结果、歌单为空、封面加载失败、音频加载失败。
* 断网提示：请求失败时 Toast 提示。
* 防重复提交：登录、评论、上传、收藏操作加 loading 锁。
* 响应式：宽屏、笔记本、平板、手机至少四档断点。

### 10.3 数据稳定性

* 数据库初始化脚本：`schema.sql` + `data.sql`。
* 重要表加唯一约束，避免重复收藏、重复歌单歌曲。
* 删除策略：歌曲建议软删除/下架，避免历史歌单断链。
* 定期备份：MySQL dump + 媒体目录备份。
* 音频文件与数据库记录一致性检查：后台提供“失效资源扫描”。

### 10.4 部署稳定性

* 使用 Docker Compose 固化服务依赖。
* MySQL 使用 volume 持久化。
* Nginx 负责静态资源、反向代理、HTTPS 可选。
* 后端暴露健康检查接口。
* 生产配置用环境变量，不把数据库密码写死进代码仓库。
* 服务器开放端口最小化：80/443/SSH，数据库不直接暴露公网。

\---

## 11\. 推荐部署方案

### 11.1 单机 Docker Compose 架构

```text
云服务器
  ├── nginx 容器：80/443，静态页面与反向代理
  ├── music-backend 容器：Spring Boot API
  ├── mysql 容器：数据库，volume 持久化
  ├── redis 容器：可选缓存
  └── minio 容器：可选对象存储
```

### 11.2 部署目录建议

```text
/opt/music-web/
  ├── docker-compose.yml
  ├── nginx/
  │   └── default.conf
  ├── backend/
  │   └── music-web.jar
  ├── frontend/
  │   └── dist/
  ├── mysql/
  │   └── data/
  ├── media/
  │   ├── audio/
  │   ├── cover/
  │   └── lyric/
  └── backup/
```

### 11.3 Nginx 路由建议

```text
/             → 前端静态页面
/api/         → Spring Boot 后端
/media/       → 静态音频、封面、歌词
/admin/       → 后台页面，或前端路由
```

### 11.4 环境变量建议

```text
SPRING\_PROFILES\_ACTIVE=prod
MYSQL\_HOST=mysql
MYSQL\_PORT=3306
MYSQL\_DATABASE=music\_web
MYSQL\_USERNAME=music\_user
MYSQL\_PASSWORD=强密码
JWT\_SECRET=强随机密钥
MEDIA\_BASE\_URL=https://your-domain.com/media
```

\---

## 12\. 分步骤实施计划与门禁

> 本节按“先后依赖”设计，而不是按固定日期设计。实际开发时可根据课程周期压缩或拉长每一步，但必须保证上一步的门禁通过后再进入下一步。

### 步骤 1：准备与原型

**当前状态：已完成。** 已锁定技术路线、MVP 边界、页面范围、素材策略、ER 初稿和 API 初稿；步骤一产出已归档到 `docs/step-1/`。

**任务：**

* [x] 确定最终技术栈和项目目录。
* [x] 输出页面清单：首页、搜索页、歌曲详情页、歌单详情页、个人中心、后台管理、沉浸播放页可选。
* [x] 完成 ER 图和核心表字段。
* [x] 准备测试素材策略：本地 FLAC 保留本机，提交音频清单与素材来源说明。
* [x] 写出第一版接口清单。

**门禁：**

* [x] ER 图能覆盖注册登录、歌曲、歌单、收藏、评论、最近播放。
* [x] 至少准备 5 首可演示音频；当前本地目录已有 13 首 FLAC，远程仓库只保存 `catalog.json` 和说明文档。
* [x] 明确哪些功能是 P0、P1、P2。

### 步骤 2：后端底座

**当前状态：已完成。** 已完成 Spring Boot 后端工程、MySQL/MyBatis-Plus 数据模型、统一响应、全局异常处理、JWT 登录权限、管理员鉴权、接口级集成测试，并通过 Docker 临时 MySQL 8.4 容器验证 `schema.sql` 与 `data.sql`；步骤二产出已归档到 `docs/step-2/`。

**任务：**

* 搭建 Spring Boot 项目，配置 MySQL、MyBatis-Plus/MyBatis。
* 完成统一返回结构、全局异常处理、参数校验。
* 完成用户注册、登录、退出、当前用户信息接口。
* 完成管理员角色校验。
* 完成歌曲、歌手、专辑、歌单基础实体与 Mapper/Service。
* 提交 `schema.sql` 和基础 `data.sql`。

**门禁：**

* Postman/接口工具可完成注册、登录、获取当前用户。
* 普通用户访问管理员接口会被拒绝。
* 数据库能从脚本重新初始化。

### 步骤 3：音乐内容管理闭环

**当前状态：已完成。** 已完成公开歌曲分页、详情、搜索、歌手/专辑查询、管理员歌曲/歌手/专辑管理、本地真实媒体上传、`/media/**` 静态访问和上架/下架闭环；步骤三产出已归档到 `docs/step-3/`。

**任务：**

* 完成歌曲分页、详情、搜索接口。
* 完成音频、封面、歌词上传接口，并保存资源 URL。
* 完成后台歌曲上架、下架、编辑接口。
* 完成歌手、专辑、分类或标签的基础管理接口。
* 完成媒体资源访问路径，确保音频、封面、歌词都能被前端读取。

**门禁：**

* 管理员上传歌曲后，普通用户能通过接口查到该歌曲。
* 歌曲能返回可访问的音频 URL。
* 下架歌曲不会出现在普通用户可播放列表中。

### 步骤 4：用户核心业务闭环

**当前状态：已完成。** 已完成歌单创建、编辑、删除、添加歌曲、移除歌曲、排序，收藏、评论、最近播放和综合搜索歌单联动；步骤四产出已归档到 `docs/step-4/`。

**任务：**

* [x] 完成歌单创建、编辑、删除、添加歌曲、移除歌曲、排序接口。
* [x] 完成收藏、取消收藏、评论、删除评论接口。
* [x] 完成播放记录/最近播放接口。
* [x] 完成搜索接口的基础联动：歌曲、歌手、专辑、歌单。
* [x] 完成个人中心所需的收藏、歌单、最近播放数据接口。

**门禁：**

* [x] 用户能创建歌单并添加歌曲。
* [x] 收藏和评论数据能持久化。
* [x] 最近播放记录能按用户查询。
* [x] 普通用户只能修改自己的歌单、收藏和评论。

### 步骤 5：前端主流程与播放器

**当前状态：已完成。** 前端已统一为 MeloSpace 品牌，完成固定侧栏、全局播放器、沉浸歌词页、歌曲行主流交互、三点菜单、队列续播和最近播放歌曲摘要展示；产出见 `docs/step-5/`。

**任务：**

* 搭建 Vue 3/Vite 前端项目和路由。
* 完成登录页、首页/发现页、搜索页、歌曲详情页、歌单页、个人中心、后台页。
* 完成全局播放器：播放、暂停、进度、音量、上一首、下一首、队列。
* 完成接口联调与登录态保存。
* 完成基础响应式布局。
* 处理加载态、空状态、错误态。

**门禁：**

* 普通用户能完成“登录 → 搜索 → 播放 → 收藏 → 建歌单 → 评论”。
* 管理员能完成“登录后台 → 上传歌曲 → 上架 → 前台播放”。
* 播放器音频加载失败时页面不崩溃。

### 步骤 6：演示亮点

**当前状态：已完成。** 当前创新点限定在已有页面与已有业务基础上的交互优化，已完成歌单创建者拖拽排序、上移/下移辅助排序、歌曲栏跳转歌手主页、歌曲库播放队列优化、发现页单屏布局与推荐封面预加载、全局播放器贴底固定、字体图标体系、固定玻璃标题栏、个人中心删除收藏/清空最近播放和 `prefers-reduced-motion` 动效降级；步骤六产出记录在 `docs/step-6/`。

**任务：**

* [x] 优先完成歌词高亮。
* [x] 优先完成拖拽歌单排序。
* [x] 选择性完成已有页面的交互增强，不新增独立场景电台模块。
* [x] 统一核心页面视觉风格。
* [x] 补充演示数据，让首页、榜单、推荐区域不空。

**门禁：**

* [x] 至少 2 个创新交互能稳定演示。
* [x] 页面风格统一，播放器始终容易找到。
* [x] P2 功能若影响 P0 稳定，立即回滚或降级。

### 步骤 7：测试、部署与答辩材料

**当前状态：已完成。** 已完成后端集成测试、前端生产构建、公网 HTTP 复测、ECS 只读复测、验收清单填写和答辩演示脚本归档；步骤七产出记录在 `docs/step-7/`。

**任务：**

* [x] 编写并执行功能测试清单。
* [x] 执行权限、上传、播放、部署、数据持久化测试。
* [x] 沿用当前阿里云 Nginx + systemd + MySQL 部署口径完成复测。
* [x] 完成服务器公网 HTTP 和 SSH 只读复测。
* [x] 补全 README、部署说明、测试说明和验收记录。
* [x] 准备答辩演示脚本。

**门禁：**

* [x] 公网环境可通过 IP 和域名访问。
* [x] MySQL 服务和媒体目录保留，S7 本轮只读复测未执行重启演练。
* [x] 演示主线可连续跑完，无阻塞级 Bug。

\---

## 13\. 测试计划

|类型|测试内容|验收标准|
|-|-|-|
|单元测试|Service 层核心逻辑|收藏、歌单排序、推荐规则正确|
|接口测试|登录、歌曲、歌单、评论、上传|返回结构统一，错误可控|
|播放测试|不同歌曲、快进、上一首、下一首、加载失败|不白屏、不死循环、不崩溃|
|权限测试|普通用户访问后台、未登录收藏|正确拒绝|
|UI 测试|首页、详情、歌单、后台、移动端|布局不乱，核心功能可用|
|部署测试|重启容器、数据库持久化、静态资源访问|重启后数据不丢失|
|安全测试|SQL 注入、XSS 评论、上传恶意文件|参数化查询、转义、限制文件类型|

\---

## 14\. 风险与应对

|风险|影响|概率|应对|
|-|-|-:|-|
|音乐版权不清晰|无法公开部署|中|使用授权素材或自有素材，只做课程展示|
|播放器跨页面状态丢失|用户体验差|中|使用 SPA 或全局状态管理|
|文件上传与数据库记录不一致|歌曲无法播放|中|上传成功后再入库，后台提供失效扫描|
|UI 工作量过大|影响核心功能|中|先做组件库与页面骨架，再逐步美化|
|推荐算法复杂化|延误进度|中|先做标签规则推荐，不做机器学习|
|服务器部署失败|影响验收|中|提前 1 周部署，准备本地 Docker 演示备份|
|MySQL 数据丢失|严重|低中|volume 持久化 + 定期备份|
|上传大文件导致服务阻塞|性能下降|中|限制大小，后续接对象存储|

\---

## 15\. 最小可交付版本定义

MVP 必须满足：

1. 用户可以注册、登录、退出。
2. 用户可以浏览歌曲、搜索歌曲、查看歌曲详情。
3. 用户可以播放歌曲，控制播放/暂停/进度/音量/上一首/下一首。
4. 用户可以创建歌单，并向歌单添加歌曲。
5. 用户可以收藏歌曲或歌单。
6. 用户可以评论歌曲或歌单。
7. 管理员可以上传和管理歌曲、封面、歌词。
8. 系统可以通过 Docker Compose 部署到服务器。
9. 数据库与音频资源重启后不丢失。
10. 页面具备统一、现代、接近音乐平台的视觉风格。

\---

## 16\. 推荐项目目录

### 后端

```text
music-web-backend/
  ├── src/main/java/com/example/music/
  │   ├── MusicApplication.java
  │   ├── config/
  │   ├── controller/
  │   ├── service/
  │   ├── service/impl/
  │   ├── mapper/
  │   ├── entity/
  │   ├── dto/
  │   ├── vo/
  │   ├── common/
  │   ├── exception/
  │   ├── security/
  │   └── util/
  ├── src/main/resources/
  │   ├── mapper/
  │   ├── application.yml
  │   ├── application-dev.yml
  │   └── application-prod.yml
  ├── Dockerfile
  └── pom.xml
```

### 前端

```text
music-web-frontend/
  ├── src/
  │   ├── api/
  │   ├── assets/
  │   ├── components/
  │   │   ├── AppSidebar.vue
  │   │   ├── GlobalPlayer.vue
  │   │   ├── SongCard.vue
  │   │   └── PlaylistCard.vue
  │   ├── stores/
  │   │   ├── playerStore.ts
  │   │   └── userStore.ts
  │   ├── views/
  │   │   ├── DiscoverView.vue
  │   │   ├── SongDetailView.vue
  │   │   ├── PlaylistView.vue
  │   │   ├── SearchView.vue
  │   │   ├── ProfileView.vue
  │   │   └── AdminView.vue
  │   ├── router/
  │   └── main.ts
  ├── Dockerfile
  └── package.json
```

\---

## 17\. 演示亮点设计

演示时建议按以下顺序：

1. 打开首页：展示 Apple Music 风格启发的新发现页面。
2. 点击“今日推荐”：播放第一首歌，底部播放器出现。
3. 打开沉浸播放页：展示封面渐变背景与歌词高亮。
4. 搜索歌曲：展示综合搜索。
5. 创建歌单：拖拽调整歌曲顺序。
6. 切换场景：选择“深夜/学习/运动”，展示规则推荐。
7. 登录管理员：上传歌曲、封面、歌词。
8. 展示服务器部署：Nginx 域名访问、Docker Compose 服务运行、数据库持久化。

\---

## 18\. 额外要求

在项目实行中建议遵守以下规则，并随项目推进实时更新：

1. 每次完成一个可验证的小功能后进行 Git 提交，提交说明写清楚变更范围，例如 `feat: add song upload api`。
2. 数据库结构变化必须同步更新 `schema.sql` 或迁移脚本，并在文档中说明影响。
3. 新增接口必须同步更新接口说明，至少包含请求方法、路径、参数、返回结构和错误情况。
4. 新增页面或交互必须补充对应测试点，避免只做展示不做验收。
5. 音乐、封面、歌词素材必须记录来源，避免答辩或公开部署时出现版权风险。
6. 部署配置中的密码、密钥、服务器地址使用环境变量或示例占位，不写死真实敏感信息。
7. 每个阶段结束后更新一次项目进度，标记已完成、延期、取消和新增的任务。


\---

## 19\. 参考资料

以下为方案涉及的公开官方资料，便于后续选型核对：

* Spring Boot System Requirements: https://docs.spring.io/spring-boot/system-requirements.html
* Spring Boot Installing: https://docs.spring.io/spring-boot/installing.html
* Spring Boot Actuator Production-ready Features: https://docs.spring.io/spring-boot/reference/actuator/index.html
* Spring Boot Actuator Endpoints: https://docs.spring.io/spring-boot/reference/actuator/endpoints.html
* Docker Compose file reference: https://docs.docker.com/reference/compose-file/
* Docker Compose startup order: https://docs.docker.com/compose/how-tos/startup-order/
* MySQL 8.4 LTS release model: https://dev.mysql.com/doc/refman/8.4/en/mysql-releases.html
* NGINX Reverse Proxy: https://docs.nginx.com/nginx/admin-guide/web-server/reverse-proxy/
* MDN HTMLMediaElement: https://developer.mozilla.org/en-US/docs/Web/API/HTMLMediaElement
* Apple MusicKit: https://developer.apple.com/musickit/
* Apple Music API: https://developer.apple.com/documentation/applemusicapi/
* MinIO S3 compatibility: https://docs.min.io/aistor/developers/
