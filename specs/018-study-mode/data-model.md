# Data Model: 背题模式

无数据库变更。

- `question.answer`：背题模式答案。
- `question.explanation`：解析库内容。
- `practice_record`：背题模式不写入。
- `point_ledger` / `ai_call_log`：仅在解析库为空并自动生成时沿用现有流程写入。
