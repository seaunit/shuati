# Tasks: 判断题题型

**Input**: Design documents from `/specs/022-true-false-question/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/

**Tests**: 按项目宪法要求，后端与前端测试必须与实现同批完成。

## Phase 1: Setup

- [x] T001 新增 Flyway `backend/src/main/resources/db/migration/V22__true_false_question_type.sql`，扩展题型和选项约束
- [x] T002 [P] 新增后端共享工具 `backend/src/main/java/com/shuati/question/QuestionTypeSupport.java`，统一定义 `TRUE_FALSE`、固定选项和答案归一化
- [x] T003 [P] 新增前端共享工具 `frontend/src/questionTypes.ts`，统一题型标签、判断题固定选项和答案显示
- [x] T004 更新 `.specify/feature.json` 和规格产物，确认本地测试环境范围

## Phase 2: Foundational (Blocking)

**Purpose**: 所有用户故事共用的类型和校验基础。

- [x] T005 先写 `backend/src/test/java/com/shuati/question/QuestionTypeSupportTest.java`，覆盖合法、非法及正误同现答案
- [x] T006 实现 `QuestionTypeSupport` 的答案归一化与固定选项生成，使 T005 通过
- [x] T007 [P] 更新 `backend/src/main/java/com/shuati/ai/PromptTemplates.java`，让出题模型识别 `TRUE_FALSE`
- [x] T008 [P] 同步 `deploy/dify/workflows/` 中的文本/截图出题提示词，加入判断题 JSON 约束

**Checkpoint**: 类型枚举、归一化和导入提示词基础就绪。

## Phase 3: User Story 1 - 练习判断题 (P1) 🎯 MVP

**Goal**: 用户可以作答判断题并得到正确颜色与 10/0 分。

**Independent Test**: 使用判断题为正确答案和错误答案各提交一次，验证题型、选项、颜色、分数和背题模式。

### Tests

- [x] T009 [US1] 新增 `backend/src/test/java/com/shuati/practice/TrueFalsePracticeTest.java`，覆盖答对、答错和不允许走 AI 判分
- [x] T010 [US1] 在 `frontend/tests/mobile-responsive.spec.ts` 增加判断题练习和背题模式 Playwright 用例

### Implementation

- [x] T011 [US1] 修改 `backend/src/main/java/com/shuati/practice/PracticeService.java`，允许 `TRUE_FALSE` 复用本地选择判分
- [x] T012 [US1] 修改 `frontend/src/views/PracticeView.vue`，显示“判断题”和固定“正确/错误”，并映射 T/F 显示
- [x] T013 [US1] 确保错题重练、进度和语音组件继续复用现有行为

**Checkpoint**: 判断题可完整练习，MVP 可独立验收。

## Phase 4: User Story 2 - 维护判断题 (P2)

**Goal**: 题库维护和后台管理可以创建、编辑判断题。

**Independent Test**: 在自建题库新增判断题、编辑答案并重新打开表单；普通用户不能修改他人题库。

### Tests

- [x] T014 [US2] 扩展 `backend/src/test/java/com/shuati/bank/BankManageQuestionApiTest.java`，覆盖创建、编辑、非法答案和权限
- [x] T015 [US2] 扩展 `frontend/tests/mobile-responsive.spec.ts`，覆盖题库维护新增/编辑判断题

### Implementation

- [x] T016 [US2] 修改 `backend/src/main/java/com/shuati/bank/BankManageController.java`，归一化并校验判断题
- [x] T017 [US2] 修改 `backend/src/main/java/com/shuati/admin/AdminContentController.java`，支持判断题创建与更新
- [x] T018 [US2] 修改 `frontend/src/views/BankManageView.vue`，增加判断题型和固定答案选择
- [x] T019 [US2] 修改 `frontend/src/views/AdminView.vue`，增加判断题表单和固定选项处理

**Checkpoint**: 内容维护入口完整可用。

## Phase 5: User Story 3 - 导入、导出与展示 (P3)

**Goal**: AI 导入生成判断题，Word、错题本和公共页面正确展示。

**Independent Test**: 导入含“正确/错误”“对/错”“√/×”的资料，确认归一化；导出 Word 并回读。

### Tests

- [x] T020 [US3] 新增或扩展导入测试 `backend/src/test/java/com/shuati/importer/ImportDifyWorkflowTest.java`，覆盖判断题归一化
- [x] T021 [US3] 扩展 `backend/src/test/java/com/shuati/bank/BankWordExportTest.java`，覆盖判断题标签和答案
- [x] T022 [US3] 扩展 `frontend/tests/mobile-responsive.spec.ts`，覆盖错题本题型标签

### Implementation

- [x] T023 [US3] 修改 `backend/src/main/java/com/shuati/importer/ImportService.java`，在导入入库前归一化判断题
- [x] T024 [US3] 修改 `backend/src/main/java/com/shuati/bank/BankWordExportService.java`，输出“判断题”和中文答案
- [x] T025 [US3] 修改 `backend/src/main/java/com/shuati/seo/SeoController.java` 与 `frontend/src/views/WrongBookView.vue`，补判断题标签和答案展示

**Checkpoint**: 判断题贯穿导入、导出、错题本和公开页面。

## Phase 6: Polish

- [x] T026 运行迁移与后端相关测试 `mvn -f backend/pom.xml test`
- [x] T027 运行前端构建 `npm --prefix frontend run build`
- [x] T028 顺序运行判断题及既有刷题 Playwright 回归
- [x] T029 更新 `specs/022-true-false-question/quickstart.md` 的验证结果
- [x] T030 提交并推送 `test` 分支

## Dependencies & Execution Order

- T001-T006 阻塞所有业务实现。
- T007-T008 可在基础工具完成后并行。
- US1 完成后再做 US2/US3 的集成验收。
- T026-T030 必须在全部实现完成后执行。

## Parallel Opportunities

- T002、T003 可在不同语言目录并行。
- T007、T008 可并行修改提示词。
- T014-T019 与 T020-T025 中不同文件的修改可由不同执行者并行，但必须避免同时修改同一测试文件。

## Implementation Strategy

1. 先完成迁移、共享类型工具和失败测试。
2. 交付 US1，确保最小闭环可刷题。
3. 完成 US2 内容维护。
4. 完成 US3 导入导出展示。
5. 最后运行完整相关回归、构建并提交测试分支。
