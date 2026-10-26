# Contract: 背题模式

## GET `/api/practice/questions/{id}/answer`

- 登录用户可访问其可见题库中的已上架题目。
- 返回 `{answer: string}`。
- 私有题库非所有者返回 403。
- 不存在或已下架返回 404。
- 不写练习记录，不消耗点数。

## AI Explanation

- explanation 非空：前端直接展示，不调用 AI。
- explanation 为空：调用既有 `GET /api/practice/questions/{id}/explanation`。
- 计费、退款、保存与限流不变。
