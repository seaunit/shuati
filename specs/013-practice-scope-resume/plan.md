# Implementation Plan: 刷题入口恢复上次练习范围

**Branch**: `013-practice-scope-resume` | **Date**: 2026-10-26 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `specs/013-practice-scope-resume/spec.md`

**Note**: This template is filled in by the `$speckit-plan` command; its definition describes the execution workflow.

## Summary

侧栏「刷题」使用裸 `/app/practice`，练习页缺少范围参数时默认加载全部题目。
现有 `practice_session` 已保存题库/单元范围，但裸入口没有读取。本次新增账号级
“最近有效范围”查询，并在前端路由进入裸练习页前补上 `bankId` 或 `unitId`；
随后的题号恢复继续复用现有 `practice_cursor`，不改变答题与进度口径。

## Technical Context

<!--
  ACTION REQUIRED: Replace the content in this section with the technical details
  for the project. The structure here is presented in advisory capacity to guide
  the iteration process.
-->

**Language/Version**: Java 17、Vue 3 + TypeScript 5

**Primary Dependencies**: Spring Boot 3.5、Spring JDBC、Vue Router、Playwright

**Storage**: MySQL 8.4；复用既有 `practice_session`、`practice_cursor`

**Testing**: JUnit 5、Playwright、`vue-tsc` + Vite build

**Target Platform**: Web + Capacitor Android App

**Project Type**: Vue 前端 + Spring Boot API

**Performance Goals**: 裸入口恢复只增加一次轻量查询；正常网络下不造成可感知额外等待

**Constraints**: 不新增数据库表；不覆盖明确练习参数；查询失败不能阻断刷题；
Web 与 App 使用同一套路由与 API

**Scale/Scope**: 单账号最多返回一条最近有效范围；限制在一次索引查询

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

- **I 密钥与用户数据**：通过。只查询当前登录账号；不返回其他账号数据，不记录密钥或用户文本。
- **II 数据库迁移**：通过。复用既有表，无 schema 变更。
- **III 测试与证据**：通过。后端补服务/接口测试，前端补 Playwright，发布前跑构建。
- **IV 收录边界**：通过。只在登录应用壳内恢复私有/公共题库范围，不影响公开页面。
- **V 点数与 AI**：通过。不触及点数、计费或 AI 调用。
- **VI 客户端同源**：通过。前端唯一实现，Android App 直接复用同一路由与 API。

## Project Structure

### Documentation (this feature)

```text
specs/013-practice-scope-resume/
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
└── src/
    ├── main/java/com/shuati/practice/
    └── test/java/com/shuati/practice/

frontend/
├── src/
│   ├── router/index.ts
│   └── views/PracticeView.vue
└── tests/mobile-responsive.spec.ts
```

**Structure Decision**: 沿用现有单仓库前后端结构。后端在 `practice` 模块提供最近有效范围，
前端在路由层把裸入口解析为带范围的地址；练习页保持现有题目加载和光标逻辑。

## Complexity Tracking

无违反项。
