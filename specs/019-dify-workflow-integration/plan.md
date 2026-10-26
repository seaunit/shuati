# Implementation Plan: Dify AI 工作流接入

**Branch**: `019-dify-workflow-integration` | **Date**: 2026-10-26 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `specs/019-dify-workflow-integration/spec.md`

## Summary

在本地测试环境引入现有 Dify 1.13.3 实例，把判题、题目解析、文本出题和截图出题四个 AI 能力迁移为 Dify 工作流。Spring Boot 新增语义化 AI 工作流端口，业务服务只表达“判题 / 解析 / 出题”意图，不再感知 OpenAI 或 Anthropic 协议。生产环境本阶段继续使用现有直连实现，两个实现通过配置隔离。

后端继续负责权益判断、点数扣减与退款、限流、工作流输出校验、题目去重、解析异步落库、`ai_call_log` 和错误映射。Dify 只接收完成任务所需的最小输入，负责提示词、模型选择、重试与输出编排；工作流定义以无密钥 DSL 形式进入仓库。

## Technical Context

**Language/Version**: Java 17；PowerShell 7.6（本地环境脚本）

**Primary Dependencies**: Spring Boot 3.5.16、Spring `RestClient`、Jackson、MySQL 8.4、Redis 6+、Dify 1.13.3

**Storage**: 业务数据继续使用 MySQL；Dify 使用独立 PostgreSQL、Redis 和 Weaviate 容器；`ai_call_log` 增加外部编排审计字段

**Testing**: JUnit 5、Spring Boot Test、MockRestServiceServer、Maven；前端无需改动，仍需通过现有生产构建

**Target Platform**: Windows 本地开发环境；Linux 生产环境本阶段不切换 AI 路径

**Project Type**: Web application，后端为主，包含本地基础设施脚本和 Dify 工作流定义

**Performance Goals**: Dify 连接超时 10 秒、单次阻塞调用读取超时 175 秒；AI 调用期间不持有数据库事务或点数账户行锁

**Constraints**: 仅本地测试环境启用 Dify；工作流密钥只来自环境变量；不自动重试产生费用的调用；不改变现有前端 API

**Scale/Scope**: 4 个独立工作流；单机本地验证；沿用现有 5000 字切片、10 万字文本上限和 5MB 截图上限

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Gate | Result |
| --- | --- | --- |
| I 密钥与用户数据 | Dify Token、模型 Key 只在本机环境或生产密钥文件；DSL 和测试不含真实凭证；日志不记录密钥 | PASS |
| II 数据库迁移 | 只新增 `V21__dify_ai_call_audit.sql`，不改已应用迁移 | PASS |
| III 测试与证据 | 先写失败测试；覆盖判题、解析、文本与截图出题、失败退款、无效输出、鉴权和密钥隔离；全量后端测试与前端构建通过 | PASS |
| IV 收录边界 | 不改变 SEO 页面、公开题库边界和 sitemap | PASS |
| V 点数与 AI 成本 | 保留 `point_ledger`、扣费/退款、Redis Streams；每次 Dify 调用写 `ai_call_log`；AI 调用不持有事务 | PASS |
| VI 客户端同源 | 只改共享后端契约，Web 与 Capacitor 同时获得新能力；不新增客户端专用接口 | PASS |

**Post-design re-check**: 设计没有绕过点数、日志、鉴权或数据库职责，PASS。

## Project Structure

### Documentation (this feature)

```text
specs/019-dify-workflow-integration/
├── spec.md
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   ├── ai-workflow-port.md
│   └── dify-http.md
└── checklists/
    └── requirements.md
```

### Source Code (repository root)

```text
backend/
├── src/main/java/com/shuati/ai/
│   ├── AiWorkflowPort.java
│   ├── AiWorkflowResult.java
│   ├── AiWorkflowInputs.java
│   ├── DirectAiWorkflowAdapter.java
│   ├── DifyWorkflowClient.java
│   ├── DifyWorkflowProperties.java
│   └── AiCallLogger.java
├── src/main/java/com/shuati/practice/
│   ├── AiGradingService.java
│   └── ExplanationSaveConsumer.java
├── src/main/java/com/shuati/importer/
│   └── ImportService.java
├── src/main/resources/
│   ├── application.yml
│   ├── application-local.yml
│   └── db/migration/V21__dify_ai_call_audit.sql
└── src/test/java/com/shuati/
    ├── ai/
    ├── practice/
    └── importer/

deploy/
├── dify/
│   └── workflows/
│       ├── shuati-judge.yml
│       ├── shuati-explain.yml
│       ├── shuati-extract-text.yml
│       └── shuati-extract-image.yml
└── scripts/
    └── dify-local.ps1

docs/
└── Dify本地测试环境.md
```

**Structure Decision**: 保留单一 Spring Boot 后端和 Dify 外部服务。业务代码只依赖后端内部的 `AiWorkflowPort`；Dify HTTP 细节限制在 `DifyWorkflowClient` 中，防止工作流平台侵入练习、题库和计费模块。

## Implementation Phases

### Phase 1: Local Dify Runtime

- 提供本地 Dify 启停与状态脚本，复用 `D:\dify\dify\docker`，验证 API、Worker、Nginx、Redis、PostgreSQL 和 Sandbox。
- 把 Redis Broker 一致性检查纳入状态检查，避免容器重建后 Nginx 或 Celery 使用旧地址/旧口令。
- 增加本地环境说明，但不把 Dify `.env` 或任何 Token 写入仓库。

### Phase 2: AI Port and Dify Adapter

- 定义四个语义方法：`judge`、`explain`、`extractText`、`extractScreenshot`。
- 工作流返回统一携带内容、提供方、工作流标识、外部运行 ID、Token 和耗时。
- `DifyWorkflowClient` 使用阻塞模式调用 `/v1/workflows/run`；截图先调用 `/v1/files/upload`。
- 直连实现封装为另一个适配器，仅在 `SHUATI_AI_ENGINE=direct` 时启用；本地默认 `dify`。
- 缺少 Dify Token、工作流返回非成功状态、输出为空或 HTTP 失败时统一映射为可理解的 502。

### Phase 3: Business Integration

- `AiGradingService` 改用语义端口，保持扣点、退款、练习记录、解析队列和 API 响应不变。
- `ImportService` 改用语义端口，保持分片、权限、去重、导入任务状态和失败退点不变。
- `ai_call_log` 记录 `provider`、`external_run_id` 和 `workflow_key`，Dify 未返回 Token 时写零。
- 后端对判题 JSON、解析 Markdown 和题目 JSON 做统一校验，不信任 Dify 输出。

### Phase 4: Workflows and Verification

- 四个 Dify DSL 在本地导入并完成真实冒烟；模型 Key 只在 Dify 控制台配置。
- 单元测试使用可控 HTTP 服务模拟 Dify，覆盖成功、401、超时、无效 JSON、空输出、文件上传和元数据缺失。
- 业务集成测试覆盖成功、失败退款、无重复扣费、解析异步落库和题库导入。
- 运行后端全量测试、前端生产构建、密钥扫描和本地真实 Dify 冒烟。

## Configuration

```text
SHUATI_AI_ENGINE=dify
DIFY_BASE_URL=http://localhost:8501/v1
DIFY_WORKFLOW_JUDGE_TOKEN=<local secret>
DIFY_WORKFLOW_EXPLAIN_TOKEN=<local secret>
DIFY_WORKFLOW_EXTRACT_TEXT_TOKEN=<local secret>
DIFY_WORKFLOW_EXTRACT_IMAGE_TOKEN=<local secret>
```

- Token 只写 `.env.local` 或进程环境，不写 `.env.example` 的真实值。
- `deploy/env.example` 只增加空占位符和说明。
- 生产本阶段保持 `SHUATI_AI_ENGINE=direct`，不部署 Dify 调用路径。

## Risks and Mitigations

| Risk | Mitigation |
| --- | --- |
| Dify 阻塞调用增加延迟或超时 | 保留 10 秒连接、175 秒读取超时；不持有事务；失败立即退款 |
| Dify 输出结构漂移 | DSL 固定输出字段，后端做 JSON Schema 级别字段校验与归一化 |
| 工作流 Token 泄露 | Token 只来自环境变量；日志、错误响应和测试夹具脱敏 |
| 工作流被误改且无法重建 | DSL 版本化入仓，发布前导出并与仓库定义比对 |
| 多工作流配置漂移 | 四个独立 Token 和启动/冒烟检查逐项验证 |
| 本地 Dify 容器 IP/Redis 口令变化 | 状态脚本检查 Nginx 502 与 Celery 队列认证；重建 API 后同步重启 Nginx |
| 维护两套 AI 实现导致耦合 | 业务层只依赖语义端口；直连适配器仅在非本地配置启用，后续生产迁移后删除 |

## Complexity Tracking

无宪法违规。新增 Dify 外部编排服务是用户明确要求的架构演进，不改变固定主框架或业务数据库。
