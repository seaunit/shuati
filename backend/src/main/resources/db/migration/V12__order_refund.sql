-- 退款处理：记录退款时间与实际扣回的点数
--   refunded_at           非空表示该订单已退款，用于「同一订单重复收到退款事件」的幂等判断
--   refunded_cents        渠道实际退款金额（分），仅作对账参考
--   refund_clawed_points  实际扣回的点数；若用户已把点数花掉，会小于订单点数（差额即损失）
ALTER TABLE subscription_order
  ADD COLUMN refunded_at datetime(6) NULL AFTER paid_at,
  ADD COLUMN refunded_cents int NULL AFTER refunded_at,
  ADD COLUMN refund_clawed_points int NOT NULL DEFAULT 0 AFTER refunded_cents;
