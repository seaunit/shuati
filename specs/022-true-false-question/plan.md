# Implementation Plan: 判断题题型

**Branch**: `test` | **Date**: 2026-10-28 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/022-true-false-question/spec.md`

**Note**: This template is filled in by the `$speckit-plan` command; its definition describes the execution workflow.

## Summary

在现有题目模型中新增 `TRUE_FALSE` 类型，复用选项 JSON 和本地选择判分链路。判断题固定保存 `T=正确`、`F=错误` 两个选项，服务端负责将常见正误表达归一化，前端提供固定二选一交互。导入、题库维护、后台管理、背题、错题本、公开页和 Word 导出同步支持，不新增 API 或数据表。

## Technical Context

<!--
  ACTION REQUIRED: Replace the content in this section with the technical details
  for the project. The structure here is presented in advisory capacity to guide
  the iteration process.
-->

**Language/Version**: Spring Boot 3.5 / Java 17；Vue 3 + TypeScript 5

**Primary Dependencies**: Flyway、JDBC/JdbcTemplate、Vue Router、Playwright、JUnit

**Storage**: MySQL 8.4；沿用 `question.type/options/answer`

**Testing**: JUnit + Playwright + `npm run build`

**Target Platform**: Web SPA 与 Capacitor Android/iOS 壳

**Project Type**: 单体 Web 应用（Spring Boot + Vue）

**Performance Goals**: 判断题判分与其他选择题一致，无额外网络请求或 AI 调用

**Constraints**: 复用现有 `/api/**`；不改变单选、多选、简答行为；答案只允许二选一

**Scale/Scope**: 题型枚举、题库维护、后台管理、导入、背题、错题本、SEO 与 Word 导出

## Constitution Check

| 原则 | 结论 | 说明 |
| --- | --- | --- |
| I 密钥与用户数据 | PASS | 不新增密钥或用户数据导出 |
| II 数据库迁移 | PASS | 新增 V22 前向迁移扩展 CHECK 约束 |
| III 测试与证据 | PASS | JUnit、Playwright、构建均纳入任务 |
| IV 收录边界 | PASS | 只调整现有公共题目展示，不扩大收录范围 |
| V 点数与 AI | PASS | 本地判分，不调用 AI、不产生点数流水 |
| VI 客户端同源 | PASS | 仅网页端实现，Capacitor 复用同一产物 |

## Project Structure

### Documentation (this feature)

```text
specs/022-true-false-question/
├── plan.md              # This file ($speckit-plan command output)
├── research.md          # Phase 0 output ($speckit-plan command)
├── data-model.md        # Phase 1 output ($speckit-plan command)
├── quickstart.md        # Phase 1 output ($speckit-plan command)
├── contracts/           # Phase 1 output ($speckit-plan command)
└── tasks.md             # Phase 2 output ($speckit-tasks command - NOT created by $speckit-plan)
```

### Source Code (repository root)
<!--
  ACTION REQUIRED: Replace the placeholder tree below with the concrete layout
  for this feature. Delete unused options and expand the chosen structure with
  real paths (e.g., apps/admin, packages/something). The delivered plan must
  not include Option labels.
-->

```text
backend/
├── src/main/java/com/shuati/
│   ├── bank/            # 题库维护、Word 导出
│   ├── admin/           # 后台题目维护
│   ├── practice/        # 本地判分
│   ├── importer/        # 导入结果归一化
│   ├── ai/              # 出题提示词
│   └── seo/             # 公共页题型和答案展示
├── src/main/resources/db/migration/
└── src/test/java/com/shuati/

frontend/
├── src/
│   ├── components/
│   ├── questionTypes.ts # 前端题型标签与固定答案工具
│   └── views/
└── tests/

mobile/                   # 复用 frontend 构建产物，无原生改动
```

**Structure Decision**: 保持现有前后端单体结构。新增前端题型工具以减少多个页面重复判断；后端用共享判断题工具统一选项和答案归一化。

## Complexity Tracking

无。
