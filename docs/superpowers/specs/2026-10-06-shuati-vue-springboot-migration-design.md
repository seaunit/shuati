# 拾题刷题系统 Vue 3 + Spring Boot 迁移设计

> Status: Draft for review
> Date: 2026-10-06
> 相关数据迁移说明：`db/README.md`

## 1. 背景

现有系统基于 Next.js 15 + Supabase（PostgreSQL + Auth + RLS），部署在 Netlify。
数据库结构与数据已经完整迁移到本机 MySQL 8.4 的 `shuati` 库，并通过行数、
外键孤儿、中文编码、微秒时间戳、bcrypt 密码哈希逐项核对。

本次要把运行架构整体替换为 Vue 3 + Spring Boot 3 + MySQL，部署形态为
可执行 jar + systemd + Nginx 反向代理；本机只承担开发与测试。

## 2. 目标与非目标

### 2.1 目标

- 功能对齐现有系统：认证、题库/单元/题目、刷题、错题本、统计、申诉、
  AI 判分与解析、异步文档导入、点数计费、后台管理与订单结算。
- 界面视觉不变：沿用现有 Tailwind 主题、布局、间距、图标与文案。
- 现有 3 个用户可用原密码登录（bcrypt 哈希直接复用）。
- 现有 `ai_config.api_key_encrypted` 无需重新加密即可解密使用。
- 前后端同域部署，JWT 放在 httpOnly Cookie。
- 生产环境从空库开始：只执行建表脚本 + 种子数据 + 管理员引导。

### 2.2 非目标（本次迁移不做）

这些是现状审计中发现的“只有字段/文案、没有落地”的能力，本次保持现状，
不顺手实现，避免范围失控：

- Pro 套餐可选 `deepseek-v4-pro` 模型（`allow_pro_model` 仍只读展示）
- 导入队列优先级
- 免费版 30 天记录保留清理
- 机构版批量账号、班级管理、题库共享、班级看板
- 在线支付网关（保留“下单 + 管理员确认收款”）
- 点数包 12 个月过期（保持“长期有效”）

## 3. 技术选型

| 层 | 选型 | 说明 |
| --- | --- | --- |
| 前端 | Vue 3 + Vite + TypeScript | Composition API + `<script setup>` |
| 路由/状态 | Vue Router 4 + Pinia | 路由守卫做登录与管理员校验 |
| 样式 | Tailwind CSS 4 + lucide-vue-next | 沿用 `@theme` 七个色值 |
| 后端 | Spring Boot 3.3 + Java 17 + Maven | 本机已具备 Java 17 / Maven 3.6.3 |
| 数据访问 | Spring Data JPA + JdbcTemplate | CRUD 用 JPA，统计聚合用原生 SQL |
| 数据库 | MySQL 8.4 | 驱动 `mysql-connector-j` |
| 迁移管理 | Flyway | 生产库空库起步，V1 建表 + V2 种子 |
| 认证 | Spring Security 6 + JWT(httpOnly Cookie) | BCrypt 校验现有哈希 |
| AI | DeepSeek OpenAI 兼容接口 | 用 Spring `RestClient`，prompt 原样移植 |
| 异步 | Spring `@Async` 有界线程池 | 任务落 `import_task` 表，前端轮询 |
| 文档解析 | Apache POI + PDFBox + jsoup | 替代 officeparser |
| 部署 | Nginx + systemd + 可执行 jar | 同域，Nginx 托管 `frontend/dist` |

## 4. 仓库结构

```
backend/                         Spring Boot 工程（Maven）
  pom.xml
  src/main/java/com/shuati/
    ShuatiApplication.java
    common/       统一响应、异常、分页、工具
    config/       Security / Async / Jackson / Web / AES
    auth/         登录、注册、登出、JWT
    user/         用户资料、管理端用户
    bank/         题库、单元、题目
    practice/     作答、会话、错题本、统计
    appeal/       申诉
    ai/           模型配置、调用客户端、prompt
    importer/     导入任务、解析器、异步 worker
    billing/      套餐、点数、订单
    admin/        后台统计与结算
  src/main/resources/
    application.yml / application-local.yml / application-prod.yml
    db/migration/V1__schema.sql, V2__seed.sql
  src/test/java/com/shuati/...

frontend/                        Vue 3 工程
  package.json / vite.config.ts
  src/
    main.ts / App.vue
    router/  stores/  api/  components/  views/  styles/

db/                              已完成的 MySQL 结构与数据脚本（保留）
deploy/
  nginx/shuati.conf
  systemd/shuati.service
  scripts/build.sh, scripts/deploy.sh
docs/superpowers/specs/          本设计文档
legacy-next/                     迁移验收前保留旧 Next.js 代码，之后删除
```

## 5. 数据库设计

### 5.1 环境

| 环境 | 库名 | 数据 |
| --- | --- | --- |
| 本机开发 | `shuati` | 已迁移的真实数据 |
| 本机测试 | `shuati_test` | 空库，测试用例隔离 |
| 生产 | `shuati` | 空库 + 建表 + 种子 + 管理员引导 |

### 5.2 表清单

业务表：`profiles`、`auth_user_archive`、`bank`、`unit`、`question`、
`practice_record`、`practice_session`、`appeal`、`ai_config`、`ai_call_log`、
`import_task`。

商业化表：`plan`、`point_pack`、`ai_price_rule`、`billing_config`、
`point_account`、`point_ledger`、`subscription_order`。

### 5.3 与 PostgreSQL 的差异（必须在应用层补回）

1. RLS 不迁移，改为 Spring Security + 服务层过滤；所有可见性判断
   （公共题库、私有题库、管理员全量）必须在 Service 层实现。
2. 两个部分唯一索引在 MySQL 无法用生成列实现（带 STORED 生成列的表不能建外键）：
   - 公共题库（`owner_id IS NULL`）名称全局唯一；
   - 全库最多一条 `bank.is_default = 1`。
   这两条由 `BankService` 在事务内校验。
3. 计费 PL/pgSQL 函数不迁移为存储过程，改成 Java 事务方法：
   `ensureAccount`、`renewPeriod`、`consumePoints`、`refundPoints`、
   `addBonusPoints`、`applyPlan`。扣点用 `SELECT ... FOR UPDATE` 锁账户行。
4. `appeal` 原库只有普通索引，代码却按“重复申诉”处理；新实现增加
   `UNIQUE (practice_record_id)`，让“该记录已申诉”真正生效。

### 5.4 迁移策略

- Flyway `V1__schema.sql`：等价于 `db/mysql/01_schema.sql`。
- Flyway `V2__seed.sql`：等价于 `db/mysql/03_billing_seed.sql`。
- 生产空库首次启动自动 `migrate`；开发库已有表，使用
  `baseline-on-migrate` 指定基线版本，避免重复建表。
- 管理员引导：首次启动读取 `SHUATI_BOOTSTRAP_ADMIN_EMAIL` 与
  `SHUATI_BOOTSTRAP_ADMIN_PASSWORD`，不存在则创建 ADMIN 账号并开点数账户；
  已存在则跳过，绝不覆盖密码。

## 6. 后端设计

### 6.1 统一约定

- 响应体保持现有格式：`{ "code": 200, "message": "ok", "data": ... }`。
- 业务异常抛 `ApiException(status, message)`，由 `GlobalExceptionHandler`
  转成同上格式；未捕获异常统一 500 且不泄露堆栈。
- 所有时间按 UTC 存储与传输，前端按本地时区展示。
- 分页统一 `{ items, total, page, size }`（现有接口无分页，保持兼容）。

### 6.2 认证与安全

- 登录成功后签发 JWT，写入 Cookie `shuati_token`：
  `HttpOnly`、`Secure`（生产）、`SameSite=Lax`、`Path=/`、有效期 7 天。
- JWT 载荷：`sub`(用户 id)、`role`、`iat`、`exp`，HS256，密钥来自
  `SHUATI_JWT_SECRET`。
- CSRF：使用 `CookieCsrfTokenRepository`，前端从 `XSRF-TOKEN` Cookie 读取，
  写请求带 `X-XSRF-TOKEN` 头；同域部署下无需额外 CORS。
- 密码：`BCryptPasswordEncoder`，兼容现有 `$2a$10$` 哈希。
- 权限：`/api/admin/**` 需 `ADMIN`；其余需登录；`/api/plans` 公开。
- 被禁用账号（`status = DISABLED`）登录与请求都返回 403。

### 6.3 模块职责

| 模块 | 职责 |
| --- | --- |
| `auth` | 注册（建 profiles + 点数账户 + 注册赠点）、登录、登出、当前用户 |
| `user` | 资料读写、管理端用户列表与启停 |
| `bank` | 题库/单元/题目 CRUD、可见性过滤、默认题库、额度校验 |
| `practice` | 选择/简答作答、AI 判分、自评、会话、错题本、统计 |
| `appeal` | 提交申诉、管理端处理 |
| `ai` | AI 配置加解密、DeepSeek 调用、prompt、调用日志 |
| `importer` | 导入任务创建、异步解析、结果入库、进度查询 |
| `billing` | 套餐/点数包/规则、点数账户与流水、下单、结算 |
| `admin` | 数据统计、AI 用量、订单与用户管理入口 |

### 6.4 AI 与加密

- AES 解密与现有 `lib/crypto.ts` 完全兼容：
  `AES/GCM/NoPadding`，key = `Base64.decode(APP_AES_SECRET)`，
  数据布局 `iv(12) + ciphertext + tag(16)`，整体 Base64。
- 默认模型从 `deepseek-flash` 起，但优先使用 `ai_config` 中的配置；
  迁移脚本额外提供一条更新：把历史 `deepseek-chat` 改为 `deepseek-flash`。
- prompt 从 `lib/prompts.ts` 原样移植为 Java 常量，不重写措辞。

### 6.5 异步导入

- `@Async("importExecutor")`，核心 2 / 最大 4 / 队列 50，线程内自建事务。
- 解析流程与现有 `runImportTask` 一致：`PARSE` → `READY` → `IMPORT` → `DONE`；
  分块大小 5000 字，最大 10 万字；失败切片按比例退点。
- 文档解析：`.docx/.doc` 与 `.pptx/.ppt` 用 Apache POI，`.pdf` 用 PDFBox，
  网页用 jsoup 提取正文。
- 前端轮询 `GET /api/import/task/{id}`，与现状一致。

### 6.6 点数事务

- `consumePoints`：锁定账户 → 续期 → 计算 MONTHLY/BONUS 拆分 → 余额不足
  返回 `ok=false`（不抛异常）→ 写 `point_ledger`。
- `refundPoints`：优先退回 MONTHLY，再退 BONUS，写流水。
- 订单结算：`PACK` 发放 BONUS，`PLAN` 调 `applyPlan` 并设置到期时间；
  结算接口幂等（已 PAID 的订单拒绝重复结算）。

## 7. 前端设计

### 7.1 视图清单

| 视图 | 路由 | 说明 |
| --- | --- | --- |
| 登录/注册 | `/login` | 登录、注册、错误文案与现状一致 |
| 首页题库 | `/app` | 题库选择、单元进度 |
| 刷题 | `/app/practice` | 单题作答、AI 解析、进度、会话 |
| 错题本 | `/app/wrong-book` | 按题库/单元筛选 |
| 统计 | `/app/stats` | 总览、单元明细、练习记录 |
| 套餐与点数 | `/app/pricing` | 套餐、加量包、点数明细、订单 |
| 后台 | `/app/admin` | 数据统计/题库/单元/题目/用户/申诉/AI 配置/计费 |

### 7.2 组件与样式

- 布局：左侧边栏（Logo、角色、点数余额、导航、退出）+ 主内容区。
- 复用组件：`MarkdownText`、`NavLinks`、`SignOutButton`、`ConfirmDialog`、
  `EmptyState`、`LoadingBlock`。
- Tailwind 4 `@theme` 原样复制：
  `ink #3f4a5a`、`paper #faf7f2`、`mist #eef1ee`、`moss #7d9b76`、
  `rose #c98a8a`、`sand #e9dfd0`、`oat #b9a68b`，以及径向渐变背景。
- 图标统一 lucide-vue-next；按钮、卡片圆角、间距按现有类名照搬。

### 7.3 状态与请求

- Pinia `authStore`：当前用户、角色、登录/登出动作。
- Pinia `billingStore`：套餐、点数、流水缓存。
- `api` 封装：`credentials: "include"`、自动带 `X-XSRF-TOKEN`、
  统一解析 `{ code, message, data }`、401 时跳登录。
- 路由守卫：未登录跳 `/login?next=...`；非管理员访问 `/app/admin` 跳首页。

## 8. API 契约

路径保持现有 `/api/**` 不变，前端迁移基本是组件改写。

公开：`GET /api/plans`。

认证：`POST /api/auth/register`、`POST /api/auth/login`、
`POST /api/auth/logout`、`GET /api/me`。

题库与题目：`GET /api/banks`、`GET /api/banks/{id}/units`、
`GET /api/banks/{id}/questions`、`GET /api/units/{id}/questions`、
`GET /api/questions?mode=seq|random`、`GET /api/progress/bank/{id}`、
`GET /api/progress/unit/{id}`。

练习：`POST/GET /api/practice/sessions`、`PATCH /api/practice/sessions/{id}`、
`POST /api/practice/submit-choice`、`POST /api/practice/submit-essay`、
`GET /api/practice/questions/{id}/explanation`、
`POST /api/practice/records/{id}/self-eval`、`GET /api/wrong-book`、
`GET /api/stats/me`、`GET/POST /api/appeals`。

导入：`POST /api/import`、`GET /api/import/list`、
`GET /api/import/task/{id}`、`POST /api/import/task/{id}/import`。

计费：`GET /api/points`、`GET/POST /api/orders`。

管理端：`/api/admin/banks`、`/api/admin/units`、`/api/admin/questions`、
`/api/admin/users`、`/api/admin/users/{id}/points`、`/api/admin/appeals`、
`/api/admin/ai-config`、`/api/admin/ai-config/test`、
`/api/admin/stats/overview|by-unit|hardest-questions|ai-usage`、
`/api/admin/billing`、`/api/admin/orders/{id}/settle`。

## 9. 部署设计

### 9.1 产物

- 后端：`backend/target/shuati-backend.jar`（`spring-boot:repackage`）。
- 前端：`frontend/dist/`（`vite build`）。
- 数据库：Flyway 启动时自动迁移；生产空库直接建表。

### 9.2 Nginx

- `root` 指向 `frontend/dist`，Vue Router history 模式回退 `index.html`。
- `location /api/` 反代 `http://127.0.0.1:8080`，透传 `Host` 与真实 IP。
- `client_max_body_size 20m`（文档上传走 base64，当前最大约 2.5MB）。
- 静态资源加长缓存，`index.html` 不缓存。

### 9.3 systemd

- `shuati.service`：`ExecStart=java -jar /opt/shuati/shuati-backend.jar`，
  `EnvironmentFile=/etc/shuati/env`，`Restart=always`，运行用户非 root。
- 环境变量：`SHUATI_DB_URL/USER/PASSWORD`、`SHUATI_JWT_SECRET`、
  `APP_AES_SECRET`、`DEEPSEEK_API_KEY`、`SHUATI_BOOTSTRAP_ADMIN_*`。

## 10. 测试策略

- 后端：JUnit 5 + Spring Boot Test，针对本机 `shuati_test` 库跑集成测试；
  覆盖登录、权限、题库可见性、扣点并发、退点、订单结算、导入分块。
- 前端：Vitest + Vue Test Utils，覆盖 API 封装、路由守卫、Markdown 渲染。
- 端到端：Playwright 跑登录 → 刷题 → 错题本 → 导入 → 扣点 → 后台结算。
- 验收前用迁移到本机的真实数据（342 题、3 用户）在开发库回归一遍。

## 11. 迁移与切换

1. 新代码在 `backend/`、`frontend/` 开发，旧 Next.js 原样保留。
2. 开发环境连本机 `shuati`（真实数据）联调，确认视觉与行为一致。
3. 生产：建空库 → Flyway 建表与种子 → 引导管理员 → 部署 jar + dist。
4. 切换完成后删除旧 Next.js 代码与 Supabase 依赖，完成收尾。

## 12. 风险

- MySQL 部分唯一索引改由服务层保证，存在并发写入绕过风险；用事务 +
  唯一键兜底，并在测试里覆盖并发建库场景。
- 文档解析库与 officeparser 行为可能有差异，需对现有 5 条导入任务回归。
- 现有 `ai_config.model` 是 `deepseek-chat`，需确认 DeepSeek 当前可用模型名，
  否则 AI 调用会整体失败。

## 13. 验收标准

- 现有 3 个用户可用原密码登录，界面与现状一致。
- 342 道题、160 个单元、3 个题库可正常刷题、判分、错题、统计。
- 选择题本地判分不扣点；简答判分 3 点；解析 1 点；导入 10 点/万字。
- 点数不足返回 402 并提示；AI 失败自动退点。
- 管理端可管理题库/单元/题目/用户/申诉/AI 配置/套餐/订单。
- 生产空库部署后可直接完成注册、刷题、导入与下单结算全流程。

## 14. 实施阶段

1. 基础工程（backend + frontend + Flyway + 配置）+ 认证
2. 题库/单元/题目 + 刷题 + 错题本 + 统计
3. AI 判分/解析 + 文档导入 + 点数计费
4. 后台管理 + 订单结算
5. Nginx/systemd 部署产物 + 端到端验收
