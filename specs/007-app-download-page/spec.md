# Feature Specification: 安卓包下载页与版本化发布

**Feature Branch**: `007-app-download-page`

**Created**: 2026-10-17

**Status**: Draft

**Input**: 用户要求 —— 「启动页面是不是有两个啊……只用一个就好了（叶子+文字）；
然后规划流程，在网页端嵌入 apk 的下载页面（可以点击下载，也可以扫描二维码下载）；
然后每次出包时加上版本号（这点写在宪法里）」。

**前置变更**：宪法 v1.2.0 → v1.3.0，「技术栈与部署约束」新增「客户端版本号与分发」；
后续 PATCH 到 v1.3.1：分发渠道由「发布脚本上传」改为**经 GitHub Release 分发**，
部署脚本从 Release 拉取（2026-10-17）。

## Requirements *(mandatory)*

- **FR-001**: 启动过程 MUST 只出现**一个**品牌启动画面（叶子 + 拾题 + 一句话）：
  系统层（Android 12+ 的 SplashScreen）只铺品牌底色、不放图标，
  MUST NOT 出现「大叶子 → 叶子+文字」两个画面。
- **FR-002**: 客户端版本号 MUST 单一来源 `mobile/version.properties`，
  `android/app/build.gradle` 从它读取；`versionCode` 每次出包递增。
- **FR-003**: 出包 MUST 产出带版本号的 APK（`shuati-android-{版本号}.apk`）+ 稳定别名
  （`shuati-android.apk`）+ 版本清单（`latest.json`）；产物 MUST NOT 进 Git。
- **FR-004**: 网页端 MUST 有下载页（`/download`，公开、不需登录）：显示版本号（来自清单）、
  体积、发布时间、SHA256，并提供**可点击的下载按钮**与**二维码**，两者指向同一地址。
- **FR-005**: 下载页版本号 MUST 只从 `latest.json` 读，MUST NOT 前端写死。
- **FR-006**: 静态产物路径（`/apk/`）与下载页路由（`/download`）MUST 分开，
  避免带斜杠访问时把页面当成目录。
- **FR-007**: 产物 MUST NOT 进 Git 仓库，MUST 经 **GitHub Release** 分发；发布脚本 MUST 支持
  一条命令完成"出包 + 生成产物与清单 + 发 Release"（有 `gh` CLI 或 `GITHUB_TOKEN` 时自动发；
  都没有时 MUST 打印手动两步，不得静默跳过）。
- **FR-008**: 打 tag `android-v{版本号}` MUST 触发 CI 出包并发 Release；
  tag 与 `mobile/version.properties` 不一致时 MUST 直接失败，不允许发错版本。
- **FR-009**: 部署脚本 MUST 从 GitHub Release 同步安装包与版本清单到 `/opt/shuati/apk/`，
  且 MUST 以清单为准：先拉 `latest.json`，再按清单里的版本号拉**带版本号的唯一 URL**
  （稳定别名 `shuati-android.apk` 的 URL 每版相同，会被 CDN 缓存串版本），
  然后覆盖别名，并 MUST 用清单里的 SHA256 校验下载结果、不一致时显式告警。
  同步失败 MUST 保留服务器上已有安装包且 MUST NOT 中断部署（提供 `SHUATI_SKIP_APK=1`、
  `SHUATI_APK_BASE_URL`、`SHUATI_APK_TAG_BASE_URL` 逃生阀）。
- **FR-010**: 应用内导航 MUST 提供进入下载页的入口（桌面端在侧边栏，手机端收在「更多」面板），
  入口 MUST 指向公开页 `/download`，MUST NOT 另开 `/app/download` 复制同一份内容
  （同一内容两个 URL 会让搜索引擎分不清正主）；游客也 MUST 能看到该入口。
- **FR-011**: 下载页二维码 MUST 是品牌化的：二维码本体为静态 SVG（页面运行时不引二维码库），
  中心嵌品牌叶子图标，下方配产品名与标语；中心图标会遮挡模块，纠错等级 MUST 为 H；
  二维码内容 MUST 是完整可扫的绝对地址（MUST NOT 用相对路径），
  且 MUST 留静区；生成 MUST 可复现（`npm run qr:download`，换域名时用
  `SHUATI_SITE_BASE` 重新生成），MUST NOT 靠手工贴图。
- **FR-012**: 二维码 MUST 指向**下载页**（`/download`），MUST NOT 直链 APK：
  直链在微信等内置浏览器里容易被拦，且用户落地看不到版本与安装说明。
- **FR-013**: 二维码 MUST 能保存到本地：页面展示位图版本（手机长按即可存相册，
  SVG 存不下来），并提供「保存二维码」下载入口；因此发布产物 MUST 同时包含
  `download-qr.png`（1024×1024）与 `download-qr.svg`。

## Success Criteria *(mandatory)*

- **SC-001**: 包里核对：APK 的 versionName/versionCode 与 `version.properties` 一致。
- **SC-002**: `latest.json` 的版本、体积、SHA256 与产物一致。
- **SC-003**: Playwright：下载页显示清单里的版本号、下载链接指向 `/apk/shuati-android.apk`、
  二维码可见；静态二维码文件可访问。
- **SC-004**: 启动只有一次品牌画面（真机验收；本机无模拟器镜像）。
- **SC-005**: 打 tag 后，Release 页面能看到三个附件，且
  `https://github.com/seaunit/shuati/releases/latest/download/shuati-android.apk`
  能下载到与 `latest.json` 中 SHA256 一致的包。
- **SC-006**: Playwright：登录态下桌面端侧边栏、手机端「更多」面板都能看到「下载安卓 App」，
  指向 `/download` 并真的进得去。
- **SC-007**: 用二维码解码器实测：品牌化二维码能解出下载地址（原始尺寸与页面实际显示
  尺寸各测一次，本地已验证）。
- **SC-008**: Playwright 用解码器解发布出去的 `download-qr.png`，解出的内容 MUST 等于
  `https://www.seaunit.site/download`（中心叶子挡模块后仍可解）。
