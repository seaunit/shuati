# Implementation Plan: 代码题解析展示伪代码

**Branch**: `017-code-answer-feedback` | **Date**: 2026-10-26 | **Spec**: [spec.md](./spec.md)

**Input**: `specs/017-code-answer-feedback/spec.md`

**Note**: This template is filled in by the `$speckit-plan` command; its definition describes the execution workflow.

## Summary

前端把 `referenceAnswer` 改用现有 `MarkdownText` 渲染；后端评分提示词对代码/算法题
要求 feedback 包含 Markdown 代码块。现有 API、数据库和历史记录均不变。

## Technical Context

<!--
  ACTION REQUIRED: Replace the content in this section with the technical details
  for the project. The structure here is presented in advisory capacity to guide
  the iteration process.
-->

**Language/Version**: Java 17、Vue 3 + TypeScript

**Primary Dependencies**: Spring Boot 3.5、现有 MarkdownText

**Storage**: 无变更

**Testing**: JUnit 提示词测试、Playwright 代码块渲染测试、前端构建

**Target Platform**: Web 与 Capacitor App

**Project Type**: Web 应用

**Performance Goals**: 不增加接口请求

**Constraints**: 历史记录无需迁移；非代码题不生成无关代码块

**Scale/Scope**: 两个文件的局部修改

## Constitution Check

- **I 用户数据**：通过，不新增敏感信息。
- **II 数据库迁移**：通过，无 schema 变更。
- **III 测试证据**：通过，前后端均补回归。
- **IV 收录边界**：通过，练习页需要登录。
- **V 点数与 AI**：通过，调用次数和计费不变。
- **VI 客户端同源**：通过，App 复用同一网页端。

## Project Structure

### Documentation (this feature)

```text
specs/017-code-answer-feedback/
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
└── src/main/java/com/shuati/ai/PromptTemplates.java

frontend/
├── src/views/PracticeView.vue
└── tests/mobile-responsive.spec.ts
```

**Structure Decision**: 复用已有 Markdown 组件，不新增组件或接口。

## Complexity Tracking

无违反项。
