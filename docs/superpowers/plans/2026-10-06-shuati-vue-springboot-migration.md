# 拾题 Vue 3 + Spring Boot 迁移实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans（本会话由我原生执行）逐任务实施。步骤用 `- [ ]` 跟踪。

**Goal:** 把 Next.js 15 + Supabase 刷题系统整体迁移到 Vue 3 + Spring Boot 3 + MySQL，jar + Nginx 同域部署，界面与功能对齐。

**Architecture:** 单仓库双工程：`backend/`（Spring Boot 3.3 + JPA/JdbcTemplate + Flyway）、`frontend/`（Vue 3 + Vite + Pinia + Tailwind 4）。Nginx 托管前端静态文件并把 `/api` 反代到本机 8080。认证用 httpOnly Cookie + JWT + CSRF 双重提交。

**Tech Stack:** Java 17、Maven、Spring Boot 3.3、Spring Security 6、Spring Data JPA、Flyway、MySQL 8.4、Vue 3、Vite、TypeScript、Pinia、Vue Router 4、Tailwind CSS 4、lucide-vue-next、JUnit 5、Vitest。

**Spec:** `docs/superpowers/specs/2026-10-06-shuati-vue-springboot-migration-design.md`

## Global Constraints

- 响应体统一 `{ code, message, data }`，与现有前端契约一致。
- 所有时间存 UTC，MySQL 列 `datetime(6)`；前端按本地时区展示。
- 认证：JWT 放 httpOnly Cookie，`SameSite=Lax`，写请求校验 `X-XSRF-TOKEN`。
- 旧 bcrypt 哈希（`$2a$10$`）必须能直接登录。
- AES 兼容现有 `AES-256-GCM`（`base64(iv[12]+ciphertext+tag[16])`）。
- 生产库空库起步；本机 `shuati` 为开发库，`shuati_test` 为测试库。
- 界面视觉不变：Tailwind 主题 `ink/paper/mist/moss/rose/sand/oat` 原样保留。
- 不在本次范围：Pro 可选模型、导入队列优先级、30 天清理、机构版、在线支付。

## Review Focus

- 并发建公共题库 / 设默认题库时的唯一性（MySQL 部分唯一索引已改服务层校验）。
- 扣点与退点在并发、周期续期、跨桶拆分时的余额正确性。
- AI 调用失败、JSON 解析失败、网络超时后的退点与降级。
- 导入大文档（`source_text` 最长 2.5MB）、URL 抓取失败、分块部分失败。
- 旧密码哈希与旧 AI Key 的兼容性。

---

## 阶段一：基础工程与认证

### Task 1: 后端工程骨架

**Files:**
- Create: `backend/pom.xml`
- Create: `backend/src/main/java/com/shuati/ShuatiApplication.java`
- Create: `backend/src/main/java/com/shuati/common/ApiResponse.java`
- Create: `backend/src/main/java/com/shuati/common/ApiException.java`
- Create: `backend/src/main/java/com/shuati/common/GlobalExceptionHandler.java`
- Create: `backend/src/main/resources/application.yml`
- Create: `backend/src/main/resources/application-local.yml`
- Create: `backend/src/main/resources/application-prod.yml`
- Test: `backend/src/test/java/com/shuati/ShuatiApplicationTests.java`

**Produces:** 可启动的 Spring Boot 工程；`ApiResponse.ok(data)`、`ApiException(status, message)`。

- [ ] 步骤 1：用 `spring-boot-starter-parent:3.3.x` 建 `pom.xml`，依赖 web、security、data-jpa、validation、flyway-core、flyway-mysql、mysql-connector-j、lombok（可选）。
- [ ] 步骤 2：写 `ApiResponse`（`code/message/data`）与 `ApiException`。
- [ ] 步骤 3：写 `GlobalExceptionHandler`：`ApiException` 按状态码返回；其他异常 500 且隐藏堆栈。
- [ ] 步骤 4：`application-local.yml` 连 `jdbc:mysql://127.0.0.1:3306/shuati`，`application-prod.yml` 用 `SHUATI_DB_URL/USER/PASSWORD`。
- [ ] 步骤 5：验证 `mvn -f backend/pom.xml test` 通过（上下文加载 + 数据源连通）。

### Task 2: Flyway 建表与种子

**Files:**
- Create: `backend/src/main/resources/db/migration/V1__schema.sql`
- Create: `backend/src/main/resources/db/migration/V2__seed.sql`
- Create: `backend/src/main/resources/db/migration/V3__ai_model_fix.sql`

**Interfaces:**
- Consumes：`db/mysql/01_schema.sql`、`db/mysql/03_billing_seed.sql`
- Produces：生产空库可一键建表；`baseline-on-migrate` 让开发库跳过 V1。

- [ ] 步骤 1：把 `db/mysql/01_schema.sql` 内容（去掉 `CREATE DATABASE`/`USE`）落到 `V1__schema.sql`。
- [ ] 步骤 2：把 `03_billing_seed.sql` 落到 `V2__seed.sql`。
- [ ] 步骤 3：`V3__ai_model_fix.sql` 把 `ai_config.model` 从 `deepseek-chat` 改为 `deepseek-flash`。
- [ ] 步骤 4：本地用 `shuati_test` 空库启动，确认 Flyway 生成 `flyway_schema_history` 且 16 张表齐全。
- [ ] 步骤 5：开发库设置 `baseline-version=3`、`baseline-on-migrate=true`，确认启动不重复建表。

### Task 3: 认证与安全

**Files:**
- Create: `backend/src/main/java/com/shuati/auth/*`（Controller/Service/DTO/JwtService/JwtAuthFilter）
- Create: `backend/src/main/java/com/shuati/config/SecurityConfig.java`
- Create: `backend/src/main/java/com/shuati/user/Profile.java`、`ProfileRepository.java`
- Test: `backend/src/test/java/com/shuati/auth/AuthFlowTest.java`

**Interfaces:**
- Produces：`POST /api/auth/register`、`POST /api/auth/login`、`POST /api/auth/logout`、`GET /api/me`
- Produces：`SecurityContext` 中的 `currentUserId`、`currentUserRole`

- [ ] 步骤 1：先写失败测试：错误密码 401、禁用账号 403、注册后余额含注册赠点、`GET /api/me` 返回角色。
- [ ] 步骤 2：实现 `Profile` 实体与仓库，`BCryptPasswordEncoder` 校验旧哈希。
- [ ] 步骤 3：实现 `JwtService`（HS256，7 天）与 `JwtAuthFilter`（从 Cookie 解析）。
- [ ] 步骤 4：`SecurityConfig` 放行 `/api/auth/**`、`/api/plans`，其余认证；`/api/admin/**` 要求 ADMIN；启用 CSRF Cookie。
- [ ] 步骤 5：实现登录（更新 `last_login_at`）、注册（建 profile + 点数账户 + 注册赠点，事务）。
- [ ] 步骤 6：跑 `mvn -f backend/pom.xml test`，全部通过。

### Task 4: 前端骨架与登录页

**Files:**
- Create: `frontend/package.json`、`vite.config.ts`、`tsconfig.json`、`index.html`
- Create: `frontend/src/main.ts`、`App.vue`、`styles/main.css`
- Create: `frontend/src/router/index.ts`、`frontend/src/stores/auth.ts`
- Create: `frontend/src/api/client.ts`
- Create: `frontend/src/views/LoginView.vue`
- Create: `frontend/src/layouts/AppLayout.vue`、`components/NavLinks.vue`

**Produces：** 视觉与现有登录页一致的 Vue 页面；`api()` 自动带 Cookie 与 CSRF。

- [ ] 步骤 1：建 Vite + Vue 3 + TS 工程，安装 Tailwind 4、Vue Router、Pinia、lucide-vue-next。
- [ ] 步骤 2：复制 `@theme` 色值与径向渐变背景到 `main.css`。
- [ ] 步骤 3：实现 `api()`：`credentials: "include"`、读 `XSRF-TOKEN` 写 `X-XSRF-TOKEN`、统一解析 `{code,message,data}`、401 跳登录。
- [ ] 步骤 4：实现登录/注册页与错误文案，路由守卫。
- [ ] 步骤 5：`npm --prefix frontend run build` 通过，并用本机后端联调登录成功。

---

## 阶段二：核心刷题

### Task 5: 题库 / 单元 / 题目读取

**Files:**
- Create: `backend/src/main/java/com/shuati/bank/{Bank,Unit,Question}.java` 与仓库
- Create: `backend/src/main/java/com/shuati/bank/BankController.java`、`BankService.java`
- Test: `backend/src/test/java/com/shuati/bank/BankVisibilityTest.java`

**Interfaces：** `GET /api/banks`、`/api/banks/{id}/units`、`/api/banks/{id}/questions`、`/api/units/{id}/questions`、`/api/questions?mode=seq|random`

- [ ] 步骤 1：写可见性测试：普通用户只见公共 + 自己题库；管理员见全部。
- [ ] 步骤 2：JPA 实体映射 16 张表（先 3 张），JSON 列用 `@JdbcTypeCode(SqlTypes.JSON)`。
- [ ] 步骤 3：Service 实现可见性过滤与题库/单元/题目聚合，保持现有字段名。
- [ ] 步骤 4：接口联调返回与现有 Next.js 版本一致的结构。

### Task 6: 练习、错题本、统计

**Files:**
- Create: `backend/src/main/java/com/shuati/practice/*`（Record/Session/Service/Controllers）
- Test: `backend/src/test/java/com/shuati/practice/PracticeFlowTest.java`

**Interfaces：** `POST /api/practice/submit-choice`、`sessions`、`/api/wrong-book`、`/api/stats/me`、`/api/progress/**`

- [ ] 步骤 1：写测试：选择题判分正确/错误、会话创建与完成、错题本只含 WRONG/PARTIAL。
- [ ] 步骤 2：实现本地判分与记录入库（不扣点）。
- [ ] 步骤 3：实现会话、进度、错题本、统计（JdbcTemplate 聚合）。

### Task 7: 前端刷题主链路

**Files:**
- Create: `frontend/src/views/{HomeView,PracticeView,WrongBookView,StatsView}.vue`
- Create: `frontend/src/components/{QuestionCard,MarkdownText,ProgressBar}.vue`

**Produces：** 与现有界面一致的题库选择、刷题、错题本、统计页面。

- [ ] 步骤 1：实现 `MarkdownText`（现有自研渲染器移植）。
- [ ] 步骤 2：实现首页题库与单元进度。
- [ ] 步骤 3：实现刷题页（单选/多选/简答、会话计分、解析占位）。
- [ ] 步骤 4：实现错题本与统计页。
- [ ] 步骤 5：用开发库 342 题回归：刷题、判分、错题、统计数字一致。

---

## 阶段三：AI、导入与计费

### Task 8: AI 客户端与判分解析

**Files:**
- Create: `backend/src/main/java/com/shuati/ai/*`（AesCrypto、AiConfig、AiClient、PromptTemplates、AiCallLog）
- Create: `backend/src/main/java/com/shuati/practice/EssayService.java`
- Test: `backend/src/test/java/com/shuati/ai/AesCompatibilityTest.java`

**Interfaces：** `POST /api/practice/submit-essay`、`GET /api/practice/questions/{id}/explanation`、`POST /api/practice/records/{id}/self-eval`

- [ ] 步骤 1：测试 AES 能解开现有 `ai_config.api_key_encrypted`。
- [ ] 步骤 2：实现 DeepSeek `chat/completions` 调用与 JSON 模式解析。
- [ ] 步骤 3：prompt 从 `lib/prompts.ts` 原样移植。
- [ ] 步骤 4：实现判分/解析失败降级与退点（先接空实现，Task 9 接点数）。

### Task 9: 点数账户与扣费

**Files:**
- Create: `backend/src/main/java/com/shuati/billing/*`（Plan/PointAccount/PointLedger/AiPriceRule/PointService）
- Test: `backend/src/test/java/com/shuati/billing/PointServiceTest.java`

**Interfaces：** `GET /api/plans`、`GET /api/points`、`GET/POST /api/orders`；`consumePoints/refundPoints/applyPlan`

- [ ] 步骤 1：写并发扣点测试：20 次并发只允许余额范围内成功。
- [ ] 步骤 2：实现 `ensureAccount`、`renewPeriod`、`consumePoints`（MONTHLY→BONUS）、`refundPoints`。
- [ ] 步骤 3：把 Task 8 的判分/解析接上扣点与退点。
- [ ] 步骤 4：实现套餐/点数包/订单查询与下单，金额以数据库为准。

### Task 10: 文档导入

**Files:**
- Create: `backend/src/main/java/com/shuati/importer/*`（ImportTask、ImportService、ImportWorker、DocParser）
- Test: `backend/src/test/java/com/shuati/importer/SplitTextTest.java`

**Interfaces：** `POST /api/import`、`GET /api/import/list`、`GET /api/import/task/{id}`、`POST /api/import/task/{id}/import`

- [ ] 步骤 1：测试 5000 字分块、10 万字上限、部分失败按比例退点。
- [ ] 步骤 2：实现 POI/PDFBox/jsoup 解析与文本抽取。
- [ ] 步骤 3：实现 `@Async` worker 与进度落库。
- [ ] 步骤 4：接入点数扣费与题库/题量额度校验。
- [ ] 步骤 5：用现有 5 条导入任务数据回归。

### Task 11: 前端套餐与点数

**Files:**
- Create: `frontend/src/views/PricingView.vue`、`frontend/src/stores/billing.ts`

**Produces：** 套餐、加量包、点数明细、订单页，视觉与现状一致。

- [ ] 步骤 1：实现套餐切换（月/季/年）与价格展示。
- [ ] 步骤 2：实现加量包、下单、订单状态。
- [ ] 步骤 3：实现点数明细与余额卡片，侧边栏同步显示。

---

## 阶段四：后台管理

### Task 12: 管理端业务接口与页面

**Files:**
- Create: `backend/src/main/java/com/shuati/admin/*`（Banks/Units/Questions/Users/Appeals/AiConfig）
- Create: `frontend/src/views/admin/{Banks,Units,Questions,Users,Appeals,AiConfig}.vue`

**Interfaces：** 对应 `/api/admin/**` 全部接口。

- [ ] 步骤 1：题库/单元/题目 CRUD + 默认题库 + 上下架，含额度校验。
- [ ] 步骤 2：用户启停、角色调整、管理员增减点数。
- [ ] 步骤 3：申诉列表与处理。
- [ ] 步骤 4：AI 配置增删改、连通性测试（默认 `deepseek-flash`）。
- [ ] 步骤 5：前端后台页面对接，保持现有布局。

### Task 13: 管理端统计与订单结算

**Files:**
- Create: `backend/src/main/java/com/shuati/admin/StatsService.java`、`BillingAdminController.java`
- Create: `frontend/src/views/admin/{Dashboard,Billing}.vue`

**Interfaces：** `/api/admin/stats/overview|by-unit|hardest-questions|ai-usage`、`/api/admin/billing`、`/api/admin/orders/{id}/settle`

- [ ] 步骤 1：统计聚合与 AI 用量（JdbcTemplate）。
- [ ] 步骤 2：订单结算（PACK 发点、PLAN 开通、幂等）。
- [ ] 步骤 3：后台看板与计费页对接。

---

## 阶段五：部署与验收

### Task 14: Nginx / systemd / 构建脚本

**Files:**
- Create: `deploy/nginx/shuati.conf`
- Create: `deploy/systemd/shuati.service`
- Create: `deploy/scripts/build.sh`、`deploy/scripts/deploy.sh`

**Produces：** 一条命令产出 jar + dist，并给出服务器部署步骤。

- [ ] 步骤 1：Nginx：静态托管 + history 回退 + `/api` 反代 + `client_max_body_size 20m`。
- [ ] 步骤 2：systemd：`EnvironmentFile`、非 root、`Restart=always`。
- [ ] 步骤 3：脚本：前端 build → 后端 package → 可选 rsync 到服务器。
- [ ] 步骤 4：生产 profile 读取环境变量，禁用 SQL 日志与详细堆栈。

### Task 15: 端到端验收与旧代码清理

- [ ] 步骤 1：本机开发库跑通：登录 → 刷题 → 错题本 → 导入 → 扣点 → 后台结算。
- [ ] 步骤 2：空库模拟生产：Flyway 建表 + 引导管理员 + 注册 → 刷题 → 导入。
- [ ] 步骤 3：Visual QA：桌面与移动端主要页面与旧界面逐页对照。
- [ ] 步骤 4：删除旧 Next.js 代码与 Supabase 依赖，更新根 README。

## 依赖关系

- Task 2 依赖 Task 1；Task 3 依赖 Task 2；Task 4 依赖 Task 3。
- Task 5/6 依赖 Task 3；Task 7 依赖 Task 4/5/6。
- Task 8/9/10 依赖 Task 6；Task 11 依赖 Task 9。
- Task 12/13 依赖 Task 9；Task 14 依赖 Task 4/13；Task 15 最后。

## 验收命令

```powershell
mvn -f backend/pom.xml test
npm --prefix frontend run build
```

端到端验收使用本机 `shuati` 开发库（真实数据）与 `shuati_test`（自动化测试）。
