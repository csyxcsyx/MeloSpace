# MeloSpace

MeloSpace 是一个 Java Web 音乐网站课程项目，基于 Spring Boot REST API、Vue 3、MySQL 和 Nginx 实现。项目支持歌曲浏览、搜索、播放、歌单、收藏、评论、后台管理、媒体上传和 LDDC 歌词匹配。

## 在线访问

- 站点：<http://melospace.asia/>
- 健康检查：<http://melospace.asia/api/actuator/health>

## 技术栈

- 后端：Java 17、Spring Boot 3.5、Spring Security、MyBatis-Plus、MySQL
- 前端：Vue 3、Vite、Pinia、Vue Router、Axios
- 部署：Ubuntu 24.04、Nginx、systemd、MySQL、本地媒体目录
- 歌词：本地 LDDC 源码 + Python 虚拟环境

## 项目结构

```text
music-web-backend/       Spring Boot 后端
music-web-frontend/      Vue 前端
scripts/                 辅助脚本，例如 LDDC 歌词导入
docs/                    阶段文档与部署记录
src/main/resources/static/media/
                          本地演示媒体目录
```

## 本地运行

建议使用 Java 17+ 和 Node.js 20+。本项目当前前端依赖不支持 Node.js 14；若本机存在多个 Node 版本，请先切换到 Node 20 或更高版本。

后端：

```bash
cd music-web-backend
./mvnw spring-boot:run
```

前端：

```bash
cd music-web-frontend
npm install
npm run dev
```

默认前端开发地址为 `http://127.0.0.1:5173/`。后端默认使用 `dev` 配置，需要本地 MySQL 可用，并按 `music-web-backend/src/main/resources/db/` 中的脚本初始化数据库。

## 本地验证

后端集成测试：

```bash
cd music-web-backend
./mvnw test
```

前端生产构建：

```bash
cd music-web-frontend
npm run build
```

Windows PowerShell 下可使用 `.\mvnw.cmd test` 和 `.\mvnw.cmd spring-boot:run`。

## 服务器运维

```bash
# 健康检查
melospace-health

# 更新部署
melospace-update

# 查看后端日志
journalctl -u melospace-backend -f

# 重启后端
systemctl restart melospace-backend
```

详细部署记录见 `docs/deployment/aliyun-ecs.md`。

## 答辩与验收

- 步骤七测试、部署复测和答辩脚本：`docs/step-7/testing-deployment-defense.md`
- 项目宪章：`music_web_project_charter.md`
- 项目计划：`music_web_feasibility_stable_plan.md`

截至 2026-07-07，后端集成测试、前端生产构建、公网访问、健康检查、歌曲接口、媒体访问和 ECS 只读服务检查均已通过。

## 说明

项目中的音乐、封面和歌词素材仅用于课程演示与功能验证。公开展示或继续扩展曲库前，应确认素材授权范围。
