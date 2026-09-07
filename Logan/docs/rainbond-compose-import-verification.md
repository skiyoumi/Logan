# Rainbond Compose 导入核对

核对日期：2026-09-04。资料仅采用 Rainbond 官方文档。

## 导入流程

进入目标团队，选择“新建应用 → Docker Compose”，上传包含 Compose 文件及其相对引用文件的 `.zip`、`.tar` 或 `.tgz`。如果文件位于压缩包根目录，Compose 文件路径填写 `../docker-compose.yml`。导入会把各个 service 转换为 Rainbond 组件，并不会执行本地 `docker compose up`。

来源：[使用 Docker Compose 部署](https://www.rainbond.com/docs/how-to-guides/app-deploy/docker-compose)

## 私有镜像仓库

可在导入时按页面提示填写私有仓库账号密码，也可提前通过“个人中心 → 镜像仓库 → 添加镜像仓库”添加阿里云 ACR。仓库地址只填写 Registry 主机名，不包含命名空间、仓库名和 Tag。

来源：[通过镜像仓库部署](https://www.rainbond.com/docs/how-to-guides/app-deploy/image/via-registry-deploy)

## 导入限制与导入后配置

- `build` 不会由 Rainbond 执行，必须使用已经推送且可拉取的 `image`。
- `networks` 和 `profiles` 不按本地 Compose 语义处理。
- `depends_on.condition: service_healthy` 不会转换成健康依赖。
- `.env`、`env_file` 和相对挂载引用的文件必须随项目包上传，并在解析页面逐项核对。
- `db-migrate` 应在导入后改为 Job；先启动并确认 MySQL 健康，运行迁移成功后再启动 backend。
- HTTP 网关可将同一域名的 `/logan-web/*` 转发到 backend:8080，将 `/*` 转发到 frontend:80。

