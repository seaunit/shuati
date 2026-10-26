# Research: Dify 生产切换

## Decision: 同机部署 Dify 1.13.3

**Rationale**: 现有业务、数据库和用户都在香港服务器，Dify 服务 API 走回环网络延迟最低，也不需要再购买一台服务器。

**Alternatives considered**:

- 单独购买 4G 以上服务器：隔离更好，但增加成本且当前用户要求继续使用现有香港服务器。
- 使用 Dify Cloud：不符合现有数据与运维边界。

## Decision: 原生 systemd + 额外 2 GiB Swap + 低并发

**Rationale**: 服务器上的其他应用都使用原生方式运行，复用现有 Python、Node、PostgreSQL、Redis 和 systemd 可以避免 Docker 常驻开销；Swap 和单 Worker 可以降低 3.5 GiB 物理内存被 OOM 击穿的概率。

**Alternatives considered**:

- Docker Compose：部署集中，但用户明确要求不使用容器，且会增加额外常驻开销。

## Decision: 控制台仅绑定 `127.0.0.1:8501`

**Rationale**: 普通用户不需要 Dify 控制台，仅后端和运维需要访问；回环端口避免管理面暴露公网。

**Alternatives considered**:

- 通过公网 8501 暴露：攻击面过大。
- 绑定域名并接入 Nginx：增加认证与路由风险，本次无必要。

## Decision: 自动化初始化、导入、凭证和 Token 创建

**Rationale**: 四个工作流和四个 Token 每次手工操作容易遗漏与漂移；脚本可以重复验证并输出可直接写入生产配置的结果。

**Alternatives considered**:

- 完全手工操作：首次可用，但不可重复、不可审计。

## Decision: 直连配置保留为回滚路径

**Rationale**: 现有 `DEEPSEEK_API_KEY` 已存在，切换 `SHUATI_AI_ENGINE=direct` 即可回退，不需要重新部署 jar。

**Alternatives considered**:

- 删除直连代码：切换更彻底，但生产故障时没有快速回退。

## Decision: 正式分支独立，不合并测试分支整体

**Rationale**: `test` 分支包含模拟邮件和测试支付配置，整体合并会污染正式环境。只将功能代码通过已审阅提交合入 `main`。
