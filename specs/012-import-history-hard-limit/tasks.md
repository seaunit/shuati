# Tasks: 导入历史硬性保留 10 条

**Input**: `specs/012-import-history-hard-limit/`

**Tests**: 后端 JUnit、全量后端回归和前端构建。

## Phase 1: Tests

- [X] T001 [US1] 在 `backend/src/test/java/com/shuati/importer/ImportReuseRetentionTest.java` 增加 12 条混合状态来源只保留最新 10 条的测试
- [X] T002 [US2] 在 `backend/src/test/java/com/shuati/importer/ImportReuseRetentionTest.java` 增加旧来源删除后已导入题目、执行记录级联和用户隔离测试
- [X] T003 [US1] 在 `backend/src/test/java/com/shuati/importer/ImportReuseRetentionTest.java` 增加新建来源自动触发清理的测试

## Phase 2: Implementation

- [X] T004 在 `backend/src/main/java/com/shuati/importer/ImportService.java` 增加来源历史上限常量、按用户窗口排序和全量清理方法
- [X] T005 在 `backend/src/main/java/com/shuati/importer/ImportService.java` 的服务启动事件中执行所有用户历史清理
- [X] T006 在 `backend/src/main/java/com/shuati/importer/ImportService.java` 的 `createTask` 成功写入后执行当前用户清理，失败只记录日志
- [X] T007 在 `backend/src/main/java/com/shuati/importer/ImportService.java` 的 `listTasks` 查询上限改为 10

## Phase 3: Verification

- [X] T008 运行 `mvn -f backend/pom.xml -Dtest=ImportReuseRetentionTest test`
- [X] T009 运行全量 `mvn -f backend/pom.xml test`
- [X] T010 [P] 运行 `npm --prefix frontend run build`
- [X] T011 执行 `$speckit-analyze` 并确认规格、计划、任务与代码一致

## Dependencies

- T001-T003 必须先失败，再实现 T004-T007。
- T008 依赖实现完成。
- T009-T011 依赖 T008 通过。
