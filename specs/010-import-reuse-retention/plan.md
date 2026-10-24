# Implementation Plan: 导入结果复用与记录保留

**Branch**: `010-import-reuse-retention` | **Date**: 2026-10-24 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `specs/010-import-reuse-retention/spec.md`

## Summary

将现有 `import_task` 拆成同一张表中的“解析来源”和“导入执行记录”：解析来源保留题目 JSON，每次导入创建独立的执行记录，复用来源中的题目，不覆盖历史状态。目标题库内的题目按规范化题干哈希去重；导入成功后按用户保留最近 10 条成功执行记录，并清理不再被引用的来源快照。

## Technical Context

**Language/Version**: Java 17；Vue 3 + TypeScript

**Primary Dependencies**: Spring Boot 3.5、JdbcTemplate、Flyway、Vue 3、Vite、Playwright、JUnit 5

**Storage**: MySQL 8.4；`import_task` 增加来源关联与导入统计，`question` 增加题干哈希

**Testing**: JUnit 集成测试覆盖复用、去重、保留上限和权限矩阵；Playwright 覆盖历史结果再次导入与结果提示；前端生产构建

**Target Platform**: Linux 生产服务器；Web 与 Capacitor 共用的 Vue 前端

**Project Type**: Spring Boot 后端 + Vue Web 前端

**Performance Goals**: 导入继续沿用全局单任务串行；100 道题导入不增加额外 AI 调用，去重查询使用索引

**Constraints**: 2 核 4G 生产环境；不得在 AI 调用期间持有数据库事务；历史记录上限为每用户 10 条；重复导入不得覆盖原解析记录

**Scale/Scope**: 当前导入接口、历史任务页面、题库维护题目写入路径与生产已有数据迁移

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

- **I 密钥与用户数据**：通过。不新增密钥；历史查询始终带 `user_id`；不跨用户清理或复用。
- **II Flyway 迁移**：通过。新增 `V19`，不改写已应用迁移；现有成功导入记录回填为执行记录。
- **III 测试与证据**：通过。后端增加 JUnit，前端增加 Playwright，并执行构建与线上自检。
- **IV 收录边界**：通过。导入历史和题目只为登录用户使用，不涉及公开收录。
- **V 点数与 AI**：通过。再次导入不调用 AI；解析调用仍在事务外；导入写库事务不包含 AI。
- **VI 客户端同源**：通过。仅修改网页端，移动端继续复用网页端产物和现有 API。
- **部署与资源约束**：通过。继续使用全局单任务；清理在成功导入事务内完成，不引入后台并发任务。

## Project Structure

### Documentation (this feature)

```text
specs/010-import-reuse-retention/
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   └── import-api.md
└── checklists/
    └── requirements.md
```

### Source Code

```text
backend/
├── src/main/java/com/shuati/common/QuestionContent.java
├── src/main/java/com/shuati/importer/ImportController.java
├── src/main/java/com/shuati/importer/ImportService.java
├── src/main/java/com/shuati/bank/BankManageController.java
├── src/main/java/com/shuati/admin/AdminContentController.java
├── src/main/resources/db/migration/V19__import_reuse_retention_dedupe.sql
└── src/test/java/com/shuati/importer/ImportReuseRetentionTest.java

frontend/
├── src/views/ImportView.vue
└── tests/mobile-responsive.spec.ts
```

**Structure Decision**: 复用现有后端导入模块和 Vue 导入页，不新增独立服务；数据库演进继续由 Flyway 管理。

## Design Decisions

1. 原解析任务保持 `source_task_id IS NULL`，继续保存 `items`；每次导入创建 `source_task_id` 指向原任务的执行记录。
2. 已有 `DONE/COMPLETED` 记录迁移时生成一条历史执行记录，并将原任务恢复为可复用的 `READY/COMPLETED`。
3. 执行记录保存 `created_count`、`skipped_count`、目标题库、状态与时间，前端展示最近一次执行结果。
4. 去重范围是目标题库内规范化题干；新增 `question.content_hash` 和索引，所有题目创建/编辑路径同步维护哈希。
5. 每次成功导入后，在同一个数据库事务中保留该用户最近 10 条成功执行记录；删除旧执行记录后，再删除没有执行记录引用的来源快照。
6. 原题库中的题目不随导入历史清理；失败或未执行的来源快照不参与成功记录上限清理。

## Complexity Tracking

无宪法例外。
