import type { CapacitorConfig } from "@capacitor/cli";

/**
 * 站点地址：**一处可配**（宪法 v1.2.0「技术栈与部署约束」要求写明远端加载还是本地资源）。
 *
 * 这里用「远端加载」：壳直接打开网页端同一份线上版本，于是
 * 1) 界面与手机浏览器看到的**必然一致**（就是同一份实现，不存在两边对齐）；
 * 2) 网页端发版即 App 最新，不用等应用商店审核；
 * 3) 同源，所以会话 Cookie 与 `/api/**` 都天然可用，不需要 CORS，也不需要改后端。
 *
 * 代价：必须联网（无离线包）。换域名只改这一行。
 */
const SITE_URL = "https://www.seaunit.site";

const config: CapacitorConfig = {
  appId: "site.seaunit.shuati",
  appName: "拾题",
  // 远端加载模式下 webDir 只是占位（CLI 需要一个存在的目录），不会被使用
  webDir: "www",
  server: {
    url: SITE_URL,
    cleartext: false,
    androidScheme: "https",
  },
  android: {
    allowMixedContent: false,
  },
  backgroundColor: "#FFFFFF",
};

export default config;
