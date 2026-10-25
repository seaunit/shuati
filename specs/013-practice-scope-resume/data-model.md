# Data Model: 刷题入口恢复上次练习范围

本次不新增或修改数据库表。

## Existing Entity: practice_session

| Field | Rule |
| --- | --- |
| `user_id` | 当前登录账号；查询必须只匹配本人 |
| `scope_type` | 仅 `BANK`、`UNIT` 参与恢复，`ALL` 不参与 |
| `scope_id` | 必须大于 0，且对应题库/单元当前可见 |
| `scope_name` | 仅用于展示，不作为恢复依据 |
| `started_at` | 按降序选择最近一条有效记录 |

## Existing Entity: practice_cursor

| Field | Rule |
| --- | --- |
| `user_id` | 与当前登录账号一致 |
| `scope_type` | 与恢复后的范围一致 |
| `scope_id` | 与恢复后的范围一致 |
| `question_id` | 由既有练习页继续读取并落位 |

## Visibility Rules

- 管理员可恢复任意仍存在的题库或单元。
- 普通用户只能恢复公共题库或自己拥有的题库/单元。
- 已删除或无权访问的范围不得返回。
