-- 答案解析区问 AI：独立用途、独立计费并可单独审计。

SET @has_log_purpose := (
  SELECT COUNT(*)
    FROM information_schema.table_constraints
   WHERE constraint_schema = DATABASE()
     AND table_name = 'ai_call_log'
     AND constraint_name = 'ai_call_log_purpose_chk'
     AND constraint_type = 'CHECK'
);

SET @drop_log_purpose_sql := IF(
  @has_log_purpose > 0,
  'ALTER TABLE ai_call_log DROP CHECK ai_call_log_purpose_chk',
  'DO 0'
);
PREPARE drop_log_purpose_stmt FROM @drop_log_purpose_sql;
EXECUTE drop_log_purpose_stmt;
DEALLOCATE PREPARE drop_log_purpose_stmt;

ALTER TABLE ai_call_log
  ADD CONSTRAINT ai_call_log_purpose_chk
  CHECK (purpose IN ('JUDGE', 'EXPLAIN', 'ASK', 'EXTRACT', 'TEST'));

SET @has_rule_purpose := (
  SELECT COUNT(*)
    FROM information_schema.table_constraints
   WHERE constraint_schema = DATABASE()
     AND table_name = 'ai_price_rule'
     AND constraint_name = 'ai_price_rule_purpose_chk'
     AND constraint_type = 'CHECK'
);

SET @drop_rule_purpose_sql := IF(
  @has_rule_purpose > 0,
  'ALTER TABLE ai_price_rule DROP CHECK ai_price_rule_purpose_chk',
  'DO 0'
);
PREPARE drop_rule_purpose_stmt FROM @drop_rule_purpose_sql;
EXECUTE drop_rule_purpose_stmt;
DEALLOCATE PREPARE drop_rule_purpose_stmt;

ALTER TABLE ai_price_rule
  ADD CONSTRAINT ai_price_rule_purpose_chk
  CHECK (purpose IN ('JUDGE', 'EXPLAIN', 'ASK', 'EXTRACT', 'TEST'));

INSERT INTO ai_price_rule (purpose, points, unit, description)
VALUES ('ASK', 1, 'CALL', '题目追问与知识扩展，每次 1 点')
ON DUPLICATE KEY UPDATE purpose = VALUES(purpose);
