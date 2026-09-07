# Logan 在 Rainbond 上的部署研究

> 调研日期：2026-09-04  
> 仅使用 Rainbond 当前官方文档与 Goodrain 官方 GitHub。本文不修改应用代码或部署配置。

## 结论

项目可以部署到 Rainbond，但当前 `../docker-compose.copy.yaml` 不能原样一键成功：`backend`、`frontend` 只有 `build:` 而没有可拉取的 `image:`。Rainbond 的 Compose 导入会把服务转换成平台组件，不是执行 `docker compose up`；官方明确说 `build` 无法仅凭 Compose 自动部署，必须先构建并推送镜像，再指定 `image`。[Compose 部署](https://www.rainbond.com/docs/how-to-guides/app-deploy/docker-compose) [镜像规范](https://www.rainbond.com/docs/how-to-guides/app-deploy/image/image-example)

推荐：CI 构建 frontend/backend 镜像后走 Compose 导入；MySQL 设为有状态单实例；`db-migrate` 设为 Job；同一域名用 `/*` 到 frontend、`/logan-web/*` 到 backend；MySQL 只对内开放。

## 1. 入口与版本

官方应用部署总览将“已有 Docker Compose 项目，希望批量创建多组件应用”指向 Compose 创建应用。[应用部署总览](https://www.rainbond.com/docs/how-to-guides/app-deploy)

控制台入口：目标团队 → **新建应用 → Docker Compose**，填写名称并上传项目压缩包；Compose 不在根目录时填写包内相对路径；私有镜像需补仓库凭据。[官方 Compose 创建流程](https://www.rainbond.com/docs/how-to-guides/app-deploy/docker-compose)

Rainbond v6.6.0 起增强 Compose：支持上传包含 Compose、依赖配置与代码的 `.tar`、`.tgz`、`.zip`，支持指定 Compose 相对路径和深度解析配置、存储路径、组件属性。建议平台至少 v6.6.0。[v6.6.0 Release](https://github.com/goodrain/rainbond/releases/tag/v6.6.0-release)

上传包应包含 `../docker-compose.copy.yaml`、`scripts/migration/mysql` 以及 `.env`、`env_file`、相对挂载引用的所有文件。解析后逐项核对服务、镜像、端口、变量、存储和配置文件状态。[Compose 项目包要求](https://www.rainbond.com/docs/how-to-guides/app-deploy/docker-compose)

## 2. 当前 Compose 的差异

| 当前配置 | Rainbond 行为 | 处理 |
| --- | --- | --- |
| frontend/backend 的 `build.context` | 不自动本地构建 | CI 先推送镜像，并在导入副本指定 `image` |
| `networks` | 被忽略，平台管理网络 | 不依赖 172.20.x 或 Docker bridge |
| `condition: service_healthy` | 只保留启动顺序，不转换健康条件 | 导入后重新配置依赖和探针 |
| `service_completed_successfully` | 官方未承诺等价转换 | migration 改 Job，成功后再启 backend |
| `${API_BASE_URL:-...}` | 动态变量需核对 | 这是构建期 ARG，应构建镜像时设定 |
| 相对 bind mount | 文件须随归档上传 | 核对 SQL 与日志挂载 |

上述限制见[官方 Compose 文档](https://www.rainbond.com/docs/how-to-guides/app-deploy/docker-compose)。Compose 组件主要从 Compose 配置读取属性，不应假设平台再从镜像补齐遗漏项。[镜像属性识别说明](https://www.rainbond.com/docs/how-to-guides/app-deploy/image/image-example)

## 3. 两种部署路线

### A. 预构建镜像 + Compose（推荐）

构建 backend（上下文 `Server`）和 frontend（上下文 `LoganSite`，ARG `API_BASE_URL=/logan-web`），使用不可变版本标签推送到集群可访问的 HTTPS 仓库。私有仓库需凭据。[镜像可访问性要求](https://www.rainbond.com/docs/how-to-guides/app-deploy/image/image-example)

准备 Rainbond 专用 Compose 副本，为两个服务增加 `image`。导入五个服务后仍需人工校对组件类型、依赖、探针、存储和网关。本文不修改该文件。

### B. 同一 Git 仓库创建源码组件

分别创建 backend（子目录 `Server`）和 frontend（子目录 `LoganSite`）。Rainbond 源码表单支持仓库、版本和子目录。[Java 源码部署](https://www.rainbond.com/docs/how-to-guides/app-deploy/source-code/springboot)

两个目录都有 Dockerfile。官方会识别 `ARG` 为构建参数、`ENV` 为运行变量、`EXPOSE` 为端口、`VOLUME` 为存储；frontend 设置构建参数 `API_BASE_URL=/logan-web`。[Dockerfile 构建](https://www.rainbond.com/docs/how-to-guides/app-deploy/source-code/dockefile) 自 v6.7.0 起新源码组件默认 CNB + Paketo，但已有 Dockerfile 可继续走自定义构建。[源码部署](https://www.rainbond.com/docs/how-to-guides/app-deploy/source-code)

没有 Git 时也可上传完整源码 ZIP，根目录须含识别文件。[源码包上传](https://www.rainbond.com/docs/how-to-guides/app-deploy/source-code/upload-package)

## 4. 组件配置

| 组件 | 类型 | 内部端口 | 外部 | 存储/顺序 |
| --- | --- | ---: | --- | --- |
| `db` | MySQL 8.0，有状态单实例 | TCP 3306 | 关闭 | `/var/lib/mysql`；最先健康 |
| `db-migrate` | Job | 无 | 关闭 | DB 健康后执行 |
| `backend` | 无状态 | HTTP 8080 | `/logan-web/*` | `/usr/local/tomcat/logfile`；migration 成功后 |
| `frontend` | 无状态 | HTTP 80 | `/*` | backend 可用后 |
| `phpmyadmin` | 无状态管理工具 | HTTP 80 | 生产默认关闭 | 依赖 DB |

MySQL 镜像名会被默认识别为有状态，但仍要人工确认。[镜像部署类型](https://www.rainbond.com/docs/how-to-guides/app-deploy/image/image-example)

MySQL 环境变量至少为：

```text
MYSQL_DATABASE=logan
MYSQL_USER=logan
MYSQL_PASSWORD=<生产密码>
MYSQL_RANDOM_ROOT_PASSWORD=yes
```

不要继续用示例密码。Rainbond 可通过组件依赖注入 `DB_HOST`、`DB_PORT`、`DB_USER`、`DB_PASSWORD`、`DB_DATABASE`。[组件依赖和变量传递](https://www.rainbond.com/docs/how-to-guides/app-ops/dependon)

项目当前 `Server/src/main/resources/db.properties` 写死 `db:3306` 和凭据。必须验证 `db` 在 Rainbond 内可解析；稳健的后续改造是读取平台注入变量，但本文不改代码。

## 5. migration Job

Rainbond Job 用于一次性数据初始化，完成后容器退出，并可手动重启再执行。[Job 组件](https://www.rainbond.com/docs/how-to-guides/app-deploy/deploy-job)

保留等价命令：

```text
-source file:///etc/migrations \
-database mysql://logan:<密码>@tcp(<MySQL内部地址>:3306)/logan \
up
```

顺序：MySQL TCP 3306 健康 → 运行 Job → 确认成功 → 启动 backend。`no change` 表示无待执行迁移，不是错误。官方未承诺 `service_completed_successfully` 等价导入，因此不要完全依赖原 `depends_on`。[Compose 依赖限制](https://www.rainbond.com/docs/how-to-guides/app-deploy/docker-compose)

## 6. 存储

MySQL 必须持久化 `/var/lib/mysql`。Rainbond 本地存储只支持单读单写，并把组件固定在存储节点；生产多节点需按容灾需求选择合适 StorageClass，并确认回收策略。[持久化存储](https://www.rainbond.com/docs/how-to-guides/app-ops/storage)

backend 要确认 `/usr/local/tomcat/logfile` 成为持久卷。Log4j 还写 `/data/applogs/logan-web/logan.log`，如需保留也应挂载实际目录。官方建议优先 stdout/stderr，文件日志必须先挂载存储。[组件日志](https://www.rainbond.com/docs/how-to-guides/app-ops/component-logs)

不要把 `.data/mysql/data` 当 Rainbond 节点固定路径；以组件存储页的卷和容器挂载路径为准。

## 7. 依赖、端口和域名

建立：frontend → backend，backend → db，phpmyadmin → db，db-migrate → db。调整依赖后更新或重启消费者。[组件依赖](https://www.rainbond.com/docs/how-to-guides/app-ops/dependon)

若没配置启动健康检查，Rainbond 可能在进程刚启动时就认为依赖已启动，因此必须配置探针。[组件生命周期](https://www.rainbond.com/docs/how-to-guides/app-ops/lifecycle)

推荐同域路由：

```text
https://logan.example.com/*           -> frontend:80
https://logan.example.com/logan-web/* -> backend:8080
```

HTTP 网关支持自定义域名、HTTPS 和同域不同路径；DNS A 记录需指向网关 IP。[HTTP 网关](https://www.rainbond.com/docs/how-to-guides/app-gateway/http) frontend 构建时设置 `API_BASE_URL=/logan-web`；这是构建期值。官方 NodeJS 文档同样推荐同源相对路径以避免 CORS。[NodeJS 前后端部署](https://www.rainbond.com/docs/how-to-guides/app-deploy/source-code/nodejs)

MySQL 3306 只对内。TCP 对外依赖 NodePort，会扩大攻击面。[TCP 网关](https://www.rainbond.com/docs/how-to-guides/app-gateway/tcp) migration 不暴露端口；phpMyAdmin 默认不公开，必须公开时使用独立域名、HTTPS 和访问控制。

## 8. 健康检查

未配置时平台只看进程状态。官方支持 TCP/HTTP；HTTP 路径返回小于 400 才算健康，可设置首次等待、间隔、超时、连续成功次数和异常处理。[健康检查](https://v5.6-docs.rainbond.com/docs/v5.2/user-manual/component-op/health/)

| 组件 | 建议 |
| --- | --- |
| MySQL | TCP 3306 |
| backend | HTTP 8080 `/logan-web/logan/latest.json`；接口不稳定则 TCP 8080 |
| frontend | HTTP 80 `/` |
| phpMyAdmin | HTTP 80 `/` |
| db-migrate | 不配置，以 Job 退出状态和日志判断 |

## 9. 上线验收

1. Rainbond ≥ v6.6，集群能拉取代码/镜像。
2. 五个服务均识别，backend/frontend 有可拉取镜像。
3. MySQL 有状态单实例、数据持久化、3306 健康且只对内。
4. migration 是 Job，执行成功；重复执行 `no change` 可接受。
5. backend 能连接 MySQL，8080 健康。
6. frontend 的 API 基址为 `/logan-web`，80 健康。
7. 同域路由正确，浏览器不再请求 `localhost:8888`。
8. phpMyAdmin 未公开或有安全策略。
9. 重启后数据和日志仍存在，并已设置备份。
