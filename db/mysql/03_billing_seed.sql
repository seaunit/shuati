-- 商业化初始数据（来源：supabase/migrations/20261006000000_billing.sql）
SET NAMES utf8mb4;
SET time_zone = '+00:00';

INSERT INTO plan
  (code, name, tagline, description, price_monthly_cents, price_quarterly_cents,
   price_yearly_cents, monthly_points, max_banks, max_questions, retention_days,
   allow_pro_model, show_on_pricing, features, sort)
VALUES
  ('free', '免费版', '先用起来', '公共题库随便刷，AI 能力有月度额度',
   0, 0, 0, 50, 1, 50, 30, 0, 1,
   JSON_ARRAY('全部公共题库无限刷', '选择题本地判分不限次', '每月 50 点 AI 额度',
              '自建题库 1 个 / 50 题', '练习记录保留 30 天'), 10),
  ('plus', 'Plus', '备考主力', 'AI 批改与题库生成的主力档位',
   1900, 4900, 15800, 1000, 20, 2000, NULL, 0, 1,
   JSON_ARRAY('免费版全部权益', '每月 1000 点 AI 额度', '自建题库 20 个 / 2000 题',
              '练习记录与错题本永久保留', '导出错题与笔记'), 20),
  ('pro', 'Pro', '冲刺提速', '高频刷题与大规模文档导入',
   3900, 9900, 32800, 3000, NULL, NULL, NULL, 1, 1,
   JSON_ARRAY('Plus 全部权益', '每月 3000 点 AI 额度', '自建题库与题量不限',
              '可选 deepseek-v4-pro 模型', '详细掌握度报告'), 30),
  ('team', '机构版', '班级与机构', '培训机构、学校、企业内训批量使用',
   0, 0, 0, 0, NULL, NULL, NULL, 1, 1,
   JSON_ARRAY('Pro 全部权益', '批量账号与班级管理', '机构题库共享给学员',
              '班级数据看板', '专属支持'), 40)
ON DUPLICATE KEY UPDATE code = VALUES(code);

INSERT INTO point_pack (code, name, price_cents, points, bonus_points, sort)
VALUES
  ('pack_9', '轻量加量包', 900, 400, 0, 10),
  ('pack_29', '常用加量包', 2900, 1400, 0, 20),
  ('pack_99', '超值加量包', 9900, 5000, 0, 30)
ON DUPLICATE KEY UPDATE code = VALUES(code);

INSERT INTO ai_price_rule (purpose, points, unit, description)
VALUES
  ('EXPLAIN', 1, 'CALL', '选择题 AI 解析，每次 1 点'),
  ('JUDGE', 3, 'CALL', '简答题 / 伪代码 AI 判分，每次 3 点'),
  ('EXTRACT', 10, 'PER_10K_CHARS', '文档 / 网页 / 正文 AI 解析成题库，每 1 万字 10 点'),
  ('TEST', 0, 'CALL', '后台连通性测试不扣点')
ON DUPLICATE KEY UPDATE purpose = VALUES(purpose);

INSERT INTO billing_config (`key`, value, description)
VALUES ('signup_bonus_points', JSON_EXTRACT('100', '$'), '新用户注册赠送点数')
ON DUPLICATE KEY UPDATE `key` = VALUES(`key`);
