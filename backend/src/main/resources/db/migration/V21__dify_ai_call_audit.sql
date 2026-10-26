ALTER TABLE ai_call_log
  ADD COLUMN provider varchar(16) NOT NULL DEFAULT 'DIRECT' AFTER purpose,
  ADD COLUMN workflow_key varchar(64) NULL AFTER provider,
  ADD COLUMN external_run_id varchar(128) NULL AFTER workflow_key,
  ADD KEY ai_call_log_provider_idx (provider),
  ADD KEY ai_call_log_external_run_idx (external_run_id);
