-- 导入记录复用：
--   source_task_id 为空 = 保留 items 的解析来源
--   source_task_id 非空 = 一次实际导入执行记录，引用来源但不复制 items
ALTER TABLE import_task
  ADD COLUMN source_task_id char(36) NULL AFTER user_id,
  ADD COLUMN created_count int NOT NULL DEFAULT 0 AFTER target_bank_id,
  ADD COLUMN skipped_count int NOT NULL DEFAULT 0 AFTER created_count,
  ADD KEY import_task_source_idx (source_task_id, updated_at),
  ADD KEY import_task_user_completed_idx (user_id, source_task_id, status, updated_at),
  ADD CONSTRAINT import_task_source_fk FOREIGN KEY (source_task_id)
    REFERENCES import_task (id) ON DELETE CASCADE;

-- 旧版本把“解析”和“导入”复用在同一行。先把成功导入回填成执行记录，
-- 再把原行恢复为可复用的 READY 来源，避免再次导入覆盖历史。
INSERT INTO import_task
  (id, user_id, source_task_id, kind, phase, status, progress,
   total_chunks, done_chunks, bank_name, bank_description, selected_indexes,
   bank_id, target_bank_id, created_count, skipped_count, created_at, updated_at)
SELECT UUID(), t.user_id, t.id, t.kind, 'DONE', 'COMPLETED', 100,
       t.total_chunks, t.done_chunks, t.bank_name, t.bank_description,
       t.selected_indexes, t.bank_id, t.target_bank_id,
       CASE
         WHEN t.selected_indexes IS NULL OR JSON_LENGTH(t.selected_indexes) = 0
           THEN COALESCE(JSON_LENGTH(t.items), 0)
         ELSE JSON_LENGTH(t.selected_indexes)
       END,
       0, t.created_at, t.updated_at
  FROM import_task t
 WHERE t.source_task_id IS NULL
   AND t.phase = 'DONE'
   AND t.status = 'COMPLETED';

UPDATE import_task
   SET phase = 'READY'
 WHERE source_task_id IS NULL
   AND phase = 'DONE'
   AND items IS NOT NULL;

-- 题干哈希用于目标题库内去重。旧题按与 Java 端一致的“折叠连续空白”规则回填。
ALTER TABLE question
  ADD COLUMN content_hash char(64) NULL AFTER content,
  ADD KEY question_content_hash_idx (content_hash);

UPDATE question
   SET content_hash = SHA2(REGEXP_REPLACE(TRIM(content), '[[:space:]]+', ' '), 256)
 WHERE content_hash IS NULL;
