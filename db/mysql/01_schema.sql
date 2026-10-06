-- ============================================================
-- 刷题系统 MySQL 8.4 建表脚本
-- 来源：Supabase(PostgreSQL 17) public schema + 仓库 billing 迁移
-- 约定：
--   uuid            -> char(36)
--   jsonb           -> json
--   timestamptz     -> datetime(6)，统一按 UTC 存储
--   boolean         -> tinyint(1)
--   text（长内容）  -> longtext
--   text（索引/名称）-> varchar(255)
-- ============================================================

SET NAMES utf8mb4;
SET time_zone = '+00:00';
SET FOREIGN_KEY_CHECKS = 0;

DROP TABLE IF EXISTS point_ledger;
DROP TABLE IF EXISTS point_account;
DROP TABLE IF EXISTS subscription_order;
DROP TABLE IF EXISTS billing_config;
DROP TABLE IF EXISTS ai_price_rule;
DROP TABLE IF EXISTS point_pack;
DROP TABLE IF EXISTS plan;
DROP TABLE IF EXISTS import_task;
DROP TABLE IF EXISTS ai_call_log;
DROP TABLE IF EXISTS ai_config;
DROP TABLE IF EXISTS appeal;
DROP TABLE IF EXISTS practice_session;
DROP TABLE IF EXISTS practice_record;
DROP TABLE IF EXISTS question;
DROP TABLE IF EXISTS unit;
DROP TABLE IF EXISTS bank;
DROP TABLE IF EXISTS auth_user_archive;
DROP TABLE IF EXISTS profiles;

-- ============ 1. 用户 ============
-- password_hash 来自 Supabase auth.users.encrypted_password（bcrypt）
CREATE TABLE profiles (
  id char(36) NOT NULL,
  email varchar(255) NOT NULL,
  password_hash varchar(255) NULL,
  nickname varchar(255) NULL,
  role varchar(16) NOT NULL DEFAULT 'USER',
  status varchar(16) NOT NULL DEFAULT 'ENABLED',
  created_at datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  last_login_at datetime(6) NULL,
  PRIMARY KEY (id),
  UNIQUE KEY profiles_email_unique (email),
  CONSTRAINT profiles_role_chk CHECK (role IN ('ADMIN', 'USER')),
  CONSTRAINT profiles_status_chk CHECK (status IN ('ENABLED', 'DISABLED'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- Supabase auth.users 原始行归档，便于日后核对
CREATE TABLE auth_user_archive (
  id char(36) NOT NULL,
  email varchar(255) NULL,
  encrypted_password varchar(255) NULL,
  email_confirmed_at datetime(6) NULL,
  created_at datetime(6) NULL,
  updated_at datetime(6) NULL,
  last_sign_in_at datetime(6) NULL,
  raw_user_meta_data json NULL,
  raw_app_meta_data json NULL,
  is_super_admin tinyint(1) NULL,
  PRIMARY KEY (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- ============ 2. 题库 / 单元 / 题目 ============
CREATE TABLE bank (
  id bigint NOT NULL AUTO_INCREMENT,
  name varchar(255) NOT NULL,
  description longtext NULL,
  is_default tinyint(1) NOT NULL DEFAULT 0,
  created_at datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  owner_id char(36) NULL,
  PRIMARY KEY (id),
  -- 私有题库：同一 owner 下名字唯一
  UNIQUE KEY bank_name_owner_unique (owner_id, name),
  -- 说明：PostgreSQL 的两个部分唯一索引无法在 MySQL 上以生成列实现
  -- （带 STORED 生成列的表不能建外键），改由 Spring Boot 服务层保证：
  --   1) 公共题库（owner_id IS NULL）名字全局唯一；
  --   2) 全库最多一个 is_default = 1。
  CONSTRAINT bank_owner_fk FOREIGN KEY (owner_id)
    REFERENCES profiles (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE unit (
  id bigint NOT NULL AUTO_INCREMENT,
  name varchar(255) NOT NULL,
  sort int NOT NULL DEFAULT 0,
  created_at datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  bank_id bigint NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY unit_bank_name_unique (bank_id, name),
  KEY unit_bank_id_idx (bank_id),
  CONSTRAINT unit_bank_fk FOREIGN KEY (bank_id)
    REFERENCES bank (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE question (
  id bigint NOT NULL AUTO_INCREMENT,
  unit_id bigint NOT NULL,
  type varchar(16) NOT NULL,
  content longtext NOT NULL,
  options json NULL,
  answer longtext NOT NULL,
  key_points json NULL,
  difficulty varchar(16) NOT NULL DEFAULT 'MEDIUM',
  explanation longtext NULL,
  images json NULL,
  tags json NULL,
  source_url varchar(2048) NULL,
  status varchar(16) NOT NULL DEFAULT 'ON',
  created_at datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
    ON UPDATE CURRENT_TIMESTAMP(6),
  PRIMARY KEY (id),
  KEY question_unit_id_idx (unit_id),
  KEY question_status_idx (status),
  CONSTRAINT question_unit_fk FOREIGN KEY (unit_id)
    REFERENCES unit (id) ON DELETE RESTRICT,
  CONSTRAINT question_type_chk CHECK (type IN ('SINGLE', 'MULTI', 'SHORT')),
  CONSTRAINT question_difficulty_chk CHECK (difficulty IN ('EASY', 'MEDIUM', 'HARD')),
  CONSTRAINT question_status_chk CHECK (status IN ('ON', 'OFF')),
  CONSTRAINT question_options_chk CHECK (type NOT IN ('SINGLE', 'MULTI') OR options IS NOT NULL),
  CONSTRAINT question_key_points_chk CHECK (type <> 'SHORT' OR key_points IS NOT NULL)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- ============ 3. 练习记录 ============
CREATE TABLE practice_record (
  id bigint NOT NULL AUTO_INCREMENT,
  user_id char(36) NOT NULL,
  question_id bigint NOT NULL,
  user_answer longtext NOT NULL,
  judge_source varchar(16) NOT NULL,
  verdict varchar(16) NOT NULL,
  score smallint NULL,
  ai_feedback json NULL,
  duration_ms int NULL,
  created_at datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (id),
  KEY practice_record_user_id_idx (user_id),
  KEY practice_record_question_id_idx (question_id),
  KEY practice_record_user_question_latest_idx (user_id, question_id, created_at DESC),
  CONSTRAINT practice_record_user_fk FOREIGN KEY (user_id)
    REFERENCES profiles (id) ON DELETE CASCADE,
  CONSTRAINT practice_record_question_fk FOREIGN KEY (question_id)
    REFERENCES question (id) ON DELETE CASCADE,
  CONSTRAINT practice_record_judge_source_chk CHECK (judge_source IN ('LOCAL', 'AI', 'SELF')),
  CONSTRAINT practice_record_verdict_chk CHECK (verdict IN ('CORRECT', 'PARTIAL', 'WRONG', 'PENDING')),
  CONSTRAINT practice_record_score_chk CHECK (score IS NULL OR (score >= 0 AND score <= 10))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE practice_session (
  id bigint NOT NULL AUTO_INCREMENT,
  user_id char(36) NOT NULL,
  scope_type varchar(16) NOT NULL,
  scope_id bigint NULL,
  scope_name varchar(255) NOT NULL DEFAULT '',
  total_questions int NOT NULL DEFAULT 0,
  answered int NOT NULL DEFAULT 0,
  correct int NOT NULL DEFAULT 0,
  partial int NOT NULL DEFAULT 0,
  wrong int NOT NULL DEFAULT 0,
  score int NULL,
  status varchar(16) NOT NULL DEFAULT 'IN_PROGRESS',
  started_at datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  finished_at datetime(6) NULL,
  PRIMARY KEY (id),
  KEY practice_session_user_idx (user_id, started_at DESC),
  CONSTRAINT practice_session_user_fk FOREIGN KEY (user_id)
    REFERENCES profiles (id) ON DELETE CASCADE,
  CONSTRAINT practice_session_scope_type_chk CHECK (scope_type IN ('ALL', 'BANK', 'UNIT')),
  CONSTRAINT practice_session_status_chk CHECK (status IN ('IN_PROGRESS', 'COMPLETED', 'ABANDONED'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- ============ 4. 申诉 ============
CREATE TABLE appeal (
  id bigint NOT NULL AUTO_INCREMENT,
  practice_record_id bigint NOT NULL,
  user_id char(36) NOT NULL,
  reason longtext NULL,
  status varchar(16) NOT NULL DEFAULT 'PENDING',
  admin_note longtext NULL,
  created_at datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  resolved_at datetime(6) NULL,
  PRIMARY KEY (id),
  KEY appeal_practice_record_id_idx (practice_record_id),
  KEY appeal_user_id_idx (user_id),
  CONSTRAINT appeal_practice_record_fk FOREIGN KEY (practice_record_id)
    REFERENCES practice_record (id) ON DELETE CASCADE,
  CONSTRAINT appeal_user_fk FOREIGN KEY (user_id)
    REFERENCES profiles (id) ON DELETE CASCADE,
  CONSTRAINT appeal_status_chk CHECK (status IN ('PENDING', 'RESOLVED', 'REJECTED'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- ============ 5. AI 配置与调用日志 ============
CREATE TABLE ai_config (
  id bigint NOT NULL AUTO_INCREMENT,
  name varchar(255) NOT NULL,
  base_url varchar(512) NOT NULL,
  api_key_encrypted longtext NOT NULL,
  model varchar(128) NOT NULL,
  purpose varchar(16) NOT NULL DEFAULT 'BOTH',
  status varchar(16) NOT NULL DEFAULT 'ENABLED',
  remark longtext NULL,
  created_at datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
    ON UPDATE CURRENT_TIMESTAMP(6),
  PRIMARY KEY (id),
  UNIQUE KEY ai_config_name_key (name),
  CONSTRAINT ai_config_purpose_chk CHECK (purpose IN ('JUDGE', 'EXPLAIN', 'BOTH')),
  CONSTRAINT ai_config_status_chk CHECK (status IN ('ENABLED', 'DISABLED'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE ai_call_log (
  id bigint NOT NULL AUTO_INCREMENT,
  user_id char(36) NULL,
  purpose varchar(16) NOT NULL,
  model varchar(128) NOT NULL,
  question_id bigint NULL,
  prompt_tokens int NOT NULL DEFAULT 0,
  completion_tokens int NOT NULL DEFAULT 0,
  total_tokens int NOT NULL DEFAULT 0,
  success tinyint(1) NOT NULL DEFAULT 1,
  error longtext NULL,
  duration_ms int NULL,
  created_at datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (id),
  KEY ai_call_log_created_at_idx (created_at DESC),
  KEY ai_call_log_purpose_idx (purpose),
  KEY ai_call_log_user_id_idx (user_id),
  CONSTRAINT ai_call_log_user_fk FOREIGN KEY (user_id)
    REFERENCES profiles (id) ON DELETE SET NULL,
  CONSTRAINT ai_call_log_purpose_chk CHECK (purpose IN ('JUDGE', 'EXPLAIN', 'EXTRACT', 'TEST'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- ============ 6. 导入任务 ============
CREATE TABLE import_task (
  id char(36) NOT NULL DEFAULT (uuid()),
  user_id char(36) NULL,
  kind varchar(16) NOT NULL,
  phase varchar(16) NOT NULL DEFAULT 'PARSE',
  status varchar(16) NOT NULL DEFAULT 'PENDING',
  progress int NOT NULL DEFAULT 0,
  total_chunks int NOT NULL DEFAULT 0,
  done_chunks int NOT NULL DEFAULT 0,
  bank_name varchar(255) NOT NULL,
  bank_description longtext NULL,
  source_text longtext NULL,
  source_name varchar(512) NULL,
  items json NULL,
  selected_indexes json NULL,
  bank_id bigint NULL,
  error longtext NULL,
  created_at datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
    ON UPDATE CURRENT_TIMESTAMP(6),
  PRIMARY KEY (id),
  KEY import_task_user_id_idx (user_id, created_at DESC),
  CONSTRAINT import_task_user_fk FOREIGN KEY (user_id)
    REFERENCES profiles (id) ON DELETE CASCADE,
  CONSTRAINT import_task_bank_fk FOREIGN KEY (bank_id)
    REFERENCES bank (id) ON DELETE SET NULL,
  CONSTRAINT import_task_kind_chk CHECK (kind IN ('doc', 'text', 'url')),
  CONSTRAINT import_task_phase_chk CHECK (phase IN ('PARSE', 'READY', 'IMPORT', 'DONE')),
  CONSTRAINT import_task_status_chk CHECK (status IN ('PENDING', 'RUNNING', 'COMPLETED', 'FAILED'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- ============ 7. 商业化：套餐 / 点数 / 订单 ============
CREATE TABLE plan (
  code varchar(32) NOT NULL,
  name varchar(64) NOT NULL,
  tagline varchar(255) NULL,
  description longtext NULL,
  price_monthly_cents int NOT NULL DEFAULT 0,
  price_quarterly_cents int NOT NULL DEFAULT 0,
  price_yearly_cents int NOT NULL DEFAULT 0,
  monthly_points int NOT NULL DEFAULT 0,
  max_banks int NULL,
  max_questions int NULL,
  retention_days int NULL,
  allow_pro_model tinyint(1) NOT NULL DEFAULT 0,
  show_on_pricing tinyint(1) NOT NULL DEFAULT 1,
  features json NOT NULL,
  sort int NOT NULL DEFAULT 0,
  status varchar(16) NOT NULL DEFAULT 'ENABLED',
  created_at datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
    ON UPDATE CURRENT_TIMESTAMP(6),
  PRIMARY KEY (code),
  CONSTRAINT plan_price_monthly_chk CHECK (price_monthly_cents >= 0),
  CONSTRAINT plan_price_quarterly_chk CHECK (price_quarterly_cents >= 0),
  CONSTRAINT plan_price_yearly_chk CHECK (price_yearly_cents >= 0),
  CONSTRAINT plan_monthly_points_chk CHECK (monthly_points >= 0),
  CONSTRAINT plan_max_banks_chk CHECK (max_banks IS NULL OR max_banks >= 0),
  CONSTRAINT plan_max_questions_chk CHECK (max_questions IS NULL OR max_questions >= 0),
  CONSTRAINT plan_retention_chk CHECK (retention_days IS NULL OR retention_days > 0),
  CONSTRAINT plan_status_chk CHECK (status IN ('ENABLED', 'DISABLED'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE point_pack (
  code varchar(32) NOT NULL,
  name varchar(64) NOT NULL,
  price_cents int NOT NULL,
  points int NOT NULL,
  bonus_points int NOT NULL DEFAULT 0,
  sort int NOT NULL DEFAULT 0,
  status varchar(16) NOT NULL DEFAULT 'ENABLED',
  created_at datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
    ON UPDATE CURRENT_TIMESTAMP(6),
  PRIMARY KEY (code),
  CONSTRAINT point_pack_price_chk CHECK (price_cents > 0),
  CONSTRAINT point_pack_points_chk CHECK (points > 0),
  CONSTRAINT point_pack_bonus_chk CHECK (bonus_points >= 0),
  CONSTRAINT point_pack_status_chk CHECK (status IN ('ENABLED', 'DISABLED'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE ai_price_rule (
  purpose varchar(16) NOT NULL,
  points int NOT NULL,
  unit varchar(24) NOT NULL DEFAULT 'CALL',
  description varchar(255) NULL,
  updated_at datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
    ON UPDATE CURRENT_TIMESTAMP(6),
  PRIMARY KEY (purpose),
  CONSTRAINT ai_price_rule_purpose_chk CHECK (purpose IN ('JUDGE', 'EXPLAIN', 'EXTRACT', 'TEST')),
  CONSTRAINT ai_price_rule_unit_chk CHECK (unit IN ('CALL', 'PER_10K_CHARS')),
  CONSTRAINT ai_price_rule_points_chk CHECK (points >= 0)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE billing_config (
  `key` varchar(64) NOT NULL,
  value json NOT NULL,
  description varchar(255) NULL,
  updated_at datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
    ON UPDATE CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`key`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE point_account (
  user_id char(36) NOT NULL,
  plan_code varchar(32) NOT NULL DEFAULT 'free',
  plan_expires_at datetime(6) NULL,
  monthly_quota int NOT NULL DEFAULT 0,
  monthly_used int NOT NULL DEFAULT 0,
  bonus_balance int NOT NULL DEFAULT 0,
  period_start datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  period_end datetime(6) NOT NULL,
  lifetime_granted int NOT NULL DEFAULT 0,
  lifetime_used int NOT NULL DEFAULT 0,
  created_at datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
    ON UPDATE CURRENT_TIMESTAMP(6),
  PRIMARY KEY (user_id),
  KEY point_account_plan_code_idx (plan_code),
  CONSTRAINT point_account_user_fk FOREIGN KEY (user_id)
    REFERENCES profiles (id) ON DELETE CASCADE,
  CONSTRAINT point_account_plan_fk FOREIGN KEY (plan_code)
    REFERENCES plan (code),
  CONSTRAINT point_account_quota_chk CHECK (monthly_quota >= 0),
  CONSTRAINT point_account_used_chk CHECK (monthly_used >= 0),
  CONSTRAINT point_account_bonus_chk CHECK (bonus_balance >= 0)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE point_ledger (
  id bigint NOT NULL AUTO_INCREMENT,
  user_id char(36) NOT NULL,
  delta int NOT NULL,
  bucket varchar(16) NOT NULL,
  balance_after int NOT NULL,
  reason varchar(64) NOT NULL,
  ref_type varchar(64) NULL,
  ref_id varchar(128) NULL,
  note longtext NULL,
  created_at datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (id),
  KEY point_ledger_user_created_idx (user_id, created_at DESC),
  KEY point_ledger_reason_idx (reason),
  CONSTRAINT point_ledger_user_fk FOREIGN KEY (user_id)
    REFERENCES profiles (id) ON DELETE CASCADE,
  CONSTRAINT point_ledger_bucket_chk CHECK (bucket IN ('MONTHLY', 'BONUS'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE subscription_order (
  id char(36) NOT NULL DEFAULT (uuid()),
  user_id char(36) NOT NULL,
  kind varchar(16) NOT NULL,
  item_code varchar(64) NOT NULL,
  period varchar(16) NULL,
  amount_cents int NOT NULL DEFAULT 0,
  points int NOT NULL DEFAULT 0,
  status varchar(16) NOT NULL DEFAULT 'PENDING',
  provider varchar(32) NULL,
  provider_order_id varchar(128) NULL,
  paid_at datetime(6) NULL,
  note longtext NULL,
  created_at datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
    ON UPDATE CURRENT_TIMESTAMP(6),
  PRIMARY KEY (id),
  KEY subscription_order_user_created_idx (user_id, created_at DESC),
  KEY subscription_order_status_idx (status),
  CONSTRAINT subscription_order_user_fk FOREIGN KEY (user_id)
    REFERENCES profiles (id) ON DELETE CASCADE,
  CONSTRAINT subscription_order_kind_chk CHECK (kind IN ('PLAN', 'PACK')),
  CONSTRAINT subscription_order_period_chk CHECK (period IS NULL OR period IN ('MONTHLY', 'QUARTERLY', 'YEARLY')),
  CONSTRAINT subscription_order_status_chk CHECK (status IN ('PENDING', 'PAID', 'CANCELLED', 'REFUNDED')),
  CONSTRAINT subscription_order_amount_chk CHECK (amount_cents >= 0)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

SET FOREIGN_KEY_CHECKS = 1;
