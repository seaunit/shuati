/**
 * 本项目的商品编码（与 Java 侧 plan.code / point_pack.code 对应）。
 * 金额一律用展示金额字符串（USD 传 "19.00"，不是分）。
 */
export const CATALOG = [
  {
    itemCode: "PLAN:plus:MONTHLY",
    name: "Plus 月付",
    type: "subscription",
    billingPeriod: "monthly",
    amount: "19.00",
    taxCategory: "saas",
  },
  {
    itemCode: "PLAN:plus:QUARTERLY",
    name: "Plus 季付",
    type: "subscription",
    billingPeriod: "quarterly",
    amount: "49.00",
    taxCategory: "saas",
  },
  {
    itemCode: "PLAN:plus:YEARLY",
    name: "Plus 年付",
    type: "subscription",
    billingPeriod: "yearly",
    amount: "158.00",
    taxCategory: "saas",
  },
  {
    itemCode: "PLAN:pro:MONTHLY",
    name: "Pro 月付",
    type: "subscription",
    billingPeriod: "monthly",
    amount: "39.00",
    taxCategory: "saas",
  },
  {
    itemCode: "PLAN:pro:QUARTERLY",
    name: "Pro 季付",
    type: "subscription",
    billingPeriod: "quarterly",
    amount: "99.00",
    taxCategory: "saas",
  },
  {
    itemCode: "PLAN:pro:YEARLY",
    name: "Pro 年付",
    type: "subscription",
    billingPeriod: "yearly",
    amount: "328.00",
    taxCategory: "saas",
  },
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
