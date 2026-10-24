# Data Model: 导入结果复用与记录保留

## Import Source

现有 `import_task` 中 `source_task_id IS NULL` 的记录。

| Field | Meaning | Rules |
| --- | --- | --- |
| `id` | 解析来源 ID | 用户可见的历史任务 ID |
| `user_id` | 所有者 | 所有查询必须匹配当前用户 |
| `kind` | 来源类型 | text / url / doc / screenshot |
| `phase` | 来源阶段 | 未解析为 `PARSE`，可复用为 `READY` |
| `items` | 解析题目快照 | 可被多次导入，不因再次导入覆盖 |
| `source_text` | 原始文本、URL 或 Base64 | 来源失去全部执行记录引用后可删除 |

## Import Execution

现有 `import_task` 中 `source_task_id` 指向来源的记录。

| Field | Meaning | Rules |
| --- | --- | --- |
| `source_task_id` | 来源任务 | 非空；来源必须属于同一用户 |
| `status` | 执行状态 | PENDING / RUNNING / COMPLETED / FAILED |
| `target_bank_id` | 用户选择的题库 | 为空表示按名称新建/复用 |
| `bank_id` | 实际写入题库 | 成功时回填 |
| `selected_indexes` | 本次选择的题目下标 | 只影响本次执行 |
| `created_count` | 新增题目数 | 成功时写入 |
| `skipped_count` | 跳过重复题数 | 成功时写入 |
| `created_at/updated_at` | 提交/完成时间 | 保留策略按完成时间倒序 |

## Question

| Field | Meaning | Rules |
| --- | --- | --- |
| `content` | 展示题干 | 原有字段 |
| `content_hash` | 规范化题干 SHA-256 | 所有创建和编辑路径必须维护 |

规范化规则：去除首尾空白，并将连续空白或换行折叠成一个空格。

## State Transitions

```text
Source: PARSE/PENDING -> PARSE/RUNNING -> READY/COMPLETED
                                      -> READY/FAILED（保留可导入的部分结果）

Execution: IMPORT/PENDING -> IMPORT/RUNNING -> DONE/COMPLETED
                                           -> IMPORT/FAILED
```

## Retention Rule

1. 按 `user_id` 查询 `source_task_id IS NOT NULL AND phase='DONE' AND status='COMPLETED'`。
2. 按 `updated_at DESC, created_at DESC` 保留前 10 条。
3. 删除其余执行记录。
4. 对受影响的来源 ID，在没有其他执行记录引用时删除来源快照。
5. 不删除 `bank`、`unit`、`question` 数据。
