# Implementation Plan: 导入历史硬性保留 10 条

**Branch**: `012-import-history-hard-limit` | **Date**: 2026-10-24 | **Spec**: [spec.md](./spec.md)

## Summary

在现有导入快照与执行记录模型上增加“按用户最多保留 10 条解析来源”的硬限制。清理在服务启动和成功创建新导入来源后执行，历史接口同时用 `limit 10` 作为读取防线；删除来源时依靠外键级联清理解析快照和执行记录，不触碰已导入题目及点数流水。

## Technical Context

**Language/Version**: Java 17；Vue 3（无需修改）

**Primary Dependencies**: Spring Boot 3.5、JdbcTemplate、Flyway、JUnit 5

**Storage**: MySQL 8.4；复用 `import_task.source_task_id` 自关联和外键级联

**Testing**: JUnit 集成测试覆盖状态混合、自动触发、用户隔离和题目保留；全量后端测试与前端构建回归

**Target Platform**: Linux 生产服务器

**Project Type**: Spring Boot 后端

**Constraints**: 每用户最多 10 条来源；不按年龄无条件删除；不删除题库、题目、点数或流水

**Scale/Scope**: `ImportService`、`ImportReuseRetentionTest` 和部署启动清理

## Constitution Check

- **I 用户数据与密钥**：通过。按用户清理，不跨账号操作。
- **II Flyway 迁移**：通过。复用现有结构，无需新迁移。
- **III 测试与证据**：通过。增加 JUnit，执行全量后端测试和前端构建。
- **V 点数与 AI**：通过。不修改点数或 AI 调用记录。
- **部署与资源约束**：通过。启动时一次清理，并在新任务创建后按用户清理，不增加常驻任务。

## Design

1. `ImportService` 增加 `SOURCE_HISTORY_LIMIT = 10`。
2. 使用窗口函数按 `user_id` 对 `source_task_id IS NULL` 的记录按 `created_at DESC, id DESC` 排名。
3. 删除排名大于 10 的来源；执行记录由 `source_task_id` 外键 `ON DELETE CASCADE` 同步删除。
4. 应用启动时先处理异常任务，再对所有用户执行一次来源清理。
5. `createTask` 成功插入新来源后调用当前用户清理；清理异常仅记录日志，不回滚新任务。
6. `listTasks` 查询上限改为 10，作为防御性读取限制。
7. 现有成功导入执行记录上限逻辑保留，继续控制内部执行记录数量。

## Structure

```text
backend/src/main/java/com/shuati/importer/ImportService.java
backend/src/test/java/com/shuati/importer/ImportReuseRetentionTest.java
specs/012-import-history-hard-limit/
```

## Complexity Tracking

无宪法例外。
