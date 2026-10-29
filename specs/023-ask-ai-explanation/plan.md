# Implementation Plan: 答案解析问 AI

**Branch**: `codex/ask-ai-explanation` | **Date**: 2026-10-29 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/023-ask-ai-explanation/spec.md`

**Note**: AI 调用仍由后端业务层统一编排，浏览器不直接接触模型或 Dify Token。

## Summary

在已提交答案的解析区域加入可连续追问的问 AI 面板。后端根据题目 ID 读取题干、选项、正确答案和已有解析，校验题库访问权限，随后通过独立 `ASK` Dify 工作流生成 Markdown 回答。每次有效追问扣 1 点，失败全额退回；`requestId` 用于防止双击或网络重试重复扣点。对话仅保存在当前页面内，不新增聊天记录表。

## Technical Context

**Language/Version**: Java 17、TypeScript 5、Vue 3、Python 3.12（Dify 运维脚本）

**Primary Dependencies**: Spring Boot 3.5、JdbcTemplate、Flyway、Redis、Vite、Tailwind 4、Dify 1.13.3

**Storage**: MySQL 8.4；Redis 用于接口限流和请求幂等；不持久化问 AI 聊天正文

**Testing**: JUnit 5、Mockito、Spring Boot Test、Playwright、前端类型检查与生产构建

**Target Platform**: Linux 生产服务器、桌面与移动浏览器、同一前端产物打包的 Android/iOS 壳

**Project Type**: Web 前后端单体应用 + 移动 WebView 壳 + 外部 Dify AI 编排

**Performance Goals**: 95% 请求在 30 秒内得到成功或失败终态；单次回答目标 200-800 个中文字符

**Constraints**: 用户问题不超过 1000 字；历史最多 20 条、总计不超过 12000 字；单用户每分钟最多 20 次；AI 调用期间禁止持有数据库事务

**Scale/Scope**: 单一刷题页面新增一个响应式组件、一个业务 API、一个 Dify 工作流和一条计费规则；不改客户端壳工程

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

- **密钥与用户数据**：通过。Dify Token 只写 `/etc/shuati/env` 或 `.env.local`；请求和日志不记录 Token；回答不会写入公开页面。
- **Flyway 迁移**：通过。新增 `V23__ask_ai.sql`，只扩展 AI 用途约束并插入价格规则，不改写历史迁移。
- **测试与证据**：通过。后端覆盖权限、成功、失败退点、幂等和审计；前端补 Playwright；发布前运行构建。
- **收录边界**：通过。问 AI 接口要求登录，结果只在 SPA 内显示，不进入 sitemap、SEO 直出页或公共缓存。
- **点数与 AI**：通过。新增 `ASK` 价格规则、`AI_ASK` 流水和 `ai_call_log` 用途；AI 调用不包在数据库事务里。
- **客户端同源**：通过。网页端先实现；Capacitor 复用同一组件和同一 `/api/**` 接口，不新增 App 专属字段。
- **配置与限流**：通过。新增 Dify Token 同步写入 `deploy/env.example`、部署文档和 bootstrap；接口按用户限流。

## Project Structure

### Documentation (this feature)

```text
specs/023-ask-ai-explanation/
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   ├── ask-ai-api.md
│   └── ask-ai-workflow.md
└── tasks.md
```

### Source Code (repository root)

```text
backend/
├── src/main/java/com/shuati/practice/AskAiService.java
├── src/main/java/com/shuati/practice/PracticeController.java
├── src/main/java/com/shuati/ai/
├── src/main/resources/db/migration/V23__ask_ai.sql
├── src/main/resources/application.yml
└── src/test/java/com/shuati/

frontend/
├── src/components/AskAiPanel.vue
├── src/views/PracticeView.vue
├── src/views/PricingView.vue
└── tests/mobile-responsive.spec.ts

deploy/
├── dify/workflows/shuati-ask.yml
├── scripts/dify-prod-bootstrap.py
├── scripts/test_dify_prod_bootstrap.py
├── env.example
└── README.md
```

**Structure Decision**: 继续使用现有 `backend/`、`frontend/` 和 `deploy/` 结构。问 AI 业务逻辑放在 `practice` 模块，因为权限、题目上下文和点数都属于刷题流程；通用 Dify 协议扩展放在 `ai` 模块。移动端不新增目录或接口。

## Complexity Tracking

无宪法例外。
