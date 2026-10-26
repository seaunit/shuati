# Implementation Plan: Dify 生产切换

**Branch**: `020-dify-production-cutover` | **Date**: 2026-10-27 | **Spec**: [spec.md](./spec.md)

## Summary

在现有香港服务器上部署固定版本 Dify 1.13.3，复用业务服务器并保持现有 MySQL、支付和邮件配置不变。Dify 控制台只监听 `127.0.0.1:8501`，正式 Spring Boot 通过 `SHUATI_AI_ENGINE=dify` 调用本机服务 API。部署脚本负责安装 Docker、增加 Swap、生成独立密钥、导入四套工作流、创建四个 Service API Token、更新 `/etc/shuati/env`、重启服务并执行四类真实冒烟。失败时切回 `direct`。

## Technical Context

**Language/Version**: Java 17 / Spring Boot 3.5.16；Dify 1.13.3；Bash 5

**Primary Dependencies**: Docker Engine、Docker Compose、Dify API/Worker/Web、PostgreSQL、Redis、Weaviate、DeepSeek 插件 0.0.26

**Storage**: Dify 独立 Docker 数据卷；业务 MySQL 不变；密钥只写 `/etc/shuati/env`

**Testing**: `mvn -f backend/pom.xml test`、`npm --prefix frontend run build`、Dify 健康检查、四类真实工作流冒烟、公网页面与端口检查

**Target Platform**: Ubuntu 24.04、2 vCPU / 4 GiB、阿里云香港

**Project Type**: Web application + external AI orchestration service

**Performance Goals**: 四类 AI 工作流各一次成功；现有站点和支付不因 Dify 部署中断超过 2 分钟

**Constraints**: Dify 控制台不得公网暴露；总内存有限；测试分支的模拟邮件/测试支付不得合入正式分支；不得覆盖现有支付私钥和 SMTP 密码

**Scale/Scope**: 当前单机生产环境，Dify API Worker 串行低并发，四个工作流

## Constitution Check

| 原则 | 结论 | 说明 |
| --- | --- | --- |
| I 密钥与用户数据 | PASS | 所有 Token 和 Dify 密钥写 `/etc/shuati/env`，仓库只保留空占位 |
| II 数据库迁移 | PASS | 业务 schema 不变；Dify 使用独立数据卷 |
| III 测试与证据 | PASS | 上线前跑全量测试与构建，上线后执行真实工作流冒烟 |
| IV 收录边界 | PASS | 本次不新增公开页面，Nginx 只保留现有公开路由 |
| V 点数与 AI | PASS | 继续使用现有点数、退款、`ai_call_log` 与外部运行 ID |
| VI 客户端同源 | PASS | Web/App 仍共享同一后端 API，客户端不直连 Dify |

## Project Structure

### Documentation

```text
specs/020-dify-production-cutover/
├── spec.md
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/production-runtime.md
└── tasks.md
```

### Source

```text
deploy/
├── dify/workflows/                 # 四个无密钥 DSL
└── scripts/
    ├── dify-prod.sh                # 安装、启动、状态、停止
    └── dify-prod-bootstrap.py      # 初始化、导入、Token、冒烟

backend/src/main/resources/         # 现有 Dify 客户端与配置
deploy/README.md                    # 生产部署与回滚说明
```

## Phase 0: Research

见 [research.md](./research.md)。已确定使用官方 Dify 1.13.3 Compose、受限回环端口、低并发和直连回滚。

## Phase 1: Design

- 运行配置见 [contracts/production-runtime.md](./contracts/production-runtime.md)。
- 数据与状态见 [data-model.md](./data-model.md)。
- 验收步骤见 [quickstart.md](./quickstart.md)。

## Risks

- 3.5 GiB 物理内存和现有业务并发较高，Dify 首次构建/启动可能触发内存压力；增加 2 GiB Swap，并限制 Worker 并发。
- 插件市场或 DeepSeek API 不可达会阻止冒烟；保持后端直连配置不变，失败即回滚。
- Dify 首次建库和镜像拉取耗时较长；部署期间不重启正式后端，完成 Dify 健康检查后再切换环境变量。
