# 数据库迁移说明

## 来源与目标

- 来源：Supabase 项目 `cfagbcceyajkfqlrajvk`（PostgreSQL 17.6），迁移时项目已从 paused 状态恢复。
- 目标：本机 MySQL 8.4，数据库名 `shuati`。
- 连接：`root` / `Root@2026`，`127.0.0.1:3306`。

## 目录

| 文件 | 说明 |
| --- | --- |
| `mysql/00_create_database.sql` | 创建 `shuati` 数据库 |
| `mysql/01_schema.sql` | 全部建表脚本（业务表 + 用户表 + 商业化表） |
| `mysql/03_billing_seed.sql` | 套餐、点数包、计费规则、注册赠点的种子数据 |
| `mysql/99_verify.sql` | 行数与外键孤儿校验 |
| `etl/introspect.mjs` | 从 Supabase 读取结构元数据，输出 `_supabase_raw/schema.json` |
| `etl/export-data.mjs` | 从 Supabase 导出数据，生成 `_supabase_raw/02_data.sql` |
| `_supabase_raw/` | 原始 dump 与导出数据，**已 gitignore，含真实用户数据** |

## 重建步骤

```powershell
$mysql = 'C:\Program Files\MySQL\MySQL Server 8.4\bin\mysql.exe'

& $mysql -u root --password=Root@2026 --default-character-set=utf8mb4 `
  --execute="source E:/CodexProject/shuati2/db/mysql/00_create_database.sql"

& $mysql -u root --password=Root@2026 --default-character-set=utf8mb4 shuati `
  --execute="source E:/CodexProject/shuati2/db/mysql/01_schema.sql"

& $mysql -u root --password=Root@2026 --default-character-set=utf8mb4 shuati `
  --execute="source E:/CodexProject/shuati2/db/_supabase_raw/02_data.sql"

& $mysql -u root --password=Root@2026 --default-character-set=utf8mb4 shuati `
  --execute="source E:/CodexProject/shuati2/db/mysql/03_billing_seed.sql"

& $mysql -u root --password=Root@2026 --default-character-set=utf8mb4 shuati `
  --execute="source E:/CodexProject/shuati2/db/mysql/99_verify.sql"
```

## 已迁移数据

| 表 | 行数 | 说明 |
| --- | ---: | --- |
| `profiles` | 3 | 含从 `auth.users` 迁移的 bcrypt `password_hash` |
| `auth_user_archive` | 3 | Supabase `auth.users` 原始行归档 |
| `bank` | 3 | |
| `unit` | 160 | |
| `question` | 342 | |
| `practice_record` | 41 | |
| `practice_session` | 8 | |
| `appeal` | 0 | |
| `ai_config` | 1 | 模型名仍是旧的 `deepseek-chat`，需要在 Spring Boot 侧更新 |
| `ai_call_log` | 147 | |
| `import_task` | 5 | `source_text` 最长 2,533,432 字符，因此使用 `LONGTEXT` |

商业化表（`plan`、`point_pack`、`ai_price_rule`、`billing_config`）在 Supabase 上**从未执行过迁移**，
本 MySQL 库直接按 `supabase/migrations/20261006000000_billing.sql` 建立并写入种子数据；
`point_account`、`point_ledger`、`subscription_order` 为空表，等待 Spring Boot 服务写入。

## 类型映射

| PostgreSQL | MySQL |
| --- | --- |
| `uuid` | `char(36)` |
| `jsonb` | `json` |
| `timestamptz` | `datetime(6)`，统一按 UTC 存储，已逐行核对微秒 |
| `boolean` | `tinyint(1)` |
| `text`（名称/索引列） | `varchar(255)` |
| `text`（正文/长内容） | `longtext` |
| `bigint ... generated as identity` | `bigint auto_increment` |

## 需要在应用层补回的行为

1. Supabase 的 RLS 不迁移，改为 Spring Security + 服务层鉴权。
2. PostgreSQL 的两个部分唯一索引在 MySQL 无法用生成列实现（带 `STORED` 生成列的表不能建外键），改由服务层保证：
   - 公共题库（`owner_id IS NULL`）名称全局唯一；
   - 全库最多一条 `bank.is_default = 1`。
3. `billing` 的 PL/pgSQL 函数（`consume_points`、`refund_points`、`apply_plan` 等）不迁移为存储过程，
   改由 Spring Boot 在事务中实现，保证 `SELECT ... FOR UPDATE` 的原子扣点。
4. Supabase `storage.objects` 为 0，没有文件需要迁移。
