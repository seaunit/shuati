# Data Model: 题库导出 Word

本次不新增或修改数据库表。

## Read-only Aggregates

### Bank

- `id`、`name`、`description`
- 导出前通过 `requireManage` 校验本人或管理员权限

### Unit

- `id`、`name`、`sort`
- 按 `sort, id` 升序排列

### Question

- `unit_id`、`type`、`content`、`options`、`answer`
- `difficulty`、`explanation`、`status`
- 按 `id` 升序稳定排列

## Document Output

- MIME：`application/vnd.openxmlformats-officedocument.wordprocessingml.document`
- 文件名：安全化题库名 + `.docx`
- 纸张：A4 纵向
- 题目编号：跨单元连续编号
