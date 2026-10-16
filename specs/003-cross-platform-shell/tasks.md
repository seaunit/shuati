# Tasks: 移动端壳（一份前端，多端打包）

**Input**: [spec.md](./spec.md)、[plan.md](./plan.md)

## Phase 1: 壳工程

- [ ] T001 建 `mobile/` 壳工程：`package.json`（钉死 Capacitor 依赖 + `apk`/`sync`/`add:ios` 脚本）
- [ ] T002 写 `mobile/capacitor.config.ts`：`appId=site.seaunit.shuati`、`appName=拾题`、
  `server.url=https://www.seaunit.site`（站点地址一处可配）、启动页与状态栏配色沿用网页端令牌
- [ ] T003 [P] 写 `mobile/README.md`：构建、出包、换域名、以后加 iOS 的步骤
- [ ] T004 [P] 补 `mobile/.gitignore`（node_modules、平台工程的构建产物、本机 SDK 路径）

## Phase 2: Android 平台

- [ ] T005 `npm install` + `npx cap add android` 生成平台工程
- [ ] T006 应用名与图标：`strings.xml` 的 app_name = 拾题；图标复用网页端 favicon 的叶子
  （自适应图标 + 矢量前景，与已退役原生客户端同一套几何）
- [ ] T007 `npm run apk` 出调试包，确认产物路径与体积（SC-003 ≤ 10 MB）

## Phase 3: 验证与文档

- [ ] T008 静态检查脚本：断言 appId / server.url / 应用名 / 图标资源存在（可作为 `npm run check`）
- [ ] T009 更新宪法（v1.2.0）、`AGENTS.md`、根 `README.md`：端关系表、命令、退役说明
- [ ] T010 标注 `specs/002-android-client` 为已退役，并写明被 003 取代的原因与时间
- [ ] T011 真机验收（用户执行）：与手机浏览器逐页对照 6 个主页面、登录态跨重启、
  网页端发版后 App 即最新

## 依赖与顺序

- Phase 1 先做（壳配置决定后面怎么生成平台工程）
- Phase 2 依赖 Phase 1；Phase 3 的 T008–T010 可与 Phase 2 并行
- T011 需要用户上手（本机没有 Android 模拟器镜像）

## 明确不做（本期）

- 不打 IPA（需要 Mac + Xcode）；只保证"同一份前端 + 同一套配置能加 iOS 平台"
- 不做推送、原生分享、离线缓存等原生能力
- 不改 `frontend/` 与 `backend/` 的任何业务代码
