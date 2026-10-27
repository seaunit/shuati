# Feature Specification: Dify 生产切换

**Feature Branch**: `020-dify-production-cutover`

**Created**: 2026-10-27

**Status**: Ready

**Input**: User description: "推送提交合并到正式分支，部署 Dify，发布正式投入使用。"

## User Scenarios & Testing *(mandatory)*

### User Story 1 - 正式环境使用 Dify (Priority: P1)

线上用户进行 AI 判题、解析、文本出题和截图出题时，业务请求由正式服务器上的 Dify 工作流处理。

**Why this priority**: 这是本次发布的核心业务价值，决定生产 AI 能力是否真正完成架构切换。

**Independent Test**: 在生产服务器分别调用四个 Dify 工作流，并验证网站返回原有业务结构、点数和调用日志正确。

**Acceptance Scenarios**:

1. **Given** 生产后端已启用 Dify，**When** 用户触发四类 AI 功能，**Then** 后端不直接请求模型供应商并成功返回业务结果。
2. **Given** Dify 工作流失败或不可用，**When** 用户触发收费 AI 功能，**Then** 请求返回明确错误且点数全额退回。
3. **Given** Dify 调用成功，**When** 查询 AI 调用记录，**Then** 包含 `provider=DIFY`、工作流键、外部运行 ID、Token 与耗时。

### User Story 2 - 安全上线与可回滚 (Priority: P2)

运维人员在不泄露密钥、不破坏现有站点和支付的前提下完成部署，并可快速切回直连 AI。

**Why this priority**: 生产切换影响付费功能，必须有安全边界和明确回滚路径。

**Independent Test**: 检查服务器端口、密钥权限、仓库扫描结果，并通过修改环境变量将 AI 引擎切回 `direct` 后确认服务恢复。

**Acceptance Scenarios**:

1. **Given** Dify 控制台已部署，**When** 从公网扫描端口，**Then** 控制台只监听回环地址，不直接暴露到公网。
2. **Given** 正式环境密钥存在，**When** 扫描 Git 仓库与构建产物，**Then** 不包含模型 Key、Dify Token 或管理员口令。
3. **Given** Dify 切换后出现不可恢复故障，**When** 运维切回 `direct` 并重启后端，**Then** 原有 AI 功能恢复。

### User Story 3 - 正式发布验收 (Priority: P3)

用户和运维人员可确认正式站点、登录、价格页、支付侧车和 AI 功能均可访问。

**Why this priority**: 部署成功不等于业务可用，必须完成公开入口与关键链路自检。

**Independent Test**: 从公网检查首页、登录页、价格页、健康检查和 Dify 工作流冒烟结果。

**Acceptance Scenarios**:

1. **Given** 生产部署完成，**When** 访问正式域名，**Then** 首页、登录页、价格页和下载页返回成功。
2. **Given** 生产服务启动完成，**When** 执行部署自检，**Then** 后端、支付侧车、Nginx、MySQL、Redis 与 Dify 均处于运行状态。
3. **Given** 正式账号登录，**When** 触发一项 Dify AI 功能，**Then** 页面成功返回结果且不出现 502 或格式错误。

### Edge Cases

- 服务器剩余内存不足时，Dify 或现有业务必须至少保留一个可用路径，不能同时被 OOM 击穿。
- Dify 首次启动未完成数据库迁移时，后端必须保持原有服务或切回直连，不能持续返回 502。
- 插件市场不可用时，四套工作流无法执行，发布必须停止并回滚到直连。
- Cloudflare 或域名链路异常时，源站健康检查仍应可独立完成。
- 任一工作流 Token 缺失或错误时，对应功能失败退点，其他不相关页面继续可用。

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: 正式环境 MUST 部署 Dify 1.13.3，并运行四套已版本化工作流。
- **FR-002**: 正式后端 MUST 使用 `SHUATI_AI_ENGINE=dify`，并通过 `http://127.0.0.1:8501/v1` 访问 Dify。
- **FR-003**: 四个 Service API Token MUST 分别写入 `/etc/shuati/env`，权限为 600，且不得进入 Git。
- **FR-004**: 模型供应商密钥 MUST 只存在于 Dify 密钥存储或生产密钥文件，不得出现在 DSL、前端和日志中。
- **FR-005**: Dify 控制台 MUST 仅绑定回环地址，并通过 SSH 隧道或受限运维网络访问。
- **FR-006**: 部署 MUST 保留 `DEEPSEEK_API_KEY`，以便将 `SHUATI_AI_ENGINE` 切回 `direct` 完成回滚。
- **FR-007**: Dify 原生服务 MUST 使用低并发 Worker、增加额外 Swap，并保留直连回滚路径，避免压垮现有 2 核 4G 服务器。
- **FR-008**: 部署 MUST 保持 `/etc/shuati/payments.env`、现有支付配置和邮件配置不被覆盖。
- **FR-009**: 上线前 MUST 通过后端全量测试、前端生产构建和密钥扫描。
- **FR-010**: 上线后 MUST 验证四类真实 Dify 工作流、网站健康、支付侧车健康和关键公开页面。
- **FR-011**: 发布 MUST 记录 Dify 启停、升级、备份和直连回滚命令。
- **FR-012**: 若 Dify 部署或冒烟失败，正式后端 MUST 在限定时间内恢复为可服务状态。

### Key Entities

- **Dify 工作流**：判题、解析、文本出题、截图出题四个独立应用及其版本化 DSL。
- **工作流凭证**：每个 Dify 应用对应的 Service API Token，只写入生产环境文件。
- **生产 AI 配置**：AI 引擎、Dify 地址、超时和回滚所需直连配置。
- **部署验收记录**：systemd 服务状态、健康检查、工作流冒烟和 AI 调用日志。

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 正式站点首页、登录页、价格页和下载页全部返回 HTTP 200。
- **SC-002**: 四套 Dify 工作流在正式环境各完成至少一次成功调用。
- **SC-003**: 公开端口扫描结果中不存在公网可达的 8501 控制台端口。
- **SC-004**: 后端、支付侧车和 Dify 核心服务连续运行 10 分钟无 OOM 或自动重启。
- **SC-005**: 从发现 Dify 严重故障到切回直连并恢复 AI 服务的操作步骤不超过 10 分钟。
- **SC-006**: Git 仓库和生产静态产物中真实 Dify Token、模型 Key、管理员口令数量为 0。

## Assumptions

- 正式环境继续使用现有香港服务器 `8.210.73.129` 和域名 `www.seaunit.site`。
- 现有 MySQL、Redis、邮件、支付和 Nginx 配置继续保留，不迁移历史业务数据。
- Dify 复用服务器上的 PostgreSQL/Redis，但使用独立数据库和 Redis DB 编号，不复用业务流程数据。
- Dify 控制台只用于运维，不向普通用户开放。
- 当前 `main` 为正式分支，`test` 分支中的模拟邮件和测试支付配置不得整体合并到 `main`。
