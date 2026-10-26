# Implementation Plan: 试卷原题保真解析

**Branch**: `016-exam-paper-preservation` | **Date**: 2026-10-26 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `specs/016-exam-paper-preservation/spec.md`

**Note**: This template is filled in by the `$speckit-plan` command; its definition describes the execution workflow.

## Summary

保留现有截图/文档导入接口，不新增数据库字段。重写题目抽取提示词，先区分“已有试卷”和
“普通学习资料”：试卷采用严格原题抽取模式，按题号/候选项保留题目边界、表格和任务约束；
只有没有现成问题的资料才进入生成模式。并用用户提供的历史截图做真实模型回归。

## Technical Context

<!--
  ACTION REQUIRED: Replace the content in this section with the technical details
  for the project. The structure here is presented in advisory capacity to guide
  the iteration process.
-->

**Language/Version**: Java 17

**Primary Dependencies**: Spring Boot 3.5、现有 AiClient、Redis 限制器

**Storage**: 读取既有 `import_task.source_text` 做回归；无 schema 变更

**Testing**: JUnit 提示词约束测试、真实模型截图回归、现有导入测试

**Target Platform**: Web 导入页，Android App 通过同一网页端复用

**Project Type**: Web API + AI 解析任务

**Performance Goals**: 不增加 AI 调用次数和点数成本；仍为每张截图一次模型调用

**Constraints**: 不自动删除或覆盖用户旧题库；不改现有导入接口契约；
纯笔记仍保持生成练习题能力

**Scale/Scope**: 修改一个系统提示词和一个截图用户提示词，并补提示词回归测试

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

- **I 用户数据**：通过。回归只使用当前用户已有导入任务，不进入 Git。
- **II 数据库迁移**：通过。无 schema 变更。
- **III 测试与证据**：通过。提示词单元测试 + 真实三张截图回归。
- **IV 收录边界**：通过。导入任务和题目不进入公开页面。
- **V 点数与 AI**：通过。调用次数不变，仍记录 `ai_call_log` 和点数账本。
- **VI 客户端同源**：通过。导入页为共享网页端实现。

## Project Structure

### Documentation (this feature)

```text
specs/016-exam-paper-preservation/
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
├── src/main/java/com/shuati/ai/PromptTemplates.java
├── src/main/java/com/shuati/importer/ImportService.java
└── src/test/java/com/shuati/ai/PromptTemplatesTest.java

frontend/
└── 无需改动
```

**Structure Decision**: 问题发生在文本进入模型前对资料形态的错误分类，修复集中在提示词；
解析、计费、历史和双端契约保持不变。

## Complexity Tracking

无违反项。
