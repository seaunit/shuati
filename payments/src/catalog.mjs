/**
 * 本项目的在售商品（与 Java 侧 point_pack.code 对应）。
 *
 * 商业模式为纯点数制：只卖一次性点数包，套餐不开放自助购买
 * （Java 侧 BillingService.createOrder 会直接拒绝 PLAN）。因此这里不再列出订阅商品。
 * 金额一律用展示金额字符串（"9.00"，不是分）。
 * 币种由 WAFFO_CURRENCY 决定，默认 CNY。
 */
export const CURRENCY = (process.env.WAFFO_CURRENCY?.trim() || "CNY").toUpperCase();

/** 在售：3 个一次性点数包 */
export const CATALOG = [
  {
    itemCode: "PACK:pack_9",
    name: "轻量加量包 400 点",
    type: "onetime",
    amount: "9.00",
    taxCategory: "digital_goods",
  },
  {
    itemCode: "PACK:pack_29",
    name: "常用加量包 1400 点",
    type: "onetime",
    amount: "29.00",
    taxCategory: "digital_goods",
  },
  {
    itemCode: "PACK:pack_99",
    name: "超值加量包 5000 点",
    type: "onetime",
    amount: "99.00",
    taxCategory: "digital_goods",
  },
];

/**
 * 历史遗留的 6 个订阅商品：已在 Waffo 后台置为 inactive，不再售卖。
 * 保留清单只是为了能在将来需要时把它们恢复成 active（见 disable-subscriptions.mjs）。
 */
export const LEGACY_SUBSCRIPTIONS = [
  {
    itemCode: "PLAN:plus:MONTHLY",
    name: "Plus 月付",
    billingPeriod: "monthly",
    amount: "19.00",
  },
  {
    itemCode: "PLAN:plus:QUARTERLY",
    name: "Plus 季付",
    billingPeriod: "quarterly",
    amount: "49.00",
  },
  {
    itemCode: "PLAN:plus:YEARLY",
    name: "Plus 年付",
    billingPeriod: "yearly",
    amount: "158.00",
  },
  {
    itemCode: "PLAN:pro:MONTHLY",
    name: "Pro 月付",
    billingPeriod: "monthly",
    amount: "39.00",
  },
  {
    itemCode: "PLAN:pro:QUARTERLY",
    name: "Pro 季付",
    billingPeriod: "quarterly",
    amount: "99.00",
  },
  {
    itemCode: "PLAN:pro:YEARLY",
    name: "Pro 年付",
    billingPeriod: "yearly",
    amount: "328.00",
  },
];
