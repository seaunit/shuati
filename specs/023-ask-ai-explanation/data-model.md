# Data Model: 答案解析问 AI

## Ask Request

不持久化，表示一次前端到后端的追问请求。

| 字段 | 类型 | 规则 |
| --- | --- | --- |
| `questionId` | long | 必填；题目必须为 `ON`，且当前用户可访问其题库 |
| `question` | string | 必填；去除首尾空白后 1-1000 字 |
| `history` | array | 可空；最多 20 条，仅允许 `user` / `assistant` |
| `history[].content` | string | 单条不超过 4000 字，总计不超过 12000 字 |
| `requestId` | string | 必填；8-64 位字母、数字或连字符；同一用户 5 分钟内幂等 |

## Ask Conversation

仅存在于前端当前页面内存。

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `questionId` | number | 当前题目；切换题目后旧对话丢弃 |
| `messages` | array | 问题和回答按时间排序 |
| `status` | enum | `idle`、`sending`、`error` |
| `requestId` | string | 每次发送生成一次，用于安全重试 |

## AI Price Rule

持久化于 `ai_price_rule`。

| 字段 | 值 |
| --- | --- |
| `purpose` | `ASK` |
| `points` | `1` |
| `unit` | `CALL` |
| `description` | 题目追问，每次 1 点 |

## AI Call Log

持久化于 `ai_call_log`。

| 字段 | 说明 |
| --- | --- |
| `purpose` | 固定 `ASK` |
| `provider` | `DIFY` 或 `DIRECT` |
| `workflow_key` | `ask` |
| `external_run_id` | Dify Run ID |
| `question_id` | 当前题目 ID |
| `success` | 成功/失败 |
| `error` | 脱敏后的失败原因 |
| `duration_ms` | 完整调用耗时 |

## Point Ledger

不新增表，使用现有 `point_ledger`。

| 场景 | `reason` | `ref_type` | `ref_id` |
| --- | --- | --- | --- |
| 成功后扣点 | `AI_ASK` | `question` | 题目 ID |
| 失败退点 | `AI_ASK_FAILED` | `question` | 题目 ID |

## State Transitions

```text
idle -> sending -> success -> idle
idle -> sending -> failed -> idle
idle -> insufficient_points/rate_limited/permission_denied -> idle
```

`sending` 状态下前端禁止重复提交。后端对相同 `requestId` 返回同一结果或“处理中”状态。
