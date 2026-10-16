/**
 * 判断"当前这份页面是不是跑在 App 壳里"。
 *
 * 用来处理只有壳里才会遇到的事，目前只有一件：App 里不该出现「下载安卓 App」入口——
 * 装都装上了，点进去还下不了（WebView 里点下载链接没反应），下载页又是独立页面、
 * 没有导航，不好退出来。
 *
 * 两个信号，都不用后端参与、也不用为客户端开 API 分支（宪法原则 VI）：
 *   1. 壳是本地页（Capacitor 里是 `https://localhost`），它跳到线上站点时会带上
 *      Referer，也就是壳的 origin —— 实测跨源导航会保留 origin；
 *   2. 新版本壳会把 `ShuatiApp` 追加进 User-Agent（capacitor.config.ts），
 *      整页跳转（登录、退出这类）之后也认得出。
 * 认出来之后写进 sessionStorage：整页跳转会丢 Referer，但不该又冒出入口。
 *
 * 注意：这是**展现层**的开关，不是业务分支——同一个组件、同一份数据，
 * 只是少画一个导航项；不要拿它去改接口或业务规则。
 */
const SHELL_ORIGINS = ["https://localhost", "capacitor://localhost"];
const STORAGE_KEY = "shuati:in-app-shell";

function detectFromEnvironment(): boolean {
  if (typeof window === "undefined") {
    return false;
  }
  if (navigator.userAgent.includes("ShuatiApp")) {
    return true;
  }
  const referrer = document.referrer ?? "";
  return SHELL_ORIGINS.some((origin) => referrer === origin || referrer.startsWith(`${origin}/`));
}

export function isInAppShell(): boolean {
  if (detectFromEnvironment()) {
    try {
      sessionStorage.setItem(STORAGE_KEY, "1");
    } catch {
      // 无痕模式等场景下写不了，按本次判断走即可
    }
    return true;
  }
  try {
    return sessionStorage.getItem(STORAGE_KEY) === "1";
  } catch {
    return false;
  }
}
