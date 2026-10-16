#!/usr/bin/env node
/**
 * 发布 Android 包：按版本号出产物 + 生成版本清单。
 *
 * 宪法 v1.3.0「客户端版本号与分发」要求：
 *   - 版本号单一来源 mobile/version.properties；
 *   - 产物文件名带版本号，同时保留稳定别名 shuati-android.apk（下载页/二维码指向它）；
 *   - 每次发布生成 latest.json（网页端下载页只读它，不许手写版本号）；
 *   - 产物不进 Git，放到服务器 Nginx 的 /download/ 目录。
 *
 * 用法：
 *   node scripts/publish-apk.mjs                 # 出包 + 生成产物与清单 + 打印上传命令
 *   SHUATI_DEPLOY_HOST=root@1.2.3.4 node scripts/publish-apk.mjs   # 顺手 scp 上去
 */
import { execFileSync, spawnSync } from "node:child_process";
import { copyFileSync, existsSync, mkdirSync, readFileSync, statSync, writeFileSync } from "node:fs";
import { createHash } from "node:crypto";
import { dirname, join, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const mobileDir = resolve(dirname(fileURLToPath(import.meta.url)), "..");
const releasesDir = join(mobileDir, "releases");
const apkPath = join(mobileDir, "android/app/build/outputs/apk/debug/app-debug.apk");
const siteBase = "https://www.seaunit.site";
// 静态产物放 /apk/：页面路由是 /download，两者不能撞车（带斜杠会被当成目录）
const publishPath = "/apk";

function readVersion() {
  const raw = readFileSync(join(mobileDir, "version.properties"), "utf8");
  const props = {};
  for (const line of raw.split(/\r?\n/)) {
    const match = /^([A-Za-z]+)\s*=\s*(.+)$/.exec(line.trim());
    if (match) props[match[1]] = match[2].trim();
  }
  if (!props.versionName || !props.versionCode) {
    console.error("mobile/version.properties 里缺少 versionName 或 versionCode");
    process.exit(1);
  }
  return { versionName: props.versionName, versionCode: Number(props.versionCode) };
}

const { versionName, versionCode } = readVersion();

if (!existsSync(apkPath) || process.env.SHUATI_SKIP_BUILD !== "1") {
  console.log("先构建 APK（npm run apk）…");
  const build = spawnSync(process.execPath, [join(mobileDir, "scripts/build-android.mjs")], {
    cwd: mobileDir,
    stdio: "inherit",
  });
  if (build.status !== 0) process.exit(build.status ?? 1);
}

if (!existsSync(apkPath)) {
  console.error(`找不到构建产物：${apkPath}`);
  process.exit(1);
}

mkdirSync(releasesDir, { recursive: true });
const versionedName = `shuati-android-${versionName}.apk`;
const versionedPath = join(releasesDir, versionedName);
const latestPath = join(releasesDir, "shuati-android.apk");
copyFileSync(apkPath, versionedPath);
copyFileSync(apkPath, latestPath);

const size = statSync(versionedPath).size;
const sha256 = createHash("sha256").update(readFileSync(versionedPath)).digest("hex").toUpperCase();
const manifest = {
  version: versionName,
  versionCode,
  sizeBytes: size,
  sizeMb: Number((size / 1024 / 1024).toFixed(2)),
  sha256,
  publishedAt: new Date().toISOString(),
  url: `${siteBase}${publishPath}/shuati-android.apk`,
  versionedUrl: `${siteBase}${publishPath}/${versionedName}`,
};
writeFileSync(join(releasesDir, "latest.json"), `${JSON.stringify(manifest, null, 2)}\n`, "utf8");

console.log(`\n版本 ${versionName}（versionCode ${versionCode}）`);
console.log(`产物：${versionedPath}（${manifest.sizeMb} MB）`);
console.log(`SHA256：${sha256}`);
console.log(`清单：${join(releasesDir, "latest.json")}`);

const host = process.env.SHUATI_DEPLOY_HOST;
if (host) {
  console.log(`\n上传到 ${host}:/opt/shuati/apk/ …`);
  const scp = spawnSync(
    "scp",
    [versionedPath, latestPath, join(releasesDir, "latest.json"), `${host}:/opt/shuati/apk/`],
    { stdio: "inherit" },
  );
  if (scp.status !== 0) {
    console.error("scp 失败，请检查隧道/网络后重试（也可以手动 scp）");
    process.exit(scp.status ?? 1);
  }
  console.log("已上传。下载页与二维码指向 /apk/shuati-android.apk，无需改前端。");
} else {
  console.log(
    [
      "",
      "下一步：把产物放到服务器的 /apk/ 目录（Nginx 已配置该路径）：",
      `  scp "${versionedPath}" "${latestPath}" "${join(releasesDir, "latest.json")}" <服务器>:/opt/shuati/apk/`,
      "或设置环境变量后由本脚本代传：SHUATI_DEPLOY_HOST=root@<服务器IP> npm run publish:apk",
    ].join("\n"),
  );
}
