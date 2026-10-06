-- AI 配置支持 OpenAI / Anthropic 两种协议
ALTER TABLE ai_config
  ADD COLUMN protocol varchar(16) NOT NULL DEFAULT 'OPENAI' AFTER base_url;

ALTER TABLE ai_config
  ADD CONSTRAINT ai_config_protocol_chk CHECK (protocol IN ('OPENAI', 'ANTHROPIC'));
