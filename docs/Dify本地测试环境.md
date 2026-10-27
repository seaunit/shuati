# Dify 本地测试环境

## 定位

由 Dify 负责 AI 编排，Spring Boot 继续负责鉴权、点数、退款、AI 调用日志、题目解析异步落库和题库写入。本地测试使用本文命令，生产部署使用 `deploy/scripts/dify-prod.sh` 和 `dify-prod-bootstrap.py`。

## 运行环境

- Dify：1.13.3
- 控制台：`http://localhost:8501`
- 源码和 Compose：`D:\dify\dify\docker`
- 业务数据库：本机 MySQL `shuati` / `shuati_test`
- Dify 数据库：独立 Docker PostgreSQL `dify` / `dify_plugin`
- Dify Redis、Weaviate、Sandbox：均运行在 Docker 内

## 启停

```powershell
pwsh -File deploy/scripts/dify-local.ps1 start
pwsh -File deploy/scripts/dify-local.ps1 status
pwsh -File deploy/scripts/dify-local.ps1 stop
```

如果 Dify 安装目录不同：

```powershell
$env:DIFY_HOME = "D:\path\to\dify"
pwsh -File deploy/scripts/dify-local.ps1 start
```

脚本会：

- 启动 Docker Desktop 并等待引擎就绪。
- 检查 Redis 密码与 Celery Broker URL 是否一致。
- 启动 Dify Compose 服务。
- 重启 Nginx，避免 API 容器重建后仍使用旧 IP。
- 检查控制台、Worker、数据库、Redis、Sandbox 和 Plugin Daemon。

## 模型供应商

在 Dify 控制台安装官方 DeepSeek 插件并配置模型 Key。模型 Key 只保存在 Dify，不进入本项目。

## 工作流

四个无密钥 DSL 位于 `deploy/dify/workflows/`：

- 简答题判分
- 题目解析
- 文本出题
- 截图出题

每个工作流单独创建 Service API Key，写入本机 `.env.local`：

```text
SHUATI_AI_ENGINE=dify
DIFY_BASE_URL=http://[::1]:8501/v1
DIFY_WORKFLOW_JUDGE_TOKEN=
DIFY_WORKFLOW_EXPLAIN_TOKEN=
DIFY_WORKFLOW_EXTRACT_TEXT_TOKEN=
DIFY_WORKFLOW_EXTRACT_IMAGE_TOKEN=
```

不要把这些 Token 写入 `application.yml`、前端、Dify DSL、文档或 Git。

## 常见问题

### 控制台 502

通常是 API 容器重建后 Nginx 仍保留旧容器 IP。重新执行：

```powershell
pwsh -File deploy/scripts/dify-local.ps1 start
```

### Worker 报 Redis 认证失败

检查 `.env` 中的 `REDIS_PASSWORD` 是否与 `CELERY_BROKER_URL` 中口令一致。启动脚本会在不输出密钥的前提下自动修正。

### Plugin Daemon 安装插件较慢

首次安装 Python 依赖需要下载包。观察：

```powershell
docker logs -f docker-plugin_daemon-1
```

### Dify 正常但业务返回“AI 编排服务未配置”

确认当前 Spring Profile 为 `local`，并检查四个 `DIFY_WORKFLOW_*_TOKEN` 是否已写入 `.env.local`。

## 数据隔离

- 不把业务 MySQL、业务 Redis 和支付侧车写入 Dify Compose。
- 不把 Dify `.env` 复制到仓库。
- 本地 Dify 的用户、工作流运行记录和向量数据只存在 Docker 数据卷。

## 当前本地状态（2026-10-26）

- Dify `1.13.3` 已启动，控制台 `http://localhost:8501` 返回 200。
- 官方 DeepSeek 插件 `0.0.26` 已安装。
- DeepSeek Provider `langgenius/deepseek/deepseek` 已配置，模型为 `deepseek-flash`。
- 四个工作流均已导入并发布：
  - `shuati-judge`
  - `shuati-explain`
  - `shuati-extract-text`
  - `shuati-extract-image`
- 四个 Service API Token 已写入本机 `.env.local`。
- 真实冒烟结果：判题、解析、文本出题、截图出题均返回 `succeeded`。
- 截图工作流必须上传为真实 `image/png` 等 MIME；`application/octet-stream` 会被模型供应商拒绝。
- Spring Boot `local` profile 已实际启动并通过 `/actuator/health` 检查。
- Windows Docker Desktop 下 Java 访问 IPv4 `127.0.0.1:8501` 会超时，本地 Dify 地址统一使用 IPv6 `[::1]:8501`。

## 生产环境

正式服务运行在香港服务器，Dify 1.13.3 采用 Python/Node/PostgreSQL/Redis 原生安装，只监听 `127.0.0.1:8501`。完整安装、四个工作流导入、Service API Token 创建和直连回滚见 `deploy/README.md`。
