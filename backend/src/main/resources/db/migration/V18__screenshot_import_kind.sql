-- 导入题库新增「截图识别」来源：import_task.kind 的 CHECK 约束要放行 screenshot，
-- 否则插入任务时会被 import_task_kind_chk 拒绝（线上表现为一提交就 500）。
ALTER TABLE import_task DROP CHECK import_task_kind_chk;

ALTER TABLE import_task
  ADD CONSTRAINT import_task_kind_chk
  CHECK (kind IN ('doc', 'text', 'url', 'screenshot'));
