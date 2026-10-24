# Admin Points API Contract

所有接口均位于 `/api/admin/**`，由 Spring Security 强制 `ROLE_ADMIN`。

## Lookup account by email

`GET /api/admin/users/by-email?email=user@example.com`

成功响应：

```json
{
  "userId": "uuid",
  "emailMasked": "us***@example.com",
  "nickname": "用户昵称",
  "status": "ENABLED",
  "entitlements": {
    "planName": "轻量加量包",
    "bonusBalance": 100,
    "available": 100
  },
  "recentLedger": [
    {
      "id": 1,
      "delta": 20,
      "balance_after": 100,
      "reason": "ADMIN_GRANT",
      "note": "活动奖励",
      "created_at": "2026-10-24T09:00:00Z"
    }
  ]
}
```

错误：

- `400`：邮箱为空或格式不正确。
- `404`：账号不存在。
- `403`：非管理员。

## Grant points

`POST /api/admin/users/{userId}/point-grants`

```json
{
  "points": 100,
  "reason": "客服补偿",
  "idempotencyKey": "d2f5d0b6-6cf4-4a2d-9137-52d8b3e9b96c"
}
```

成功响应：

```json
{
  "grantId": "uuid",
  "targetUserId": "uuid",
  "points": 100,
  "balanceAfter": 200,
  "reused": false,
  "createdAt": "2026-10-24T09:00:00Z"
}
```

规则：

- `points` 必须是 1 至 1,000,000 的 JSON 整数。
- `reason` 去除首尾空白后长度为 2 至 100。
- `idempotencyKey` 必须是 UUID。
- 相同管理员和幂等键重复提交只能到账一次。
- 相同幂等键但目标、点数或原因不同返回 `409`。
- 目标账号不存在返回 `404`，不可用返回 `409`。

## Deprecated generic grant

`POST /api/admin/users/{id}/points`

- `SET_PLAN` 行为保留。
- `GRANT` 不再直接修改余额，返回 `400` 并提示使用 `/point-grants`。
