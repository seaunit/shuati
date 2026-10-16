# 拾题移动端壳（Capacitor）

一句话：**App 就是网页端本身**。这里不放业务代码，只负责把同一份前端实现打包成
Android / iOS 应用（宪法原则 VI「客户端与网页端同源（一份前端，多端打包）」）。

## 它怎么工作

`capacitor.config.ts` 里的 `SITE_URL` 指向网页端线上地址，壳用 WebView 直接加载它：

- 界面 = 网页端，逐页一致，不存在"两套 UI 要对齐"；
- 网页端发版 → App 下次打开即最新，不用重新发版审核；
- 同源，所以登录 Cookie 与 `/api/**` 天然可用，**后端一行不用改**。

代价：必须联网（不做离线包）。换域名只改 `capacitor.config.ts` 里的 `SITE_URL`。

## 构建（本机，不需要 Android Studio）

前置：Node 20+、JDK 17、Android SDK（`local.properties` 里的 `sdk.dir`）。

```powershell
cd mobile
npm install          # 首次
npm run apk          # 同步 web/插件 + 出调试包
```

产物：`mobile/android/app/build/outputs/apk/debug/app-debug.apk`

> 首次跑 `npm run apk` 时，Gradle wrapper 会下载一次分发包；之后都走本地缓存。

安装到真机：

```powershell
& "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" install -r android\app\build\outputs\apk\debug\app-debug.apk
```

**装之前请先卸载旧的调试包**：旧的原生客户端（`../android/`，已退役）与本壳应用同名但签名不同，
直接覆盖安装会失败。

## 以后上 iOS

同一份前端、同一套配置，只需要：

```powershell
npm run add:ios      # 生成 ios/ 平台工程，配置从 capacitor.config.ts 复用
npm run sync
```

然后在 Mac 上用 Xcode 打开 `mobile/ios/App` 出 IPA（本机是 Windows，打不了 IPA）。
**不需要**改 `frontend/`、`backend/`，也不需要新增任何后端接口。

## 改配置的常见位置

| 要改什么 | 改哪里 |
| --- | --- |
| 站点地址（换域名） | `capacitor.config.ts` 的 `SITE_URL` |
| 应用名 | `capacitor.config.ts` 的 `appName` + `android/app/src/main/res/values/strings.xml` |
| 包名 | `capacitor.config.ts` 的 `appId` + `android/app/build.gradle` 的 `applicationId` |
| 应用图标 | `android/app/src/main/res/mipmap-*/`（与网页端 favicon 同一片叶子） |
| 启动页 / 状态栏配色 | `capacitor.config.ts` + `android/app/src/main/res/values/styles.xml` |

## 自检

```powershell
npm run check        # 断言 appId / 站点地址 / 应用名 / 图标资源存在
```

## 已知风险（宪法要求显式记录）

- **远端加载模式的合规性**：部分应用商店会质疑"壳应用"。若被拒，切换方案是"打包本地前端资源 +
  API 绝对地址"，但那会引入跨域与会话问题，需要单独排期（spec 里已把该项留为可切换）。
- **必须联网**：无离线能力；无网时显示 WebView 自身的错误页（可理解，但不是品牌化页面）。
- **前端契约变更**：App 用的是线上版本，前端与后端的兼容窗口由"远端加载"保证（同一份版本）。
