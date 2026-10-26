# Contract: Dify 1.13.3 Service API

## Authentication

每个工作流使用独立 Bearer Token：

```http
Authorization: Bearer <DIFY_WORKFLOW_TOKEN>
Content-Type: application/json
```

Token 不进入浏览器、仓库、日志或测试夹具。

## Run Workflow

```http
POST /v1/workflows/run
```

请求：

```json
{
  "inputs": {
    "questionContent": "...",
    "referenceAnswer": "...",
    "userAnswer": "..."
  },
  "response_mode": "blocking",
  "user": "shuati:<opaque-user-id>"
}
```

截图工作流在 `files` 中传本地文件：

```json
{
  "inputs": {
    "instruction": "..."
  },
  "files": [
    {
      "type": "image",
      "transfer_method": "local_file",
      "upload_file_id": "<upload-file-id>"
    }
  ],
  "response_mode": "blocking",
  "user": "shuati:<opaque-user-id>"
}
```

可选请求头：

```http
X-Trace-Id: <backend-request-id>
```

## Success Response

后端只依赖以下稳定字段：

```json
{
  "workflow_run_id": "...",
  "task_id": "...",
  "data": {
    "id": "...",
    "workflow_id": "...",
    "status": "succeeded",
    "outputs": {
      "result": "..."
    },
    "error": null,
    "elapsed_time": 1.23,
    "total_tokens": 100
  }
}
```

各工作流输出键：

| Workflow | Output key | Format |
| --- | --- | --- |
| judge | `result` | JSON string |
| explain | `explanation` | Markdown string |
| extract-text | `result` | JSON string |
| extract-image | `result` | JSON string |

## Upload Image

```http
POST /v1/files/upload
Content-Type: multipart/form-data
```

Fields:

- `file`: PNG / JPEG 图片
- `user`: 与工作流请求相同的用户标识

Success:

```json
{
  "id": "upload-file-id",
  "name": "screenshot.png",
  "size": 12345,
  "mime_type": "image/png"
}
```

## Failure Handling

- `400`：请求或工作流输入不合法
- `401`：Token 无效
- `404`：应用或工作流不存在
- `429`：Dify 限流
- `5xx`：Dify 或模型供应商失败
- 网络错误/超时：后端映射为统一 502

任何失败都不得自动重放工作流。
