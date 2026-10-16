# Feature Specification: 移动端壳（一份前端，多端打包）

**Feature Branch**: `003-cross-platform-shell`

**Created**: 2026-10-16

**Status**: Draft

**Input**: 用户要求 —— 「安卓端的样式和布局做成和手机端用浏览器打开访问网页端的就行，
要做到一套代码可以多个平台打包，以后打包成 IOS 也这样做」，并明确「先修宪法和文档再开始做」。

**前置变更**：宪法 v1.1.0 → v1.2.0，原则 VI 更名为「客户端与网页端同源（一份前端，多端打包）」。

## User Scenarios & Testing *(mandatory)*

### User Story 1 - 手机 App 与手机浏览器看到的完全一样 (Priority: P1)

用户在手机上装「拾题」App，打开后看到的首页、题库卡片、进度条、底部导航，与他在手机浏览器里
访问 `seaunit.site` 看到的**完全一致** —— 因为就是同一份前端实现，不存在"两边各写一遍再对齐"。

**Why this priority**：这是用户提出这次改造的全部理由：原生那份界面对不上网页端，且每加一个功能
都要写两遍。

**Independent Test**：同一台手机，浏览器打开站点与打开 App，逐页对照 6 个主页面。

**Acceptance Scenarios**：

1. **Given** 已安装 App，**When** 打开，**Then** 直接进入站点首页（App 内不需要再登录一次网页）。
2. **Given** 同时打开浏览器与 App，**When** 对照 首页 / 题库 / 刷题 / 错题本 / 统计 / 我的，
   **Then** 版式、文案、交互逐页一致。
3. **Given** 在 App 里登录，**When** 杀进程后重开，**Then** 仍是登录态（会话不丢）。
4. **Given** App 内的账号，**When** 与浏览器对照，**Then** 练习进度、错题本、点数余额、题库归属一致。

---

### User Story 2 - 网页端发版，App 不用重新发版就生效 (Priority: P1)

运营在服务器上更新了网页端，用户下次打开 App 就是新版本，不需要等应用商店审核。

**Why this priority**：备考季改动频繁，发版周期决定迭代速度。

**Independent Test**：改一处网页端文案 → 服务器部署 → App 里刷新即看到新文案。

**Acceptance Scenarios**：

1. **Given** 网页端已部署新版本，**When** 用户在 App 里下拉刷新或重开，**Then** 看到新版本内容。

---

### User Story 3 - 以后打 iOS 只加壳配置 (Priority: P2)

需要上 iOS 时，用同一份前端资源 + 同一套壳配置添加 iOS 平台即可，不改前端、不改后端。

**Independent Test**：在本仓库执行「添加 iOS 平台」的配置动作，确认它只依赖同一份前端与同一份配置。

**Acceptance Scenarios**：

1. **Given** 现有壳工程，**When** 添加 iOS 平台，**Then** 不需要改动 `frontend/`、`backend/`，
   也不需要新增任何后端接口。

---

### Edge Cases

- 无网络：壳加载不到站点 → MUST 给出可理解的错误页（而不是白屏）。
- 弱网/首次启动：MUST 有加载指示，不出现"点了没反应"。
- 站点的证书/域名变更：属于壳配置，MUST 在文档里写清改哪里。
- 应用商店对"远端加载"的政策风险：MUST 在 plan 的「风险」里显式记录并跟踪。
- 同一台手机上旧的原生 App 与本壳的包名/签名冲突：MUST 在安装说明里写明先卸载旧包。

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: App MUST 使用同一份前端实现（当前方案：Capacitor 壳 + WebView 远端加载站点），
  MUST NOT 另写一套原生界面。
- **FR-002**: App MUST 复用既有 `/api/**` 与同一套 JWT（Cookie）认证；
  MUST NOT 新增同义接口、平行路由或「仅 App 生效」的分支。
- **FR-003**: 会话 MUST 跨 App 重启保持（不放一次性的内存态里）。
- **FR-004**: 应用名、图标 MUST 与网页端品牌一致（同一片叶子、同一套颜色令牌）。
- **FR-005**: 壳工程 MUST 能在本机离线构建（依赖版本钉死、不需要 Android Studio），
  构建与出包命令 MUST 写进 `AGENTS.md` / `README.md` / `mobile/README.md`。
- **FR-006**: iOS MUST 用同一份前端资源与同一套壳配置即可添加（本期不产出 IPA，只做到配置就绪）。
- **FR-007**: 产物 MUST NOT 含任何密钥；客户端 MUST NOT 本地实现业务判定（如选择题对错）。
- **FR-008**: 退出登录 MUST 清掉 App 内会话，与网页端一致。
- **FR-009**: 站点地址 MUST 是配置项（一处可改），便于以后换域名或改成本地打包资源。

### Key Entities

- **壳工程（mobile/）**：Capacitor 配置 + Android/iOS 平台工程 + 图标与启动页资源。
- **站点地址**：壳加载的远端入口，与网页端同一份线上版本。
- **会话 Cookie**：与网页端同一份 `shuati_token`，由 WebView 的 Cookie 存储持有。

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 6 个主页面与手机浏览器逐页对照无差异（截图对比留档）。
- **SC-002**: 登录后杀进程重进仍是登录态。
- **SC-003**: 调试包体积 ≤ 10 MB（不打包前端资源、只做壳）。
- **SC-004**: 从零到出包不超过 3 条命令。
- **SC-005**: 本次改造后端与前端业务代码 **零改动**（只有壳工程与文档是新增）。

## Assumptions

- 采用「远端加载站点」模式（壳直接加载 `https://www.seaunit.site`）：好处是与网页端**必然**一致、
  发版即时生效、跨域与会话问题都不存在；代价是必须联网（不做离线包）。
- Android 包名沿用 `site.seaunit.shuati`；安装前需卸载旧的调试包（原生那份签名不同）。
- iOS 出 IPA 需要 Mac + Xcode，本期只把配置准备到位。
- 推送、原生分享、离线缓存等原生能力不在本期范围（需要时按宪法 VI 通过壳插件接入，并在 plan 单列）。
