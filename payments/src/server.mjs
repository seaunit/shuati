import http from "node:http";
import { existsSync } from "node:fs";
import { fileURLToPath } from "node:url";

// 先加载 .env，再动态 import SDK 封装（SDK 在构造时就会读环境变量）
const envPath = fileURLToPath(new URL("../.env", import.meta.url));
if (existsSync(envPath)) {
  try {
    process.loadEnvFile(envPath);
  } catch (e) {
    console.warn("[payments] 加载 .env 失败：", e.message);
  }
}

const { client, productIdOf, sdk, environment } = await import("./waffo.mjs");
const { WebhookEventType } = sdk;

const PORT = Number(process.env.PORT || 8090);
const JAVA_BASE_URL = (process.env.JAVA_BASE_URL || "http://127.0.0.1:8080").replace(/\/+$/, "");
const INTERNAL_SECRET = process.env.INTERNAL_SECRET || "";
const SUCCESS_URL = process.env.CHECKOUT_SUCCESS_URL || undefined;
/** 没有真实私钥时可用于本地联调：返回假的 checkoutUrl，不调用 Waffo */
const DRY_RUN = process.env.WAFFO_DRY_RUN === "true";

function json(res, status, payload) {
  const body = JSON.stringify(payload);
  res.writeHead(status, {
    "content-type": "application/json; charset=utf-8",
    "content-length": Buffer.byteLength(body),
  });
  res.end(body);
}

function readRawBody(req) {
  return new Promise((resolve, reject) => {
    const chunks = [];
    req.on("data", (chunk) => chunks.push(chunk));
    req.on("end", () => resolve(Buffer.concat(chunks)));
    req.on("error", reject);
  });
}

function requireInternal(req, res) {
  if (!INTERNAL_SECRET || req.headers["x-internal-secret"] !== INTERNAL_SECRET) {
    json(res, 401, { ok: false, message: "unauthorized" });
    return false;
  }
  return true;
}

/** 创建收银台会话：只有 Java 后端能调（带共享密钥） */
async function handleCheckout(req, res) {
  if (!requireInternal(req, res)) {
    return;
  }
  const raw = await readRawBody(req);
  let body;
  try {
    body = JSON.parse(raw.toString("utf-8") || "{}");
  } catch {
    json(res, 400, { ok: false, message: "invalid json" });
    return;
  }

  const {
    orderId,
    itemCode,
    currency = "CNY",
    buyerIdentity,
    buyerEmail,
    amount,
    successUrl,
  } = body;
  if (!orderId || !itemCode || !buyerIdentity) {
    json(res, 400, { ok: false, message: "orderId / itemCode / buyerIdentity 必填" });
    return;
  }

  const productId = body.productId || productIdOf(itemCode);
  if (!productId) {
    json(res, 400, {
      ok: false,
      message: `未配置商品映射：${itemCode}（请设置 WAFFO_PRODUCT_MAP）`,
    });
    return;
  }

  if (DRY_RUN) {
    const fake = `https://pancake.waffo.ai/store/dry-run/checkout/${orderId}#token=DRYRUN`;
    json(res, 200, { ok: true, checkoutUrl: fake, sessionId: `cs_dry_${orderId}`, dryRun: true });
    return;
  }

  try {
    const session = await client.checkout.authenticated.create({
      productId,
      currency,
      buyerIdentity,
      buyerEmail,
      successUrl: successUrl || SUCCESS_URL,
      orderMerchantExternalId: orderId,
      // metadata 会原样透传到 webhook 的 event.data，便于回查订单
      metadata: { orderId, itemCode },
      ...(amount ? { priceSnapshot: { amount, taxCategory: "saas" } } : {}),
    });
    json(res, 200, {
      ok: true,
      checkoutUrl: session.checkoutUrl,
      sessionId: session.sessionId,
      expiresAt: session.expiresAt,
    });
  } catch (e) {
    // 注意：不要把私钥或完整请求体写进日志
    console.error("[payments] 创建收银台失败：", e?.status, e?.message);
    json(res, 502, { ok: false, message: e?.message || "checkout failed" });
  }
}

/** Waffo 回调：必须用原始 body 验签，验签通过后转发给 Java 结算订单 */
async function handleWebhook(req, res) {
  const raw = await readRawBody(req);
  const signature = req.headers["x-waffo-signature"];
  if (!signature) {
    json(res, 401, { ok: false, message: "missing signature" });
    return;
  }

  let event;
  try {
    event = sdk.verifyWebhook(raw.toString("utf-8"), String(signature), { environment });
  } catch (e) {
    console.warn("[payments] webhook 验签失败：", e.message);
    json(res, 401, { ok: false, message: "invalid signature" });
    return;
  }

  const data = event.data || {};
  const orderId =
    data.metadata?.orderId || data.orderMerchantExternalId || data.externalOrderId || null;

  console.log(
    `[payments] webhook ${event.eventType} mode=${event.mode} order=${orderId ?? "unknown"} delivery=${event.id}`,
  );

  if (!orderId) {
    // 拿不到内部订单号时也回 200，避免平台无限重试；人工去后台核对
    json(res, 200, { ok: true, ignored: "no orderId" });
    return;
  }

  try {
    const resp = await fetch(`${JAVA_BASE_URL}/api/payments/waffo/notify`, {
      method: "POST",
      headers: {
        "content-type": "application/json",
        "x-internal-secret": INTERNAL_SECRET,
        // Java 侧自定义安全头校验（防 CSRF），服务间调用同样要带
        "x-requested-with": "ShuatiApp",
      },
      body: JSON.stringify({
        orderId,
        eventType: event.eventType,
        eventId: event.eventId,
        deliveryId: event.id,
        mode: event.mode,
        amount: data.amount,
        currency: data.currency,
      }),
    });
    // 404 = 本地查不到该订单（例如后台的测试事件），回 200 让平台停止重试
    if (resp.status === 404) {
      console.log(`[payments] 忽略未知订单 ${orderId}（${event.eventType}）`);
      json(res, 200, { ok: true, ignored: "order not found" });
      return;
    }
    if (!resp.ok) {
      throw new Error(`java notify ${resp.status}`);
    }
    json(res, 200, { ok: true });
  } catch (e) {
    // 返回非 2xx，让 Waffo 重试
    console.error("[payments] 转发结算失败：", e.message);
    json(res, 500, { ok: false, message: "notify failed" });
  }
}

const server = http.createServer(async (req, res) => {
  const url = new URL(req.url || "/", "http://localhost");
  try {
    if (req.method === "GET" && url.pathname === "/health") {
      json(res, 200, { ok: true, environment, dryRun: DRY_RUN });
      return;
    }
    if (req.method === "POST" && url.pathname === "/checkout") {
      await handleCheckout(req, res);
      return;
    }
    if (req.method === "POST" && url.pathname === "/webhooks/waffo") {
      await handleWebhook(req, res);
      return;
    }
    json(res, 404, { ok: false, message: "not found" });
  } catch (e) {
    console.error("[payments] 未处理异常：", e);
    if (!res.headersSent) {
      json(res, 500, { ok: false, message: "internal error" });
    }
  }
});

server.listen(PORT, "127.0.0.1", () => {
  console.log(
    `[payments] listening on 127.0.0.1:${PORT} env=${environment} dryRun=${DRY_RUN} webhookEvents=${Object.keys(WebhookEventType).length}`,
  );
});
