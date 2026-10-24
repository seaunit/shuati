# Tasks: 管理员后台赠送点数

**Input**: Design documents from `/specs/011-admin-points-grant/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/admin-points-api.md

**Tests**: 本项目宪法要求后端 JUnit、前端 Playwright 与生产构建验证。

**Organization**: 先完成数据库和审计服务，再按用户故事交付赠点、防护与后台交互。

## Format: `[ID] [P?] [Story] Description`

- **[P]**: 可并行执行（不同文件且无未完成依赖）
- **[Story]**: 对应用户故事
- 每项任务必须带明确文件路径

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: 建立赠点审计数据模型和服务骨架

- [X] T001 新增 `backend/src/main/resources/db/migration/V20__admin_point_grants.sql`，创建 `admin_point_grant`，包含 `admin_id`、`admin_email`、`user_id`、`idempotency_key`、`points`、`reason`、`ledger_id`、`created_at` 及唯一键
- [X] T002 新建 `backend/src/main/java/com/shuati/admin/AdminPointGrantService.java`，定义账号查询、参数校验、幂等赠点和响应模型
- [X] T003 [P] 在 `backend/src/main/java/com/shuati/admin/AdminUserController.java` 增加邮箱查询和赠点接口骨架，并保留旧 `SET_PLAN` 能力

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: 完成事务、流水和权限基础，所有赠点故事共用

- [X] T004 在 `backend/src/main/java/com/shuati/admin/AdminPointGrantService.java` 实现管理员行锁、目标点账户行锁、余额溢出检查和事务提交
- [X] T005 在 `backend/src/main/java/com/shuati/admin/AdminPointGrantService.java` 实现 `ADMIN_GRANT` 点流水写入并关联 `admin_point_grant.ledger_id`
- [X] T006 在 `backend/src/main/java/com/shuati/admin/AdminUserController.java` 为查询与赠点配置 `@RateLimit`
- [X] T007 在 `backend/src/main/java/com/shuati/admin/AdminUserController.java` 停止旧 `GRANT` 分支直接加减余额，返回迁移提示

**Checkpoint**: 账务事务与权限边界完成，可开始用户故事验证。

---

## Phase 3: User Story 1 - 管理员按账号邮箱赠送点数 (Priority: P1) 🎯 MVP

**Goal**: 管理员查询账号后安全赠点，目标余额与点流水同步更新。

**Independent Test**: 管理员向普通账号赠 100 点，确认余额增加、流水和赠点审计记录完整。

### Tests for User Story 1

- [X] T008 [US1] 新建 `backend/src/test/java/com/shuati/admin/AdminPointGrantApiTest.java`，覆盖管理员查询、成功赠点、余额和流水一致性
- [X] T009 [P] [US1] 在 `frontend/tests/mobile-responsive.spec.ts` 增加管理员邮箱查询、赠点成功和余额刷新测试

### Implementation for User Story 1

- [X] T010 [US1] 在 `backend/src/main/java/com/shuati/admin/AdminPointGrantService.java` 实现邮箱精确查询、脱敏账号摘要和最近流水
- [X] T011 [US1] 在 `backend/src/main/java/com/shuati/admin/AdminPointGrantService.java` 实现合法整数赠点、原因校验和最新余额返回
- [X] T012 [US1] 在 `frontend/src/views/AdminView.vue` 增加“赠送点数”入口、账号查询、确认卡片和赠点表单
- [X] T013 [US1] 在 `frontend/src/views/PricingView.vue` 增加 `ADMIN_GRANT` 的用户流水文案“管理员赠送”

**Checkpoint**: 管理员能完成一次可追溯赠点。

---

## Phase 4: User Story 2 - 权限与输入安全校验 (Priority: P1)

**Goal**: 普通用户无法赠点，非法参数和重复请求都不会改变余额。

**Independent Test**: 未登录、普通用户、非法点数、空原因和重复幂等键分别请求，确认只有首次合法请求到账。

### Tests for User Story 2

- [X] T014 [US2] 在 `backend/src/test/java/com/shuati/admin/AdminPointGrantApiTest.java` 覆盖 401/403、0/负数/小数/超上限、空原因和不存在账号
- [X] T015 [US2] 在 `backend/src/test/java/com/shuati/admin/AdminPointGrantApiTest.java` 覆盖相同幂等键重复 10 次只到账一次，以及同键不同载荷返回 409

### Implementation for User Story 2

- [X] T016 [US2] 在 `backend/src/main/java/com/shuati/admin/AdminPointGrantService.java` 实现 UUID 幂等键、同载荷复用首次结果和异载荷冲突
- [X] T017 [US2] 在 `backend/src/main/java/com/shuati/admin/AdminUserController.java` 严格拒绝小数与非法 JSON 数值
- [X] T018 [US2] 在 `frontend/src/views/AdminView.vue` 增加客户端校验、提交中禁用和失败错误展示

**Checkpoint**: 越权、非法输入和双击重复请求均被安全拦截。

---

## Phase 5: User Story 3 - 管理端可审计与可辨认 (Priority: P2)

**Goal**: 管理端清楚展示目标账号身份、余额、预计余额与最近操作结果。

**Independent Test**: 查询不同账号时摘要完全刷新；赠点后页面余额和近期流水更新。

### Tests for User Story 3

- [X] T019 [US3] 在 `frontend/tests/mobile-responsive.spec.ts` 覆盖查询账号切换、预计余额、成功和失败反馈

### Implementation for User Story 3

- [X] T020 [US3] 在 `frontend/src/views/AdminView.vue` 展示脱敏邮箱、昵称、状态、当前余额、预计余额、原因和近期流水
- [X] T021 [US3] 在 `frontend/src/views/AdminView.vue` 在查询变化时清除旧账号状态，成功后重新查询同一账号

**Checkpoint**: 管理员可以在提交前明确确认目标与结果。

---

## Phase 6: Polish & Cross-Cutting Concerns

- [X] T022 更新 `specs/011-admin-points-grant/quickstart.md` 为实际验证命令与结果
- [X] T023 运行 `mvn -f backend/pom.xml -Dtest=AdminPointGrantApiTest,PointServiceTest,BillingRefundTest test`
- [X] T024 [P] 运行 `npx playwright test tests/mobile-responsive.spec.ts --grep "admin point grant"`（工作目录 `frontend`）
- [X] T025 [P] 运行 `npm --prefix frontend run build`
- [X] T026 运行全量后端测试与 `$speckit-analyze`

---

## Dependencies & Execution Order

- **Setup**：T001-T003 完成数据库和接口骨架。
- **Foundational**：T004-T007 完成事务、流水、限流与旧入口收口。
- **US1**：依赖 Foundational。
- **US2**：依赖 Foundational，并与 US1 共用赠点事务。
- **US3**：依赖 US1 查询与赠点响应。
- **Polish**：依赖所有目标故事。

## Parallel Opportunities

- T003 与 T002 可在迁移完成后并行推进不同文件。
- T009 和 T008 可并行准备前后端测试。
- T024 和 T025 可在后端测试稳定后并行执行。

## Implementation Strategy

先完成 T001-T013，交付可用的管理员赠点闭环；再补 T014-T018 的安全与幂等保护；最后完成 T019-T026 的界面说明、全量回归和部署前检查。
