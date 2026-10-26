# Production Runtime Contract

## Backend

- `SHUATI_AI_ENGINE=dify`
- `DIFY_BASE_URL=http://127.0.0.1:8501/v1`
- `DIFY_CONNECT_TIMEOUT=10s`
- `DIFY_READ_TIMEOUT=175s`
- `DIFY_WORKFLOW_JUDGE_TOKEN`
- `DIFY_WORKFLOW_EXPLAIN_TOKEN`
- `DIFY_WORKFLOW_EXTRACT_TEXT_TOKEN`
- `DIFY_WORKFLOW_EXTRACT_IMAGE_TOKEN`

回滚值：

- `SHUATI_AI_ENGINE=direct`

## Dify

- 版本：`1.13.3`
- 控制台绑定：`127.0.0.1:8501`
- 服务 API：`http://127.0.0.1:8501/v1`
- 工作流：四个固定名称
- 插件：`langgenius/deepseek:0.0.26`
- Worker：`SERVER_WORKER_AMOUNT=1`、`CELERY_WORKER_AMOUNT=1`、`CELERY_WORKER_CONCURRENCY=1`

## Required Health Checks

```text
GET /console/api/setup -> 200
backend /actuator/health -> {"status":"UP"}
payment /health -> ok
```

## Secret Boundary

- 仓库只允许出现空 Token 占位。
- 真实值只允许在 `/etc/shuati/env` 或 Dify 容器加密存储中。
- 日志、截图、构建产物不得包含真实 Token。
