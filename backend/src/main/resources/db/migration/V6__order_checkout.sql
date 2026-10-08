-- 在线支付：记录收银台地址与会话号（Waffo Pancake）
ALTER TABLE subscription_order
  ADD COLUMN checkout_url varchar(1024) NULL AFTER status,
  ADD COLUMN provider_session_id varchar(128) NULL AFTER provider_order_id;
