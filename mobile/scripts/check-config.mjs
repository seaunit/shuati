#!/usr/bin/env node
/**
 * 壳配置自检：确认关键配置没被改坏（宪法 v1.2.0 要求的关键项）。
 * 任何一条不满足就退出码非 0，部署前能直接拦住。
 */
import { existsSync, readFileSync } from "node:fs";
import { dirname, join, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const mobileDir = resolve(dirname(fileURLToPath(import.meta.url)), "..");
const read = (relative) => readFileSync(join(mobileDir, relative), "utf8");

const checks = [];
const check = (name, ok, detail = "") => checks.push({ name, ok, detail });

const config = read("capacitor.config.ts");
const splash = read("www/index.html");
check("appId 是 site.seaunit.shuati", /appId:\s*"site\.seaunit\.shuati"/.test(config));
check("应用名是拾题", /appName:\s*"拾题"/.test(config));
check("走本地启动页（没有 server.url）", !/server:\s*\{[^}]*\burl:/.test(config));
check(
  "启动页是要展示品牌内容（叶子 + 拾题 + 一句话）",
  splash.includes("拾题") && splash.includes("安静地练习，温柔地记住") && splash.includes("<svg"),
);
check(
  "启动页跳转目标是 /app/（应用壳，不是 SEO 落地页）",
  /SITE_URL = "https:\/\/[^"]+\/app\/"/.test(splash),
);
check(
  "跳转目标在 allowNavigation 里（否则会被系统浏览器接管）",
  /allowNavigation:\s*\[[^\]]*seaunit\.site[^\]]*\]/.test(config),
);
check("最小 SDK 不低于 26（图标只用自适应图标）", /minSdkVersion = (2[6-9]|[3-9]\d)/.test(read("android/variables.gradle")));
check(
  "应用名写入 Android strings.xml",
  /<string name="app_name">拾题<\/string>/.test(read("android/app/src/main/res/values/strings.xml")),
);
check(
  "图标前景是网页端那片叶子（不是模板默认 logo）",
  ["android/app/src/main/res/drawable/ic_launcher_foreground.xml",
    "android/app/src/main/res/drawable-v24/ic_launcher_foreground.xml"]
    .every((p) => existsSync(join(mobileDir, p)) && read(p).includes("#7D9B76")),
  "（两份都要是叶子：drawable-v24 会覆盖 drawable）",
);
check(
  "图标有自适应配置",
  existsSync(join(mobileDir, "android/app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml")),
);
check("产物不含密钥（仓库内无 .env/私钥）", !existsSync(join(mobileDir, ".env")) && !existsSync(join(mobileDir, "capacitor.config.json")));

let failed = 0;
for (const item of checks) {
  if (!item.ok) failed += 1;
  console.log(`${item.ok ? "✓" : "✗"} ${item.name}${item.detail ? ` ${item.detail}` : ""}`);
}
console.log(`\n${checks.length - failed}/${checks.length} 项通过`);
process.exit(failed === 0 ? 0 : 1);
