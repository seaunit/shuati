# Tasks: 导入结果复用与记录保留

**Input**: Design documents from `/specs/010-import-reuse-retention/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/import-api.md

**Tests**: 本项目宪法要求后端 JUnit、前端 Playwright 与生产构建验证。

**Organization**: 按用户故事拆分，先完成共享数据模型，再依次交付复用、去重与保留上限。

## Format: `[ID] [P?] [Story] Description`

- **[P]**: 可并行执行（不同文件且无未完成依赖）
- **[Story]**: 对应用户故事
- 每个任务都包含明确文件路径

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: 建立本功能共享的数据字段与题干规范化能力

- [X] T001 新增 `backend/src/main/resources/db/migration/V19__import_reuse_retention_dedupe.sql`，为 `import_task` 增加 `source_task_id`、`created_count`、`skipped_count`，为 `question` 增加并回填 `content_hash`，并迁移现有成功导入记录
- [X] T002 [P] 新建 `backend/src/main/java/com/shuati/common/QuestionContent.java`，实现空白规范化与 SHA-256 题干哈希
- [X] T003 [P] 更新 `backend/src/main/java/com/shuati/bank/BankManageController.java` 的题目新增/编辑 SQL，写入 `content_hash`
- [X] T004 [P] 更新 `backend/src/main/java/com/shuati/admin/AdminContentController.java` 的题目新增/编辑 SQL，写入 `content_hash`

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: 建立解析来源/导入执行记录共用的查询与服务边界

- [X] T005 在 `backend/src/main/java/com/shuati/importer/ImportService.java` 增加来源与执行记录查询、最近导入摘要拼装和用户所有权校验
- [X] T006 在 `backend/src/main/java/com/shuati/importer/ImportController.java` 调整导入接口，使其返回新建执行记录 ID 并由 Worker 执行该 ID
- [X] T007 更新 `backend/src/test/java/com/shuati/importer/ImportTargetBankTest.java`，适配“来源 ID 创建执行记录”的新契约并继续覆盖本人/他人/管理员题库权限

**Checkpoint**: 数据结构和来源/执行边界完成，用户故事可以开始实现。

---

## Phase 3: User Story 1 - 复用已解析题目再次导入 (Priority: P1) 🎯 MVP

**Goal**: 已完成导入的解析结果仍可打开、选题目并再次导入任意有权限题库，原记录不被覆盖。

**Independent Test**: 对同一来源连续创建两次导入执行，第二次使用另一个题库；两次执行记录分别存在，来源任务状态与题目 JSON 不变。

### Tests for User Story 1

- [X] T008 [US1] 在 `backend/src/test/java/com/shuati/importer/ImportReuseRetentionTest.java` 增加同来源重复导入、不同题库导入和他人来源不可复用的集成测试
- [X] T009 [P] [US1] 在 `frontend/tests/mobile-responsive.spec.ts` 增加已完成导入结果仍显示“再次导入”及执行中禁用的交互测试

### Implementation for User Story 1

- [X] T010 [US1] 在 `backend/src/main/java/com/shuati/importer/ImportService.java` 实现 `requestImport` 创建子执行记录，不修改来源任务，并在异步导入时读取来源 `items`
- [X] T011 [US1] 在 `backend/src/main/java/com/shuati/importer/ImportService.java` 返回来源列表和详情中的最近导入摘要字段
- [X] T012 [US1] 在 `frontend/src/views/ImportView.vue` 增加最近导入状态、再次导入按钮、轮询执行状态和来源记录展示

**Checkpoint**: 用户可以复用原解析结果，误导入后无需重新解析。

---

## Phase 4: User Story 2 - 重复导入自动去重 (Priority: P1)

**Goal**: 同一目标题库内规范化题干相同的题目只保留一道，不同题库仍允许相同题干。

**Independent Test**: 将含重复题干的来源连续导入同一题库两次，确认题目数量不变、新增/跳过计数准确；再导入另一题库，确认可新增。

### Tests for User Story 2

- [X] T013 [US2] 在 `backend/src/test/java/com/shuati/importer/ImportReuseRetentionTest.java` 增加同题库去重、批内去重、不同题库保留和计数准确测试
- [X] T014 [P] [US2] 在 `frontend/tests/mobile-responsive.spec.ts` 增加“新增 X 道、跳过 Y 道重复题”结果展示与全部重复场景测试

### Implementation for User Story 2

- [X] T015 [US2] 在 `backend/src/main/java/com/shuati/importer/ImportService.java` 的事务内预加载目标题库题干哈希、执行批内去重并写入 `content_hash`
- [X] T016 [US2] 在 `backend/src/main/java/com/shuati/importer/ImportService.java` 回写 `created_count`、`skipped_count`，全部重复时仍正常完成
- [X] T017 [US2] 在 `frontend/src/views/ImportView.vue` 展示新增数、跳过数及最近一次导入结果

**Checkpoint**: 重复导入不会污染题库，跨题库复用不受影响。

---

## Phase 5: User Story 3 - 控制成功导入记录的存储占用 (Priority: P2)

**Goal**: 每次成功导入后，每个用户只保留最近 10 条成功执行记录，并清理失去引用的来源快照。

**Independent Test**: 为一个用户预置 10 条较旧成功记录后执行第 11 次导入，确认仅保留最新 10 条、最早来源被删除、已导入题目保留、其他用户记录不变。

### Tests for User Story 3

- [X] T018 [US3] 在 `backend/src/test/java/com/shuati/importer/ImportReuseRetentionTest.java` 增加 10 条边界、第 11 条清理、来源删除、题目保留和用户隔离测试

### Implementation for User Story 3

- [X] T019 [US3] 在 `backend/src/main/java/com/shuati/importer/ImportService.java` 使用事务模板包裹制卡、计数、完成状态和清理逻辑，确保失败整体回滚
- [X] T020 [US3] 在 `backend/src/main/java/com/shuati/importer/ImportService.java` 实现每用户最近 10 条成功执行记录保留及无引用来源快照清理

**Checkpoint**: 成功记录有明确上限，清理不跨用户且不影响已导入题目。

---

## Phase 6: Polish & Cross-Cutting Concerns

**Purpose**: 文档、回归与发布前验证

- [X] T021 [P] 更新 `specs/010-import-reuse-retention/quickstart.md`，补充实际测试命令和预期结果
- [X] T022 运行 `mvn -f backend/pom.xml -Dtest=ImportReuseRetentionTest,ImportTargetBankTest,ImportSingleTaskTest,BankManageQuestionApiTest test`
- [X] T023 [P] 运行 `npx playwright test tests/mobile-responsive.spec.ts --grep "import"`（工作目录 `frontend`）
- [X] T024 [P] 运行 `npm --prefix frontend run build`
- [X] T025 执行 `$speckit-analyze` 对齐规格、计划、任务与代码，并修复发现的问题

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: 无依赖，先完成数据库与哈希基础设施。
- **Foundational (Phase 2)**: 依赖 Setup，阻塞所有用户故事。
- **User Story 1 (Phase 3)**: 依赖 Foundational。
- **User Story 2 (Phase 4)**: 依赖 Foundational；与 US1 共用导入事务，建议在 US1 后完成。
- **User Story 3 (Phase 5)**: 依赖 US1 的执行记录结构；与 US2 同一事务。
- **Polish (Phase 6)**: 依赖所有目标故事完成。

### Within Each User Story

- 测试先写并确认失败，再实现。
- 数据迁移与共享工具先于服务逻辑。
- 服务逻辑先于前端交互。
- 每个故事完成后运行独立验证。

## Parallel Opportunities

- T002、T003、T004 可并行处理不同文件。
- T009 与 T008 可并行准备前后端测试。
- T014 与 T013 可并行准备前后端去重测试。
- T023 与 T024 可在后端测试稳定后并行执行。

## Implementation Strategy

### MVP First

先完成 T001-T012，交付“历史解析结果可再次导入且不覆盖原记录”。随后补 T013-T017 的去重能力，最后完成 T018-T020 的 10 条保留策略。

### Incremental Delivery

1. 数据迁移与哈希工具完成。
2. 来源/执行记录模型完成。
3. 再次导入可用并验证。
4. 同题库去重启用并验证。
5. 10 条清理启用并验证。
6. 完成前后端回归、构建、部署和线上自检。
