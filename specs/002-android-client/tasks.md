# Tasks: 拾题安卓客户端

**Input**: Design documents from `/specs/002-android-client/`

**Prerequisites**: [plan.md](./plan.md)、[spec.md](./spec.md)、[research.md](./research.md)、
[data-model.md](./data-model.md)、[contracts/api-reuse.md](./contracts/api-reuse.md)、[quickstart.md](./quickstart.md)

**Tests**: 后端行为不可回归 + 宪法 III 要求证据，因此本功能包含 JVM 单元测试任务（不含 instrumented 测试，
本机无模拟器镜像，理由见 plan.md）。

**Organization**: 按用户故事分阶段，每个故事可独立实现、独立验证。

## Format: `[ID] [P?] [Story] Description`

- **[P]**: 可与同阶段其它任务并行（不同文件、无依赖）
- **[Story]**: 对应 spec.md 的用户故事

## Path Conventions

单个模块 Android 工程，模块根为 `android/app/`，Kotlin 源码包为
`android/app/src/main/java/site/seaunit/shuati/`，测试在 `android/app/src/test/java/site/seaunit/shuati/`。

---

## Phase 1: Setup

**Purpose**: 建出能用本机现有 SDK + Gradle 缓存出包的工程骨架

- [x] T001 创建 `android/` 目录骨架：`settings.gradle.kts`、`build.gradle.kts`、`gradle.properties`、`gradle/libs.versions.toml`（钉死 AGP 8.12.0 / Kotlin 2.1.20 / Gradle 9.3.1 / compose 1.11.0-beta02 系列 / okhttp 4.12.0 / kotlinx-serialization-json 1.6.3）
- [x] T002 [P] 生成 Gradle wrapper 并指向 9.3.1：`android/gradle/wrapper/gradle-wrapper.properties`、`android/gradlew`、`android/gradlew.bat`
- [x] T003 [P] 写 `android/.gitignore`（`local.properties`、`.gradle/`、`build/`、`*.iml`）与 `android/local.properties`（`sdk.dir` 指向本机 SDK，不入库）
- [x] T004 [P] 建 `android/app/build.gradle.kts`：namespace/applicationId `site.seaunit.shuati`、minSdk 26、compileSdk 36、targetSdk 36、BuildConfig 字段 `BASE_URL`（默认 `https://www.seaunit.site`）、debug 允许明文 HTTP
- [x] T005 [P] 建 `android/app/src/main/AndroidManifest.xml`（单 Activity、`allowBackup="false"`、网络权限、debug 的 `usesCleartextTraffic`）
- [x] T006 [P] 建主题与色彩资源 `android/app/src/main/res/values/{themes,colors,strings}.xml`：白底 + ink 文字 + moss 主色，术语与网页端一致
- [x] T007 [P] 建应用图标与占位资源 `android/app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml` + 矢量前景（复用网页端叶片标志的纯色版本）

**Checkpoint**: `gradlew.bat :app:assembleDebug` 能出一个空壳 APK

---

## Phase 2: Foundational（阻塞所有用户故事）

**Purpose**: API 客户端、会话、错误口径、导航壳 —— 四条故事都依赖

**⚠️ 必须全部完成后再开始任何用户故事**

- [x] T008 实现 `core/ApiEnvelope.kt`（含 `ApiException`）：解析 `{code,message,data}`，非 200/非 2xx 一律抛带 `message` 原文与 HTTP 状态码的异常（对应 [contracts/api-reuse.md](./contracts/api-reuse.md) §0）
- [x] T009 实现 `core/SessionStore.kt`（含 `CookieStorage` / `PersistentCookieJar`）：把 `shuati_token` Cookie 存进应用私有 SharedPreferences，重启后自动带上；不打印 Cookie 值
- [x] T010 实现 `core/ApiClient.kt`：OkHttp + kotlinx.serialization；写请求统一加 `X-Requested-With: ShuatiApp`；401 触发 `onUnauthorized` 回调；提供 `get/post/patch/postVoid` 泛型方法
- [x] T011 [P] 建 `data/dto/Dtos.kt`：Bank/BankUnit/Question/Option/ChoiceResult/EssayResult/Progress/WrongBookItem/StatsMe/Entitlements/PointPack/Catalog/OrderCreated —— 字段名与后端 JSON 逐字一致（见 [data-model.md](./data-model.md)）
- [x] T012 [P] 建 `core/UiState.kt`：`Loading / Ready / Failed(message)`，`Failed.message` 只允许放服务端原文或断网兜底文案
- [x] T013 实现 `data/repo/AuthRepository.kt`：登录、注册、注册发码、找回密码、找回发码、退出、`/api/me`（契约 §1）
- [x] T014 实现 `MainActivity.kt` + `ui/AppNav.kt` 导航壳：底部四个入口（题库 / 错题本 / 统计 / 我的）+ 登录等全屏页；未登录时错题本、统计、我的引导登录
- [x] T015 建 `ui/theme/Theme.kt`：与网页端一致的色板（ink/paper/mist/moss/rose/oat），控件用 Material3 默认形状

> 实现偏差（已回写文档）：没有引入 ViewModel —— 应用是单 Activity + Compose 导航，
> 旋转与尺寸变化已在 manifest 用 `configChanges` 兜住，改用 `ui/components/Common.kt` 里的
> `Loader` + `rememberLoader` 承担异步三态，少一层工厂样板。

**Checkpoint**: 无凭据启动 App 能进首页壳；任意接口 401 能回到登录页

---

## Phase 3: User Story 1 - 登录后在手机上刷题（Priority: P1）🎯 MVP

**Goal**: 登录 → 选题库/单元 → 答题（选择即时判定、简答 AI 判分）→ AI 解析 → 退出重进能续做

**Independent Test**: 用测试账号登录，答 1 道选择题 + 1 道简答题，点一次 AI 解析，杀掉应用重进确认停在第 3 题

- [x] T016 [P] [US1] 实现 `data/repo/PracticeRepository.kt`：建/结束会话、选择题提交、简答提交、AI 解析、自评（契约 §3）；题目与进度走 `data/repo/BankRepository.kt`（契约 §2）
- [x] T017 [US1] 实现登录页 `ui/auth/AuthScreens.kt#LoginScreen`：邮箱 + 密码 + 图形验证码（`GET /captcha/image` 转 Bitmap，点图刷新），文案与网页端一致
- [x] T018 [US1] 登录态与验证码刷新用 Compose 状态 + `rememberCoroutineScope` 承担（见上方偏差说明），未单独建 AuthViewModel
- [x] T019 [P] [US1] 实现 `ui/banks/BankScreens.kt#BankListScreen`：公共题库列表 + 进度（`answered_count/question_count`）+ 未登录可浏览、点进去引导登录
- [x] T020 [US1] 实现 `ui/banks/BankScreens.kt#UnitListScreen`：进入题库看单元与各自进度，提供「整库刷题」入口
- [x] T021 [US1] 实现 `ui/practice/PracticeScreen.kt#PracticeState`：加载题目 → 建会话 → 拉进度定位续做题目（`answeredIds` 里第一道未答的）→ 提交 → 下一题 → 结束会话
- [x] T022 [US1] 选择题交互：单选/多选选择器、提交后正确项标绿、错选标红、显示得分（`ui/practice/ResumeLocator.kt#AnswerRules`）
- [x] T023 [US1] 简答交互：多行输入、提交后展示 `verdict/score/feedback(命中点/漏点)/referenceAnswer`；`degraded=true` 时提供自评按钮
- [x] T024 [US1] AI 解析交互：非空 `explanation` 直接展示并标「来自解析库」；点「AI 解析 / 重新生成解析」调接口；失败用服务端原文提示
- [x] T025 [US1] 点数不足（402）与限流（429）走同一条错误透出路径，服务端文案原样显示

**Checkpoint**: US1 独立可测，且与网页端对同一题目的判定一致

---

## Phase 4: User Story 2 - 新用户注册与找回密码（Priority: P1）

**Goal**: App 内完成注册与找回密码，不必去网页端

**Independent Test**: 新邮箱注册成功并直接进入已登录状态；再用该邮箱走一次找回密码并用新密码登录

- [x] T026 [P] [US2] 实现注册页 `ui/auth/AuthScreens.kt#RegisterScreen`：邮箱 + 邮箱验证码（60 秒倒计时）+ 密码；点发送调 `POST /api/auth/register/email-code`
- [x] T027 [P] [US2] 实现 `ui/auth/AuthScreens.kt#ForgotPasswordScreen`：邮箱 + 邮箱验证码 + 新密码，对应 `/api/auth/password-reset/email-code` 与 `/api/auth/password-reset`
- [x] T028 [US2] 登录页加「注册 / 找回密码」入口并接通导航，注册/找回页均不要求图形验证码（与网页端一致）
- [x] T029 [US2] 倒计时与冷却提示取服务端返回的 `cooldownSeconds`，冷却期间发送按钮禁用

**Checkpoint**: US1 与 US2 都独立可用

---

## Phase 5: User Story 3 - 错题本与学习统计（Priority: P2）

**Goal**: 复盘错题、看累计做题量与正确率

**Independent Test**: 制造一道错题 → 错题本可见 → 统计数值与网页端一致

- [x] T030 [P] [US3] 实现 `data/repo/ReviewRepository.kt`：`GET /api/wrong-book`、`GET /api/stats/me`
- [x] T031 [US3] 实现 `ui/review/ReviewScreens.kt#WrongBookScreen`：题干、单元名、题库名、判定（与网页端同字段；接口不下发正确答案，故不做"点开看答案"）
- [x] T032 [US3] 实现 `ui/review/ReviewScreens.kt#StatsScreen`：累计做题量、正确/部分正确/错误、正确率，以及按单元统计

**Checkpoint**: 复盘链路可用，数值与网页端一致

---

## Phase 6: User Story 4 - 点数与套餐（只读 + 跳浏览器购买）（Priority: P3）

**Goal**: 看清余额与套餐名，想充值时跳到网页端同一支付入口

**Independent Test**: 点数页数值与网页端一致；点商品能用系统浏览器打开支付页

- [x] T033 [P] [US4] 实现 `data/repo/BillingRepository.kt`：`GET /api/points`、`GET /api/plans`、`POST /api/orders`（取 `checkoutUrl`）
- [x] T034 [US4] 实现 `ui/profile/ProfileScreen.kt`：可用点数、套餐名（买过点数包显示包名）、最近流水、退出登录（点数与"我的"合并在一个 tab）
- [x] T035 [US4] 点数包列表（名称/点数/价格 → 点击下单后用 `Intent.ACTION_VIEW` 打开支付页）；MUST NOT 内嵌支付表单

**Checkpoint**: 四条故事全部独立可用

---

## Phase 7: Polish & 跨故事收尾

- [x] T036 [P] 写 JVM 单测 `app/src/test/java/site/seaunit/shuati/core/ApiClientTest.kt`：MockWebServer 验证信封解析、错误 message 透出、401 回调、写请求带 `X-Requested-With`、Cookie 跨请求保持（5 条）
- [x] T037 [P] 写纯逻辑单测 `app/src/test/java/site/seaunit/shuati/practice/ResumeLocatorTest.kt`：续做定位与选项着色（6 条）
- [x] T038 [P] 写 `android/README.md`：构建、安装、改 BASE_URL、常见故障（SDK 路径、明文 HTTP）
- [x] T039 跑通 `gradlew.bat :app:testDebugUnitTest`（11 条全绿）与 `:app:assembleDebug`（app-debug.apk 12.78 MB，SC-005 ≤ 20 MB）
- [ ] T040 按 [quickstart.md](./quickstart.md) 在真机走一遍 10 条验收清单，记录结果（含 SC-004 与网页端交叉核对）
  — 待用户安装 `app-debug.apk` 后一次性验收
- [x] T041 更新根 `README.md` 与 `AGENTS.md` 的目录说明，加入 `android/`，并说明安卓端「同源」约束

---

## Dependencies & Execution Order

### Phase Dependencies

- **Phase 1 Setup**：无依赖，可立即开始
- **Phase 2 Foundational**：依赖 Phase 1，完成后才允许开始任何用户故事
- **Phase 3 US1**：依赖 Phase 2；US1 含登录页，是其它故事实际使用 App 的前提
- **Phase 4 US2 / Phase 5 US3 / Phase 6 US4**：依赖 Phase 2（US2/US3/US4 之间互不依赖，可并行）
- **Phase 7 Polish**：依赖需要交付的故事完成

### User Story Dependencies

- **US1 (P1)**：Phase 2 之后即可，无其它故事依赖
- **US2 (P1)**：Phase 2 之后即可；与 US1 共用 AuthRepository（T013），不依赖 US1 的界面
- **US3 (P2)**：Phase 2 之后即可；需要已登录（US1 的登录页），数据来自服务端
- **US4 (P3)**：Phase 2 之后即可；同上

### Parallel Opportunities

- Phase 1：T002–T007 全部可并行
- Phase 2：T011、T012 可并行；T013/T014/T015 之间文件不重叠，可并行
- Phase 3：T016 与 T019 可并行（仓储层与题库页互不依赖）
- Phase 4/5/6：三个故事彼此独立，可由不同人并行推进

---

## Implementation Strategy

### MVP First

1. Phase 1 + Phase 2
2. Phase 3（US1）→ 打真机包，先在手机上把「登录 + 刷题 + 续做」走通
3. **停下验证 US1**，与网页端交叉核对同一道题的判定与解析来源

### Incremental Delivery

1. US1 可用 → 交付（考生已经能在手机上刷题）
2. US2 可用 → 新用户不必再去网页端注册
3. US3 可用 → 复盘链路补齐
4. US4 可用 → 充值引导到位
5. 每个故事完成后都重新出包 + 真机点一遍，避免"最后一起验收"

---

## Notes

- [P] = 不同文件、无依赖，可并行
- 每个故事完成即是一次可交付增量
- 提交信息用中文，格式 `type(scope): 描述`；安卓端改动一律带 `android` 作用域
- 后端 MUST 保持 0 改动；任何"顺手给安卓加个接口"的想法都要先回到 spec

---

## 验收反馈修正（用户第一轮真机反馈）

- [x] T042 登录页验证码太小 → 按服务端出图尺寸（150×48）与网页端 `h-44 w-110` 的展示口径，
  改成固定 `140×48dp` 的可点击区块（`ContentScale.Fit` + 边框 + 点图刷新），不再是随行宽被压缩的小图
- [x] T043 App 图标要与网页端一致 → 直接搬 `frontend/public/favicon.svg` 里那条描边叶子路径：
  32×32 等比放大 2.25 倍塞进 108dp 画布安全区，底板用 favicon 的 `#EEF1EE`，描边 `#7D9B76`
- [x] T044 点数流水不要把页面撑长 → 改成固定高度（`heightIn(max = 300dp)`）的滚动区，
  与网页端「点数明细」的 `max-h-80 overflow-y-auto` 同一口径，并在标题里标出条数

## 验收反馈修正（用户第二轮真机反馈）

- [x] T045 刷题报"HTTP 200" → 根因是 `question.images`（全库 NULL）与 `tags`（多数 NULL）
  返回 `null`，DTO 里是非空 `List`，库在显式 null 上直接抛异常，表现成 HTTP 200 却报错。
  修：`JsonSupport` 打开 `coerceInputValues`；解析失败时把原始响应写进 Logcat 并把文案
  改成"返回数据无法解析（HTTP 200）"；补 `DtoDecodeTest` 3 条（含不加该开关会抛异常的反证）
- [x] T046 [US5] 新增 `data/repo/ManageRepository.kt`：题库 / 单元 / 题目的增删改查 + 上下架 + 删除题库验证码
- [x] T047 [US5] 新增 `ui/manage/ManageBankListScreen.kt`：我维护的题库列表 + 新建空题库
- [x] T048 [US5] 新增 `ui/manage/ManageBankDetailScreen.kt`：题库信息、单元增删改、题目列表（状态筛选 + 题干检索）、
  新增/编辑题目弹窗（题型、单元、选项与正确答案、难度、标签、解析）、上下架、删除，删除题库走邮箱验证码
- [x] T049 底部导航加第 5 个入口「题库管理」并接上路由
- [x] T050 契约与规格回写：`contracts/api-reuse.md` 把 `/api/manage/**` 从"不做"移到正式契约（§6）；
  `spec.md` 补 US5 与验收场景

> 仍未实现（明确留到下一轮）：网页端的「导入题目」文档解析流程（`/api/import/**`）。
> 安卓端要做的话需要文件选择 + 上传 + 解析任务轮询 + 结果勾选，是独立一块，本轮不合并。

## 验收反馈修正（用户第三轮真机反馈）

- [x] T051 底部 tab 与整站版式改成微信那一套：
  根因是 Material3 `NavigationBar` 取 `surfaceContainer` 角色，而 `lightColorScheme` 只覆盖了少数角色，
  落到基线色就是淡紫 —— 底栏发紫、还带默认胶囊选中块。
  改法：theme 补齐 `surfaceContainer*` 角色；底栏自绘（白底 + 1dp 发丝线 + 无胶囊，选中只换绿/灰）；
  新增 `WeCell / WeTag / WeButton / WeTextAction / Hairline / SectionHeader` 一套组件，
  页面从"圆角白卡片 + 灰边框"改成"浅灰底 + 通栏白块 + 发丝分隔线"，圆角统一收到 3–4dp；
  题库、单元、刷题、错题本、统计、我的、题库管理、登录注册全部按这套重排；
  状态栏颜色跟页面浅灰对齐，验证码块在白色表单里换成浅灰底以便识别可点。

## 验收反馈修正（用户第四轮真机反馈）

- [x] T052 刷题界面对齐网页端的三处差异：
  ① 选择题看不出选了哪个 → 选项右侧加勾选圈（选中实心绿 + 白勾、未选灰色空心圈），
  选中行加浅绿底、字母变绿（网页端是"绿边 + 浅绿底 + 字母染色"，同一语义）；
  ② 缺「上一题 / 下一题」→ 操作条钉在屏幕底部：`上一题`（第 1 题禁用）、
  未提交时是`提交答案`，提交后出现`AI 解析`与`下一题 / 完成练习`，与网页端按钮组合一致；
  ③ 一轮刷完再进来停在最后一题 → `ResumeLocator` 抄错了网页端的边界：
  网页端只在"未答题下标 > 0"时跳转，全部答完时回到第 1 题，安卓端原来返回 `lastIndex`。
  现已对齐，单测同步改成"全部答完从头开始"。

- [x] T053 解析提前泄露（用户反馈"没选答案就把正确答案显示出来了"）：
  题库里有 322 道题带解析库内容，安卓端进题就把 `explanation` 渲染出来了 ——
  解析里常直接写出正确选项，等于提前泄题。网页端的条件是
  `v-if="result && (explanation || explanationError)"`，即**必须提交后**才显示。
  修法：把这条规则下沉成纯函数 `AnswerRules.canShowExplanation(submitted, explanation)`
  并补 5 条断言的单测（提交前 / 空串 / null / 提交后全覆盖），界面按它渲染。

- [x] T054 作答状态的可读性（用户反馈"选错的地方没有标红、多选应该是小方框"）：
  ① 标红太弱 —— 原来只有字母和小标签染色 + 6% 底色，阳光下几乎看不出来；
  改成网页端那套"字母徽章填充成实心红底白字 + 10% 红底 + 错选项文字也变红"，
  正确项同理填充实心绿 + 10% 绿底；
  ② 多选与单选的选中标记不分 —— 改成通用规范：单选圆圈、多选方框，
  选中都是实心绿 + 白勾，未选是灰色空心（按密度换算描边，避免细得看不见）。

- [x] T055 App 图标的叶子没居中（用户反馈"看起来怪怪的"）：
  几何错误 —— 网页端 SVG 里叶子外面还有一层 `translate(4,4)`，搬路径时漏掉了，
  只加了画布居中的 `translate(18)`，于是叶子整体偏到左上（中心 43.9 vs 画布中心 54，偏约 9%）。
  正确变换 `(p + 4) × 2.25 + 18 = p × 2.25 + 27`，平移量改为 27。
  校验：叶子包围盒 [31.5, 74.2]，中心 (52.9, 52.9)，只剩网页端原图本身的 1% 不对称；
  最远点半径 31.8 < 安全区 33，不会被启动器遮罩裁掉；叶子边长占可见区 59%，与网页端 19/32 同比例。

- [x] T056 「我的」页退出登录不好找（用户反馈"点击账号也可以触发退出登录选项，不用一直滑下去"）：
  流水那块是固定 320dp 的滚动区，把退出登录顶到了下面。
  现在账号那一栏整行可点（带箭头提示），点它直接弹出退出登录确认框；
  底部那一行也收口到同一个确认框，避免一处点一下直接退、另一处要确认。

- [x] T057 错题本筛选 + 不记录重复错题（用户要求"网页端和安卓端的错题本可以筛选，而且不记录重复的错题"）：
  - **不记录重复**：核对后确认服务端本来就按题目去重（按时间倒序 `putIfAbsent`，只留最近一次，
    最近一次答对就移出错题本）。新增后端 `WrongBookDedupTest` 3 条把行为钉住：
    同题错两次错题本只有一条（练习记录仍是两条）、答对后自动移出、看不到别人的错题。
  - **筛选**：网页端 `WrongBookView.vue` 加"题库 + 判定"两组 chip 与计数行，本地过滤不打接口；
    安卓端同维度同语义（`WrongBookFilter` 纯函数 + 5 条 JVM 单测），题库 chip 用 FlowRow 自动换行。
  - 两端都把"按最近一次作答结果统计、同一道题只保留最新一次、答对后自动移出"写进了页面说明。
  - 证据：前端 `npm run build` 通过、Playwright 39 条全绿（新增错题本筛选 1 条）；
    后端 106 条全绿（新增去重 3 条）；安卓 20 条单测全绿 + `assembleDebug` 出包。
