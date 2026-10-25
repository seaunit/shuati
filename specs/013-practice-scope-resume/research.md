# Research: 刷题入口恢复上次练习范围

## Decision: 复用服务端练习记录，不在本地保存最近范围

**Rationale**: 练习记录已经包含账号、范围类型、范围 id 和开始时间，可以直接恢复；
服务端存储还能保证 Web 与 Android App 看到同一范围。

**Alternatives considered**:

- 浏览器 `localStorage`：实现简单，但换设备或 App 与 Web 切换后会分叉，不满足跨端一致。
- 仅扩大前端读取 `/api/practice/sessions` 的条数：当前接口是统计用途且有 50 条限制，
  长期会产生边界问题，也不适合把业务恢复规则塞进展示列表。

## Decision: 新增“最近有效范围”查询，忽略 ALL 与错题重练

**Rationale**: 用户当前进入裸入口会生成 `ALL` 练习记录。若简单取最新一条，仍会落到全量题；
必须按范围类型筛选最近一次 `BANK` 或 `UNIT`，并在服务端校验题库当前可见。

**Alternatives considered**:

- 前端取最新记录：仍会被大量 `ALL` 记录抢占，且不能可靠过滤已删除范围。
- 修改现有统计列表接口：会改变统计语义，并扩大无关接口职责。

## Decision: 前端路由进入裸练习页前补范围参数

**Rationale**: 练习页已经完整支持 `bankId`、`unitId`、`ids`，补参数后可自然复用题目加载、
进度、光标和会话逻辑；URL 也能反映真实范围，刷新和跨端行为一致。

**Alternatives considered**:

- 在 `PracticeView` 内切换范围：需要先加载全量题再二次请求，慢且容易产生错误会话。
- 改侧栏为动态链接：只解决点击侧栏，不解决直接打开裸入口，且状态同步复杂。
