# 拾题安卓客户端

网页端是功能定义来源：本模块**只复用既有后端接口**，不新增接口、不新增字段、不新增业务规则
（见 `specs/002-android-client/contracts/api-reuse.md` 与 `.specify/memory/constitution.md` 原则 VI）。

## 构建

```powershell
cd E:\CodexProject\shuati2\android

.\gradlew.bat :app:testDebugUnitTest   # JVM 单测（不需要设备）
.\gradlew.bat :app:assembleDebug       # 产物：app\build\outputs\apk\debug\app-debug.apk
```

本机依赖：JDK 17、Android SDK（`%LOCALAPPDATA%\Android\Sdk`，platform `android-36`）、
Gradle 9.3.1（wrapper 已指向）。`android/local.properties` 里写 `sdk.dir`，该文件不入库。

## 服务端地址

默认 `https://www.seaunit.site`（`app/build.gradle.kts` 的 `BASE_URL`）。本地联调覆盖：

```powershell
.\gradlew.bat :app:assembleDebug -PshuatiApiBaseUrl=http://192.168.1.5:8080
```

debug 包允许明文 HTTP（`usesCleartextTraffic` 只在 debug 打开），release 包强制 HTTPS。

## 安装到真机

```powershell
& "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" install -r .\app\build\outputs\apk\debug\app-debug.apk
```

## 与后端的约定（改动前必读）

- 认证：登录下发 `shuati_token` Cookie，客户端用持久化 CookieJar 保存；后端不认 `Authorization` 头。
- 写请求：必须带 `X-Requested-With: ShuatiApp`，否则后端直接 403（`CustomHeaderFilter`）。
- 错误文案：一律显示服务端 `message` 原文（`{code,message,data}` 信封），客户端只在断网等无文案时兜底。
- 题目：列表接口不下发答案，选择题对错由 `POST /api/practice/submit-choice` 返回，不在本地判分。
- 点数：客户端不做任何本地扣点；AI 解析/判分走既有接口，复用服务端按题缓存。

## 目录

```text
app/src/main/java/site/seaunit/shuati/
├── AppContainer.kt        # 手写依赖容器（会话、ApiClient、五个仓储）
├── MainActivity.kt
├── core/                  # ApiClient / 会话 / 信封 / 三态
├── data/dto|repo/         # 与后端 JSON 逐字一致的模型 + 仓储
└── ui/                    # theme / auth / banks / practice / review / profile / components
```

## 常见问题

- **找不到 SDK**：检查 `local.properties` 的 `sdk.dir` 是否存在（Windows 用正斜杠或双反斜杠）。
- **403 请求缺少安全校验头**：写请求没带 `X-Requested-With`，检查 `ApiClient` 拦截器是否被改坏。
- **登录后立刻掉线**：Cookie 没持久化或域名不匹配，检查 `SessionStore` 里的 Cookie 编解码。
- **本机没有模拟器镜像**：`system-images` 为空，UI 测试跑不了，端到端验收走真机（见 `quickstart.md`）。
