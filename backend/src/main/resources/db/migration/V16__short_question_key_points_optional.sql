-- 简答题的评分要点改为可选。
--
-- 背景：主观题由 AI 判分，模型完全可以根据【题目】+【参考答案】自己提炼出 3~5 条评分要点
-- 再据此打分，要求出题人逐条手写要点属于多余负担（新增题目表单里的「评分要点」已去掉）。
-- 所以放开原来的非空约束：key_points 为 NULL 表示「没有提供，由 AI 现场提炼」；
-- 已存在带要点的题目不受影响，仍按给定要点判分。

-- MySQL 没有 DROP CHECK IF EXISTS，用 information_schema 判断后再执行，
-- 这样迁移可重入：约束已经不存在时不会报错中断部署。
SET @has_constraint := (
  SELECT COUNT(*)
    FROM information_schema.table_constraints
   WHERE constraint_schema = DATABASE()
     AND table_name = 'question'
     AND constraint_name = 'question_key_points_chk'
     AND constraint_type = 'CHECK'
);

SET @drop_sql := IF(
  @has_constraint > 0,
  'ALTER TABLE question DROP CHECK question_key_points_chk',
  'DO 0'
);

PREPARE drop_stmt FROM @drop_sql;
EXECUTE drop_stmt;
DEALLOCATE PREPARE drop_stmt;
