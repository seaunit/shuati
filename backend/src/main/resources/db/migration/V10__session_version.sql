ALTER TABLE profiles
  ADD COLUMN session_version int NOT NULL DEFAULT 0 AFTER email_verified_at;
