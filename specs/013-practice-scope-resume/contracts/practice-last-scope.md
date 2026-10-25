# Contract: 最近有效练习范围

## GET `/api/practice/last-scope`

返回当前登录账号最近一次有效的题库或单元练习范围。

### Success

```json
{
  "code": 200,
  "message": "ok",
  "data": {
    "scope_type": "BANK",
    "scope_id": 42
  }
}
```

没有可恢复范围时：

```json
{
  "code": 200,
  "message": "ok",
  "data": {}
}
```

### Rules

- 仅查询当前账号。
- 仅考虑 `BANK`、`UNIT`，并验证范围仍存在且当前账号可见。
- 按 `started_at`、`id` 倒序取一条。
- 接口要求登录。

## Frontend Contract

- 裸 `/app/practice` 在进入页面前请求该接口。
- 得到 `BANK`/`UNIT` 后使用 `router.replace` 补 `bankId` 或 `unitId`。
- 已有 `bankId`、`unitId` 或 `ids` 时不得调用或覆盖。
- 接口失败或返回空范围时保持裸入口的全量题回退。
