# AGENTS.md

本文件面向在本仓库工作的 AI 代理（Codex / Claude Code / 其他）。

**先读宪法**：[`.specify/memory/constitution.md`](.specify/memory/constitution.md) 是最高约束。
本文件与宪法冲突时，以宪法为准；想改变约束，先按宪法的修订流程改宪法，再改代码。

---

## 一、功能改造一律走 SDD（Spec-Driven Development）

本仓库已初始化 spec-kit：骨架在 `.specify/`，技能在 `.agents/skills/`。
**任何功能级改造 MUST 按下面顺序推进，不允许直接跳去写代码。**

| 步骤 | 命令 | 产出 |
| --- | --- | --- |
| 1 | `$speckit-specify` | `specs/<NNN-短名>/spec.md`：做什么、为谁做、验收标准 |
| 2 | `$speckit-clarify` | 需求有歧义时先问清（最多 5 个针对性问题），答案回写进 spec |
| 3 | `$speckit-plan` | `plan.md`：技术方案、影响面、数据变更、风险 |
| 4 | `$speckit-tasks` | `tasks.md`：可执行、有依赖顺序的任务清单 |
| 5 | `$speckit-implement` | 按 tasks 执行实现 |
| 6 | `$speckit-analyze` / `$speckit-converge` | 交付前一致性检查；发现漏项用 converge 追加任务 |

补充规则：

- 宪法只在**原则发生变化**时才动（`$speckit-constitution`），当前版本 v1.0.0。
- 规格产物放仓库根的 `specs/<NNN-短名>/`，与代码同一次提交，不要放到 `docs/` 下另起一套。
- 实现过程中如果发现 spec 与现状不符，**先回改 spec/plan**，再继续写代码，不要"实现一套、文档另一套"。
- 交付前用 `$speckit-analyze` 对齐 spec / plan / tasks / 代码四者；不一致就先补齐。

### 可以不走完整 SDD 的情况

- 纯文案、注释、格式化、依赖小版本升级；
- 线上事故的紧急修复（修完 MUST 补回归测试，并在提交信息里写明原因）；
- 用户明确说"只讨论方案 / 只改这一行 / 先别动代码"。

除上述情况，**先出 spec 再写代码**。改动范围越大，越不能省。

---

## 二、硬性约束（摘自宪法，违反即返工）

1. **密钥与用户数据**：真实密钥、口令、私钥、生产连接串 MUST NOT 进仓库；只允许放
   本地 `.env.local` 或生产 `/etc/shuati/env`、`/etc/shuati/payments.env`。
   提交前检查新增文件不含 `*_KEY`、`*_SECRET`、`PASSWORD`、`*.pem` 与真实用户数据。
2. **数据库**：schema 变更只走 `backend/src/main/resources/db/migration/V{n}__{name}.sql`；
   **已应用的迁移禁止改写**；生产库从空库起步，迁移是唯一建库来源。
3. **测试与证据**：后端改动补 JUnit（涉及权限覆盖"本人 / 他人 / 管理员"矩阵，涉及计费覆盖幂等与退点）；
   前端交互改动补 Playwright；发布前跑 `npm run build`。声称完成 MUST 给出可复现证据。
4. **收录边界**：只有 `owner_id IS NULL` 的公共题库可被搜索引擎收录；
   用户自建题库及其题目 MUST NOT 出现在公开页面或 sitemap 中。
5. **点数与 AI**：点动必写 `point_ledger`；扣费/退款在事务内用行锁、退款幂等且不得为负；
   AI 调用必记 `ai_call_log`，收费项必登 `ai_price_rule`；**AI 调用禁止持有数据库事务**。

---

## 三、常用命令

```powershell
# 后端测试（需要本机 MySQL 的 shuati_test 库与 Redis）
$env:SHUATI_DB_PASSWORD='<本机 MySQL 口令>'; mvn -f backend/pom.xml test; Remove-Item Env:\SHUATI_DB_PASSWORD

# 前端类型检查 + 生产构建
npm --prefix frontend run build

# 前端端到端测试（Playwright，会自己起 vite）
cd frontend; npx playwright test tests/mobile-responsive.spec.ts

# 本地起后端（profile=local，读 .env.local）
mvn -f backend/pom.xml spring-boot:run -Dspring-boot.run.profiles=local

# 本地起前端
npm --prefix frontend run dev      # http://127.0.0.1:5173

# 移动端壳（Capacitor：同一份前端产物打包成 App；JDK 17 + 本机 Android SDK，不需要 Android Studio）
cd mobile; npm install; npm run apk

# 已退役的原生客户端（v1.2.0 起被 mobile/ 取代，仅供查阅，不再构建分发）
# cd android; .\gradlew.bat :app:assembleDebug
```

```bash
# 生产部署 / 重新部署（服务器上执行；交互输入 60 秒无输入按回车处理）
curl -fsSL https://raw.githubusercontent.com/seaunit/shuati/main/deploy/scripts/provision-ubuntu.sh | sudo bash
```

部署、自检与运维的完整说明见 [`deploy/README.md`](deploy/README.md)；
本地开发与目录说明见 [`README.md`](README.md)。

## 三之二、端与端的关系（宪法原则 VI 的落地）

| 端 | 目录 | 定位 |
| --- | --- | --- |
| 后端 | `backend/` | 唯一的 API 与业务规则来源（`/api/**`），网页端与移动端共用 |
| 网页端 | `frontend/` | **唯一界面实现**：新功能先在网页端定义并可用 |
| 移动端壳 | `mobile/` | Capacitor 壳：把同一份前端产物打包成 Android / iOS App，只做打包与原生能力 |
| 支付侧车 | `payments/` | Waffo Pancake 下单与回调，两端都只是跳到它给出的支付页 |
| 旧原生客户端 | `android/` | **已退役**（宪法 v1.2.0 起由 `mobile/` 取代，保留代码仅供查阅，不再构建分发） |

移动端的三条硬约束：**不另写原生界面**、不新增接口或"仅 App 生效"的字段与分支、
不在客户端重复实现业务判定（例如选择题对错由服务端返回，不在客户端判分）。契约清单两端通用，见
[`specs/002-android-client/contracts/api-reuse.md`](specs/002-android-client/contracts/api-reuse.md)。

---

## 四、交付习惯

- 提交信息用中文，格式 `type(scope): 描述`（feat / fix / perf / style / docs / chore / security / data）；
  一次提交只做一件事。
- 面向用户的文案、注释、文档用简体中文；代码风格与周边保持一致。
- 结论必须有执行证据（命令输出、接口返回、页面实测）；没有工具支撑的数据
  （搜索量、流量、收录量等）标「待验证」，**不允许编造**。
- 上线闭环：改代码 → 跑测试 → 提交推送 → 服务器重新部署 → 线上自检。
- 已知风险（未备案站点的收录配额、跨境链路 TTFB、历史口令轮换等）要显式记录并跟踪。
