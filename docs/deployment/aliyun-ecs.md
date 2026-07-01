# MeloSpace 阿里云 ECS 提前部署记录

> 记录日期：2026-07-02  
> 部署目标：将原计划后置的服务器部署提前到步骤 5.5，在演示亮点增强前先暴露环境、依赖、构建和公网访问问题。

## 1. 服务器信息

|项目|内容|
|-|-|
|云厂商|阿里云 ECS|
|地域|美国硅谷|
|公网 IP|47.89.235.138|
|系统|Ubuntu 24.04|
|规格|2 vCPU / 2GB RAM / 40GB 系统盘|
|SSH 用户|root|
|SSH 端口|22|
|私钥|使用本机 `REX.pem` 登录，私钥不提交仓库|

## 2. 本次部署策略

当前仓库暂未提供 Dockerfile 与 docker-compose.yml，因此本次提前部署采用轻量单机方式：

|组件|方案|
|-|-|
|前端|服务器构建 Vue/Vite，Nginx 托管 `dist/`|
|后端|服务器构建 Spring Boot jar，systemd 管理后台服务|
|数据库|本机 MySQL，执行 `schema.sql` 和 `data.sql` 初始化|
|媒体资源|`/opt/melospace/media` 持久化，Nginx 通过后端 `/media/**` 访问|
|公网入口|Nginx 监听 80，`/api/` 与 `/media/` 反向代理到 `127.0.0.1:8080`|
|开机自启|启用 `mysql`、`nginx`、`melospace-backend` systemd 服务|

正式答辩前可继续补 Docker Compose 版本，但这次提前部署先保证公网可访问与运维命令完整。

## 3. 计划安装依赖

```bash
apt update
apt install -y git curl nginx mysql-server openjdk-17-jdk maven
curl -fsSL https://deb.nodesource.com/setup_20.x | bash -
apt install -y nodejs
```

实际安装验证：

|依赖|版本|
|-|-|
|Git|2.43.0|
|OpenJDK|17.0.19|
|Maven|3.8.7|
|Node.js|20.20.2|
|npm|10.8.2|
|MySQL|8.0.46|
|Nginx|1.24.0|
|Python|3.12.3|

## 4. 计划部署目录

```text
/opt/melospace/
  repo/                 # GitHub 仓库代码
  app/backend.jar        # 后端运行 jar
  frontend/dist/         # 前端构建产物
  media/                 # 音频、封面、歌词和后续上传文件
  lddc-src/              # LDDC v0.9.2 源码
  venv-lddc/             # LDDC 专用 Python 虚拟环境
  logs/                  # 后端日志
  env/backend.env        # 后端环境变量，包含数据库密码和 JWT secret，不提交仓库
```

## 5. 服务与访问

|项目|内容|
|-|-|
|前端访问地址|`http://47.89.235.138/`|
|域名访问地址|`http://melospace.asia/`，DNS A 记录生效后可用|
|www 访问地址|`http://www.melospace.asia/`，DNS A 记录生效后可用|
|后端健康检查|`http://47.89.235.138/api/actuator/health`|
|域名健康检查|`http://melospace.asia/api/actuator/health`|
|后端本机端口|`127.0.0.1:8080`|
|systemd 服务名|`melospace-backend`|
|Nginx 配置|`/etc/nginx/sites-available/melospace`|

### 5.1 域名接入

服务器侧 Nginx 已将 `melospace.asia` 和 `www.melospace.asia` 加入 `server_name`：

```nginx
server_name melospace.asia www.melospace.asia 47.89.235.138 _;
```

需要在阿里云云解析 DNS 中添加以下公网解析记录：

|记录类型|主机记录|记录值|解析线路|TTL|
|-|-|-|-|-|
|A|`@`|`47.89.235.138`|默认|600 秒或默认值|
|A|`www`|`47.89.235.138`|默认|600 秒或默认值|

当前外部 DNS 查询仅看到 `melospace.asia` 的 SOA 记录，尚未看到 A 记录；DNS 添加并生效后，浏览器即可直接访问 `http://melospace.asia/`。

## 6. 运维命令

### 查看日志

```bash
journalctl -u melospace-backend -f
systemctl status melospace-backend --no-pager -l
tail -f /var/log/nginx/access.log
tail -f /var/log/nginx/error.log
```

### 重启服务

```bash
systemctl restart melospace-backend
systemctl reload nginx
```

### 停止服务

```bash
systemctl stop melospace-backend
systemctl stop nginx
```

### 更新部署

```bash
cd /opt/melospace/repo
git pull --ff-only origin master
cd music-web-backend
mvn -DskipTests package
install -m 0644 target/music-web-backend-0.0.1-SNAPSHOT.jar /opt/melospace/app/backend.jar
cd ../music-web-frontend
npm ci
npm run build
rm -rf /opt/melospace/frontend/dist
cp -a dist /opt/melospace/frontend/dist
systemctl restart melospace-backend
systemctl reload nginx
```

服务器已补充一键更新脚本，日常可直接执行：

```bash
melospace-update
```

健康检查脚本：

```bash
melospace-health
```

## 7. 验证清单

* [x] `ssh root@47.89.235.138` 可登录。
* [x] `java -version`、`mvn -version`、`node -v`、`npm -v`、`mysql --version`、`nginx -v` 正常。
* [x] GitHub 仓库 `https://github.com/csyxcsyx/MeloSpace.git` 已拉取到 `/opt/melospace/repo`。
* [x] MySQL 已创建 `music_web` 数据库和 `music_user` 用户。
* [x] 后端服务 `melospace-backend` 为 `active (running)`。
* [x] Nginx 配置检查 `nginx -t` 通过。
* [x] `http://47.89.235.138/` 可打开前端。
* [x] `http://47.89.235.138/api/actuator/health` 返回 `UP`。
* [x] Nginx 已接受 `melospace.asia` 和 `www.melospace.asia` 的 Host 访问。
* [x] 服务器重启后服务已设置为 systemd 开机自启。

## 8. 本次执行结果

首次部署完成时间为 2026-07-02 01:34 CST 左右。应用首次构建验证版本为 `b9c6a1e`，提交说明为 `docs: move aliyun deployment earlier`。

已完成：

* 创建 2GB swap，降低 2GB 内存服务器构建时的风险。
* 安装 Git、OpenJDK 17、Maven、Node.js 20、npm、MySQL、Nginx。
* 拉取 `https://github.com/csyxcsyx/MeloSpace.git` 到 `/opt/melospace/repo`。
* 初始化 MySQL 数据库 `music_web` 和应用用户 `music_user`。
* 构建后端 jar 并部署到 `/opt/melospace/app/backend.jar`。
* 构建前端 dist 并部署到 `/opt/melospace/frontend/dist`。
* 同步本机演示媒体到 `/opt/melospace/media`，共 36 个文件，约 619MB。
* 写入并启用 `melospace-backend` systemd 服务。
* 写入 Nginx 站点配置并启用 80 端口访问。
* 新增服务器脚本 `melospace-update` 和 `melospace-health`。

已验证：

* `http://47.89.235.138/` 返回前端页面，HTTP 200。
* `http://47.89.235.138/api/actuator/health` 返回 `{"status":"UP"}`。
* `http://47.89.235.138/api/songs?page=1&size=2` 返回歌曲数据。
* 示例媒体文件 `/media/audio/I%20Do%20-%20%E5%91%A8%E6%9D%B0%E4%BC%A6.flac` 返回 HTTP 200。

## 9. 域名接入执行结果

执行时间为 2026-07-02 01:57 CST 左右。

已完成：

* 修改服务器 Nginx 配置，将 `melospace.asia` 和 `www.melospace.asia` 加入 `server_name`。
* 执行 `nginx -t` 检查通过，并 reload Nginx。
* 使用 Host header 在服务器本机验证前端和健康检查通过。
* 使用 `curl --resolve` 从本机绕过 DNS 验证公网链路通过。

已验证：

```bash
curl --noproxy "*" --resolve melospace.asia:80:47.89.235.138 -I http://melospace.asia/
curl --noproxy "*" --resolve melospace.asia:80:47.89.235.138 http://melospace.asia/api/actuator/health
curl --noproxy "*" --resolve www.melospace.asia:80:47.89.235.138 http://www.melospace.asia/api/actuator/health
```

结果分别为前端 HTTP 200、主域名健康检查 `UP`、www 健康检查 `UP`。剩余动作是等待阿里云 DNS 中的 A 记录生效。

## 10. LDDC 歌词导入修复记录

执行时间为 2026-07-02 02:12 CST 左右。

问题现象：

* 管理后台点击“用 LDDC 匹配歌词”时报错 `LDDC 歌词导入脚本不存在`。
* 原因是后端服务运行目录为 `/opt/melospace/app`，默认相对路径会解析到 `/opt/melospace/scripts/import_lddc_lyrics.py`；真实脚本位于 `/opt/melospace/repo/scripts/import_lddc_lyrics.py`。
* 线上服务器也缺少 LDDC 源码、Python 虚拟环境和运行依赖。

已完成：

* 安装 `python3-venv` 和 `python3-pip`。
* 克隆 `chenmozhijin/LDDC` v0.9.2 到 `/opt/melospace/lddc-src`。
* 创建 LDDC 专用虚拟环境 `/opt/melospace/venv-lddc`。
* 安装 LDDC 运行依赖：`PySide6-Essentials`、`httpx[brotli,http2]`、`mutagen`、`diskcache`、`charset-normalizer`、`pyaes`、`psutil`。
* 为 `melospace` 用户创建 `/opt/melospace/.config`、`/opt/melospace/.cache`、`/opt/melospace/.local/share`，解决 LDDC 写配置目录权限问题。
* 在 `/opt/melospace/env/backend.env` 中加入 LDDC 生产配置，并重启后端。

当前 LDDC 环境变量：

```bash
MUSIC_WEB_LDDC_PYTHON=/opt/melospace/venv-lddc/bin/python
MUSIC_WEB_LDDC_SCRIPT_PATH=/opt/melospace/repo/scripts/import_lddc_lyrics.py
MUSIC_WEB_LDDC_SRC_PATH=/opt/melospace/lddc-src
MUSIC_WEB_LDDC_LYRICS_DIR=/opt/melospace/media/lyrics
MUSIC_WEB_LDDC_TIMEOUT_SECONDS=240
```

已验证：

* `scripts/import_lddc_lyrics.py --help` 可在服务器上正常执行。
* 使用 LDDC dry-run 匹配 `I Do / 周杰伦 / 太阳之子` 成功，来源为 `KG`。
* 通过后端接口 `POST /api/admin/lyrics/lddc` 验证成功，返回 `code: 0` 和歌词 URL `/media/lyrics/%E5%91%A8%E6%9D%B0%E4%BC%A6%20-%20I%20Do.lrc`。

常用排查命令：

```bash
melospace-health
journalctl -u melospace-backend -f
grep '^MUSIC_WEB_LDDC' /opt/melospace/env/backend.env
sudo -u melospace /opt/melospace/venv-lddc/bin/python /opt/melospace/repo/scripts/import_lddc_lyrics.py --help
```
