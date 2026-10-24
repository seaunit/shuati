# Data Model: 导入历史硬性保留 10 条

## Import Source

- 条件：`source_task_id IS NULL AND user_id IS NOT NULL`
- 排序：`created_at DESC, id DESC`
- 保留：前 10 条
- 删除：第 11 条及之后
- 删除内容：来源行中的 `items`、`source_text` 等大字段

## Import Execution

- 条件：`source_task_id` 指向被删除来源
- 处理：随来源外键级联删除

## Preserved Data

以下数据不参与历史清理：

- `bank`
- `unit`
- `question`
- `point_account`
- `point_ledger`
- `ai_call_log`

## Rules

```text
每个用户：
  来源数量 <= 10 -> 不删除
  来源数量 > 10  -> 删除最旧的 (数量 - 10) 条
```
