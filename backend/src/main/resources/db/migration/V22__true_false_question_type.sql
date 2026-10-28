-- 判断题：固定两个选项 T=正确、F=错误，答案只保存 T/F。

SET @has_type_constraint := (
  SELECT COUNT(*)
    FROM information_schema.table_constraints
   WHERE constraint_schema = DATABASE()
     AND table_name = 'question'
     AND constraint_name = 'question_type_chk'
     AND constraint_type = 'CHECK'
);

SET @drop_type_sql := IF(
  @has_type_constraint > 0,
  'ALTER TABLE question DROP CHECK question_type_chk',
  'DO 0'
);

PREPARE drop_type_stmt FROM @drop_type_sql;
EXECUTE drop_type_stmt;
DEALLOCATE PREPARE drop_type_stmt;

ALTER TABLE question
  ADD CONSTRAINT question_type_chk
  CHECK (type IN ('SINGLE', 'MULTI', 'TRUE_FALSE', 'SHORT'));

SET @has_options_constraint := (
  SELECT COUNT(*)
    FROM information_schema.table_constraints
   WHERE constraint_schema = DATABASE()
     AND table_name = 'question'
     AND constraint_name = 'question_options_chk'
     AND constraint_type = 'CHECK'
);

SET @drop_options_sql := IF(
  @has_options_constraint > 0,
  'ALTER TABLE question DROP CHECK question_options_chk',
  'DO 0'
);

PREPARE drop_options_stmt FROM @drop_options_sql;
EXECUTE drop_options_stmt;
DEALLOCATE PREPARE drop_options_stmt;

ALTER TABLE question
  ADD CONSTRAINT question_options_chk
  CHECK (type NOT IN ('SINGLE', 'MULTI', 'TRUE_FALSE') OR options IS NOT NULL);
