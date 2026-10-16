#!/usr/bin/env node
/**
 * 出 Android 调试包。
 *
 * 前置：Capacitor 8 的 Android 模板要求 **JDK 21**（`capacitor.build.gradle` 里写死 JavaVersion.VERSION_21），
 * 本机默认的 JDK 17 编不了。这里按顺序找一个 JDK 21+：
 *   1. 环境变量 SHUATI_JDK21（指向 JDK 根目录）
 *   2. 项目内便携 JDK：mobile/.jdk/jdk-21*（已 gitignore）
 *   3. JAVA_HOME（若其版本 ≥ 21）
 *   4. PATH 上的 java（若版本 ≥ 21）
 * 找不到就给出明确指引，不静默用错版本。
 */
import { spawnSync } from "node:child_process";
import { existsSync, readdirSync, statSync } from "node:fs";
import { dirname, join, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const mobileDir = resolve(dirname(fileURLToPath(import.meta.url)), "..");
const androidDir = join(mobileDir, "android");

function javaMajor(javaExe) {
  // 注意：java -version 的输出走 stderr，两个流都要读，否则解析不到版本号
  const result = spawnSync(javaExe, ["-version"], { encoding: "utf8" });
  const out = `${result.stderr ?? ""}${result.stdout ?? ""}`;
  return Number(/version "(\d+)/.exec(out)?.[1] ?? 0);
}

function candidates() {
  const list = [];
  if (process.env.SHUATI_JDK21) list.push(process.env.SHUATI_JDK21);
  const localJdk = join(mobileDir, ".jdk");
  if (existsSync(localJdk)) {
    for (const name of readdirSync(localJdk)) {
      if (name.startsWith("jdk")) list.push(join(localJdk, name));
    }
  }
  if (process.env.JAVA_HOME) list.push(process.env.JAVA_HOME);
  return list;
}

function findJdk21() {
  for (const home of candidates()) {
    const exe = join(home, "bin", process.platform === "win32" ? "java.exe" : "java");
    if (existsSync(exe) && javaMajor(exe) >= 21) return home;
  }
  return null;
}

const jdkHome = findJdk21();
if (!jdkHome) {
  console.error(
    [
      "找不到 JDK 21：Capacitor 8 的 Android 模板要求 Java 21（本机默认是 JDK 17，编不了）。",
      "",
      "任选一种方式后重跑 npm run apk：",
      "  1) 把 JDK 21 解压到 mobile/.jdk/（推荐，已 gitignore）",
      "  2) 设置环境变量 SHUATI_JDK21 指向 JDK 21 根目录",
      "  3) 把 JAVA_HOME 指向 JDK 21",
    ].join("\n"),
  );
  process.exit(1);
}

console.log(`使用 JDK: ${jdkHome}`);

const env = { ...process.env, JAVA_HOME: jdkHome };

function run(command, args, cwd) {
  const result = spawnSync(command, args, { cwd, env, stdio: "inherit" });
  if (result.error) {
    console.error(`执行失败：${command} — ${result.error.message}`);
    process.exit(1);
  }
  if (result.status !== 0) {
    process.exit(result.status ?? 1);
  }
}

// 1) 同步 web 资源与插件（远端加载模式下这一步很轻）
// 直接用 node 跑 Capacitor CLI 的 JS 入口：Windows 上新版 Node 禁止无 shell 地 spawn .cmd，
// 走 node 既避开这个限制，也不依赖 PATH 里的 npx。
const capBin = join(mobileDir, "node_modules/@capacitor/cli/bin/capacitor");
run(process.execPath, [capBin, "sync", "android"], mobileDir);

// 2) 出包（Windows 下通过 cmd 调 .bat，避免 shell 拼接参数的告警）
if (process.platform === "win32") {
  run("cmd.exe", ["/c", "gradlew.bat", "assembleDebug"], androidDir);
} else {
  run("./gradlew", ["assembleDebug"], androidDir);
}

const apk = join(androidDir, "app", "build", "outputs", "apk", "debug", "app-debug.apk");
if (!existsSync(apk)) {
  console.error(`构建结束但没找到产物：${apk}`);
  process.exit(1);
}
const mb = (statSync(apk).size / 1024 / 1024).toFixed(2);
console.log(`\n产物：${apk}（${mb} MB）`);
