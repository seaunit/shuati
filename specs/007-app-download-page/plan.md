# Implementation Plan: 安卓包下载页与版本化发布

**Spec**: [spec.md](./spec.md)

## 技术方案

| 事项 | 做法 |
| --- | --- |
| 启动画面去重 | `styles.xml` 的 `AppTheme.NoActionBarLaunch` 增加 `windowSplashScreenAnimatedIcon` 指向一个透明 drawable，系统阶段只铺品牌底色；品牌由本地启动页（`www/index.html`）展示 |
| 版本号单一来源 | 新增 `mobile/version.properties`；顶层 `project.file("../../version.properties")` 读取（`android {}` 里解析不到 `file()`） |
| 发布脚本 | `mobile/scripts/publish-apk.mjs`：读版本 → 出包 → 复制成 `releases/shuati-android-{版本}.apk` 与别名 → 生成 `latest.json` → 打印/执行 scp 到 `/opt/shuati/apk/`；`releases/` 进 gitignore |
| 下载页 | `frontend/src/views/DownloadView.vue` + 公开路由 `/download`：读 `/apk/latest.json`，给出下载按钮与二维码；文本说明安装步骤与常见问题 |
| 二维码 | 用 `qrcode` CLI 一次性生成静态 SVG `frontend/public/download-qr.svg`（内容为 `/apk/shuati-android.apk`），运行时不引依赖 |
| 服务器 | Nginx：`/apk/` → `root /opt/shuati; try_files $uri =404`（apk MIME、清单 no-store、带版本号产物长缓存）；部署脚本 `install -d` 建目录、不清内容 |
| 入口 | 登录页页脚加「下载安卓 App」链接 |

## 影响面

| 位置 | 变更 | 风险 |
| --- | --- | --- |
| `mobile/` | 版本来源、启动主题、发布脚本 | 低：版本来源改动已用 aapt2 核对 |
| `frontend/` | 新增公开下载页 + 登录页页脚链接 | 低：新页面，不动既有流程 |
| `deploy/` | Nginx 新增 `/apk/`、脚本建目录 | 低：新增 location，`nginx -t` 会校验 |

## 测试策略

- Playwright：下载页（清单驱动版本、下载链接、二维码可见 + 静态文件可访问）；
- 包内核对：aapt2 读 versionName/versionCode；`latest.json` 与产物 SHA256 一致；
- 真机：确认启动只出现一次品牌画面（本机无模拟器镜像）。
