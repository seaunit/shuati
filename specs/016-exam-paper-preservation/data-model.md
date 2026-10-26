# Data Model: 试卷原题保真解析

本次不新增或修改数据库字段。

## Existing Entities

### import_task

- `kind=screenshot|text|doc|url`
- `source_text` 保留截图 data URL 或原文
- `items` 保存 AI 解析后的题目 JSON

### parsed question item

- `unit`：原资料章节标题
- `type`：`SHORT` / `SINGLE` / `MULTI`
- `content`：原题题干、表格和必要背景
- `answer`、`key_points`、`explanation`：参考答案与解析

## Validation Rules

- 原题没有显式选项时只能使用 `SHORT`。
- 同一题号或候选项内部的小问不能被拆成多个 item。
- 依赖共享表格的每道题必须在 `content` 中携带完整上下文。
