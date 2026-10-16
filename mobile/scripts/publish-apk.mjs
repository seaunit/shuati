#!/usr/bin/env node
/**
 * 发布 Android 包：按版本号出产物 + 生成版本清单。
 *
 * 宪法 v1.3.1「客户端版本号与分发」要求：
 *   - 版本号单一来源 mobile/version.properties；
 *   - 产物文件名带版本号，同时保留稳定别名 shuati-android.apk（下载页/二维码指向它）；
 *   - 每次发布生成 latest.json（网页端下载页只读它，不许手写版本号）；
 *   - 产物不进 Git，经 GitHub Release 分发（部署脚本从 releases/latest/download/ 拉到 /apk/）。
 *
 * 用法：
 *   node scripts/publish-apk.mjs                 # 出包 + 产物 + 清单 + 发 Release
 *   SHUATI_DEPLOY_HOST=root@1.2.3.4 node scripts/publish-apk.mjs   # 兜底：顺手 scp 上去
 *
 * 没有 gh CLI 也没有 GITHUB_TOKEN 时，脚本会打印手动建 Release 的两步指引（仍然可用）。
 */
import { execFileSync, spawnSync } from "node:child_process";
import { copyFileSync, existsSync, mkdirSync, readFileSync, statSync, writeFileSync } from "node:fs";
import { createHash } from "node:crypto";
import { connect } from "node:net";
import { basename, dirname, join, resolve } from "node:path";
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
const tag = `android-v${versionName}`;
const releaseTitle = `安卓包 v${versionName}`;
const releaseNotes = [
  `拾题安卓客户端 v${versionName}（versionCode ${versionCode}）`,
  "",
  `- 体积 ${manifest.sizeMb} MB`,
  `- SHA256 ${sha256}`,
  "- 部署脚本会从本 Release 的 latest 别名拉取安装包到服务器 /opt/shuati/apk/",
].join("\n");
const releaseAssets = [versionedPath, latestPath, join(releasesDir, "latest.json")];

const repoSlug = (() => {
  try {
    const remote = execFileSync("git", ["remote", "get-url", "origin"], {
      cwd: mobileDir,
      encoding: "utf8",
    }).trim();
    const match = /github\.com[:/]([^/]+\/[^/.]+?)(\.git)?$/.exec(remote);
    return match ? match[1] : null;
  } catch {
    return null;
  }
})();

/** 走 GitHub API（GITHUB_TOKEN / GH_TOKEN）：建 Release 并上传产物，重名资产先删再传 */
async function publishWithToken(token) {
  const headers = {
    Authorization: `Bearer ${token}`,
    Accept: "application/vnd.github+json",
    "User-Agent": "shuati-publish-apk",
  };
  let release = await fetch(`https://api.github.com/repos/${repoSlug}/releases/tags/${tag}`, {
    headers,
  })
    .then((response) => (response.ok ? response.json() : null))
    .catch(() => null);
  if (!release) {
    const created = await fetch(`https://api.github.com/repos/${repoSlug}/releases`, {
      method: "POST",
      headers: { ...headers, "Content-Type": "application/json" },
      body: JSON.stringify({ tag_name: tag, name: releaseTitle, body: releaseNotes }),
    });
    if (!created.ok) {
      console.warn(`创建 Release 失败：HTTP ${created.status}`);
      return false;
    }
    release = await created.json();
  }
  const assets = await fetch(
    `https://api.github.com/repos/${repoSlug}/releases/${release.id}/assets`,
    { headers },
  )
    .then((response) => (response.ok ? response.json() : []))
    .catch(() => []);

  for (const file of releaseAssets) {
    const name = basename(file);
    const existing = assets.find((asset) => asset.name === name);
    if (existing) {
      await fetch(`https://api.github.com/repos/${repoSlug}/releases/assets/${existing.id}`, {
        method: "DELETE",
        headers,
      });
    }
    const upload = await fetch(
      `https://uploads.github.com/repos/${repoSlug}/releases/${release.id}/assets?name=${encodeURIComponent(name)}`,
      {
        method: "POST",
        headers: { ...headers, "Content-Type": "application/octet-stream" },
        body: readFileSync(file),
      },
    );
    console.log(upload.ok ? `  已上传资产 ${name}` : `  上传 ${name} 失败：HTTP ${upload.status}`);
  }
  return true;
}

/** 找 gh CLI：PATH 上没有时再试 Windows 默认安装位置（安装器默认不写 PATH） */
function findGh() {
  const probe = (command) => {
    const result = spawnSync(command, ["--version"], { encoding: "utf8" });
    return !result.error && result.status === 0 ? command : null;
  };
  const onPath = probe("gh");
  if (onPath) return onPath;
  const candidates = [
    join(process.env.ProgramFiles ?? "C:/Program Files", "GitHub CLI", "gh.exe"),
    join(process.env["ProgramFiles(x86)"] ?? "C:/Program Files (x86)", "GitHub CLI", "gh.exe"),
    process.env.LOCALAPPDATA ? join(process.env.LOCALAPPDATA, "Programs", "GitHub CLI", "gh.exe") : null,
  ].filter(Boolean);
  for (const candidate of candidates) {
    if (existsSync(candidate) && probe(candidate)) return candidate;
  }
  return null;
}

/**
 * GitHub 直连常被重置，而 gh CLI 不读 git 的 http.proxy。
 * 外面没设 HTTPS_PROXY 时沿用 git 配的代理（先探一下端口通不通，
 * 代理关了还硬塞给 gh 只会让它连不上），让"本机一条命令发 Release"真的能跑通。
 */
async function proxyEnv() {
  if (process.env.HTTPS_PROXY || process.env.https_proxy) return {};
  const result = spawnSync("git", ["config", "--get", "http.proxy"], {
    cwd: mobileDir,
    encoding: "utf8",
  });
  const proxy = result.status === 0 ? (result.stdout ?? "").trim() : "";
  if (!proxy) return {};
  if (process.env.SHUATI_PROXY_PROBE !== "0" && !(await isProxyReachable(proxy))) {
    console.warn(`git 配的代理 ${proxy} 连不上，本次直连 GitHub 试试`);
    return {};
  }
  return { HTTPS_PROXY: proxy, HTTP_PROXY: proxy };
}

/** 探一下代理端口（最长 1.5 秒），连不上就当没配 */
function isProxyReachable(proxy) {
  let url;
  try {
    url = new URL(proxy);
  } catch {
    return Promise.resolve(false);
  }
  const port = Number(url.port || (url.protocol === "https:" ? 443 : 80));
  return new Promise((resolve) => {
    const socket = connect({ host: url.hostname, port });
    const done = (ok) => {
      socket.destroy();
      resolve(ok);
    };
    socket.setTimeout(1500);
    socket.once("connect", () => done(true));
    socket.once("timeout", () => done(false));
    socket.once("error", () => done(false));
  });
}

/** 优先 gh CLI，其次 GITHUB_TOKEN，都没有就打印手动步骤 */
async function publishRelease() {
  if (!repoSlug) {
    console.log("\n没识别到 GitHub 仓库（git remote origin），跳过发 Release");
    return false;
  }
  const env = { ...process.env, ...(await proxyEnv()) };
  const gh = findGh();
  if (gh) {
    const auth = spawnSync(gh, ["auth", "status"], { encoding: "utf8", env });
    if (auth.status === 0) {
      console.log(`\n用 gh CLI 发 Release ${tag} …`);
      const options = { cwd: mobileDir, stdio: "inherit", env };
      const create = spawnSync(
        gh,
        ["release", "create", tag, "--title", releaseTitle, "--notes", releaseNotes, ...releaseAssets],
        options,
      );
      if (create.status === 0) return true;
      // 同一个版本重新出包（改了壳再发一次）会走到这里：覆盖附件即可
      console.warn("gh release create 失败（多半是 Release 已存在），改用 gh release upload --clobber 覆盖附件");
      const upload = spawnSync(gh, ["release", "upload", tag, "--clobber", ...releaseAssets], options);
      if (upload.status === 0) return true;
      console.warn("gh release upload 也失败了，改用 GITHUB_TOKEN 试试");
    } else {
      console.warn("gh 已安装但没登录（gh auth login），改用 GITHUB_TOKEN 试试");
    }
  }
  const token = process.env.GITHUB_TOKEN || process.env.GH_TOKEN;
  if (token) {
    console.log(`\n用 GITHUB_TOKEN 发 Release ${tag} …`);
    return publishWithToken(token);
  }
  console.log(
    [
      "",
      `没有 gh CLI 也没有 GITHUB_TOKEN —— 手动发一次 Release（每个版本两步）：`,
      `  1) 打开 https://github.com/${repoSlug}/releases/new?tag=${tag}`,
      `     标题填「${releaseTitle}」，正文贴：${releaseNotes.split("\n")[0]}`,
      "  2) 把这几个文件拖进附件：",
      ...releaseAssets.map((file) => `     ${file}`),
      "",
      "发完再跑部署脚本即可（它会从 releases/latest/download/ 自动拉取）。",
      "想全自动：装 gh CLI 并 gh auth login，或设置 GITHUB_TOKEN 后重跑 npm run publish:apk。",
    ].join("\n"),
  );
  return false;
}

const released = await publishRelease();
if (released) {
  console.log(
    `\nRelease ${tag} 已就绪：部署脚本会从 releases/latest/download/shuati-android.apk 拉取，无需再 scp。`,
  );
}

/* 需要手工上传到服务器时（例如还没发 Release）的兜底提示 */
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
      "（备用）也可以直接 scp 到服务器 /apk/（Nginx 已配置该路径）：",
      `  scp "${versionedPath}" "${latestPath}" "${join(releasesDir, "latest.json")}" <服务器>:/opt/shuati/apk/`,
      "或设置环境变量后由本脚本代传：SHUATI_DEPLOY_HOST=root@<服务器IP> npm run publish:apk",
    ].join("\n"),
  );
}
