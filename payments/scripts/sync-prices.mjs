/**
 * 把已创建商品的价格同步成 catalog 里的金额与币种（默认 CNY）。
 * 用途：商品最早按 USD 建过，这里一次性改成人民币。
 * 运行： node scripts/sync-prices.mjs
 */
import { existsSync } from "node:fs";
import { fileURLToPath } from "node:url";

const envPath = fileURLToPath(new URL("../.env", import.meta.url));
if (existsSync(envPath)) {
  process.loadEnvFile(envPath);
}

const { client, productIdOf, environment } = await import("../src/waffo.mjs");
const { CATALOG, CURRENCY } = await import("../src/catalog.mjs");

console.log(`环境=${environment} 目标币种=${CURRENCY}，开始同步 ${CATALOG.length} 个商品价格…\n`);

for (const item of CATALOG) {
  const productId = productIdOf(item.itemCode);
  if (!productId) {
    console.log(`↷ ${item.itemCode} 未配置映射，跳过`);
    continue;
  }
  const prices = {
    [CURRENCY]: { amount: item.amount, taxIncluded: true, taxCategory: item.taxCategory },
  };
  try {
    if (item.type === "subscription") {
      await client.subscriptionProducts.update({ id: productId, prices });
    } else {
      await client.onetimeProducts.update({ id: productId, prices });
    }
    console.log(`✅ ${item.itemCode} -> ${CURRENCY} ${item.amount}  (${productId})`);
  } catch (e) {
    console.log(`❌ ${item.itemCode} 失败：${e?.status ?? ""} ${e?.message ?? e}`);
    if (e?.errors) console.log("   " + JSON.stringify(e.errors).slice(0, 400));
  }
}

console.log("\n完成。可以用 GraphQL 查询 product.prices 复核。");
