import {
  WaffoPancake,
  WaffoPancakeError,
  WebhookEventType,
  verifyWebhook,
} from "@waffo/pancake-ts";

const merchantId = process.env.WAFFO_MERCHANT_ID?.trim();
if (!merchantId) {
  throw new Error("缺少 WAFFO_MERCHANT_ID");
}

/**
 * 私钥只在本进程内存里使用，绝不返回给前端，也不写进日志。
 *
 * 私钥文件在不同批次里可能长这样：带 PEM 头的文本，或「一整串 base64」（裸 PKCS#8）。
 * 手抄时很容易把「base64(文件内容)」和「文件内容本身」搞混，所以这里不做格式假设，
 * 把候选逐一带进 SDK 构造器试，取第一个能解析成功的：
 *   1) WAFFO_PRIVATE_KEY_BASE64 解出来的文本（部署脚本把文件内容 base64 后的结果）
 *   2) WAFFO_PRIVATE_KEY_BASE64 原文（直接把私钥文本粘进变量）
 *   3) WAFFO_PRIVATE_KEY 原文
 *   4) WAFFO_PRIVATE_KEY 再解一次 base64
 */
function resolvePrivateKey() {
  const candidates = [];
  const base64 = process.env.WAFFO_PRIVATE_KEY_BASE64?.trim();
  if (base64) {
    candidates.push(Buffer.from(base64, "base64").toString("utf-8"));
    candidates.push(base64);
  }
  const raw = process.env.WAFFO_PRIVATE_KEY?.trim();
  if (raw) {
    candidates.push(raw);
    candidates.push(Buffer.from(raw, "base64").toString("utf-8"));
  }
  if (candidates.length === 0) {
    throw new Error("缺少 WAFFO_PRIVATE_KEY 或 WAFFO_PRIVATE_KEY_BASE64");
  }
  for (const candidate of candidates) {
    try {
      new WaffoPancake({ merchantId, privateKey: candidate });
      return candidate;
    } catch {
      // 这个形态解析不了，试下一个
    }
  }
  throw new Error(
    "Waffo 私钥无法解析：请粘贴私钥文件的完整内容（PEM 文本或一整串 base64 都可以）",
  );
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
