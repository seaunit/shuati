# Research: Dify AI 工作流接入

## Decision: 复用现有本地 Dify 1.13.3 安装

**Rationale**: `D:\dify` 已包含官方源码、完整 Compose 服务和已下载镜像，Web 控制台已完成初始化。复用可避免重复占用数 GB 镜像和本地存储。

**Alternatives considered**:
- 新建第二套 Dify：隔离更强，但镜像、数据库和内存成本翻倍。
- 使用 Dify Cloud：不符合“先改造本地测试环境”和本地数据隔离要求。

## Decision: 使用 Dify Service API 的阻塞工作流模式

**Rationale**: Dify `/v1/workflows/run` 支持 `response_mode=blocking`，返回工作流运行 ID、状态、输出、错误、总 Token 和耗时，能直接映射现有同步 API。当前产品调用方已经按同步响应设计，无需改前端。

**Alternatives considered**:
- Streaming：适合聊天式输出，但判题和题目提取需要完整 JSON，反而增加拼接和失败处理。
- 直接读取 Dify 数据库：破坏平台边界，版本升级风险高。

## Decision: 四个独立工作流和四个独立 API Token

**Rationale**: 判题、解析、文本出题和截图出题具有不同输入、输出和模型策略。独立应用可单独禁用、升级、导出和故障定位，Token 权限也最小化。

**Alternatives considered**:
- 单一工作流按 `purpose` 分支：成本更低，但 DSL 复杂、失败互相影响、版本回退不可独立。
- 只使用一个通用模型节点：无法表达截图文件变量和 JSON 输出差异。

## Decision: 业务层依赖语义化 AI 工作流端口

**Rationale**: 业务服务应表达“判题”而不是“给某个模型发送 system/user prompt”。该边界让 Dify、直连模型或未来的其他平台可以替换，而练习、题库、计费和权限代码无需变化。

**Alternatives considered**:
- 在各业务服务直接调用 Dify HTTP：耦合最低成本但工作流细节扩散，无法测试和替换。
- 保留 `AiClient.chat` 作为抽象：仍暴露 system prompt、JSON mode 和模型协议，无法达到降低耦合的目标。

## Decision: Dify 工作流 DSL 版本化，模型凭证不版本化

**Rationale**: 工作流提示词、节点连接和输出结构属于产品逻辑，需要代码评审和版本回退；模型供应商 Key 属于环境机密，只能保存在 Dify 或后端密钥文件。

**Alternatives considered**:
- 只在 Dify 数据库维护：无法评审、备份和重建。
- DSL 中保存模型 Key：会产生严重泄露风险。

## Decision: 本地测试使用 HTTP Mock，另设真实 Dify 冒烟

**Rationale**: 自动化测试必须离线、可重复且不消耗真实 Token；真实冒烟确认本地 Dify 的运行、文件上传和输出契约没有偏差。

**Alternatives considered**:
- 自动化测试调用真实 Dify：不稳定、成本高、可能依赖公网模型。
- 只做 Mock：无法发现 Dify 版本或 DSL 配置问题。

## Decision: 保留生产直连实现但通过配置隔离

**Rationale**: 用户要求第一阶段只改造本地测试环境。语义端口允许本地使用 Dify、生产暂时使用直连实现，且不改变 API。生产切换和删除直连代码作为后续独立变更。

**Alternatives considered**:
- 本次直接切换生产：超出用户明确范围，且本地模型与工作流尚未验收。
- 删除直连实现：会导致生产无法启动或 AI 功能不可用。

## Decision: 网页抓取仍由后端负责

**Rationale**: robots.txt、SSRF、超时、内容长度和合规拒绝属于业务安全边界，必须保留在 Spring Boot。Dify 只接收允许抓取后的文本并将其转换为题目。

**Alternatives considered**:
- 把 URL 直接交给 Dify 抓取：会把合规与 SSRF 边界移出项目控制，违反现有安全要求。

## Decision: 扩展 `ai_call_log` 记录外部运行信息

**Rationale**: Dify 工作流运行 ID 是跨系统排障的主要关联键。现有 `model` 和 Token 字段无法区分直连调用与 Dify 调用，也无法定位具体工作流。

**Alternatives considered**:
- 只写日志文件：无法支持后台统计、筛选和持久审计。
- 把运行 ID 拼进 `error`：语义错误，查询困难。

## Decision: 截图走 Dify 文件上传

**Rationale**: Dify 1.13.3 的 `/v1/files/upload` 返回文件 ID，工作流请求的 `files` 数组支持 `local_file` 和 `upload_file_id`。相比在 inputs 中塞超长 Base64，文件接口更清晰且符合平台契约。

**Alternatives considered**:
- Base64 作为普通文本变量：可能触发变量长度限制且不利于多模态节点。
- 只传远程 URL：本地开发没有公网可达文件，且增加隐私暴露面。
