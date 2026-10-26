# Research: 背题模式

## Decisions

- 答案通过 `GET /api/practice/questions/{id}/answer` 单独读取，不从题目列表提前下发。
- `BankService` 负责题库可见性校验，无权私有题返回 403。
- 前端背题模式默认关闭，仅当前练习页有效。
- 当前题已有 `explanation` 时直接展示；没有时调用现有 explanation 接口。
- 每道题记录自动生成状态，避免同一页面内重复扣点。
- 智能生成结果按题目 id 回填，防止切题后的并发响应串题。
