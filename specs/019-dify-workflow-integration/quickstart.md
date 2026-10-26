# Quickstart: Dify AI 工作流本地验证

## Prerequisites

- Docker Desktop 已安装并允许启动 Linux 容器。
- 本地 Dify 源码位于 `D:\dify\dify`，或通过 `DIFY_HOME` 指向其 `docker` 目录。
- 本机 MySQL `shuati_test`、Redis、Java 17 和 Maven 可用。
- 四个 Dify 工作流已导入，模型供应商已在 Dify 控制台配置。

## 1. Start Dify

```powershell
pwsh -File deploy/scripts/dify-local.ps1 start
pwsh -File deploy/scripts/dify-local.ps1 status
```

Expected:

- `http://localhost:8501/console/api/setup` 返回 `200`。
- `docker-api-1`、`docker-worker-1`、`docker-nginx-1`、PostgreSQL、Redis 和 Sandbox 正常。
- Worker 日志不再出现 Redis 认证失败。

## 2. Configure Local Secrets

在 Dify 中创建四个工作流的 Service API Token，仅写入本机 `.env.local`：

```text
SHUATI_AI_ENGINE=dify
DIFY_BASE_URL=http://[::1]:8501/v1
DIFY_WORKFLOW_JUDGE_TOKEN=
DIFY_WORKFLOW_EXPLAIN_TOKEN=
DIFY_WORKFLOW_EXTRACT_TEXT_TOKEN=
DIFY_WORKFLOW_EXTRACT_IMAGE_TOKEN=
```

密钥扫描：

```powershell
git diff --check
rg -n "app-[A-Za-z0-9]|DIFY_WORKFLOW_.*=.+" --glob '!*.local' .
```

Expected: 仓库中无真实 Token。

### 本地接入证据（2026-10-26）

- Dify 版本：`1.13.3`
- DeepSeek 插件：`0.0.26`
- 模型：`deepseek-flash`
- 四个工作流已导入、发布并配置独立 API Token。
- 四条真实冒烟均返回 `succeeded`。
- 截图上传使用 `image/png` MIME；Octet-stream 会被 DeepSeek 拒绝。
- Spring Boot `local` profile 启动成功，健康接口返回 `UP`。
- Windows Docker Desktop 的 Java 客户端使用 `http://[::1]:8501/v1`，IPv4 `127.0.0.1:8501` 会超时。

## 3. Run Automated Tests

```powershell
mvn -f backend/pom.xml -Dtest=DifyWorkflowClientTest test
mvn -f backend/pom.xml test
npm --prefix frontend run build
```

Expected:

- Dify HTTP 契约测试通过。
- 后端全量测试 0 失败。
- 前端生产构建成功。

## 4. Run Local Application

```powershell
mvn -f backend/pom.xml spring-boot:run -Dspring-boot.run.profiles=local
npm --prefix frontend run dev
```

Expected:

- 应用启动时选择 Dify 适配器。
- 日志只出现工作流键和运行 ID，不出现 Token。

## 5. Real Dify Smoke

按顺序验证：

1. 提交一道简答题，确认返回判分 JSON 且点数流水出现 `JUDGE` 扣费。
2. 点击 AI 解析，确认返回 Markdown、解析库异步更新。
3. 文本导入一道题，确认 `import_task` 结果可导入。
4. 截图导入一道题，确认 Dify 文件上传成功。

在后台或数据库检查：

```sql
select provider, purpose, workflow_key, external_run_id,
       prompt_tokens, completion_tokens, total_tokens, success, duration_ms
from ai_call_log
order by created_at desc
limit 20;
```

Expected:

- `provider=DIFY`
- `external_run_id` 非空
- 成功调用 `success=1`
- 失败调用存在退款流水

## 6. Failure Drill

临时停止 Dify Worker 或把某个 Token 改为无效值，再触发对应功能。

Expected:

- 用户看到“AI 编排服务不可用/执行失败”的清晰提示。
- 该次扣点全额退回。
- `ai_call_log` 有失败记录。
- 项目没有回退到直接模型供应商调用。

## 7. Stop Environment

```powershell
pwsh -File deploy/scripts/dify-local.ps1 stop
```

Expected: Dify 容器停止，项目数据库和本地文件不受影响。
