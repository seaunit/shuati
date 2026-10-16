# 拾题（Shuati）Constitution

## Core Principles

### I. 用户数据与密钥绝不进仓库（NON-NEGOTIABLE）

仓库是公开的，因此：

- 真实密钥、口令、私钥、生产连接串 MUST NOT 出现在仓库任何文件中；只允许存在于
  本地 `.env.local` 或生产 `/etc/shuati/env`、`/etc/shuati/payments.env`（权限 600、属主 `shuati`）。
- 提交前 MUST 自检新增文件不含 `*_KEY`、`*_SECRET`、`PASSWORD`、`*.pem` 及真实用户数据；
  `db/_supabase_raw/` 保持 gitignore，历史清理过的真实口令 MUST NOT 被重新引入。
- 用户密码 MUST 仅以哈希存储（bcrypt）；验证码 MUST 仅存哈希且按用途隔离
  （注册 / 重置密码 / 删除题库互不复用）；日志 MUST NOT 输出明文口令、验证码、AI Key 或支付私钥。
- 涉及他人数据的查询 MUST 显式带 owner 条件；公开页面 MUST NOT 暴露私有题库、其题目或任何用户数据。

**理由**：这些数据一旦外泄无法撤回，且仓库与站点都对外可见，事后补救成本远高于事先约束。

### II. 数据库只经 Flyway 迁移演进（NON-NEGOTIABLE）

- schema 变更 MUST 通过 `backend/src/main/resources/db/migration/V{n}__{snake_case}.sql` 完成，
  编号连续；**已应用过的迁移 MUST NOT 被改写**（生产已执行，改写会导致校验失败无法启动）。
- 生产库从空库起步，迁移是唯一的建库来源；本机 `db/mysql/*.sql` 仅用于重建开发库。
- 破坏性变更 MUST 拆为「新增 → 回填/双写 → 切换 → 清理」多步，每步可单独上线。
- 数据回填类迁移 MUST 可重入（例如按内容哈希定位、只填空值），重复执行不产生副作用。

**理由**：迁移失败会直接阻断部署，而生产数据无法回滚，只能靠前向兼容的步骤保证安全。

### III. 改功能必须带测试与验证证据

- 后端行为变更 MUST 有 JUnit 覆盖：涉及权限 MUST 覆盖「本人 / 他人 / 管理员」权限矩阵，
  涉及计费 MUST 覆盖幂等、退点与边界。
- 前端交互变更 MUST 有 Playwright 用例；发布前 MUST 通过 `npm run build`（含类型检查）。
- 测试 MUST 与功能在同一次提交中更新，「只改实现不更新测试」不算完成。
- 声称完成前 MUST 给出可复现证据（测试输出、接口返回、页面截图）；不得以「应该没问题」结案。

**理由**：项目多处是权限、计费、删除等不可逆操作，回归只有自动化测试能低成本保证。

### IV. 公开内容服务端直出，私有内容永不进索引

- 面向搜索引擎的页面 MUST 由后端直出完整 HTML（蜘蛛不执行 JS）；
  `canonical`、`sitemap.xml`、`robots.txt` 三者 MUST 保持一致。
- 只有 `owner_id IS NULL` 的公共题库可被收录；用户自建题库及其题目 MUST NOT 出现在公开页面或 sitemap 中。
- 站点验证文件 MUST 长期保留在站点根目录；根目录下不存在的 `.html` MUST 返回 404，
  MUST NOT 被 SPA 回退成首页（否则验证与抓取都会被误导）。
- 统计代码 MUST 同时覆盖后端直出页面与 SPA 外壳；新增任何第三方统计 MUST 同步更新隐私政策。

**理由**：自然搜索流量落在直出页面上，收录范围同时也是一条数据边界，两者都必须显式约束。

### V. 点数与 AI 成本必须可对账

- 任何点数变动 MUST 写 `point_ledger`（含变动后余额），账本是客诉与对账的唯一依据。
- 扣费与退款 MUST 在事务内用行锁保证原子；退款 MUST 幂等，且 MUST NOT 使余额为负。
- 每次 AI 调用 MUST 记录到 `ai_call_log`（用途、模型、token、耗时、成败）；
  新增计费动作 MUST 在 `ai_price_rule` 中给出点数，不得出现未登记的收费。
- AI 调用 MUST NOT 持有数据库事务；耗时写库 MUST 走队列（Redis Streams），
  投递失败 MUST 退化为同步写，不允许丢失结果。

**理由**：点数即收入，AI 调用即成本，只有账本与调用日志齐全才能同时守住用户信任与毛利。

### VI. 安卓端与网页端同源（网页端优先）

- 网页端是唯一的功能定义来源：新功能 MUST 先在网页端（后端直出页 + Vue SPA）定义并可用，
  安卓端随后对齐同一套字段、状态与交互语义；MUST NOT 为安卓端单独设计一套业务规则。
- 后端只有一套 API：安卓端 MUST 复用既有 `/api/**` 与同一套认证方式（JWT），
  MUST NOT 为安卓端新增同义接口、平行路由或「仅 App 生效」的返回分支。
- 确需新增接口或字段时 MUST 同时满足两条：网页端也确实需要，且已在 spec / plan 中写明理由与影响面；
  只服务安卓端的接口与字段 MUST NOT 合入。
- 视觉与组件同样以网页端为准：颜色、间距、圆角、图标取 `frontend/src` 的既有设计令牌，
  安卓端 MUST NOT 私建组件体系或另起一套视觉风格，避免两端口径漂移。
- 安卓端特有能力（推送、后台任务、原生分享、离线缓存等）MUST 在 plan 中单独列出，
  并写明它对后端契约与数据结构的影响。
- 涉及数据的改动 MUST 验证「同一账号在 Web 与 App 上看到同一份数据」：
  练习进度、错题本、点数余额、题库归属。

**理由**：两套实现最容易在接口与交互上分叉，一旦分叉就要长期维护两套规则；
把网页端定为唯一来源，能用最小成本保证体验与数据一致。

## 技术栈与部署约束

- 技术栈固定：Spring Boot 3.5 + Java 17 + Maven + Flyway；Vue 3 + Vite + Pinia + Tailwind 4；
  MySQL 8.4；Redis 6+；在线支付为 Node 侧车（`@waffo/pancake-ts`）。
  更换主框架或数据库 MUST 先修订本宪法。
- 客户端范围：Web（Vue 3 SPA + 后端直出的 SEO 页）是第一实现；安卓 App 是同一后端的客户端，
  共用 `/api/**` 与同一账号体系。新增客户端 MUST NOT 改变既有 API 契约；
  契约变更 MUST 走前向兼容的多步发布（新增 → 双端适配 → 清理旧字段）。
- 部署形态固定：可执行 jar + systemd + Nginx 反向代理（前后端同域，`/api`、`/captcha` 走反代），
  Cloudflare 在前，真实客户端 IP 取 `CF-Connecting-IP`。
- 生产配置只经 `/etc/shuati/env`；新增配置项 MUST 同时更新 `deploy/env.example` 与 `deploy/README.md`。
- 限流双层（Nginx 边缘 + Redis 应用层）：新增对外接口 MUST 评估并设置阈值。
- 部署脚本 MUST 幂等可重跑；交互输入 MUST 有超时默认值；Nginx 配置切换 MUST 先备份，
  `nginx -t` 失败 MUST 自动回滚。
- 服务器规格有限（2 核 4G），资源敏感的重任务 MUST 保持全局串行，禁止叠加并发压垮实例。

## 协作与交付规范

- 提交信息使用中文，格式 `type(scope): 描述`（feat / fix / perf / style / docs / chore / security / data），
  一次提交只做一件事。
- 面向用户的文案、代码注释与文档 MUST 使用简体中文，与既有代码风格保持一致。
- 对外结论 MUST 来自实际执行结果（命令输出、接口返回、页面实测）；无工具支撑的数据
  （搜索量、流量、收录量等）MUST 标注「待验证」，MUST NOT 编造。
- 功能上线 MUST 走完整闭环：改代码 → 跑测试 → 提交推送 → 服务器重新部署 → 线上自检；
  部署与自检命令 MUST 记录在 `deploy/README.md`。
- 已知风险 MUST 显式记录并跟踪（例如未备案站点的收录配额、跨境链路 TTFB、历史口令轮换）。

## Governance

- 本宪法优先于其他工程实践文档；与本文冲突的临时做法 MUST 先修订本文再执行。
- 修订流程：提出变更 → 说明动机与影响面 → 更新本文并递增版本 → 提交信息注明
  `docs: amend constitution to vX.Y.Z`。
- 版本策略（语义化）：MAJOR = 删除或重定义既有原则；MINOR = 新增原则或实质性扩充；
  PATCH = 措辞澄清、错别字与非语义调整。
- 合规检查：每次功能提交前 MUST 自检是否违反任一原则；发现违反 MUST 先修正，
  或先按流程修订宪法，MUST NOT 默许例外长期存在。
- 运行时指导文件：`README.md`（本地开发与结构）、`deploy/README.md`（部署与运维）、
  `docs/`（限流、验证码、支付等专题说明）。
- 任何临时破例 MUST 在提交信息中写明原因、影响范围与清理计划。

**Version**: 1.1.0 | **Ratified**: 2026-10-15 | **Last Amended**: 2026-10-16
