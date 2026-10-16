# Implementation Plan: 移动端壳（一份前端，多端打包）

**Branch**: `003-cross-platform-shell` | **Date**: 2026-10-16 | **Spec**: [spec.md](./spec.md)

## Summary

把"另写一套原生界面"改成"同一份前端产物打包成 App"：新增 `mobile/` 壳工程（Capacitor），
Android 平台先落地并出调试包；iOS 只需再加一个平台。壳用**远端加载**模式直接打开
`https://www.seaunit.site`，因此界面与网页端**必然**一致、发版即时生效、同源所以会话与接口天然可用。

## Technical Context

**Language/Version**: Node 20+（壳工程与构建脚本）；Android 侧由 Capacitor 生成的 Gradle 工程承载

**Primary Dependencies**: `@capacitor/core` / `@capacitor/cli` / `@capacitor/android`（版本在
`mobile/package.json` 里钉死）；本机 JDK 17 + Android SDK（`android-36`）已具备

**Storage**: 无（App 不落业务数据；会话由 WebView 的 Cookie 存储持有）

**Testing**: 壳本身没有业务逻辑，验证方式是**产物 + 真机对照**：
`npm run apk` 出包 → 安装 → 与手机浏览器逐页对照（SC-001/002）；
壳配置的静态断言（站点地址、包名、应用名、图标资源存在）由脚本检查

**Target Platform**: Android 8.0+（Capacitor 的 minSdk 由壳工程决定）；iOS 后续用同一份配置

**Project Type**: mobile shell（新增 `mobile/` 顶层目录，与 `frontend/`、`backend/`、`payments/` 并列）

**Constraints**:

- 不新增任何后端接口，不改前端业务代码（宪法 VI）
- 站点地址 MUST 一处可配
- 产物 MUST NOT 含密钥
- 构建 MUST 能在本机完成（不需要 Android Studio）

**Scale/Scope**: 壳工程 ≈ 1 个配置文件 + 1 个 package.json + Capacitor 生成的平台工程；
本次交付 Android 调试包 1 个、iOS 平台"配置就绪"

## Constitution Check

| 原则 | 本次结论 |
| --- | --- |
| I. 密钥与用户数据绝不进仓库 | PASS：壳不含任何密钥；站点地址是公开域名 |
| II. 数据库只经 Flyway 演进 | N/A：不涉及数据库 |
| III. 改功能必须带测试与验证证据 | PASS（按壳的特性调整）：壳无业务逻辑，证据 = 出包成功 + 真机与浏览器逐页对照 + 配置静态检查 |
| IV. 公开内容直出、私有内容不进索引 | N/A：不涉及收录；壳加载的正是网页端同一份线上版本 |
| V. 点数与 AI 成本可对账 | N/A：壳不接触点数与 AI 调用 |
| VI. 客户端与网页端同源（一份前端，多端打包） | PASS：本次改造正是这条原则的落地 —— 唯一界面实现 + 一份前端多端打包 |

**Gate 结果**：通过。宪法已按修订流程先改为 v1.2.0。

## Project Structure

### Documentation (this feature)

```text
specs/003-cross-platform-shell/
├── spec.md      # 需求与验收（本目录）
├── plan.md      # 本文件
└── tasks.md     # 任务清单
```

### Source Code (repository root)

```text
mobile/
├── package.json            # 依赖与脚本（apk / sync / add:ios 等），版本钉死
├── capacitor.config.ts     # appId、appName、server.url（站点地址一处可配）、启动页与状态栏
├── README.md               # 构建、换域名、出 iOS 的步骤
└── android/                # Capacitor 生成的 Android 工程（图标/应用名在 res 下）
    └── app/src/main/res/   # 应用名与图标（复用网页端 favicon 的叶子）
```

**Structure Decision**：壳单独放 `mobile/`，与 `frontend/`（唯一界面实现）分离；
`android/`（旧原生客户端）保留但标记退役，不再构建分发。

## 影响面

| 位置 | 变更 | 风险 |
| --- | --- | --- |
| `mobile/`（新增） | 壳工程 | 低：与后端、前端业务代码零耦合 |
| `frontend/` | 不改代码 | 无 |
| `backend/` | 不改代码 | 无 |
| `android/`（旧原生） | 标记退役，代码保留 | 无（不再构建） |
| 文档 | 宪法 v1.2.0、AGENTS.md、README、mobile/README | 无 |

## 数据变更

无（不涉及数据库）。

## 测试策略

- 出包：`cd mobile && npm install && npm run apk` MUST 成功，产物路径固定；
- 配置静态检查：脚本断言 `capacitor.config.ts` 里的 `appId` / `server.url`、Android 侧的应用名与图标资源存在；
- 真机验收（用户执行）：与手机浏览器逐页对照 6 个主页面；登录后杀进程重进仍是登录态；
  网页端改文案 → 部署 → App 刷新即见（SC-001/002 与 US2）。

## 风险与回滚

| 风险 | 说明 | 缓解 |
| --- | --- | --- |
| 应用商店对"远端加载站点"的政策 | 部分商店会质疑"壳应用" | 记录为已知风险；必要时改为"打包本地前端资源 + 绝对 API 地址"（本次已在 spec 里保留该项为可切换） |
| 必须联网 | 无离线能力 | 明确写进 spec 的 Assumptions；离线需求出现时按"本地资源 + 增量更新"单独排期 |
| 旧原生 App 与新壳同包名 | 签名不同，直接覆盖安装会失败 | 安装说明写明先卸载旧调试包 |
| 换域名 | 站点地址写死在壳里 | 站点地址集中在一处配置（FR-009） |
