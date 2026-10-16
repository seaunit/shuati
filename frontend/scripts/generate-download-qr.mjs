#!/usr/bin/env node
/**
 * 生成下载页的品牌二维码（静态 SVG，页面运行时不引依赖）。
 *
 * 为什么留一个脚本而不是直接贴一张图：
 *   1. 二维码内容就是下载地址，域名一变必须重新生成，脚本是唯一来源；
 *   2. 中心要盖品牌叶子，会挡住模块，必须用 **H 级纠错**（30% 冗余）才扫得出来——
 *      这个参数不能靠"下次随手重新生成"记着；
 *   3. 静区（quiet zone）也在这里统一加，省得谁导出时裁掉导致扫不出来。
 *
 * 用法：
 *   npm run qr:download
 *   SHUATI_SITE_BASE=https://example.com npm run qr:download   # 换域名时
 */
import { writeFileSync } from "node:fs";
import { dirname, join, resolve } from "node:path";
import { fileURLToPath } from "node:url";

import QRCode from "qrcode";

const frontendDir = resolve(dirname(fileURLToPath(import.meta.url)), "..");
const siteBase = (process.env.SHUATI_SITE_BASE ?? "https://www.seaunit.site").replace(/\/+$/, "");
const targetUrl = `${siteBase}/apk/shuati-android.apk`;

const MARGIN = 4; // 静区宽度（模块数），低于 4 很多扫码器会识别不稳
const BADGE = 13; // 中心叶子徽标边长（模块数）；再大就明显吃掉可读余量了

// 站点配色（与 frontend/src/styles/main.css 一致）
const INK = "#17181a";
const MOSS = "#2f6b45";

// 纠错等级必须 ≥ H：中心徽标挡掉的模块全靠这份冗余兜底
const qr = QRCode.create(targetUrl, { errorCorrectionLevel: "H" });
const size = qr.modules.size;
const total = size + MARGIN * 2;

// 按行做游程编码，比一个模块一个 rect 小两个数量级
const runs = [];
for (let y = 0; y < size; y += 1) {
  let x = 0;
  while (x < size) {
    if (!qr.modules.get(x, y)) {
      x += 1;
      continue;
    }
    let run = 0;
    while (x + run < size && qr.modules.get(x + run, y)) run += 1;
    runs.push(`M${x + MARGIN} ${y + MARGIN + 0.5}h${run}`);
    x += run;
  }
}

// 徽标：白底圆角方块 + 叶子（叶子路径取自 favicon.svg，24 网格缩放到徽标内 60%）
const badgeLeft = MARGIN + size / 2 - BADGE / 2;
const pad = BADGE * 0.17;
const leafSize = BADGE - pad * 2;
const leafScale = leafSize / 24;

const svg = `<?xml version="1.0" encoding="utf-8"?>
<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 ${total} ${total}" width="${total * 8}" height="${total * 8}" shape-rendering="crispEdges" role="img" aria-label="扫码下载拾题安卓版">
  <title>扫码下载拾题安卓版</title>
  <path fill="#ffffff" d="M0 0h${total}v${total}H0z"/>
  <path stroke="${INK}" stroke-width="1" d="${runs.join("")}"/>
  <g transform="translate(${badgeLeft} ${badgeLeft})">
    <rect width="${BADGE}" height="${BADGE}" rx="${BADGE * 0.24}" fill="#ffffff"/>
    <g transform="translate(${pad} ${pad}) scale(${leafScale})" fill="none" stroke="${MOSS}" stroke-width="2.4" stroke-linecap="round" stroke-linejoin="round">
      <path d="M11 20A7 7 0 0 1 9.8 6.1C15.5 5 17 4.48 19 2c1 2 2 4.18 2 8 0 5.5-4.78 10-10 10Z"/>
      <path d="M2 21c0-3 1.85-5.36 5.08-6C9.5 14.52 12 13 13 12"/>
    </g>
  </g>
</svg>
`;

const output = join(frontendDir, "public", "download-qr.svg");
writeFileSync(output, svg, "utf8");
console.log(`二维码内容：${targetUrl}`);
console.log(`纠错等级：H ｜ 版本：${(size - 17) / 4} ｜ 模块：${size}×${size} ｜ 中心徽标：${BADGE}×${BADGE}`);
console.log(`已写入：${output}`);
