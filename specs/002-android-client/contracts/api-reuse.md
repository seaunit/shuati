# Phase 1 Contracts: 复用既有后端 API

**本文件是安卓端与后端之间唯一允许的契约面。** 任何不在这里列出的接口，安卓端 MUST NOT 调用；
任何本节没要求的接口/字段，MUST NOT 为了安卓端新增（宪法 VI）。

所有响应统一信封（网页端同款）：

```json
{ "code": 200, "message": "ok", "data": {} }
```

`code != 200` 或 HTTP 非 2xx 时，客户端 MUST 用 `message` 作为提示原文。

## 0. 客户端必须自带的约束

| 约束 | 依据 | 客户端做法 |
| --- | --- | --- |
| 认证靠 Cookie | `JwtAuthFilter.readToken()` 只读 Cookie，无 Authorization 分支 | OkHttp `CookieJar` 持有 `shuati_token` |
| 写请求必须带安全头 | `CustomHeaderFilter`：非 GET/HEAD/OPTIONS 且缺头 → 403 | 拦截器统一加 `X-Requested-With: ShuatiApp` |
| 不做 CORS 预检消费 | 服务端未开 CORS | 无（原生客户端不受同源限制，但头必须带） |
| 频率限制 | `@RateLimit`（登录 10/分/IP、注册 5/时/IP、AI 判分 30/分/用户、AI 解析 60/分/用户） | 直接展示服务端 429/`Retry-After` 文案，不本地重试风暴 |

## 1. 认证

| 用途 | 请求 | 说明 |
| --- | --- | --- |
| 图形验证码 | `GET /captcha/image` → `{ticket, imageBase64}` | 登录页必须用；`imageBase64` 是 data URL |
| 登录 | `POST /api/auth/login` `{email,password,captchaTicket,captchaCode}` | 成功 `Set-Cookie: shuati_token`；文案与网页端一致 |
| 注册发码 | `POST /api/auth/register/email-code` `{email}` | 60 秒冷却 |
| 注册 | `POST /api/auth/register` `{email,password,emailCode}` | 成功直接下发会话 Cookie |
| 找回密码发码 | `POST /api/auth/password-reset/email-code` `{email}` | |
| 找回密码 | `POST /api/auth/password-reset` `{email,emailCode,password}` | |
| 退出 | `POST /api/auth/logout` | 服务端清 Cookie，客户端同步清本地 |
| 当前账号 | `GET /api/me` | 401 视为未登录（游客） |

## 2. 题库与题目（游客可用）

| 用途 | 请求 |
| --- | --- |
| 题库列表 | `GET /api/banks` |
| 单元列表 | `GET /api/banks/{id}/units` |
| 题库题目 | `GET /api/banks/{id}/questions?mode=seq` |
| 单元题目 | `GET /api/units/{id}/questions?mode=seq` |
| 全部题目 | `GET /api/questions?mode=seq` |
| 进度 | `GET /api/progress/bank/{id}` \| `/api/progress/unit/{id}` \| `/api/progress/all` |

## 3. 刷题（需登录）

| 用途 | 请求 | 响应要点 |
| --- | --- | --- |
| 建会话 | `POST /api/practice/sessions` `{scopeType,scopeId,scopeName,totalQuestions}` | `{id}` |
| 结束会话 | `PATCH /api/practice/sessions/{id}` `{status,answered,correct,partial,wrong,score}` | 失败可忽略 |
| 选择题提交 | `POST /api/practice/submit-choice` `{questionId,selected:[..],durationMs}` | `{recordId,verdict,score,correctAnswer}` |
| 简答提交 | `POST /api/practice/submit-essay` `{questionId,answer,durationMs}` | `{recordId,verdict,score,feedback,referenceAnswer,degraded}`；点数不足 → 402 + 原文提示 |
| AI 解析 | `GET /api/practice/questions/{id}/explanation` | `{explanation}`；按题缓存，重复查看不重复扣点 |
| 自评 | `POST /api/practice/records/{id}/self-eval` `{verdict}` | 仅 `degraded` 时使用 |

## 4. 错题与统计

| 用途 | 请求 |
| --- | --- |
| 错题本 | `GET /api/wrong-book` |
| 学习统计 | `GET /api/stats/me` |

## 5. 点数与套餐（只读）

| 用途 | 请求 |
| --- | --- |
| 点数与流水 | `GET /api/points` |
| 商品目录 | `GET /api/plans` |
| 下单取支付地址 | `POST /api/orders` `{kind:"PACK", itemCode}` → `{orderId, checkoutUrl, checkoutError}` |

购买动作**不在客户端内完成**：客户端只调下单接口拿到 `checkoutUrl`，再用系统浏览器打开。
后端 `createOrder` 目前只允许 `kind = PACK`（纯点数制），客户端的商品来源就是 `GET /api/plans`
返回的 `packs`，与网页端同一份目录、同一个下单接口、同一个支付页（spec FR-012）。

## 6. 明确不做的接口

- 全部 `/api/admin/**`（管理能力只在网页端）
- 全部 `/api/manage/**`（题库维护只在网页端）
- `/api/import/**`（文档解析成题库只在网页端）
- `GET /api/orders`（历史订单只在网页端看，安卓端不做订单列表）

理由：安卓端第一期只对齐「考生刷题」这条主链路，管理类能力继续留在网页端，
避免同一功能两套实现（宪法 VI）。
