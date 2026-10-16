import type { CapacitorConfig } from "@capacitor/cli";

/**
 * 启动方式：**本地启动页 → 远端应用**（宪法 v1.2.0 要求写明远端加载还是本地资源）。
 *
 * 1. 不带 `server.url`：Capacitor 加载本地 `www/index.html`，也就是品牌启动页
 *    （叶子 + 拾题 + 一句话），本地文件不依赖网络，冷启动秒开、没有白屏；
 * 2. 启动页展示完品牌后跳转到线上应用 `https://www.seaunit.site/app/`，
 *    所以 App 里的界面始终与网页端同一份实现，网页端发版 App 即最新；
 * 3. **跳转目标必须写进下面的 `allowNavigation`**，否则 Capacitor 会把它当成外部链接
 *    交给系统浏览器（那样就"跳出 App"了）。
 *
 * 站点入口 URL 只写在一个地方：`www/index.html` 里的 `SITE_URL`（启动页要跳的目标）。
 * 换域名时改 `www/index.html` 的 SITE_URL + 这里的 allowNavigation。
 *
 * 代价：必须联网（不做离线包）。
 */
const config: CapacitorConfig = {
  appId: "site.seaunit.shuati",
  appName: "拾题",
  webDir: "www",
  server: {
    // 允许在 App 内部导航到站点（含 www 与非 www，避免用户被跳去浏览器）
    allowNavigation: ["www.seaunit.site", "seaunit.site"],
    cleartext: false,
    androidScheme: "https",
  },
  android: {
    allowMixedContent: false,
  },
  // 与启动页同色，避免启动瞬间闪白
  backgroundColor: "#F7F7F7",
};

export default config;
