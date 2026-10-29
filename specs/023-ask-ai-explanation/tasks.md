---

description: "Task list for 答案解析问 AI"
---

# Tasks: 答案解析问 AI

**Input**: Design documents from `/specs/023-ask-ai-explanation/`

**Prerequisites**: `plan.md`, `spec.md`, `research.md`, `data-model.md`, `contracts/`, `quickstart.md`

**Tests**: 必须执行 TDD。JUnit、Playwright 与前端构建属于交付门禁。

**Organization**: 按用户故事拆分，先完成共享 AI 编排和计费基础设施，再逐级交付单次追问、连续追问和防护能力。

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: 增加独立 ASK 工作流、配置项和数据库迁移骨架。

- [X] T001 [P] 新增 `deploy/dify/workflows/shuati-ask.yml`，定义题目、选项、答案、解析、历史和当前问题的输入契约
- [X] T002 [P] 新增 `backend/src/main/resources/db/migration/V23__ask_ai.sql`，扩展 `ai_call_log` 与 `ai_price_rule` 的 `ASK` 用途并插入默认 1 点规则
- [X] T003 [P] 更新 `deploy/env.example`、`deploy/README.md`、`deploy/dify/workflows/README.md`，加入 `DIFY_WORKFLOW_ASK_TOKEN` 和第五个工作流说明
- [X] T004 更新 `backend/src/main/resources/application.yml`，绑定 `DIFY_WORKFLOW_ASK_TOKEN`

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: 在实现刷题 API 和页面之前，完成可复用的 ASK 编排端口、题目权限读取和部署同步。

**⚠️ CRITICAL**: 本阶段完成前不得开始用户故事实现。

### Tests

- [X] T005 [P] 更新 `backend/src/test/java/com/shuati/ai/DifyWorkflowPropertiesTest.java`，验证 `ASK` Token 映射
- [X] T006 [P] 更新 `backend/src/test/java/com/shuati/ai/DifyWorkflowClientTest.java`，验证 ASK 输入字段、Token 和 Markdown 输出
- [X] T007 [P] 更新 `backend/src/test/java/com/shuati/ai/AiWorkflowSelectionTest.java`，覆盖 ASK 配置选择
- [X] T008 [P] 更新 `deploy/scripts/test_dify_prod_bootstrap.py`，验证第五个工作流被同步和冒烟

### Implementation

- [X] T009 更新 `backend/src/main/java/com/shuati/ai/AiWorkflowKey.java`，新增 `ASK("ask", "ASK")`
- [X] T010 更新 `backend/src/main/java/com/shuati/ai/AiWorkflowInputs.java`，新增 `AskInput(questionContent, options, referenceAnswer, explanation, conversation, userQuestion)`
- [X] T011 更新 `backend/src/main/java/com/shuati/ai/AiWorkflowPort.java` 与 `DifyWorkflowProperties.java`，新增 `ask` 语义方法和 Token 映射
- [X] T012 更新 `backend/src/main/java/com/shuati/ai/DifyWorkflowClient.java`，调用 `ask` 工作流并读取 `answer` 输出
- [X] T013 更新 `backend/src/main/java/com/shuati/ai/PromptTemplates.java` 与 `DirectAiWorkflowAdapter.java`，实现直连模式 ASK 提示词
- [X] T014 更新 `deploy/scripts/dify-prod-bootstrap.py`，同步 `shuati-ask`、写入 Token 并把冒烟扩展为五类工作流
- [X] T015 在 `backend/src/main/java/com/shuati/bank/BankService.java` 增加可复用的题目 AI 上下文读取方法，校验题目 `ON` 且复用 `requireBankAccess`

**Checkpoint**: ASK 工作流、计费用途和题目上下文能力可用。

---

## Phase 3: User Story 1 - 围绕当前题目追问 (Priority: P1) 🎯 MVP

**Goal**: 用户提交答案后，在答案解析区域发送问题并获得带当前题目上下文的 Markdown 回答。

**Independent Test**: 提交题目后发送一个关联问题，接口只根据服务端读取的题目上下文回答；未提交答案时页面无入口；无权题目在调用 AI 前被拒绝。

### Tests

- [X] T016 [P] [US1] 新增 `backend/src/test/java/com/shuati/practice/AskAiServiceTest.java`，覆盖公共题成功、服务端上下文、他人私有题 403 且无扣点
- [X] T017 [P] [US1] 在 `frontend/tests/mobile-responsive.spec.ts` 增加“提交后显示问 AI、未提交不显示、成功展示 Markdown 回答”的 Playwright 用例

### Implementation

- [X] T018 [US1] 新增 `backend/src/main/java/com/shuati/practice/AskAiService.java`，校验问题 1-1000 字、读取题目上下文、扣点、调用 ASK、记录日志和返回 `answer/cost/available`
- [X] T019 [US1] 更新 `backend/src/main/java/com/shuati/practice/PracticeController.java`，新增 `POST /api/practice/questions/{id}/ask-ai` 和用户级 20 次/分钟限流
- [X] T020 [P] [US1] 新增 `frontend/src/components/AskAiPanel.vue`，实现输入、加载、Markdown 回答、错误和点数刷新
- [X] T021 [US1] 更新 `frontend/src/views/PracticeView.vue`，在答案或背题解析区域加入问 AI 面板，并以题目 ID 作为重置键
- [X] T022 [US1] 更新 `frontend/src/views/PricingView.vue`，显示 `ASK` 价格规则和 `AI_ASK` 流水名称

**Checkpoint**: 单次问 AI 已可独立演示和测试。

---

## Phase 4: User Story 2 - 连续追问同一道题 (Priority: P2)

**Goal**: 同一道题可连续追问，页面保留本轮历史并在后续请求中带入；切题或刷新后不串题。

**Independent Test**: 同一题连续发送两个问题，第二次请求包含第一条问答；切换题目后历史清空且不发给新题。

### Tests

- [X] T023 [P] [US2] 扩展 `backend/src/test/java/com/shuati/practice/AskAiServiceTest.java`，覆盖历史角色白名单、20 条上限、总长度上限和历史序列化
- [X] T024 [P] [US2] 扩展 `frontend/tests/mobile-responsive.spec.ts`，覆盖连续追问请求体、消息追加和切题清空

### Implementation

- [X] T025 [US2] 在 `backend/src/main/java/com/shuati/practice/AskAiService.java` 规范化历史消息为 JSON，限制最多 20 条、单条 4000 字、总计 12000 字
- [X] T026 [US2] 在 `frontend/src/components/AskAiPanel.vue` 维护用户/助手消息队列、自动滚动、Enter 发送和 Shift+Enter 换行
- [X] T027 [US2] 在 `frontend/src/views/PracticeView.vue` 确保切换题目时问 AI 对话、错误和请求状态全部重置

**Checkpoint**: 连续追问可用，题目之间完全隔离。

---

## Phase 5: User Story 3 - 点数、失败与滥用保护 (Priority: P3)

**Goal**: 明确展示成本，余额不足不调用 AI，失败退点，高频请求被限流，重复请求不重复扣费。

**Independent Test**: 模拟余额不足、AI 失败、重复 `requestId` 和超频四类请求，验证调用次数、余额、流水和提示。

### Tests

- [X] T028 [P] [US3] 扩展 `backend/src/test/java/com/shuati/practice/AskAiServiceTest.java`，覆盖余额不足无调用、AI 失败全额退点、空回答失败、相同 `requestId` 只扣一次
- [X] T029 [P] [US3] 扩展 `frontend/tests/mobile-responsive.spec.ts`，覆盖发送中禁用、402/429/502 提示和成功后点数余额刷新

### Implementation

- [X] T030 [US3] 在 `backend/src/main/java/com/shuati/practice/AskAiService.java` 使用 Redis 保存 `ask:{userId}:{requestId}` 的 5 分钟处理状态和成功响应，失败时释放锁
- [X] T031 [US3] 在 `frontend/src/components/AskAiPanel.vue` 为每次发送生成 `crypto.randomUUID()`，失败保留同一请求 ID 以支持安全重试
- [X] T032 [US3] 在 `frontend/src/components/AskAiPanel.vue` 调用 `useBillingStore().load()` 同步侧栏点数并处理 402 引导
- [X] T033 [US3] 验证 `@RateLimit(name = "ai:ask", limit = 20, windowSeconds = 60, scope = USER)` 与全局 IP 限流共同生效

**Checkpoint**: 计费、幂等和异常路径可对账。

---

## Phase 6: Polish & Cross-Cutting Concerns

**Purpose**: 全量验证、文档一致性和交付证据。

- [X] T034 [P] 新增或更新 `backend/src/test/resources/application-test.yml` 中的 ASK Token 测试配置（如现有配置结构需要）
- [X] T035 [P] 更新 `README.md` 或相关开发文档，说明本地导入 `shuati-ask.yml` 和配置 Token
- [X] T036 运行后端相关 JUnit：`mvn -f backend/pom.xml -Dtest=AskAiServiceTest,DifyWorkflowClientTest,DifyWorkflowPropertiesTest,AiWorkflowSelectionTest test`
- [X] T037 运行前端构建：`npm --prefix frontend run build`
- [X] T038 运行 Playwright：`cd frontend; npx playwright test tests/mobile-responsive.spec.ts`
- [X] T039 运行 Dify bootstrap 单元测试：`python -m unittest deploy/scripts/test_dify_prod_bootstrap.py -v`
- [X] T040 按 `quickstart.md` 完成本地端到端验证并记录接口、账单和页面测试结果

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup**：无依赖。
- **Foundational**：依赖 Setup，阻塞所有用户故事。
- **US1**：依赖 Foundational，交付核心 MVP。
- **US2**：依赖 US1，扩展同一接口与组件。
- **US3**：依赖 US1，增强防护与计费可靠性。
- **Polish**：依赖所有目标故事完成。

### User Story Dependencies

- **US1 (P1)**：Foundational 完成后可独立交付。
- **US2 (P2)**：依赖 US1 的 API 与面板。
- **US3 (P3)**：依赖 US1 的扣点和错误处理；可部分与 US2 并行，但同一文件任务需串行。

### Within Each User Story

- 先写失败测试，确认失败原因正确。
- 先完成服务端权限、计费和契约，再接前端。
- 每完成一个故事运行对应测试并保持全量构建可用。

### Parallel Opportunities

- T001、T002、T003 可并行。
- T005-T008 可并行。
- T016 与 T017 可并行。
- T020 可与后端 T018-T019 并行，但 T021 依赖 T020。
- T028 与 T029 可并行。
- T034-T035 可并行。

## Implementation Strategy

### MVP First

先完成 Phase 1、Phase 2 和 US1，实现一次带题目上下文的问 AI。MVP 验收通过后再加连续追问和幂等防护。

### Incremental Delivery

1. 完成共享 ASK 端口、迁移与工作流。
2. 交付单次问 AI 和答案区入口。
3. 增加同题连续追问。
4. 增加幂等、限流、失败退点和点数同步。
5. 完成 JUnit、Playwright、构建和 quickstart 验证。
