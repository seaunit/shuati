/**
 * 在 Waffo 后台创建本项目所需的商品（幂等：重复名称会返回 409，跳过即可）。
 * 运行： npm --prefix payments run setup
 * 输出末尾会打印可直接粘贴到 .env 的 WAFFO_PRODUCT_MAP。
 */
import { existsSync } from "node:fs";
import { fileURLToPath } from "node:url";

const envPath = fileURLToPath(new URL("../.env", import.meta.url));
if (existsSync(envPath)) {
  process.loadEnvFile(envPath);
}

const { client, storeId, environment } = await import("../src/waffo.mjs");
const { CATALOG } = await import("../src/catalog.mjs");

if (!storeId) {
  console.error("缺少 WAFFO_STORE_ID");
  process.exit(1);
}

console.log(`环境=${environment} 店铺=${storeId}，开始创建 ${CATALOG.length} 个商品…\n`);

const mapping = [];

for (const item of CATALOG) {
  const prices = {
    USD: { amount: item.amount, taxIncluded: true, taxCategory: item.taxCategory },
  };
  try {
    const created =
      item.type === "subscription"
        ? await client.subscriptionProducts.create({
            storeId,
            name: item.name,
            billingPeriod: item.billingPeriod,
            prices,
          })
        : await client.onetimeProducts.create({
            storeId,
            name: item.name,
            prices,
          });
    const product = created.product ?? created;
    mapping.push(`${item.itemCode}=${product.id}`);
    console.log(`✅ ${item.name}  ->  ${product.id}`);
  } catch (e) {
    if (e?.status === 409) {
      console.log(`↷ ${item.name} 已存在（409），请到后台复制其 Product ID`);
    } else {
      console.log(`❌ ${item.name} 失败：${e?.status ?? ""} ${e?.message ?? e}`);
    }
  }
}

console.log("\n把下面这行填进 payments/.env：\n");
console.log(`WAFFO_PRODUCT_MAP=${mapping.join(",")}`);
