# Phase 1 Quickstart: 安卓端构建 / 安装 / 验收

## 前置条件（本机已具备）

| 项 | 位置 / 版本 |
| --- | --- |
| JDK | 17.0.18 |
| Android SDK | `%LOCALAPPDATA%\Android\Sdk`（platform `android-36`，build-tools 36.0.0） |
| Gradle | 9.3.1（wrapper 已指向该版本，本机 `~/.gradle/wrapper/dists` 有缓存） |
| adb | `%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe` |
| 模拟器镜像 | **没有**（`system-images` 为空），因此只能真机验收 |

`ANDROID_HOME` 未设置，仓库内 `android/local.properties` 写明 `sdk.dir`（该文件已 gitignore）。

## 构建命令

```powershell
cd E:\CodexProject\shuati2\android

# 1) JVM 单元测试（不需要设备）
.\gradlew.bat :app:testDebugUnitTest

# 2) 出调试包（产物在 app\build\outputs\apk\debug\app-debug.apk）
.\gradlew.bat :app:assembleDebug
```

服务器地址：默认 `https://www.seaunit.site`（写在 `android/app/build.gradle.kts` 的 `BASE_URL`）。
本地联调时改成后端地址，例如真机同一局域网用 `http://<电脑内网IP>:8080`，
或模拟器用 `http://10.0.2.2:8080`（debug 包已允许明文 HTTP）。

## 安装到真机

```powershell
& "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" install -r .\app\build\outputs\apk\debug\app-debug.apk
```

手机需打开「USB 调试」并允许安装来自 USB 的应用。

## 验收清单（对应 spec 的四条主干）

| # | 步骤 | 期望 |
| --- | --- | --- |
| 1 | 打开 App（未登录） | 首页显示公共题库列表，可浏览；点刷题提示先登录 |
| 2 | 输入邮箱 + 密码 + 图形验证码登录 | 登录成功，首页顶部显示邮箱；验证码错误时提示与网页端同一句「验证码错误或已过期」 |
| 3 | 进任意题库 → 开始刷题 → 答一道选择题 | 显示对错；错选标红、正确项标绿；正确时得 10 分 |
| 4 | 在同一轮里答一道简答题 | 显示 AI 判分结果与点评；点数不足时原样显示服务端提示；扣点后「点数」页余额同步减少 |
| 5 | 点「AI 解析」 | 首次生成并扣点；再点一次直接出解析（来自解析库），不再扣点 |
| 6 | 连答 3 题后杀掉 App 重进同一题库 | 从第 4 题（未答的那道）继续，并提示已续上进度 |
| 7 | 打开错题本 | 列出刚答错的题，含单元名、题库名 |
| 8 | 打开统计 | 累计做题量、正确率与网页端同账号数值一致 |
| 9 | 打开点数与套餐 | 余额与套餐名与网页端一致；点商品用系统浏览器打开与网页端相同的支付页 |
| 10 | 注册 / 找回密码 | 都能在 App 内收到邮箱验证码完成；60 秒内不能重复发送 |

## 与网页端交叉核对（SC-004）

同一账号、同一道题，分别在安卓端与网页端作答，核对三件事：

1. 对错一致；
2. 得分一致（选择题 10/0，简答题 0~10）；
3. 解析来源一致（首次 = 本次 AI 解析，之后 = 来自解析库）。

## 已知限制

- 本机无模拟器镜像，`androidTest`（Compose UI 测试）本期不写、不跑；
- 图片题（`images` 字段）本期只提示"该题含图片，请在网页端查看"，不做图片加载；
- 支付只在网页端完成，客户端不内嵌支付表单。
