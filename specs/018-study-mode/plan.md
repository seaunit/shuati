# Implementation Plan: 背题模式

**Branch**: `018-study-mode` | **Date**: 2026-10-26 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/[###-feature-name]/spec.md`

**Note**: This template is filled in by the `$speckit-plan` command; its definition describes the execution workflow.

## Summary

新增只读答案接口，按题库可见性返回正确答案；练习页增加本地背题开关，开启后按题加载答案，
并复用现有 AI 解析接口自动补全缺失解析。后端不新增表，Web 与 App 共用。

## Technical Context

<!--
  ACTION REQUIRED: Replace the content in this section with the technical details
  for the project. The structure here is presented in advisory capacity to guide
  the iteration process.
-->

**Language/Version**: Java 17、Vue 3 + TypeScript

**Primary Dependencies**: Spring Boot 3.5、现有 AI 解析与点数服务

**Storage**: 读取 `question.answer/explanation`，无 schema 变更

**Testing**: JUnit 权限测试、Playwright 背题模式测试、前端构建

**Target Platform**: Web 与 Android/iOS WebView 壳

**Project Type**: Web 应用

**Performance Goals**: 切题时一次答案请求；已有解析不调用 AI

**Constraints**: 不提前在题目列表下发答案；同题自动解析请求去重

**Scale/Scope**: 一个只读接口、一个练习页模式

## Constitution Check

- **I 用户数据**：通过，答案接口校验题库可见性。
- **II 数据库迁移**：通过，无变更。
- **III 测试证据**：通过，前后端均补测试。
- **IV 收录边界**：通过，练习页需登录。
- **V 点数与 AI**：通过，复用现有扣费、退款和日志。
- **VI 客户端同源**：通过，网页端唯一实现。

## Project Structure

### Documentation (this feature)

```text
specs/018-study-mode/
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
└── src/main/java/com/shuati/
    ├── bank/BankService.java
    └── practice/PracticeController.java

frontend/
└── src/views/PracticeView.vue
```

**Structure Decision**: 答案读取归 `BankService` 权限体系，控制器暴露只读接口；AI 解析继续复用
`AiGradingService.explanation`。

## Complexity Tracking

无违反项。
