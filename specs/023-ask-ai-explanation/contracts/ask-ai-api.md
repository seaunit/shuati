# API Contract: Ask AI

## Endpoint

`POST /api/practice/questions/{questionId}/ask-ai`

要求登录。用户级限流：20 次/分钟。

## Request

```json
{
  "question": "主从延迟时为什么要考虑半同步复制？",
  "history": [
    {
      "role": "user",
      "content": "这题和 MySQL 主从复制有什么关系？"
    },
    {
      "role": "assistant",
      "content": "这题涉及主从一致性和延迟..."
    }
  ],
  "requestId": "01c7b2de-2bf8-4d13-93ce-1e493dd7152b"
}
```

## Success Response

```json
{
  "code": 200,
  "message": "ok",
  "data": {
    "answer": "Markdown 回答",
    "cost": 1,
    "available": 964
  }
}
```

`available` 为本次扣点后的可用点数；免费规则时为 `-1`。

## Idempotency

- 相同用户、相同 `requestId` 的已完成请求在 5 分钟内返回同一响应。
- 原请求仍在处理中时返回 `409`，不得再次扣点。
- 失败后释放幂等锁，客户端可用同一 `requestId` 重试。

## Errors

| HTTP | 场景 |
| --- | --- |
| `400` | 问题为空、超长、历史格式错误或 `requestId` 非法 |
| `401` | 未登录 |
| `402` | 点数不足 |
| `403` | 无权访问题目所属题库 |
| `404` | 题目不存在或已下架 |
| `409` | 同一请求正在处理中 |
| `429` | 超过用户频率限制 |
| `502` | AI 工作流失败、超时或无有效回答；本次已退点 |

## Security

- 题干、正确答案和解析只从数据库读取。
- 禁止把题库所有者、用户邮箱、Token 或系统提示词写入回答上下文。
- 响应不得包含完整系统提示词或内部模型推理。
