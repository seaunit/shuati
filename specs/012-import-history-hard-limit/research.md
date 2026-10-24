# Research: 导入历史硬性保留 10 条

## Decision 1: 以解析来源作为页面历史计数单位

**Decision**: 只统计 `source_task_id IS NULL` 的记录。

**Rationale**: 页面历史展示的是解析来源，导入执行记录不是独立历史行；按来源计数与用户看到的列表一致。

## Decision 2: 所有状态统一计数

**Decision**: COMPLETED、FAILED、READY、PARSE 都计入 10 条上限。

**Rationale**: 截图中的历史数量包含失败和已完成记录；只限制成功记录无法阻止列表增长。

## Decision 3: 不增加按天无条件删除

**Decision**: 未指定具体天数阈值时，不因时间流逝单独删除；只有超过 10 条才删除最旧记录。

**Rationale**: 猜测天数会误删用户仍可复用的解析快照，而 10 条上限已满足确定性的存储约束。

## Decision 4: 启动清理加任务创建清理

**Decision**: 服务启动后清理所有用户，创建新任务后清理当前用户。

**Rationale**: 启动清理立即使现有页面收敛到 10 条；创建时清理解決长期运行时继续增长的问题。

## Decision 5: 来源删除级联执行记录

**Decision**: 删除来源，依赖 `source_task_id` 外键级联清理执行记录。

**Rationale**: 现有约束已保证关联完整性，不需要重复手写删除逻辑。
