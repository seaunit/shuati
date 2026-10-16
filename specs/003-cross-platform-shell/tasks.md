# Tasks: 移动端壳（一份前端，多端打包）

**Input**: [spec.md](./spec.md)、[plan.md](./plan.md)

## Phase 1: 壳工程

- [x] T001 建 `mobile/` 壳工程：`package.json`（钉死 Capacitor 8.5.3 + `apk`/`check`/`add:ios` 脚本）
- [x] T002 写 `mobile/capacitor.config.ts`：`appId=site.seaunit.shuati`、`appName=拾题`、
  `server.url=https://www.seaunit.site`（站点地址一处可配）、启动页与状态栏配色沿用网页端令牌
- [x] T003 [P] 写 `mobile/README.md`：构建、出包、换域名、以后加 iOS 的步骤
- [x] T004 [P] 补 `mobile/.gitignore`（node_modules、便携 JDK、平台工程的构建产物、本机 SDK 路径）

## Phase 2: Android 平台

- [x] T005 `npm install` + `npx cap add android` 生成平台工程（compileSdk 36 / minSdk 26 / targetSdk 36）
- [x] T006 应用名与图标：`strings.xml` 的 app_name = 拾题；图标复用网页端 favicon 的叶子
  （自适应图标 + 矢量前景，与已退役原生客户端同一套几何）
- [x] T007 `npm run apk` 出调试包：4.19 MB（SC-003 ≤ 10 MB ✓）

## Phase 3: 验证与文档

- [x] T008 静态检查脚本 `npm run check`：8 项断言（appId / 站点地址 / 应用名 / minSdk / 图标是叶子 / 无密钥），全部通过
- [x] T009 更新宪法（v1.2.0）、`AGENTS.md`、根 `README.md`：端关系表、命令、退役说明
- [x] T010 标注 `specs/002-android-client` 为已退役，并写明被 003 取代的原因与时间
- [ ] T011 真机验收（用户执行）：与手机浏览器逐页对照 6 个主页面、登录态跨重启、
  网页端发版后 App 即最新

## Phase 4: 入口路径修正（用户反馈）

- [x] T012 App 入口从站根改成 `/app/`：
  站根 `/` 由后端直出 SEO 落地页（搜索引擎看的那份），`/app/` 才是应用壳；
  壳的 `SITE_URL` 改为 `https://www.seaunit.site/app/`，
  `npm run check` 增加一条断言（入口必须以 `/app/` 结尾），文档同步说明。

## Phase 5: 品牌启动页（用户反馈）

- [x] T013 启动页：改成「本地启动页 → 远端应用」模式
  - `www/index.html` = 品牌启动页（叶子与 favicon 同一条路径 + 拾题 + 「安静地练习，温柔地记住」），
    淡入、至少露出 700ms、顺便 `fetch` 预热连接，然后 `location.replace` 到线上 `/app/`
  - `capacitor.config.ts` 去掉 `server.url`（改由启动页跳转），并加
    `server.allowNavigation=[www.seaunit.site, seaunit.site]`（不加就会被系统浏览器接管）；
    `backgroundColor` 与启动页同色，避免闪白
  - `res/values/splash.xml` + `styles.xml`：冷启动窗口底色铺成同色纯色（不用模板 splash 图，避免跳色）
  - `npm run check` 扩到 11 项：本地启动页 / 品牌内容 / 跳转目标 / allowNavigation 全覆盖
  - 证据：包内 `assets/public/index.html` 含叶子 svg + 拾题 + 标语，跳转目标
    `https://www.seaunit.site/app/`；`assets/capacitor.config.json` 无 `server.url` 且
    `allowNavigation` 就位；APK 4.19 MB

## 依赖与顺序

- Phase 1 先做（壳配置决定后面怎么生成平台工程）
- Phase 2 依赖 Phase 1；Phase 3 的 T008–T010 可与 Phase 2 并行
- T011 需要用户上手（本机没有 Android 模拟器镜像）

## 明确不做（本期）

- 不打 IPA（需要 Mac + Xcode）；只保证"同一份前端 + 同一套配置能加 iOS 平台"
- 不做推送、原生分享、离线缓存等原生能力
- 不改 `frontend/` 与 `backend/` 的任何业务代码
