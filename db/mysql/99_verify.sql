-- 迁移结果校验：行数与外键孤儿检查
USE shuati;

SELECT 'profiles' AS table_name, COUNT(*) AS rows_count FROM profiles
UNION ALL SELECT 'auth_user_archive', COUNT(*) FROM auth_user_archive
UNION ALL SELECT 'bank', COUNT(*) FROM bank
UNION ALL SELECT 'unit', COUNT(*) FROM unit
UNION ALL SELECT 'question', COUNT(*) FROM question
UNION ALL SELECT 'practice_record', COUNT(*) FROM practice_record
UNION ALL SELECT 'practice_session', COUNT(*) FROM practice_session
UNION ALL SELECT 'appeal', COUNT(*) FROM appeal
UNION ALL SELECT 'ai_config', COUNT(*) FROM ai_config
UNION ALL SELECT 'ai_call_log', COUNT(*) FROM ai_call_log
UNION ALL SELECT 'import_task', COUNT(*) FROM import_task
UNION ALL SELECT 'plan', COUNT(*) FROM plan
UNION ALL SELECT 'point_pack', COUNT(*) FROM point_pack
UNION ALL SELECT 'ai_price_rule', COUNT(*) FROM ai_price_rule
UNION ALL SELECT 'billing_config', COUNT(*) FROM billing_config;

SELECT 'bank_orphan' AS check_name, COUNT(*) AS bad_rows
  FROM bank b LEFT JOIN profiles p ON p.id = b.owner_id
 WHERE b.owner_id IS NOT NULL AND p.id IS NULL
UNION ALL
SELECT 'unit_orphan', COUNT(*)
  FROM unit u LEFT JOIN bank b ON b.id = u.bank_id
 WHERE b.id IS NULL
UNION ALL
SELECT 'question_orphan', COUNT(*)
  FROM question q LEFT JOIN unit u ON u.id = q.unit_id
 WHERE u.id IS NULL
UNION ALL
SELECT 'record_user_orphan', COUNT(*)
  FROM practice_record r LEFT JOIN profiles p ON p.id = r.user_id
 WHERE p.id IS NULL
UNION ALL
SELECT 'record_question_orphan', COUNT(*)
  FROM practice_record r LEFT JOIN question q ON q.id = r.question_id
 WHERE q.id IS NULL
UNION ALL
SELECT 'session_orphan', COUNT(*)
  FROM practice_session s LEFT JOIN profiles p ON p.id = s.user_id
 WHERE p.id IS NULL
UNION ALL
SELECT 'import_bank_orphan', COUNT(*)
  FROM import_task t LEFT JOIN bank b ON b.id = t.bank_id
 WHERE t.bank_id IS NOT NULL AND b.id IS NULL;

SELECT COUNT(*) AS users_with_password FROM profiles WHERE password_hash IS NOT NULL;
SELECT COUNT(*) AS enabled_questions FROM question WHERE status = 'ON';
SELECT MAX(LENGTH(source_text)) AS max_source_len FROM import_task;
