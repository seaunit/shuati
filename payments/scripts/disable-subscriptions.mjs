/**
 * 商业模式改为纯点数制：把 6 个订阅商品下架（保留 3 个 CNY 一次性点数包）。
 * 运行： node scripts/disable-subscriptions.mjs
 */
import { existsSync } from "node:fs";
import { fileURLToPath } from "node:url";

const envPath = fileURLToPath(new URL("../.env", import.meta.url));
if (existsSync(envPath)) {
  process.loadEnvFile(envPath);
}

const { client, productIdOf, environment } = await import("../src/waffo.mjs");
const { LEGACY_SUBSCRIPTIONS } = await import("../src/catalog.mjs");

const targets = LEGACY_SUBSCRIPTIONS;
console.log(`环境=${environment}，下架 ${targets.length} 个订阅商品…\n`);

for (const item of targets) {
  const id = productIdOf(item.itemCode);
  if (!id) {
    console.log(`↷ ${item.itemCode} 未配置映射，跳过`);
    continue;
  }
  try {
    await client.subscriptionProducts.updateStatus({ id, status: "inactive" });
    console.log(`✅ ${item.name} 已下架 (${id})`);
  } catch (e) {
    console.log(`❌ ${item.name} 失败：${e?.status ?? ""} ${e?.message ?? e}`);
    if (e?.errors) console.log("   " + JSON.stringify(e.errors).slice(0, 400));
  }
}

console.log("\n完成。点数包保持销售中。");
