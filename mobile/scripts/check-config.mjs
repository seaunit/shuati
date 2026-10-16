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
check("appId 是 site.seaunit.shuati", /appId:\s*"site\.seaunit\.shuati"/.test(config));
check("应用名是拾题", /appName:\s*"拾题"/.test(config));
check(
  "入口是 /app/（应用壳）而不是站根（那是 SEO 落地页）",
  /const SITE_URL = "https:\/\/[^"]+\/app\/"/.test(config),
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
