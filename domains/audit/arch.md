# Audit 核心设计
`audit` 是面向管理员的业务操作审计模块。它把“谁在什么请求中，对哪些业务对象做了什么”保存为可检索的事实记录，并通过稳定的 RPC 契约提供查询能力。

审计记录不是技术日志的替代品：业务审计强调操作人、业务动作和资源；异常堆栈、性能、调用拓扑仍由可观测性模块负责。

## 1. 职责与边界

### 1.1 负责

- 定义业务模块接入审计所需的最小契约：`@Audited`、`Loggable`、`AuditAction`。
- 在被标记的业务方法**成功返回后**，从方法参数中提取审计片段并聚合为一条操作记录。
- 记录操作人快照、稳定操作名、资源动作、资源标识、可读摘要以及 Trace/Request 标识。
- 将审计事实持久化到 `audit_operation_log`。
- 提供简单列表查询和管理员分页检索 RPC。
- 将分页查询映射为全局 `list_audit_logs` 权限检查。

### 1.2 不负责

- 不记录失败调用；失败原因由技术日志和 Trace 解释。
- 不定义各业务模块的资源语义。动作、资源类型、资源 ID 和摘要由资源所属模块提供 Handler。
- 不负责登录态生成、权限模型和链路上下文传播，只消费这些模块提供的当前上下文。
- 不保存请求体、响应体、密码、Token、密钥、完整二进制数据或导入文件内容。
- 不保证审计写入与业务数据强一致，也不因审计存储失败回滚已成功的业务操作。
- 不提供审计记录修改、删除或归档接口；当前模型把记录视为追加事实。

## 2. 模块组成

```text
domains/audit
├── api                         跨模块稳定契约
│   ├── @Audited               方法级审计入口
│   ├── Loggable               可被审计的命令/事件
│   ├── AuditAction            CREATE / EDIT / DELETE
│   ├── model                  查询、分页和展示 DTO
│   └── service                AuditLogService RPC 契约
└── service                     审计运行时实现
    ├── aspect                 成功调用拦截与聚合
    ├── handler                类型分派与片段转换
    ├── model                  内部 AuditEvent
    ├── persistence            Store、Entity、Mapper
    ├── service                查询实现
    └── config                 自动装配与查询授权映射
```

`api` 只依赖公共 RPC 基础，使业务 API 可以声明 `Loggable` 而不引入切面、MyBatis 或授权实现。`service` 承担 Spring AOP、Dubbo、鉴权接入和 MySQL 持久化。

## 3. 写入调用链

```text
业务方法 @Audited(operation)
        │
        ├─ proceed() 抛异常 ───────────────► 不写成功审计，异常原样抛出
        │
        └─ proceed() 成功
              │
              ├─ 读取 UserContextHolder
              │    └─ 无有效 userId：告警并跳过
              │
              ├─ 遍历全部方法参数
              │    └─ AuditHandlerRegistry 按精确运行时类型匹配 Handler
              │          └─ 生成 0..N 个 AuditFragment
              │
              ├─ 无片段：跳过
              │
              └─ 聚合 AuditEvent + TraceContext
                    └─ AuditLogStore.append
                          ├─ 成功：插入 audit_operation_log
                          └─ 失败：记录 error，不改变业务返回值
```

### 3.1 方法级操作

`@Audited("timetable.import")` 的值是方法级 `operation`，应使用稳定、可筛选的业务标识。若留空，切面退化为 `声明类简单名.方法名`；这适合兜底，不适合长期对外查询，因为重构会改变值。

切面先执行 `joinPoint.proceed()`，因此：

- 只有正常返回的业务方法才产生成功审计；
- Handler 同时收到原始参数和业务返回值；
- 业务异常不会被审计层吞掉或改写。

### 3.2 参数级片段

实现 `Loggable` 只表明对象具备可读摘要，并不会自动写入审计。参数必须同时存在唯一的 `AuditLogHandler<T>`，Registry 才能把它转换为：

| 字段 | 来源 | 含义 |
| --- | --- | --- |
| `action` | Handler | `CREATE`、`EDIT` 或 `DELETE` |
| `objectType` | Handler | 稳定的资源类型，如 `laboratory` |
| `objectId` | Handler | 被操作资源 ID |
| `eventType` | `Loggable.eventType()` | 默认是事件 Java 全限定类名 |
| `description` | `Loggable.log()` | 面向管理员的短摘要 |

Registry 按参数的**精确运行时类**查找，不做父类或接口多态匹配；未知参数和 `null` 会被忽略。同一事件类型注册两个 Handler 会在 Registry 初始化时直接失败，避免审计语义不确定。

一个方法可携带多个可识别参数。它们会聚合到同一个 `AuditEvent`，表达“一次业务操作涉及多个资源”，而不是生成多条互相孤立的记录。创建操作调用前通常没有 ID，Handler 可覆盖 `objectId(event, result)`，从成功返回值中补齐新资源 ID。

### 3.3 上下文快照

切面成功返回后读取当前 `UserContextHolder`，保存 `userId`、用户名和显示名称。`userId` 缺失时不会生成匿名审计，只记录跳过告警。用户字段是操作发生时的快照，之后修改用户资料不反向更新历史审计。

`TraceContext.traceId()` 和 `requestId()` 被一并保存，用于从业务审计跳转到技术调用链。审计模块只读取它们，不创建或传播上下文。

## 4. 持久化模型

`AuditLogStore` 是写入端口，默认实现 `MybatisAuditLogStore`。自动装配允许应用以自定义 Bean 替换 Store；默认实现使用 `AuditLogMapper` 插入 MySQL 表 `audit_operation_log`，ID 为随机 UUID。

一条数据库记录对应一次被审计的方法调用。多个片段按列压平：

- `actions`、`object_types`、`object_ids`、`event_types` 使用逗号连接并去重；
- `description` 以操作人显示名（依次回退到用户名、用户 ID）开头，各片段摘要用中文分号连接；
- 空白片段字段在连接时被过滤；
- `occurred_at` 使用服务成功返回后生成的本地时间。

表在 `sql/schema.sql` 中定义，并为“操作人 + 时间”“操作 + 时间”、`trace_id` 和 `request_id` 建索引。表不声明指向业务表或用户表的外键，以保留资源删除、用户变更后的历史事实。

当前集合字段是面向展示与轻量筛选的反规范化字符串，`LIKE` 查询也可能匹配部分文本。若未来需要对单个资源进行高基数、严格等值或统计查询，应演进为“操作主表 + 片段明细表”，而不是继续扩大分隔字符串。

## 5. 失败策略与一致性

当前策略明确偏向业务可用性：

- 业务方法失败：不写成功审计，业务异常继续向上传递。
- 缺少有效用户上下文：业务结果照常返回，审计层输出 `audit_skipped` 告警。
- 没有任何参数 Handler：业务结果照常返回，不落库。
- Handler 转换异常：发生在持久化保护块之前，会影响调用返回；接入方必须保证 Handler 是确定、轻量且不会失败的纯转换。
- `AuditLogStore.append` 抛出运行时异常：切面记录 `audit_persist_failure`，业务结果照常返回。

因此审计写入是 **best effort**，不是与业务事务绑定的强一致操作。若合规场景要求不可丢失，应在该端口后引入事务消息、Outbox 或可靠事件链路，并明确重试、幂等和告警规则。

## 6. 查询设计

`AuditLogService` 以 Dubbo 服务暴露两种读取方式：

- `query(AuditLogQuery)`：轻量列表查询，可按操作人、动作、资源类型筛选；默认最多 100 条，调用方传入的上限被限制在 500 条。
- `page(AuditLogPageQuery)`：管理员检索入口，默认第 1 页、每页 20 条，单页最多 100 条。

分页查询支持操作人 ID/用户名/显示名、精确 `operation`、动作、资源类型与 ID、事件类型、描述、Trace ID、Request ID 和发生时间区间。结果固定按 `occurred_at DESC, id DESC` 排序，第二排序键保证同一时间点翻页顺序稳定。

`page` 标记 `@ActionAuthorized`，对应 Handler 将查询映射到全局应用资源上的 `list_audit_logs` 动作。Web 模块仅把 `GET /api/audit-logs` 的查询参数组装为 `AuditLogPageQuery` 并调用 RPC；HTTP 表达、用户认证以及角色如何获得该动作，不属于 audit 的内部设计。

注意：当前权限保护显式覆盖 `page`；`query` 是供受控内部调用方使用的较低层能力。新增外部入口应优先调用 `page`，或为其建立独立、清晰的授权契约。

## 7. 自动装配与配置

`AuditAutoConfiguration` 通过 Spring Boot 自动装配导入，并扫描审计 Mapper。配置开关为：

```yaml
lab:
  audit:
    enabled: true
```

该开关缺省为 `true`。关闭后不会创建 Registry、默认 Store 和切面，同时也不会导入审计查询的授权 Handler。Registry、Store 与 Aspect 均使用 `@ConditionalOnMissingBean`，宿主应用可替换默认实现。

业务服务若要产生审计，必须让该自动配置进入 Spring 上下文，并提供它所依赖的用户上下文、Trace 上下文、MyBatis 会话工厂和业务模块 Handler。接入行为是否生效应以集成测试或实际落库验证为准，不能仅以存在 `@Audited` 判断。

## 8. 业务模块接入契约

资源所属模块只需在自己的边界内完成四件事：

1. 让写命令实现 `Loggable`，摘要简短、确定且不包含敏感信息。
2. 注册唯一的 `AuditLogHandler<Command>`，定义动作、资源类型和资源 ID。
3. 在成功写入口添加带稳定 operation 的 `@Audited`。
4. 测试 Handler 映射；创建类操作还要验证能否从返回值补齐 ID，方法级测试需验证最终产生一条聚合记录。

审计模块不应反向依赖具体业务 DTO。各业务模块可以依赖 `audit-api` 声明契约；需要注册 Handler 的 Provider 依赖 audit service 的扩展类型。新增动作枚举会影响所有消费者，应优先复用通用动作，把更具体的语义放在 `operation` 与资源类型中。

安全约束：摘要不得包含密码、Session/Cookie/Authorization、AES 或基础设施凭据、完整设备 payload、完整文件内容。批量操作只记录目标范围、动作名称、数量和结果摘要。

## 9. 扩展点与演进边界

- **新增业务资源**：由资源所属模块新增 `Loggable` 与 Handler，不修改 Registry 分派逻辑。
- **替换存储**：实现 `AuditLogStore` 并注册 Bean；需自行保证查询端与新存储的一致性，或同步替换查询实现。
- **增加动作**：修改 `AuditAction` 属于公共契约变更，应评估所有展示和筛选消费者。
- **异步可靠写入**：应保持 `AuditEvent` 的方法级聚合语义，并补充幂等键、失败队列和可观测指标。
- **结构化片段查询**：新增明细表或结构化列，避免把逗号字符串当作长期关系模型。
- **失败操作审计**：若业务需要记录“尝试但失败”，必须与当前“成功事实”模型区分状态和错误摘要，且避免保存异常中的敏感数据。

## 10. 代码导航

| 关注点 | 位置 |
| --- | --- |
| 公共接入契约 | `domains/audit/api/src/main/java/xyz/jasenon/lab/audit/api/` |
| 查询 RPC 契约 | `domains/audit/api/src/main/java/xyz/jasenon/lab/audit/api/service/AuditLogService.java` |
| 成功调用拦截与聚合 | `domains/audit/service/src/main/java/xyz/jasenon/lab/audit/aspect/AuditLogAspect.java` |
| Handler 模板与精确类型注册表 | `domains/audit/service/src/main/java/xyz/jasenon/lab/audit/handler/` |
| 内部聚合事件 | `domains/audit/service/src/main/java/xyz/jasenon/lab/audit/model/AuditEvent.java` |
| 写入端口与 MyBatis 实现 | `domains/audit/service/src/main/java/xyz/jasenon/lab/audit/persistence/` |
| 查询、过滤和分页 | `domains/audit/service/src/main/java/xyz/jasenon/lab/audit/service/AuditLogServiceImpl.java` |
| 自动装配与查询权限映射 | `domains/audit/service/src/main/java/xyz/jasenon/lab/audit/config/` |
| HTTP 查询适配器 | `web/src/main/java/xyz/jasenon/lab/web/audit/AuditLogController.java` |
| 数据表定义 | `sql/schema.sql` 中的 `audit_operation_log` |
| 核心行为测试 | `domains/audit/service/src/test/java/xyz/jasenon/lab/audit/` |
