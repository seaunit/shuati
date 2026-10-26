# Tasks: Dify AI 工作流接入

**Input**: Design documents from `specs/019-dify-workflow-integration/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/

**Tests**: 本功能必须采用 TDD；每个行为先写失败测试，再实现。

## Phase 1: Setup

**Purpose**: 建立本地 Dify 配置、目录和文档骨架。

- [x] T001 在 `backend/src/main/resources/application.yml` 和 `backend/src/main/resources/application-local.yml` 增加 AI 引擎选择、Dify 地址、超时和四个工作流 Token 配置
- [x] T002 [P] 在 `deploy/env.example`、`deploy/README.md` 和 `README.md` 增加本地 Dify 配置说明与空密钥占位符
- [x] T003 [P] 创建 `deploy/dify/workflows/README.md`，约定四个工作流 DSL 的命名、导入、版本和密钥边界

---

## Phase 2: Foundational

**Purpose**: 完成所有用户故事共享的 AI 端口、审计模型与 Dify HTTP 基础设施。

**CRITICAL**: 本阶段完成前不得接入具体业务。

### Tests First

- [x] T004 [P] 新增 `backend/src/test/java/com/shuati/ai/DifyWorkflowPropertiesTest.java`，验证地址归一化、超时边界和缺失 Token 的错误信息
- [x] T005 [P] 新增 `backend/src/test/java/com/shuati/ai/AiCallLoggerTest.java`，验证 DIRECT/DIFY 字段、Token 缺失和错误脱敏
- [x] T006 [P] 新增 `backend/src/test/java/com/shuati/ai/DifyWorkflowClientTest.java` 的公共 HTTP 用例，覆盖成功响应、非成功状态、超时、空响应和外部运行 ID
- [x] T007 运行 `mvn -f backend/pom.xml -Dtest=DifyWorkflowPropertiesTest,AiCallLoggerTest,DifyWorkflowClientTest test`，确认测试因缺少实现而失败

### Implementation

- [x] T008 [P] 在 `backend/src/main/java/com/shuati/ai/AiWorkflowInputs.java` 定义 `JudgeInput`、`ExplainInput`、`ExtractTextInput`、`ExtractScreenshotInput`
- [x] T009 [P] 在 `backend/src/main/java/com/shuati/ai/AiWorkflowResult.java` 定义内容、提供方、工作流键、外部运行 ID、模型、Token 和耗时字段
- [x] T010 在 `backend/src/main/java/com/shuati/ai/AiWorkflowPort.java` 定义判题、解析、文本出题和截图出题四个语义方法
- [x] T011 在 `backend/src/main/java/com/shuati/ai/DifyWorkflowProperties.java` 与 `application*.yml` 实现配置绑定、Token 分组和超时校验
- [x] T012 新增 `backend/src/main/resources/db/migration/V21__dify_ai_call_audit.sql`，为 `ai_call_log` 增加 `provider`、`external_run_id`、`workflow_key`
- [x] T013 在 `backend/src/main/java/com/shuati/ai/AiCallLogger.java` 实现审计写入、Token 缺失归零、错误脱敏和不阻断主流程
- [x] T014 在 `backend/src/main/java/com/shuati/ai/DifyWorkflowClient.java` 实现共享 RestClient、Bearer 认证、`X-Trace-Id`、阻塞运行和统一错误映射
- [x] T015 在 `backend/src/main/java/com/shuati/ai/DirectAiWorkflowAdapter.java` 封装现有 `AiClient` 与 `PromptTemplates`，仅在 `SHUATI_AI_ENGINE=direct` 时生效
- [x] T016 在 `backend/src/main/java/com/shuati/ai/` 配置 `dify` 与 `direct` 两个实现的条件装配，默认生产直连、本地 Dify
- [x] T017 运行 T004-T006 定向测试，确认全部转绿

**Checkpoint**: 语义化 AI 端口、Dify 公共客户端、直连适配器和审计基础可用。

---

## Phase 3: User Story 1 - 本地启动 Dify 环境 (Priority: P1) MVP

**Goal**: 一条命令可靠启停现有 Dify，并检测容器、Redis、Worker 和 Web。

**Independent Test**: 停止 Docker 后执行启动和状态检查，确认控制台 200、核心容器正常、Worker 无 Redis 认证错误。

### Tests and Verification

- [x] T018 [US1] 在 `deploy/scripts/dify-local.ps1` 增加 `start`、`stop`、`status` 三个动作的最小实现，先用当前运行态验证输出契约
- [x] T019 [US1] 在 `deploy/scripts/dify-local.ps1` 增加 `$env:DIFY_HOME`、`D:\dify\dify\docker` 路径发现、Docker Desktop 启动等待和 Compose 校验
- [x] T020 [US1] 在 `deploy/scripts/dify-local.ps1` 增加 Redis Broker 口令一致性、Worker 错误日志、Nginx 502 和 `console/api/setup` 检查
- [x] T021 [US1] 运行 `pwsh -File deploy/scripts/dify-local.ps1 status`，记录 Dify 版本、HTTP 状态和核心容器结果

### Implementation

- [x] T022 [US1] 在 `docs/Dify本地测试环境.md` 补充启动、停止、排障、Docker Desktop、端口和本地数据隔离说明
- [x] T023 [US1] 验证 API 容器重建后 Nginx 重新解析上游，Redis Worker 无认证错误，控制台可访问

**Checkpoint**: 本地 Dify 可重复启动和诊断。

---

## Phase 4: User Story 2 - 通过 Dify 完成简答题判分 (Priority: P1)

**Goal**: 判题通过 Dify 完成，扣点、退款、练习记录和 API 响应保持不变。

**Independent Test**: 使用 Dify Mock 提交简答题，分别覆盖成功、失败、超时和无效 JSON，确认响应、点数和记录正确。

### Tests First

- [x] T024 [P] [US2] 在 `backend/src/test/java/com/shuati/ai/DifyWorkflowClientTest.java` 增加 judge 请求字段、输出解析和运行元数据测试
- [x] T025 [P] [US2] 在 `backend/src/test/java/com/shuati/practice/AiGradingServiceDifyTest.java` 增加成功判题、失败退款、无效输出退款和无数据库事务等待测试
- [x] T026 [US2] 运行 `mvn -f backend/pom.xml -Dtest=DifyWorkflowClientTest,AiGradingServiceDifyTest test`，确认新增测试失败

### Implementation

- [x] T027 [US2] 在 `backend/src/main/java/com/shuati/ai/DifyWorkflowClient.java` 实现 `judge` 和判题输出结构校验
- [x] T028 [US2] 在 `backend/src/main/java/com/shuati/practice/AiGradingService.java` 改用 `AiWorkflowPort.judge`，保持 API、计费、退款和记录契约
- [x] T029 [US2] 在 `backend/src/main/java/com/shuati/practice/AiGradingService.java` 使用 `AiCallLogger` 记录 Dify 运行 ID、用途、工作流、Token 和耗时
- [x] T030 [US2] 新增 `deploy/dify/workflows/shuati-judge.yml`，迁移现有判题提示词和 JSON 输出约束
- [x] T031 [US2] 运行 T024-T025 测试并确认转绿

**Checkpoint**: 本地判题完全通过 Dify，失败自动退款。

---

## Phase 5: User Story 3 - 通过 Dify 完成解析与题库提取 (Priority: P2)

**Goal**: 解析、文本出题和截图出题迁移到 Dify，业务校验、解析队列和题库落库保持不变。

**Independent Test**: 分别跑通解析、文本提取和截图提取，确认返回契约、异步落库、题目结构和失败退点正确。

### Tests First

- [x] T032 [P] [US3] 在 `backend/src/test/java/com/shuati/ai/DifyWorkflowClientTest.java` 增加 explain、extract-text、文件上传和 extract-image 契约测试
- [x] T033 [P] [US3] 在 `backend/src/test/java/com/shuati/practice/AiExplanationBillingTest.java` 增加 Dify 解析成功、队列落库、失败退款和无直接模型调用测试
- [x] T034 [P] [US3] 在 `backend/src/test/java/com/shuati/importer/ImportDifyWorkflowTest.java` 增加文本出题、截图出题、无效题目 JSON、超限截图和失败退点测试
- [x] T035 [US3] 运行定向测试，确认新增路径失败

### Implementation

- [x] T036 [US3] 在 `backend/src/main/java/com/shuati/ai/DifyWorkflowClient.java` 实现 `explain`、`extractText`、`extractScreenshot` 和 `/v1/files/upload`
- [x] T037 [US3] 在 `backend/src/main/java/com/shuati/practice/AiGradingService.java` 改用 `AiWorkflowPort.explain`，保持 Redis Streams 解析落库和失败退款
- [x] T038 [US3] 在 `backend/src/main/java/com/shuati/importer/ImportService.java` 改用 `AiWorkflowPort.extractText` 与 `extractScreenshot`，保持分片、权限、去重和任务状态
- [x] T039 [US3] 在 `backend/src/main/java/com/shuati/importer/ImportService.java` 增加 Dify 题目输出结构校验和归一化，非法输出视为工作流失败
- [x] T040 [P] [US3] 新增 `deploy/dify/workflows/shuati-explain.yml`，迁移解析提示词和 Markdown 输出约束
- [x] T041 [P] [US3] 新增 `deploy/dify/workflows/shuati-extract-text.yml`，迁移文本出题提示词和 Java 默认伪代码规则
- [x] T042 [P] [US3] 新增 `deploy/dify/workflows/shuati-extract-image.yml`，配置图片文件变量、截图出题提示词和题目 JSON 输出
- [x] T043 [US3] 运行 T032-T034 测试并确认转绿

**Checkpoint**: 四类 AI 业务均通过语义端口调用 Dify。

---

## Phase 6: User Story 4 - 观察、隔离和回退 AI 调用 (Priority: P3)

**Goal**: 管理员可区分 Dify 与直连调用，失败可追踪，密钥与外部运行数据不泄露。

**Independent Test**: 触发成功、401、超时和无效输出，检查 `ai_call_log`、管理员接口、退款和密钥扫描。

### Tests First

- [x] T044 [P] [US4] 在 `backend/src/test/java/com/shuati/admin/AiUsageAuditTest.java` 增加 provider、workflow、run ID 和失败记录查询测试
- [x] T045 [P] [US4] 在 `backend/src/test/java/com/shuati/ai/DifyWorkflowClientTest.java` 和 `AiCallLoggerTest.java` 验证错误响应、日志和异常消息不含 Token
- [x] T046 [US4] 运行定向测试，确认审计与脱敏用例失败

### Implementation

- [x] T047 [US4] 在 `backend/src/main/java/com/shuati/admin/AdminStatsController.java` 返回 Dify provider、workflow key 和外部运行 ID，保持现有统计字段兼容
- [x] T048 [US4] 在 `backend/src/main/java/com/shuati/ai/` 统一异常脱敏和请求追踪，确保 401、429、5xx 与超时映射一致
- [x] T049 [US4] 检查所有 `AiClient.chat*` 业务调用点，确认本地 Dify profile 不会触发直连模型
- [x] T050 [US4] 运行审计与脱敏定向测试并确认转绿

**Checkpoint**: 外置 AI 调用可追踪、可区分且不泄露机密。

---

## Phase 7: Polish & Cross-Cutting Concerns

- [x] T051 [P] 更新 `specs/019-dify-workflow-integration/quickstart.md`，写入实际本地 Dify 版本、端口、启动結果和冒烟证据
- [x] T052 [P] 更新 `docs/Dify本地测试环境.md`，说明四个工作流导入、Token 创建、模型 Key 配置和故障演练
- [x] T053 运行 `mvn -f backend/pom.xml test`，要求 0 失败、0 错误
- [x] T054 运行 `npm --prefix frontend run build`，确认 API 契约变化未破坏前端
- [x] T055 执行密钥扫描，检查 `DIFY_WORKFLOW_*_TOKEN`、模型 Key、Authorization Header 和 Dify `.env` 未进入仓库
- [x] T056 在真实本地 Dify 导入四个 DSL，配置模型供应商并完成四个工作流冒烟
- [x] T057 逐项验证判题、解析、文本出题和截图出题，不依赖公网时记录 Mock 证据，真实冒烟记录 Dify 运行 ID
- [x] T058 提交代码并保持生产未切换，提交信息说明本阶段仅本地 Dify
- [x] T059 运行 `$speckit-analyze` 对齐 spec、plan、tasks、代码和测试结果

---

## Dependencies & Execution Order

### Phase Dependencies

- Setup (T001-T003) 可立即开始。
- Foundational (T004-T017) 阻塞所有用户故事。
- US1 (T018-T023) 只依赖本文档与 Docker，不依赖业务改造。
- US2 (T024-T031) 依赖 T004-T017。
- US3 (T032-T043) 依赖 T004-T017；与 US2 可并行，但共享 `DifyWorkflowClient.java` 时需串行合并。
- US4 (T044-T050) 依赖 US2 或 US3 至少一条真实 Dify 调用路径。
- Polish 依赖所有目标故事完成。

### Story Dependencies

- US1 无业务依赖。
- US2 与 US3 共享 AI 端口和 Dify HTTP 基础。
- US4 依赖 US2/US3 产生的运行数据。

### Parallel Opportunities

- T002/T003 可并行。
- T004/T005/T006 可并行编写测试。
- T008/T009 可并行定义输入与结果记录。
- US2 的测试文件与 US3 的测试文件可并行编写。
- T040/T041/T042 三个 DSL 可并行。

## Implementation Strategy

### MVP First

1. 完成 Setup + Foundational。
2. 完成 US1，确保本地 Dify 可重复启动。
3. 完成 US2，先跑通判题与点数闭环。
4. 停止并验收，确认本地判题无直连模型调用。

### Incremental Delivery

1. 本地 Dify 运行脚本和状态检查。
2. 判题 Dify 工作流。
3. 解析、文本出题和截图出题。
4. 审计、脱敏、真实 Dify 冒烟和全量回归。

## Notes

- 生产 AI 路径在本阶段保持直连。
- 不把 Dify Token、模型 Key、Dify `.env` 或运行数据提交到仓库。
- 测试必须使用 Mock；真实冒烟只作为本地证据。
- 共享文件任务不得并行修改同一工作副本。
