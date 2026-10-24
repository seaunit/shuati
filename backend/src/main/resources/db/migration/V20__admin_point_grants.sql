-- 管理员赠点审计：每次赠送必须留下操作人、原因、目标用户和对应点数流水。
CREATE TABLE admin_point_grant (
  id char(36) NOT NULL,
  admin_id char(36) NULL,
  admin_email varchar(255) NOT NULL,
  user_id char(36) NOT NULL,
  idempotency_key varchar(64) NOT NULL,
  points int NOT NULL,
  reason varchar(100) NOT NULL,
  ledger_id bigint NOT NULL,
  created_at datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (id),
  UNIQUE KEY admin_point_grant_admin_key_unique (admin_id, idempotency_key),
  UNIQUE KEY admin_point_grant_ledger_unique (ledger_id),
  KEY admin_point_grant_user_created_idx (user_id, created_at DESC),
  CONSTRAINT admin_point_grant_admin_fk FOREIGN KEY (admin_id)
    REFERENCES profiles (id) ON DELETE SET NULL,
  CONSTRAINT admin_point_grant_user_fk FOREIGN KEY (user_id)
    REFERENCES profiles (id) ON DELETE CASCADE,
  CONSTRAINT admin_point_grant_ledger_fk FOREIGN KEY (ledger_id)
    REFERENCES point_ledger (id) ON DELETE CASCADE,
  CONSTRAINT admin_point_grant_points_chk CHECK (points BETWEEN 1 AND 1000000),
  CONSTRAINT admin_point_grant_reason_chk CHECK (CHAR_LENGTH(reason) BETWEEN 2 AND 100)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
