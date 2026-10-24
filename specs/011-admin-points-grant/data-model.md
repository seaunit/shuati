# Data Model: 管理员后台赠送点数

## AdminPointGrant

新增表 `admin_point_grant`。

| Field | Type | Rules |
| --- | --- | --- |
| `id` | char(36) | 赠点记录主键 |
| `admin_id` | char(36) | 操作管理员；账号删除时置空 |
| `admin_email` | varchar(255) | 操作时邮箱快照，永久保留 |
| `user_id` | char(36) | 目标账号；账号删除时随数据清理 |
| `idempotency_key` | varchar(64) | 客户端 UUID；同管理员唯一 |
| `points` | int | 1 至 1,000,000 |
| `reason` | varchar(100) | 2 至 100 字 |
| `ledger_id` | bigint | 对应点流水，唯一 |
| `created_at` | datetime(6) | 创建时间 |

关系：

- 多位管理员可向同一用户赠点。
- 一条赠点记录对应一条 `point_ledger`。
- `admin_id` 使用 `ON DELETE SET NULL`，`admin_email` 保留审计快照。
- `user_id` 与 `ledger_id` 使用限制性外键。

## PointAccount

复用现有表：

- `bonus_balance` 增加赠点值。
- `lifetime_granted` 同步增加。
- 赠点事务必须对目标行执行 `FOR UPDATE`。

## PointLedger

赠点写入规则：

- `delta = points`
- `bucket = BONUS`
- `balance_after = 赠送后可用点数`
- `reason = ADMIN_GRANT`
- `ref_type = admin_point_grant`
- `ref_id = admin_point_grant.id`
- `note = 管理员填写的原因`

## Idempotency

- 唯一键：`(admin_id, idempotency_key)`。
- 相同键、相同目标、点数和原因：返回首次赠点结果，`reused=true`。
- 相同键、不同载荷：返回 `409`，余额不变。

## State Flow

```text
管理员提交
  -> 校验管理员与参数
  -> 锁管理员账号
  -> 查询幂等记录
     -> 已存在且载荷一致：返回首次结果
     -> 已存在但载荷不同：409
  -> 查询并锁目标账户
  -> 更新赠送余额
  -> 写点流水
  -> 写赠点记录
  -> 提交事务
```
