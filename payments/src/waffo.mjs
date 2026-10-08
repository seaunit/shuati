import {
  WaffoPancake,
  WaffoPancakeError,
  WebhookEventType,
  verifyWebhook,
} from "@waffo/pancake-ts";

/**
 * 私钥只在本进程内存里使用，绝不返回给前端，也不写进日志。
 * 支持三种形态：PEM 原文、带 \n 转义的 PEM、base64（推荐 CI/CD）。
 */
function resolvePrivateKey() {
  const base64 = process.env.WAFFO_PRIVATE_KEY_BASE64?.trim();
  if (base64) {
    return Buffer.from(base64, "base64").toString("utf-8");
  }
  const raw = process.env.WAFFO_PRIVATE_KEY?.trim();
  if (!raw) {
    throw new Error("缺少 WAFFO_PRIVATE_KEY 或 WAFFO_PRIVATE_KEY_BASE64");
  }
  return raw;
}

const merchantId = process.env.WAFFO_MERCHANT_ID?.trim();
if (!merchantId) {
  throw new Error("缺少 WAFFO_MERCHANT_ID");
}

export const environment = process.env.WAFFO_ENV?.trim() || "test";
export const storeId = process.env.WAFFO_STORE_ID?.trim() || "";

export const client = new WaffoPancake({
  merchantId,
  privateKey: resolvePrivateKey(),
  environment,
});

/** itemCode -> Waffo Product ID，例如 "PLAN:plus:MONTHLY" / "PACK:pack_9" */
export function productIdOf(itemCode) {
  const map = process.env.WAFFO_PRODUCT_MAP?.trim();
  if (!map) {
    return null;
  }
  for (const pair of map.split(",")) {
    const [key, value] = pair.split("=");
    if (key?.trim() === itemCode && value?.trim()) {
      return value.trim();
    }
  }
  return null;
}

export const sdk = {
  WaffoPancake,
  WaffoPancakeError,
  WebhookEventType,
  verifyWebhook,
};
