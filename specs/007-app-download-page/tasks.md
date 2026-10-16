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
- [x] T014 首次发布 `android-v1.0.0`：Release 三个附件齐全，手动触发 workflow 走通 CI 出包
      （run 38935256900 绿，1m41s），`releases/latest/download/shuati-android.apk` 实下 SHA256
      与 `latest.json` 一致（1EEA8819…）
- [x] T015 同步逻辑改为「清单为准 + 优先带版本号 URL + SHA256 校验」：实测发现
      `releases/latest/download/shuati-android.apk` 会缓存上一版（同一 URL 每版复用），
      改成拉唯一 URL 后不再串版本；四个场景（正常 / 缺带版本包 / 别名是旧包 / 清单拉不到）
      已在本地假 curl 测试台里跑通
- [x] T016 应用内导航加「下载安卓 App」入口（桌面端侧边栏、手机端「更多」），
      指向公开页 `/download`；补 Playwright 覆盖两个断点
- [x] T017 二维码品牌化：生成脚本 `npm run qr:download`（H 级纠错 + 静区 + 中心叶子徽标），
      QR 卡下方补「拾题 + 安静地练习，温柔地记住」；用解码器实测 640px 与页面实际尺寸
      176px 都能解出 `https://www.seaunit.site/apk/shuati-android.apk`
- [x] T018 二维码改指下载页而非 APK 直链（`https://www.seaunit.site/download`），
      并出 1024×1024 位图供保存：页面展示 PNG（手机长按可存相册）+「保存二维码」按钮，
      文案同步改成"扫码打开本页"；Playwright 用 jsQR 解发布出去的 PNG 断言内容
- [x] T019 修线上"下载到的 APK 只有几 KB"：HTTPS 那份 `shuati-prod.conf` 漏了 `/apk/`
      规则（部署脚本又会跳过有证书的配置），导致 `/apk/*` 掉进 SPA 回退返回首页 HTML。
      两份模板补齐规则；部署脚本改为每次重建配置（`nginx -t` 不过自动回滚），
      并在结尾自检 `/apk/` 的 Content-Type
- [x] T020 二维码卡片化：产品名与标语画进 PNG（原来是挂在 HTML 上，保存下来只有光秃秃
      一个码）；位图随之变成竖版，Playwright 断言宽高与保存文件名
- [x] T021 App 里隐藏「下载安卓 App」入口：`frontend/src/appShell.ts` 用壳的本地 origin
      （Referer）与新版本壳的 UA 标记识别，识出后写 sessionStorage；`capacitor.config.ts`
      追加 `appendUserAgent: "ShuatiApp"`；Playwright 用 `https://localhost` 当壳真跑一遍
