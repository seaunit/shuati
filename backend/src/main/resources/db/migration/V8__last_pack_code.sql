-- 纯点数制：记录用户最近购买的点数包，用于界面展示（替代“免费版”文案）
ALTER TABLE point_account
  ADD COLUMN last_pack_code varchar(32) NULL AFTER plan_code;

-- 回填历史已支付的点数包订单
UPDATE point_account pa
   SET last_pack_code = (
     SELECT o.item_code
       FROM subscription_order o
      WHERE o.user_id = pa.user_id
        AND o.kind = 'PACK'
        AND o.status = 'PAID'
      ORDER BY o.paid_at DESC
      LIMIT 1
   );
