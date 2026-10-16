# Feature Specification: 安卓包下载页与版本化发布

**Feature Branch**: `007-app-download-page`

**Created**: 2026-10-17

**Status**: Draft

**Input**: 用户要求 —— 「启动页面是不是有两个啊……只用一个就好了（叶子+文字）；
然后规划流程，在网页端嵌入 apk 的下载页面（可以点击下载，也可以扫描二维码下载）；
然后每次出包时加上版本号（这点写在宪法里）」。

**前置变更**：宪法 v1.2.0 → v1.3.0，「技术栈与部署约束」新增「客户端版本号与分发」。

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
- **FR-007**: 发布脚本 MUST 支持一条命令完成"出包 + 生成产物与清单"，并能把产物传到
  服务器 `/opt/shuati/apk/`；重新部署 MUST NOT 删除已上传的安装包。

## Success Criteria *(mandatory)*

- **SC-001**: 包里核对：APK 的 versionName/versionCode 与 `version.properties` 一致。
- **SC-002**: `latest.json` 的版本、体积、SHA256 与产物一致。
- **SC-003**: Playwright：下载页显示清单里的版本号、下载链接指向 `/apk/shuati-android.apk`、
  二维码可见；静态二维码文件可访问。
- **SC-004**: 启动只有一次品牌画面（真机验收；本机无模拟器镜像）。
