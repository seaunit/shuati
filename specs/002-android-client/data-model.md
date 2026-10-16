# Phase 1 Data Model: 拾题安卓客户端

客户端不做本地持久化（除会话 Cookie），所以这里的"模型"= 后端返回字段的映射 + 界面状态。
字段名与后端 JSON **逐字一致**（一律 snake_case 照抄，不做驼峰转换，避免和网页端出现两套口径）。

## 1. 会话（Session）

| 字段 | 来源 | 说明 |
| --- | --- | --- |
| `cookie` | 登录/注册响应 `Set-Cookie: shuati_token=...` | 只存这份 Cookie 原文，应用私有 SharedPreferences；不解析 JWT |
| `user.id` | `GET /api/me` → `data.id` | 判断是否已登录 |
| `user.email` / `user.nickname` / `user.role` / `user.status` | 同上 | 首页与「我的」展示；`role=ADMIN` 只在网页端做管理，安卓端不提供管理入口 |

状态流转：未登录 → （登录/注册成功）→ 已登录 → （`POST /api/auth/logout` 或任意接口返回 401）→ 未登录。
401 时清空本地 Cookie 并跳登录页（spec FR-010）。

## 2. 题库（Bank）

`GET /api/banks` → `data[]`

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `id` / `name` / `description` | number / string / string? | |
| `is_default` | bool | 默认题库 |
| `is_public` | bool | `owner_id == null` → 公共题库；未登录只返回这类 |
| `owner_id` | string? | 非空 = 某个账号自建题库 |
| `unit_count` / `question_count` / `answered_count` | number | 首页展示进度用 |
| `created_at` | string (UTC ISO) | |

## 3. 单元（Unit）

`GET /api/banks/{id}/units` → `data[]`：`id`、`bank_id`、`name`、`sort`、`created_at`、
`question_count`（上架题数）、`answered_count`（已答题数）。

## 4. 题目（Question）

`GET /api/banks/{id}/questions?mode=seq`、`GET /api/units/{id}/questions?mode=seq`、
`GET /api/questions?mode=seq` → `data[]`

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `id` / `unit_id` | number | |
| `type` | `SINGLE` / `MULTI` / `SHORT` | 决定作答界面形态 |
| `content` | string | 题干（Markdown 文本，安卓端按纯文本 + 简单加粗渲染） |
| `options` | `[{key,text}]?` | 选择题选项；简答题为 null |
| `difficulty` | `EASY` / `MEDIUM` / `HARD` | |
| `images` / `tags` | array | 本期只展示标签，不展示图片（后端有该字段，对齐保留） |
| `status` | `ON` / `OFF` | 列表接口只返回 `ON` |
| `explanation` | string? | 解析库里的解析；非空时直接展示并标「来自解析库」 |

**注意**：列表接口**不下发 `answer`**（防作弊），正确答案由提交接口返回；
安卓端与网页端一致 —— 不本地判定对错，靠 `submit-choice` 的 `verdict`。

## 5. 作答结果（PracticeResult）

选择题 `POST /api/practice/submit-choice` → `data`：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `recordId` | number | 作答记录 id，简答题自评时要用 |
| `verdict` | `CORRECT` / `WRONG` | 判定结果 |
| `score` | number | 正确 10 / 错误 0 |
| `correctAnswer` | string | 正确选项，如 `"A"` 或 `"AB"` |

简答题 `POST /api/practice/submit-essay` → `data`：`recordId`、`verdict`
（`CORRECT` / `PARTIAL` / `WRONG`）、`score`（0~10）、`feedback`
（`hit_points` / `missed_points` / `comment`）、`referenceAnswer`、`degraded`（AI 不可用降级为需自评）。

自评 `POST /api/practice/records/{id}/self-eval` body `{verdict}`：`degraded = true` 时的兜底动作。

## 6. 进度（Progress）

`GET /api/progress/bank/{id}` / `/api/progress/unit/{id}` / `/api/progress/all` → `data`：
`total`、`answered`、`correct`、`answeredIds[]`。

续做规则（与网页端逐字一致）：在已加载的 `questions` 列表里找**第一道不在 `answeredIds` 里的题**，
把它设为当前题并提示「已为你续上进度」；找不到就停在最后一题。进度接口失败不影响刷题，退化为从第一题开始。

## 7. 练习会话（PracticeSession）

`POST /api/practice/sessions` body `{scopeType: ALL|BANK|UNIT, scopeId?, scopeName, totalQuestions}` → `data.id`；
整轮做完时 `PATCH /api/practice/sessions/{id}` body `{status: "COMPLETED", answered, correct, partial, wrong, score}`。
这两个调用**失败不影响做题**（网页端也是 `.catch(() => null)`）。

## 8. 错题本（WrongBookItem）

`GET /api/wrong-book` → `data[]`：`record_id`、`question_id`、`verdict`、`score`、`last_at`、
`type`、`content`、`difficulty`、`unit_id`、`unit_name`、`bank_name`。

## 9. 统计（Stats）

`GET /api/stats/me` → `data`：`overall{total,correct,partial,wrong,pending}`、
`byUnit[]`（单元维度做题量与正确率）等。安卓端展示 `overall` 与按单元的前几条。

## 10. 点数与套餐（Entitlements）

`GET /api/points` → `data`：`entitlements{planCode,planName,planExpiresAt,monthlyQuota,monthlyUsed,
monthlyLeft,bonusBalance,available,lifetimeUsed,periodStart,periodEnd,maxBanks}`、`ledger[]`、`rules[]`。
`GET /api/plans` → 点数包/套餐目录（`kind`、`code`、`name`、`priceCents`、`points` 等，以接口实际返回为准）。

安卓端只读展示：可用点数 = `available`；套餐名用 `planName`（买过点数包会显示包名）；
点某个商品 → 系统浏览器打开网页端该商品的支付入口（spec FR-012）。

## 11. 界面状态（每屏一份 UiState）

统一三态，避免各屏各写一套：

```kotlin
sealed interface UiState<out T> {
  data object Loading : UiState<Nothing>
  data class Ready<T>(val data: T) : UiState<T>
  data class Failed(val message: String) : UiState<Nothing>   // message 一律是服务端原文
}
```

规则：`Failed.message` MUST 直接取 `ApiError.message`（来自 `{code,message,data}` 信封），
客户端只在没有服务端文案时（如断网）才用自己的兜底文案（spec FR-009）。
