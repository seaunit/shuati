# 拾题移动端壳（Capacitor）

一句话：**App 就是网页端本身**。这里不放业务代码，只负责把同一份前端实现打包成
Android / iOS 应用（宪法原则 VI「客户端与网页端同源（一份前端，多端打包）」）。

## 它怎么工作

启动方式是「**本地启动页 → 远端应用**」：

1. 不带 `server.url`，Capacitor 先加载本地 `www/index.html` —— 也就是**品牌启动页**
   （叶子 + 拾题 + 「安静地练习，温柔地记住」），本地文件不依赖网络，冷启动秒开、没有白屏；
2. 启动页展示完品牌（至少 700ms，顺便预热连接）后跳转到线上应用 `https://www.seaunit.site/app/`。

> **入口是 `https://www.seaunit.site/app/`，不是站根。**
> 站根 `/` 由后端直出 SEO 落地页（给搜索引擎看的），`/app/` 才是应用壳；
> App 进入的是 `/app/`，打开就是题库首页。

> **跳转目标必须同时写进 `capacitor.config.ts` 的 `server.allowNavigation`**，
> 否则 Capacitor 会把它当成外部链接交给系统浏览器（用户就"跳出 App"了）。
> 冷启动瞬间的窗口底色由 `res/values/splash.xml` + `styles.xml` 铺成本地启动页同色，避免闪白。

- 界面 = 网页端，逐页一致，不存在"两套 UI 要对齐"；
- 网页端发版 → App 下次打开即最新，不用重新发版审核；
- 同源，所以登录 Cookie 与 `/api/**` 天然可用，**后端一行不用改**。

代价：必须联网（不做离线包）。换域名要改两处：`www/index.html` 的 `SITE_URL` 与
`capacitor.config.ts` 的 `allowNavigation`（`npm run check` 会同时校验这两个）。

## 构建（本机，不需要 Android Studio）

前置：Node 20+、JDK 17、Android SDK（`local.properties` 里的 `sdk.dir`）。

```powershell
cd mobile
npm install          # 首次
npm run apk          # 同步 web/插件 + 出调试包
```

产物：`mobile/android/app/build/outputs/apk/debug/app-debug.apk`

> 首次跑 `npm run apk` 时，Gradle wrapper 会下载一次分发包；之后都走本地缓存。

## 版本号与发布（宪法 v1.3.0 要求）

版本号**只有一个来源**：`mobile/version.properties`

```properties
versionName=1.0.0   # 语义化版本，每次出包按需递增
versionCode=1       # 每次出包必须 +1（Android 用它判断能不能覆盖安装）
```

`android/app/build.gradle` 从这里读取，所以**不要**在别处再写版本号。

出包 + 生成发布产物与版本清单 + 发 GitHub Release：

```powershell
npm run publish:apk      # 需要 gh CLI 已登录，或设置 GITHUB_TOKEN
```

产物（都在 `mobile/releases/`，**不进 Git**）：

| 文件 | 用途 |
| --- | --- |
| `shuati-android-{版本号}.apk` | 带版本号的产物，留档 |
| `shuati-android.apk` | 稳定别名的"最新包"，下载页与二维码指向它 |
| `latest.json` | 版本清单（版本号、体积、SHA256、发布时间、下载地址），下载页只读它 |

这三个文件会作为 Release `android-v{版本号}` 的附件上传；服务器上的部署脚本从
`https://github.com/seaunit/shuati/releases/latest/download/` 拉到 `/opt/shuati/apk/`，
**平时不需要手动 scp**。

推荐打 tag 让 CI 自动出包发 Release（本机不装 JDK 21 / Android SDK 也行）：

```powershell
git tag android-v1.0.0
git push origin android-v1.0.0    # 触发 .github/workflows/android-release.yml
```

没有 gh CLI、也没有 `GITHUB_TOKEN` 时，脚本会打印手动建 Release 的两步；不想用 Release 也可以
退回手工上传（Nginx 已把 `/apk/` 指向 `/opt/shuati/apk/`）：

```powershell
scp .\releases\shuati-android-1.0.0.apk .\releases\shuati-android.apk .\releases\latest.json <服务器>:/opt/shuati/apk/
$env:SHUATI_DEPLOY_HOST='root@<服务器IP>'; npm run publish:apk
```

服务器重新部署后无需改前端：网页端下载页 `https://www.seaunit.site/download` 会用清单里的新版本号，
二维码和下载按钮都指向 `/apk/shuati-android.apk`（下次出包只要覆盖这个文件）。

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
| 站点地址 / 入口路径 | `www/index.html` 的 `SITE_URL`（`npm run check` 断言它指向 `/app/`） |
| 启动页样式（logo / 文案 / 底色） | `www/index.html`（同一份也用于 iOS） |
| 启动瞬间的窗口底色 | `android/app/src/main/res/values/splash.xml` + `styles.xml` |
| 应用名 | `capacitor.config.ts` 的 `appName` + `android/app/src/main/res/values/strings.xml` |
| 包名 | `capacitor.config.ts` 的 `appId` + `android/app/build.gradle` 的 `applicationId` |
| 应用图标 | `android/app/src/main/res/mipmap-*/`（与网页端 favicon 同一片叶子） |
| 启动页 / 状态栏配色 | `capacitor.config.ts` + `android/app/src/main/res/values/styles.xml` |

## 自检

```powershell
npm run check        # 断言 appId / 站点地址 / 应用名 / 图标资源存在
```

> 只有**改动客户端本身**（图标、启动页、版本号）才需要重新出包并让用户重新下载；
> 平时的功能改动都在网页端，部署即生效。

## 已知风险（宪法要求显式记录）

- **远端加载模式的合规性**：部分应用商店会质疑"壳应用"。若被拒，切换方案是"打包本地前端资源 +
  API 绝对地址"，但那会引入跨域与会话问题，需要单独排期（spec 里已把该项留为可切换）。
- **必须联网**：无离线能力；无网时显示 WebView 自身的错误页（可理解，但不是品牌化页面）。
- **前端契约变更**：App 用的是线上版本，前端与后端的兼容窗口由"远端加载"保证（同一份版本）。
