-- 练习光标：记住「当前刷到哪道题」。
--
-- 背景：原来的进度是按 practice_record 推出来的「第一道没答过的题」，
-- 于是位置记不住 —— 用户点「从头开始」回到第 1 题、练到第 2 题退出，
-- 再进来会跳回第一道没答过的那题（可能是第 10 题），而不是第 2 题；
-- 「上一题」同理。只要题目以前答过，位置就不算数。
--
-- 现在按 (账号, 范围) 记一个光标：客户端每切一题就更新它，进入练习时优先按光标落位；
-- 没有光标时才回退到老的「第一道没答过的题」规则，历史进度不受影响。
--
-- 范围与既有 scope 保持一致：ALL 用 scope_id = 0，BANK/UNIT 用对应 id。
-- 题目被删除时光标随外键级联删除；被下架时光标还在，客户端定位不到会自动回退。

CREATE TABLE practice_cursor (
  user_id char(36) NOT NULL,
  scope_type varchar(16) NOT NULL,
  scope_id bigint NOT NULL DEFAULT 0,
  question_id bigint NOT NULL,
  updated_at datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
    ON UPDATE CURRENT_TIMESTAMP(6),
  PRIMARY KEY (user_id, scope_type, scope_id),
  CONSTRAINT practice_cursor_user_fk FOREIGN KEY (user_id)
    REFERENCES profiles (id) ON DELETE CASCADE,
  CONSTRAINT practice_cursor_question_fk FOREIGN KEY (question_id)
    REFERENCES question (id) ON DELETE CASCADE,
  CONSTRAINT practice_cursor_scope_type_chk CHECK (scope_type IN ('ALL', 'BANK', 'UNIT'))
);
