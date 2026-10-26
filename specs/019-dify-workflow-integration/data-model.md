# Data Model: Dify AI 工作流接入

## Existing Entities

### `point_ledger`

不改变结构。继续记录 AI 功能扣费和失败退款，是用户点数的唯一账本。

关键约束：

- Dify 调用前完成扣费。
- 工作流失败、超时、无效输出或服务不可用时全额退款。
- 扣费与退款沿用现有短事务和行锁，不在 Dify 等待期间持有锁。

### `practice_record`

不改变结构。简答题判分成功后继续写入：

- `judge_source=AI`
- `verdict`
- `score`
- `ai_feedback`
- `duration_ms`

### `question`

不改变结构。AI 解析成功后继续异步覆盖 `explanation`。

### `import_task`

不改变结构。文本和截图导入仍由该任务模型记录来源、进度、提取结果和错误。

## Changed Entity

### `ai_call_log`

新增字段：

| Field | Type | Null | Default | Purpose |
| --- | --- | --- | --- | --- |
| `provider` | `varchar(16)` | No | `DIRECT` | `DIRECT` 或 `DIFY` |
| `external_run_id` | `varchar(128)` | Yes | `NULL` | Dify `workflow_run_id` |
| `workflow_key` | `varchar(64)` | Yes | `NULL` | `judge`、`explain`、`extract-text`、`extract-image` |

Retain:

- `user_id`
- `purpose`
- `model`
- `question_id`
- `prompt_tokens`
- `completion_tokens`
- `total_tokens`
- `success`
- `error`
- `duration_ms`
- `created_at`

Validation:

- `provider=DIRECT` 时 `external_run_id` 可为空。
- `provider=DIFY` 时 `external_run_id` 在 Dify 正常响应后应存在。
- Token 缺失时写 0，不阻断 AI 结果返回。
- 错误文本必须脱敏，不包含 Token、Authorization Header 或完整供应商响应。

## Runtime Entities

### Dify Workflow

字段：

- workflow key
- display name
- DSL version
- required input variables
- output contract
- model provider configured inside Dify

四个实例：

1. `judge`
2. `explain`
3. `extract-text`
4. `extract-image`

### Dify Run Metadata

后端内传递，不单独建业务表：

- provider
- workflow key
- external run ID
- status
- outputs
- total tokens
- elapsed time
- error

## State Transitions

### Paid AI Call

`NOT_STARTED → CHARGED → DIFY_RUNNING → SUCCEEDED → LOGGED`

失败分支：

`NOT_STARTED → CHARGED → DIFY_FAILED → REFUNDED → FAILURE_LOGGED`

要求：

- `DIFY_RUNNING` 不得位于数据库事务内。
- 退款只能执行一次。
- 失败响应不得返回用户点数已扣除的状态。

### Explanation Save

`DIFY_SUCCEEDED → RESPONSE_RETURNED → QUEUED → EXPLANATION_UPDATED → USAGE_LOGGED`

队列不可用时：

`DIFY_SUCCEEDED → RESPONSE_RETURNED → SYNC_SAVE → USAGE_LOGGED`
