# Implementation Plan: 题库导出 Word

**Branch**: `015-bank-word-export` | **Date**: 2026-10-26 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `specs/015-bank-word-export/spec.md`

**Note**: This template is filled in by the `$speckit-plan` command; its definition describes the execution workflow.

## Summary

在题库维护页增加“导出 Word”。后端先执行现有 `requireManage` 权限校验，再由独立服务
读取题库、单元和题目，使用已引入的 Apache POI 生成 A4 `.docx`。文档按单元分组、题目全文
连续编号，包含题型、难度、题干、选项、答案和解析，并对常见 Markdown 做可读化处理。

## Technical Context

<!--
  ACTION REQUIRED: Replace the content in this section with the technical details
  for the project. The structure here is presented in advisory capacity to guide
  the iteration process.
-->

**Language/Version**: Java 17、Vue 3 + TypeScript

**Primary Dependencies**: Spring Boot 3.5、Apache POI `XWPFDocument`、Vue 3、lucide-vue-next

**Storage**: MySQL；只读现有 `bank`、`unit`、`question`

**Testing**: JUnit 5 + POI 回读、Playwright、Vue 生产构建

**Target Platform**: Web 与 Capacitor App 共用网页端

**Project Type**: Spring Boot API + Vue SPA

**Performance Goals**: 500 题题库在数秒内生成；导出过程不调用 AI

**Constraints**: 不新增数据库表；不下载远程图片；权限与现有题库维护完全一致；
同一用户导出限流 5 次/分钟

**Scale/Scope**: 单次导出一个题库，按单元排序，全量题目与答案解析

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

- **I 用户数据**：通过。导出只返回当前有维护权限的题库，不记录题目内容到日志。
- **II 数据库迁移**：通过。无 schema 变更。
- **III 测试与证据**：通过。补权限测试、DOCX 回读测试、Playwright 下载测试与构建检查。
- **IV 收录边界**：通过。导出接口要求登录且不进入公开 SEO 页面。
- **V 点数与 AI**：通过。不触发 AI、不改变点数。
- **VI 客户端同源**：通过。Web 与 App 共用题库维护页和同一导出接口。

## Project Structure

### Documentation (this feature)

```text
specs/015-bank-word-export/
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
├── src/main/java/com/shuati/bank/BankWordExportService.java
├── src/main/java/com/shuati/bank/BankManageController.java
└── src/test/java/com/shuati/bank/BankWordExportTest.java

frontend/
├── src/
│   └── views/BankManageView.vue
└── tests/mobile-responsive.spec.ts
```

**Structure Decision**: 文档生成放在独立服务中，控制器只负责权限、限流和 HTTP 下载响应；
前端继续使用题库维护页，不新增路由。

## Complexity Tracking

无违反项。
