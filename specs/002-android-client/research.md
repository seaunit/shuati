# Phase 0 Research: 拾题安卓客户端

本机实测环境（2026-10-16）：

- JDK 17.0.18；`ANDROID_HOME` **未设置**，SDK 实际在 `%LOCALAPPDATA%\Android\Sdk`
- SDK 平台：`android-36`、`android-37.0`；build-tools 35.0.0 / 36.0.0；`system-images` 为空（没有模拟器镜像）
- Gradle 9.3.1 已在 `~/.gradle/wrapper/dists` 缓存；`~/.gradle/caches` 里已有 AGP 8.12.0、
  Kotlin 2.1.20（含 compose 编译器插件）、compose 1.9.3/1.10.6/1.11.0-beta02、
  material3 1.5.0-alpha17、activity-compose、navigation-compose、lifecycle-viewmodel-compose、
  okhttp 4.12.0、kotlinx-serialization-json 1.6.3、kotlinx-coroutines-android
- 网络：`repo.maven.apache.org`、`dl.google.com/dl/android/maven2`、`services.gradle.org` 均可直连
  （HEAD 200，0.4~1.5s），所以缺的测试依赖可以现拉

## 决策 1：UI 技术栈 —— 原生 Kotlin + Jetpack Compose

**Decision**: Kotlin 2.1.20 + Jetpack Compose（Material3），单 Activity + Compose 导航。

**Rationale**: 本机构建链（AGP 8.12 / Kotlin 2.1.20 / compose 编译器插件）与依赖缓存全部就位，
不需要额外装 Android Studio 就能出包；Compose 写列表/表单类界面代码量最小，
也最容易和网页端的排版语义对齐（一屏一列表、底部操作条）。

**Alternatives considered**:

- *Flutter / React Native*：需要装一整套新工具链（Flutter SDK / Node 侧原生构建），
  首次构建要下载几百 MB，收益只是"跨平台"，而本项目明确只要安卓。
- *WebView 包壳*：最省事，但与宪法 VI 的意图相反（那是把网页塞进壳里，不是"同一套契约的客户端"），
  也无法做原生的列表体验与手势。

## 决策 2：不引入 Retrofit，用 OkHttp + kotlinx.serialization

**Decision**: OkHttp 4.12.0 直连 + kotlinx-serialization-json 手工解析，`ApiClient` 只有 200 行左右。

**Rationale**: 现有接口全部是 `{code, message, data}` 信封 + JSON 体，返回体里大量是
「字段不固定的 Map」（后端直接返回 `Map<String, Object>`）。Retrofit 的强类型接口在这里
反而要写一堆 `Map<String, Any?>` 的声明，还要额外引 `converter`；而本机缓存里没有 Retrofit。

**Alternatives considered**: *Retrofit + kotlinx-serialization converter*（多两个依赖、多一层注解处理，
对 12 个界面不划算）；*Ktor Client*（同样是新依赖，无收益）。

## 决策 3：会话复用 Cookie，不引 JWT 解析

**Decision**: 用 OkHttp 自带 `CookieJar` 落盘 `shuati_token`（应用私有 SharedPreferences），
请求时自动带回；不解析 JWT 内容，不自己拼 `Authorization` 头。

**Rationale**: 后端 `JwtAuthFilter.readToken()` **只认 Cookie**，没有 Authorization 头分支。
所以安卓端必须走 Cookie，才能做到"后端 0 改动"（宪法 VI）。

**Alternatives considered**: *给后端加 Authorization 支持*（违反"不新增仅 App 生效的分支"，
且会让 CSRF 防护模型出现第二套）；*把 token 读出来自己存*（HttpOnly + 无法可靠解析，纯属绕远）。

## 决策 4：写请求必须带 `X-Requested-With: ShuatiApp`

**Decision**: 在 OkHttp 拦截器里给所有非 GET 请求统一加上该头。

**Rationale**: `CustomHeaderFilter` 对非 GET/HEAD/OPTIONS 请求强制校验，缺失直接 403
「请求缺少安全校验头」。网页端在 `api()` 里也是同一处逻辑。

**Alternatives considered**: 不适用（这是服务端既有约束，客户端只能遵守）。

## 决策 5：测试策略 —— JVM 单测为主，instrumented 测试不进本期

**Decision**: 用 JUnit + MockWebServer 覆盖 `ApiClient` 的关键行为：信封解析、错误 message 透出、
401 回调、安全头注入、Cookie 跨请求保持；再加两个纯逻辑单测（续做定位、选项判定）。
不写 `androidTest`（Compose UI 测试）。

**Rationale**: 本机**没有模拟器镜像**，`androidTest` 需要真机/模拟器才能执行，
写了也跑不出证据（宪法 III 要求"可复现证据"，跑不了等于没测）。
真机验收按 `quickstart.md` 的四条主干手工核对。

**Alternatives considered**: *Robolectric*（本机缓存没有，且 Compose 在 Robolectric 下问题多，
收益不如直接在真机点一遍）。

## 决策 6：构建可离线、版本全部钉死

**Decision**: `gradle/libs.versions.toml` 里钉死版本；Gradle wrapper 指向 9.3.1；
`ANDROID_HOME` 未设置时通过仓库内 `.gitignore` 的 `local.properties` 指定 `sdk.dir`。

**Rationale**: 本机缓存的组合（AGP 8.12.0 + Kotlin 2.1.20 + compose 1.11.0-beta02 + material3 1.5.0-alpha17）
是已验证可解析的；版本漂移会引入重新下载甚至解析失败。

**Alternatives considered**: *用 Compose BOM*（BOM 本身不在缓存里，且会把版本随机漂到最新，
与本机缓存组合不一致）。

## 决策 7：支付不在 App 内完成

**Decision**: 点数页只读；点套餐用系统浏览器打开网页端同一个支付入口。

**Rationale**: 支付链路是 Node 侧车 + 网页回跳，安卓端内嵌等于把支付表单塞进 WebView，
既要处理回跳又是新的攻击面；跳系统浏览器复用现成实现，且符合"网页端为准"。

**Alternatives considered**: *内嵌 WebView + 深链回跳*（本期不做，收益低、风险高）；
*接原生 SDK*（服务商没提供）。
