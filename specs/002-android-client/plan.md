# Implementation Plan: 拾题安卓客户端

**Branch**: `002-android-client` | **Date**: 2026-10-16 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/002-android-client/spec.md`

## Summary

用原生 Kotlin + Jetpack Compose 写一个「拾题的手机客户端」，把网页端已经在用的 `/api/**`
原样复用过来：同一套 JWT 会话、同一个安全校验头、同一份错误文案、同一份判定口径。
后端一行不改（宪法 VI「安卓端与网页端同源」）。

本期交付 P1～P3 四个故事：登录/注册/找回密码、公共题库刷题闭环（含 AI 判分与 AI 解析）、
错题本与统计、点数与套餐（只读 + 跳浏览器购买）。

## Technical Context

**Language/Version**: Kotlin 2.1.20，JVM target 17（本机 JDK 17.0.18，AGP 8.12 要求 17+）

**Primary Dependencies**:

- Android Gradle Plugin 8.12.0 + Gradle 9.3.1（两者本机 `~/.gradle` 已有缓存，wrapper 直接钉这两个版本）
- Compose：`activity-compose 1.10.1`、`material3 1.5.0-alpha17`（传递到 compose 1.11.0-beta02）、
  `navigation-compose 2.9.4`、`lifecycle-viewmodel-compose 2.10.0`、`material-icons-core`
- 网络：OkHttp 4.12.0 + kotlinx-serialization-json 1.6.3（不引 Retrofit：本地缓存没有，
  现有接口用 OkHttp + 手写 DTO 已经够，少一层依赖少一次踩坑）
- 测试：JUnit 4.13.2 + OkHttp MockWebServer 4.12.0（JVM 单测，可离线跑）

**Storage**: 仅应用私有 `SharedPreferences` 存会话 Cookie（`allowBackup=false`）；
题目与解析不落盘（spec FR-013）。无数据库、无 Android Room。

**Testing**: `gradlew :app:testDebugUnitTest`（JVM）+ `gradlew :app:assembleDebug`（产物）。
本机**没有模拟器镜像**（`system-images` 为空），Compose instrumented 测试不进本期自动验证，
改由真机安装验收（见 quickstart.md）。

**Target Platform**: Android 8.0+（minSdk 26），compileSdk 36 / targetSdk 36
（本机 SDK 只有 `android-36`、`android-37.0`）

**Project Type**: mobile-app（新增 `android/` 顶层目录，与 `backend/`、`frontend/`、`payments/` 并列）

**Performance Goals**: 冷启动到可交互 ≤ 3s（4G）；列表分页/首屏 ≤ 1s；调试包 ≤ 20 MB

**Constraints**:

- 写请求必须带 `X-Requested-With: ShuatiApp`，否则后端按设计返回 403
- 会话是 `shuati_token` Cookie（HttpOnly），客户端用 CookieJar 持久化，不解析 JWT 内容
- 后端接口 0 变更（spec SC-006）

**Scale/Scope**: 约 12 个界面、1 个 API 客户端、1 个会话存储、约 3000 行 Kotlin

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| 原则 | 本次结论 |
| --- | --- |
| I. 密钥与用户数据绝不进仓库 | PASS：安卓端不持有任何密钥；`local.properties`（含 SDK 路径）进 .gitignore；仓库里不出现口令 |
| II. 数据库只经 Flyway 演进 | N/A：本次不动数据库，无迁移 |
| III. 改功能必须带测试与验证证据 | PASS：JVM 单测覆盖 API 客户端关键行为（信封解析、401、安全头、Cookie 持久化），构建产物 + 真机验收清单作为证据 |
| IV. 公开内容服务端直出、私有内容不进索引 | PASS：安卓端不缓存题目（FR-013），自建题库对未登录用户不可见（FR-008/FR-011） |
| V. 点数与 AI 成本可对账 | PASS：安卓端不做任何本地扣点，AI 解析/判分全部走既有接口，复用服务端缓存与扣点逻辑 |
| VI. 安卓端与网页端同源（网页端优先） | PASS：不新增接口、不新增字段、不新增业务规则；功能面严格等于网页端已有能力 |

**Gate 结果**：通过，无需 Complexity Tracking。

## Project Structure

### Documentation (this feature)

```text
specs/002-android-client/
├── plan.md              # 本文件
├── spec.md              # 需求规格（已通过质量校验）
├── research.md          # Phase 0：技术选型与理由
├── data-model.md        # Phase 1：客户端数据模型（DTO 与界面状态）
├── contracts/api-reuse.md  # Phase 1：复用既有 API 的契约清单
├── quickstart.md        # Phase 1：构建 / 安装 / 验收步骤
├── checklists/requirements.md
└── tasks.md             # Phase 2 产出（$speckit-tasks）
```

### Source Code (repository root)

```text
android/
├── settings.gradle.kts
├── build.gradle.kts                 # 插件版本统一在此声明
├── gradle.properties
├── gradle/libs.versions.toml        # 版本目录（钉死上面那套版本）
├── gradle/wrapper/gradle-wrapper.properties
├── gradlew / gradlew.bat
├── .gitignore                       # local.properties、build/、.gradle/
└── app/
    ├── build.gradle.kts             # 只有一个模块，够用
    ├── proguard-rules.pro
    └── src/
        ├── main/
        │   ├── AndroidManifest.xml
        │   ├── res/                 # 图标、主题色、字符串（与网页端同一套术语与主色）
        │   └── java/site/seaunit/shuati/
        │       ├── MainActivity.kt
        │       ├── core/            # ApiClient / ApiEnvelope / ApiError / SessionStore / CookieJar
        │       ├── data/dto/        # 与后端字段一一对应的 DTO
        │       ├── data/repo/       # AuthRepository / BankRepository / PracticeRepository / BillingRepository
        │       └── ui/
        │           ├── theme/       # 与网页端一致的白底 + ink + moss
        │           ├── auth/        # 登录（含图形验证码）、注册、找回密码
        │           ├── banks/       # 首页题库列表
        │           ├── practice/    # 刷题（选择/简答、AI 判分、AI 解析、续做）
        │           ├── wrongbook/   # 错题本
        │           ├── stats/       # 统计
        │           └── billing/     # 点数与套餐（只读 + 跳浏览器购买）
        └── test/java/site/seaunit/shuati/   # JVM 单测（ApiClient 行为、续做取题、选项判定）
```

**Structure Decision**：新增 `android/` 顶层目录，单模块（`:app`），
避免多模块带来的构建复杂度；先按「一个模块 + 按职责分包」组织，等真有并行开发需求再拆模块。

## Complexity Tracking

无违反项，本节留空。
