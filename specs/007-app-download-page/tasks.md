# Tasks: 安卓包下载页与版本化发布

- [x] T001 宪法 v1.3.0：「客户端版本号与分发」写进「技术栈与部署约束」
- [x] T002 启动画面去重：透明系统图标，只保留本地启动页那一个品牌画面
- [x] T003 版本号单一来源 `mobile/version.properties` + `build.gradle` 顶层读取
- [x] T004 `publish-apk.mjs`：带版本号产物 + 稳定别名 + `latest.json` + 打印/执行上传
- [x] T005 网页端下载页 `/download`（清单驱动版本 + 下载按钮 + 二维码 + 安装说明）
- [x] T006 静态二维码 `frontend/public/download-qr.svg`（指向 `/apk/shuati-android.apk`）
- [x] T007 Nginx `/apk/` 与部署脚本建目录（重新部署不删已上传的包）
- [x] T008 Playwright 下载页用例 + 前端构建回归
- [x] T009 出包核对：versionName=1.0.0 / versionCode=1；`latest.json` 与产物 SHA256 一致
- [ ] T010 真机验收：启动只有一个品牌画面；下载页点按钮/扫码都能装
- [x] T011 发布改走 GitHub Release：`publish-apk.mjs` 自动建 Release 并上传三个附件（无凭据时打印手动两步）
- [x] T012 新增 `.github/workflows/android-release.yml`：打 tag `android-v*` 出包并发 Release（tag 与版本号一致性校验）
- [x] T013 部署脚本从 `releases/latest/download/` 同步 APK 到 `/opt/shuati/apk/`（失败保留旧包；`SHUATI_SKIP_APK=1` / `SHUATI_APK_BASE_URL` 可覆盖）
- [ ] T014 打 tag `android-v1.0.0` 触发首次 CI 发布，核对 Release 附件与 `releases/latest/download/` 可下载
