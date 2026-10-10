/**
 * Waffo 测试路线冒烟：不写库、不改商品，只验证
 * 私钥 / 商户 / 店铺 / 商品映射是否有效，并真实创建一个测试收银台会话。
 *
 * 运行：npm --prefix payments run smoke
 * 指定商品：npm --prefix payments run smoke -- PACK:pack_29
 */
import { existsSync } from "node:fs";
import { fileURLToPath } from "node:url";

const envPath = fileURLToPath(new URL("../.env", import.meta.url));
if (!existsSync(envPath)) {
  console.error("缺少 payments/.env，请先从 payments/.env.example 复制并填写");
  process.exit(1);
}
process.loadEnvFile(envPath);

const { client, productIdOf, storeId, environment } = await import("../src/waffo.mjs");
const { CATALOG, CURRENCY } = await import("../src/catalog.mjs");

const itemCode = process.argv[2] || CATALOG[0].itemCode;

console.log(`环境    = ${environment}`);
console.log(`店铺    = ${storeId || "(未配置)"}`);
console.log(`币种    = ${CURRENCY}`);
console.log(`DRY_RUN = ${process.env.WAFFO_DRY_RUN === "true"}`);
console.log("在售商品映射：");

let missing = 0;
for (const item of CATALOG) {
  const id = productIdOf(item.itemCode);
  if (!id) {
    missing += 1;
  }
  console.log(`  ${id ? "OK  " : "MISS"} ${item.itemCode} -> ${id ?? "未配置"}`);
}
if (missing > 0) {
  console.error(`\n有 ${missing} 个商品没配 WAFFO_PRODUCT_MAP，先跑 npm --prefix payments run setup`);
  process.exit(1);
}

const productId = productIdOf(itemCode);
if (!productId) {
  console.error(`\n${itemCode} 不在在售目录里，或没有配置映射`);
  process.exit(1);
}

console.log(`\n创建测试收银台：${itemCode} (${productId}) …`);
try {
  const session = await client.checkout.authenticated.create({
    productId,
    currency: CURRENCY,
    buyerIdentity: "smoke-test@seaunit.site",
    buyerEmail: "smoke-test@seaunit.site",
    orderMerchantExternalId: `smoke-${Date.now()}`,
    metadata: { source: "smoke-checkout" },
  });
  console.log("成功");
  console.log(`sessionId   = ${session.sessionId}`);
  console.log(`expiresAt   = ${session.expiresAt}`);
  console.log(`checkoutUrl = ${session.checkoutUrl}`);
  console.log("\n用测试卡结账：成功 4576 7500 0000 0110 / 失败 4576 7500 0000 0220（任意未来有效期与 CVC）");
} catch (e) {
  console.error("失败：", e?.status ?? "", e?.message ?? e);
  if (e?.errors) {
    console.error("   ", JSON.stringify(e.errors).slice(0, 400));
  }
  process.exit(1);
}
