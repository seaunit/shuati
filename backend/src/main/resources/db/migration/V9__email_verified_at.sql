ALTER TABLE profiles
  ADD COLUMN email_verified_at datetime(6) NULL AFTER status;
