#!/usr/bin/env node
/**
 * 生成下载页的品牌二维码（静态 SVG，页面运行时不引依赖）。
 *
 * 为什么留一个脚本而不是直接贴一张图：
 *   1. 二维码内容是下载页地址，域名一变必须重新生成，脚本是唯一来源；
 *   2. 中心要盖品牌叶子，会挡住模块，必须用 **H 级纠错**（30% 冗余）才扫得出来——
 *      这个参数不能靠"下次随手重新生成"记着；
 *   3. 静区（quiet zone）也在这里统一加，省得谁导出时裁掉导致扫不出来。
 *
 * 产出两份：
 *   - src/assets/download-qr.svg：矢量原图（缩放不糊，也方便印刷）
 *   - src/assets/download-qr.png：位图，给手机长按保存/转发用（SVG 存不下来）
 *
 * 放在 src/assets 而不是 public：下载页 import 它，构建时会带上内容指纹文件名，
 * 改了图就是换了个 URL，不会被浏览器/CDN 拿旧缓存糊弄（放 public 时踩过：
 * 图换了、文件名没变，用户看到的还是上一版，缓存 4 小时）。
 *
 * 两张图都是**完整卡片**：二维码 + 中心叶子 + 产品名 + 标语。
 * 文字必须画进图里——只挂在 HTML 上的话，用户长按保存或点「保存二维码」拿到的
 * 就是一张光秃秃的二维码，转给朋友谁也认不出是什么。
 *
 * 用法：
 *   npm run qr:download
 *   SHUATI_SITE_BASE=https://example.com npm run qr:download   # 换域名时
 */
import { writeFileSync } from "node:fs";
import { dirname, join, resolve } from "node:path";
import { fileURLToPath } from "node:url";

import QRCode from "qrcode";
import sharp from "sharp";

const frontendDir = resolve(dirname(fileURLToPath(import.meta.url)), "..");
const siteBase = (process.env.SHUATI_SITE_BASE ?? "https://www.seaunit.site").replace(/\/+$/, "");
// 扫出来的是**下载页**，不是 APK 直链：直链在微信/部分浏览器里会被拦，
// 而且用户落地就能看到版本、安装说明和常见问题
const targetUrl = `${siteBase}/download`;

const MARGIN = 4; // 静区宽度（模块数），低于 4 很多扫码器会识别不稳
const BADGE = 13; // 中心叶子徽标边长（模块数）；再大就明显吃掉可读余量了
const UNIT = 10; // 一个二维码模块在画布里占多少单位

// 站点配色（与 frontend/src/styles/main.css 一致）
const INK = "#17181a";
const MOSS = "#2f6b45";
const GRAY = "#6b7280";
const FONT = "PingFang SC, Microsoft YaHei, Noto Sans CJK SC, sans-serif";

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

// 卡片：上面二维码，下面产品名与标语（文字画进图里，保存/转发出去都认得）
const qrSide = total * UNIT;
// 字号按卡片宽度等比算（二维码版本会随内容长度变，写死数字就会溢出被裁）：
// 标语 11 个全角字，取 0.07 倍宽度 → 约 0.77 倍卡片宽，两边还留得下边距
const nameSize = Math.round(qrSide * 0.13);
const taglineSize = Math.round(qrSide * 0.07);
const nameBaseline = qrSide + Math.round(qrSide * 0.16) + Math.round(nameSize * 0.72);
const taglineBaseline = nameBaseline + Math.round(taglineSize * 1.3);
const cardHeight = taglineBaseline + Math.round(taglineSize * 0.45);

const svg = `<?xml version="1.0" encoding="utf-8"?>
<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 ${qrSide} ${cardHeight}" role="img" aria-label="扫码打开拾题下载页">
  <title>扫码打开拾题下载页</title>
  <rect width="${qrSide}" height="${cardHeight}" fill="#ffffff"/>
  <g transform="scale(${UNIT})" shape-rendering="crispEdges">
    <path stroke="${INK}" stroke-width="1" d="${runs.join("")}"/>
    <g transform="translate(${badgeLeft} ${badgeLeft})">
      <rect width="${BADGE}" height="${BADGE}" rx="${BADGE * 0.24}" fill="#ffffff"/>
      <g transform="translate(${pad} ${pad}) scale(${leafScale})" fill="none" stroke="${MOSS}" stroke-width="2.4" stroke-linecap="round" stroke-linejoin="round">
        <path d="M11 20A7 7 0 0 1 9.8 6.1C15.5 5 17 4.48 19 2c1 2 2 4.18 2 8 0 5.5-4.78 10-10 10Z"/>
        <path d="M2 21c0-3 1.85-5.36 5.08-6C9.5 14.52 12 13 13 12"/>
      </g>
    </g>
  </g>
  <text x="${qrSide / 2}" y="${nameBaseline}" text-anchor="middle" font-family="${FONT}" font-size="${nameSize}" font-weight="700" fill="${INK}">拾题</text>
  <text x="${qrSide / 2}" y="${taglineBaseline}" text-anchor="middle" font-family="${FONT}" font-size="${taglineSize}" fill="${GRAY}">安静地练习，温柔地记住</text>
</svg>
`;

const svgPath = join(frontendDir, "src", "assets", "download-qr.svg");
const pngPath = join(frontendDir, "src", "assets", "download-qr.png");
writeFileSync(svgPath, svg, "utf8");
const pngWidth = 1024;
const pngHeight = Math.round((pngWidth * cardHeight) / qrSide);
await sharp(Buffer.from(svg), { density: 384 })
  .resize(pngWidth, pngHeight, { fit: "contain", background: "#ffffff" })
  .png({ compressionLevel: 9 })
  .toFile(pngPath);

console.log(`二维码内容：${targetUrl}`);
console.log(`纠错等级：H ｜ 版本：${(size - 17) / 4} ｜ 模块：${size}×${size} ｜ 中心徽标：${BADGE}×${BADGE}`);
console.log(`已写入：${svgPath}`);
console.log(`已写入：${pngPath}（${pngWidth}×${pngHeight}：二维码 + 叶子 + 产品名 + 标语）`);
