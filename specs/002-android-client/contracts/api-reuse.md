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
| 练习光标（读） | `GET /api/progress/cursor?scopeType=ALL\|BANK\|UNIT&scopeId=` → `{questionId: number\|null}` |
| 练习光标（写/清） | `PUT /api/progress/cursor` `{scopeType, scopeId, questionId}`（`questionId=null` 清空） |

进度接口回答"哪些题答过"，光标回答"**当前停在哪道题**"。进入练习时**光标优先**，
没有光标才回退到「第一道没答过的题」；一轮做完会清空光标（下次从第 1 题重新开始）。

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

## 6. 题库管理（需登录；权限由服务端判定）

网页端早就有题库管理，安卓端复用同一套接口，不做任何本地权限判断 ——
有没有维护权由服务端 `requireManage` 决定，客户端只负责把 403/404 的文案显示出来。

| 用途 | 请求 |
| --- | --- |
| 我维护的题库 | `GET /api/manage/banks` |
| 新建空题库 | `POST /api/manage/banks` `{name}` → `{id}`（受平台题库数量上限约束） |
| 改题库名 / 描述 | `PATCH /api/manage/banks/{id}` `{name, description}` |
| 删除题库发码 | `POST /api/manage/banks/{id}/delete-code` → `{cooldownSeconds, expiresInSeconds}` |
| 删除题库 | `POST /api/manage/banks/{id}/delete` `{code}`（DELETE_BANK 专用码，不可复用其它场景） |
| 单元列表 | `GET /api/manage/banks/{id}/units` |
| 新增单元 | `POST /api/manage/units` `{bankId, name}` |
| 单元改名 | `PATCH /api/manage/units/{id}` `{name}` |
| 删除单元 | `DELETE /api/manage/units/{id}`（单元下还有题会被拒） |
| 题目列表 | `GET /api/manage/banks/{id}/questions?status=ALL\|ON\|OFF&q=` |
| 新增题目 | `POST /api/manage/questions` `{bankId, unitId, type, content, answer, options, keyPoints, difficulty, explanation, tags}` |
| 编辑题目 | `PUT /api/manage/questions/{id}`（**整题改写，注意是 PUT 不是 PATCH**；`unitId` 传 null 表示移到「未分类」） |
| 上下架 | `PATCH /api/manage/questions/{id}/status` `{status}` |
| 删除题目 | `DELETE /api/manage/questions/{id}` |

## 7. 明确不做的接口

- 全部 `/api/admin/**`（平台管理能力只在网页端）
- `/api/import/**`（文档解析成题库只在网页端）
- `GET /api/orders`（历史订单只在网页端看，安卓端不做订单列表）

理由：安卓端第一期只对齐「考生刷题」这条主链路，管理类能力继续留在网页端，
避免同一功能两套实现（宪法 VI）。
