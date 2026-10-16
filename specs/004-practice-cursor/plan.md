# Implementation Plan: 练习光标

**Branch**: `004-practice-cursor` | **Date**: 2026-10-16 | **Spec**: [spec.md](./spec.md)

## 技术方案

### 1. 数据（新表，走 Flyway 迁移）

`V17__practice_cursor.sql`：

```sql
create table practice_cursor (
  user_id char(36) not null,
  scope_type varchar(16) not null,   -- ALL / BANK / UNIT
  scope_id bigint not null default 0, -- ALL 用 0
  question_id bigint not null,
  updated_at datetime(6) not null default current_timestamp(6) on update current_timestamp(6),
  primary key (user_id, scope_type, scope_id),
  foreign key (user_id) references profiles(id) on delete cascade,
  foreign key (question_id) references question(id) on delete cascade,
  check (scope_type in ('ALL','BANK','UNIT'))
)
```

题目被删掉时光标随外键级联删除；被下架时光标还在，客户端定位不到会自动回退（见 FR/Edge Cases）。

### 2. 后端（两个接口，放在既有 `/api/progress/` 家族里）

| 用途 | 请求 | 说明 |
| --- | --- | --- |
| 读光标 | `GET /api/progress/cursor?scopeType=BANK&scopeId=1` | 返回 `{questionId: number\|null}` |
| 写/清光标 | `PUT /api/progress/cursor` `{scopeType, scopeId, questionId}` | `questionId=null` 表示清空 |

**为什么必须新增接口**（宪法 VI 要求写明理由）：光标是账号级数据，只有服务端存才能满足
「网页端与 App 看到同一份进度」；客户端的 `localStorage` 做不到，两端也各存一份必然分叉。
这一条网页端同样需要（网页端有完全相同的"记不住位置"问题），不是为某一端单独开的口子。

权限与限流：走 `anyRequest().authenticated()`；写接口加 `@RateLimit`（同用户每分钟 240 次，
按"每切一题一次"估的上限）。范围做可见性校验（BANK → `requireBankAccess`，UNIT → 先查所属题库）。

写库用 `insert ... on duplicate key update`，天然幂等。

### 3. 客户端落位规则（两端同一套）

```
进入练习：
  光标 = GET /api/progress/cursor(范围)
  如果光标题在当前题目列表里 → 用它
  否则 → 第一道没答过的题（老规则，>0 才跳，否则第 1 题）
切换当前题（下一题/上一题/从头开始）→ PUT 光标
完成练习 → PUT {questionId: null} 清空
错题重练（?ids=）→ 不读不写光标
```

## 影响面

| 位置 | 变更 | 风险 |
| --- | --- | --- |
| DB | 新增 `practice_cursor` 表（V17） | 低：只加表，不动既有表 |
| `BankService` / `BankController` | 新增光标读写与两个接口 | 低：新增路径，不动既有接口 |
| `frontend/PracticeView.vue` | 落位改为光标优先；切题写光标 | 中：改动进入练习的定位逻辑，用 Playwright 覆盖 |
| `android/PracticeState` | 同上 | 中：纯函数 `startIndex` 单测覆盖 |
| 契约文档 | `specs/002-android-client/contracts/api-reuse.md` 补两个接口 | 无 |

## 数据变更

新增表，无历史数据回填（没有光标 = 走老规则，行为与现在一致）。

## 测试策略

- 后端 JUnit：写入后读到 / 范围隔离 / 清空 / 跨账号不可见 / 范围不可见时报错；
- 网页端 Playwright：有光标时按光标落位；切换题目会写光标（断言请求体）；
- 安卓端 JUnit：`ResumeLocator.startIndex`（光标优先、光标失效回退、无光标回退）；
- 真机/浏览器手工验收：用户描述的那条流程（答一题 → 从头开始 → 到第 2 题 → 退出 → 重进）。

## 风险与回滚

- 风险：切题频繁写库（每切一题一次小写入）。按量级可忽略（单用户每分钟几十次），
  且写接口有 Redis 限流兜底。
- 回滚：客户端去掉读写光标的代码即回到旧行为；表可以留着（不影响老逻辑）。
