-- 自建题库数量改为平台统一上限（20 个），不再按套餐区分。
--
-- 判断依据：题库数量属于「防滥用限制」而不是付费权益 —— 有人批量建空库会拖累后台，
-- 但正常用户建到十几个就已经很多了。付费分层继续用点数、题量与模型区分即可。
-- 代码里真正的校验读的是 shuati.limits.max-banks（默认 20），
-- 这里把 plan 表的展示数据与定价页文案同步过来，避免页面还在宣传「1 个 / 20 个」的旧差异。

UPDATE plan SET max_banks = 20 WHERE max_banks IS NULL OR max_banks <> 20;

UPDATE plan
   SET features = JSON_SET(
         features,
         '$[3]',
         CONCAT('自建题库最多 20 个 / 合计 ', COALESCE(max_questions, 0), ' 题'))
 WHERE code = 'free';

UPDATE plan
   SET features = JSON_SET(
         features,
         '$[2]',
         CONCAT('自建题库最多 20 个 / 合计 ', COALESCE(max_questions, 0), ' 题'))
 WHERE code = 'plus';

UPDATE plan
   SET features = JSON_SET(features, '$[2]', '自建题库最多 20 个 / 题量不限')
 WHERE code = 'pro';
