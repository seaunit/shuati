-- 导入支持「导入到已有题库」：解析完成后由用户指定目标题库。
--   target_bank_id 为空  -> 沿用原逻辑，按 bank_name 新建/复用用户自己的同名题库
--   target_bank_id 有值  -> 往这个题库里加题（公共题库只有管理员可以，权限在服务层校验）
-- 注意与 bank_id 区分：bank_id 是导入完成后回写的「实际落到哪个题库」，只作结果记录。
ALTER TABLE import_task
  ADD COLUMN target_bank_id bigint NULL AFTER bank_id,
  ADD CONSTRAINT import_task_target_bank_fk FOREIGN KEY (target_bank_id)
    REFERENCES bank (id) ON DELETE SET NULL;
