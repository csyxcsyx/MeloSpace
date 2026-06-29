# 步骤二后端基础架构说明

## 1. 完成内容

- 新增 `music-web-backend/` Spring Boot 后端工程，使用 Java 17、Spring Boot 3.5.16、Maven Wrapper。
- 接入 Spring Security、JWT、MyBatis-Plus、MySQL、Validation、Actuator。
- 新增核心数据表脚本：`schema.sql` 和 `data.sql`，覆盖用户、歌曲、歌单、收藏、评论、播放历史和上传文件。
- 新增统一 JSON 响应、全局异常处理、JWT 无状态认证、管理员角色鉴权。
- 新增注册、登录、退出、当前用户、管理员 dashboard、健康检查接口。

## 2. 当前接口

|方法|路径|说明|
|-|-|-|
|POST|`/api/auth/register`|注册普通用户并返回 JWT。|
|POST|`/api/auth/login`|登录并返回 JWT。|
|POST|`/api/auth/logout`|无状态退出，前端删除本地 Token 即可。|
|GET|`/api/users/me`|获取当前登录用户，需要 `Authorization: Bearer <token>`。|
|GET|`/api/admin/dashboard`|管理员权限验证接口，仅 `ADMIN` 可访问。|
|GET|`/actuator/health`|健康检查。|

统一响应结构：

```json
{
  "code": 0,
  "message": "ok",
  "data": {},
  "timestamp": "2026-06-29T23:30:00+08:00"
}
```

## 3. 默认账号

|角色|用户名|密码|
|-|-|-|
|管理员|`admin`|`Admin@123456`|
|普通用户|`demo`|`User@123456`|

数据库只保存 BCrypt 哈希，不保存明文密码。

## 4. 本地验证

当前可执行：

```powershell
$env:JAVA_HOME='C:\Program Files\Java\jdk-21.0.10'
cd music-web-backend
.\mvnw.cmd test
```

Docker CLI 已安装，但本次验证时 Docker Desktop Linux engine 未启动，因此临时 MySQL 8.4 容器加载 `schema.sql` / `data.sql` 的验证未执行。Docker 启动后可重新执行该项验证。
