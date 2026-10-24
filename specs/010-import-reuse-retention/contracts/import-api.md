# Import API Contract

## List import sources

`GET /api/import/list`

返回当前用户最近 50 条解析来源，并附带最近一次导入执行摘要：

```json
{
  "id": "source-task-id",
  "kind": "doc",
  "phase": "READY",
  "status": "COMPLETED",
  "progress": 100,
  "latest_import_id": "execution-task-id",
  "latest_import_status": "COMPLETED",
  "latest_import_progress": 100,
  "latest_created_count": 98,
  "latest_skipped_count": 2,
  "latest_target_bank_id": 4,
  "latest_bank_id": 4,
  "latest_import_error": null
}
```

没有执行记录时，所有 `latest_*` 字段为 `null`。

## Get import source

`GET /api/import/task/{sourceTaskId}`

- 仅来源所有者可访问。
- 返回来源的 `items` 与最近一次导入执行摘要。
- 来源不存在或无权访问时返回 `404`。

## Start import

`POST /api/import/task/{sourceTaskId}/import`

```json
{
  "selectedIndexes": [0, 1, 2],
  "targetBankId": 4
}
```

响应：

```json
{
  "jobId": "execution-task-id"
}
```

规则：

- 每次调用创建新的执行记录，不修改来源任务。
- 全局已有 PENDING/RUNNING 任务时返回 `409`。
- 目标题库无权写入时返回 `403`。
- 来源不属于当前用户时返回 `404`。

## Import result

执行成功后，来源接口中的最近导入摘要显示：

- `latest_created_count`：本次新增题目数
- `latest_skipped_count`：因题干重复而跳过的题目数
- `latest_import_status`：`COMPLETED`

全部题目重复时仍返回 `COMPLETED`，`latest_created_count=0`。
