#!/usr/bin/env node
/**
 * 生成 Android 冷启动用的品牌图（叶子 + 拾题 + 一句话）。
 *
 * 为什么要它：App 打开后要等远端网页端（跨海链路，TTFB 常有半秒上下）才能真正出画面。
 * 只靠本地启动页兜不住——它跳走之后、远端首帧之前，屏幕上什么都没有，用户看到的就是
 * "启动页一闪 → 几秒空白"。
 *
 * 做法是把这张图垫在 **窗口背景**（`res/drawable/splash_brand.xml`）上，
 * 同时把 WebView 背景调成透明（`capacitor.config.ts` 的 backgroundColor），
 * 于是"还没有内容可画"的那几秒露出的是它，而不是空白。
 *
 * 图里的尺寸/配色与 `www/index.html` 的启动页、网页端的首屏启动页保持一致，
 * 三处接力时才不会跳版。改启动页样式时记得同时改这里并重跑：
 *   npm run splash:mark
 */
import { mkdirSync } from "node:fs";
import { dirname, join, resolve } from "node:path";
import { fileURLToPath } from "node:url";

import sharp from "sharp";

const mobileDir = resolve(dirname(fileURLToPath(import.meta.url)), "..");
const output = join(
  mobileDir,
  "android",
  "app",
  "src",
  "main",
  "res",
  "drawable-nodpi",
  "splash_brand_mark.png",
);

// 版面按 200×200dp 设计，导出 3 倍图；layer-list 里再用 dp 指定显示尺寸，
// 这样密度不同也不会被拉伸成糊的
const SCALE = 3;
const FONT = "Microsoft YaHei, PingFang SC, Hiragino Sans GB, Noto Sans CJK SC, sans-serif";

const svg = `<svg xmlns="http://www.w3.org/2000/svg" width="${200 * SCALE}" height="${200 * SCALE}" viewBox="0 0 200 200">
  <rect x="52" y="6" width="96" height="96" rx="28" fill="#eef1ee"/>
  <g transform="translate(72 26) scale(2.3333)" fill="none" stroke="#7d9b76" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
    <path d="M11 20A7 7 0 0 1 9.8 6.1C15.5 5 17 4.48 19 2c1 2 2 4.18 2 8 0 5.5-4.78 10-10 10Z"/>
    <path d="M2 21c0-3 1.85-5.36 5.08-6C9.5 14.52 12 13 13 12"/>
  </g>
  <text x="100" y="151" text-anchor="middle" font-family="${FONT}" font-size="34" font-weight="700" fill="#1a1a1a">拾题</text>
  <text x="100" y="186" text-anchor="middle" font-family="${FONT}" font-size="15" fill="#8a8f98">安静地练习，温柔地记住</text>
</svg>
`;

mkdirSync(dirname(output), { recursive: true });
await sharp(Buffer.from(svg))
  .png({ compressionLevel: 9 })
  .toFile(output);

console.log(`已写入：${output}（${200 * SCALE}×${200 * SCALE}，显示尺寸按 200dp 居中）`);
