# Rule 核心设计

> 本文描述 `domains/rule` 当前代码所实现的架构与边界。规则模块负责“把持久化策略编译成可运行拓扑，并由设备状态或时间边界驱动动作”；设备接入、鉴权判定、WebSocket 会话、Redis 基础设施及通用补偿调度均由外部模块负责。

## 1. 模块职责

Rule 由两个 Maven 模块组成：

- `api`：对外稳定契约，包含策略 CRUD、告警日志查询以及不可变的 `RuntimeRevision` 定义。
- `engine`：编译、增量求值、生命周期与时间调度、动作执行、通知和持久化恢复。

一条策略由三类定义组成：

1. 设备条件组：按声明顺序组合字段谓词；空条件组恒为真。
2. 时间条件组：若干日历约束下的时间窗口或瞬时时间点；空条件组恒为真。
3. 动作组：引用一个设备条件组和一个时间条件组，二者同时满足时执行组内动作。

核心数据流如下：

```text
RuntimeRevision ──纯编译──> RuntimePlan ──原子安装──> Runtime
                                                   │
设备快照 ──字段差分──> DeviceEvent ──> EvalForest ──┼──> RuntimeScheduler ──> Action
时间条件 ──下一边界调度──> TimeEvent ────────────────┘                         │
                                                                              └──> RuleExecutionNotice
                                                                                   ├─ 告警日志
                                                                                   └─ 定向实时事件
```

## 2. Revision、Plan 与 Runtime

### 2.1 三层模型

- `RuntimeRevision` 是表单、Dubbo 与数据库共享的不可变版本。它只表达用户意图，不持有线程、观察关系或可变求值状态。
- `RuntimePlan` 是 `RuntimeRevisionCompiler` 的无副作用编译结果，包含设备表达式链、恒真组、时间条件对象、动作绑定及启动恢复所需的设备事件键。
- `Runtime` 是已安装的内存实例，持有 Forest 根句柄、时间窗口状态、动作组反向索引和生命周期状态。

这种分层保证非法配置在触碰全局拓扑前失败，也使数据库提交和内存安装之间的顺序明确。

### 2.2 Revision 版本语义

`rule_runtime` 保存策略元数据和当前 `revision_no`；`rule_runtime_revision` 以 `(runtime_id, revision_no)` 唯一保存不可变 JSON、schema 版本、SHA-256 校验值和创建者。更新、启用、停用都追加 revision，不原地覆盖历史定义。

更新事务通过锁定当前 revision 取得下一个单调版本号，先插入新版本，再发布元数据中的当前版本。数据库事务成功后才调用 `Engine.register` 替换内存实例。创建使用唯一键拒绝同名 Runtime，删除是元数据软删除并注销内存实例。

当前一致性边界是“数据库先提交、内存后同步”，二者不是同一个原子事务：若提交后进程在内存同步前失败，数据库仍是事实来源，重启恢复会重新安装当前启用版本。

### 2.3 编译校验

编译器负责：

- 校验 Runtime、条件组、条件和动作组 ID 非空且同层唯一；
- 校验动作组引用的设备条件组、时间条件组真实存在；
- 将设备字段、操作符、目标值和 `AND`/`OR` 顺序关系编译成 `EvalNode` 链；首个条件固定按 `AND` 接入；
- 将空设备条件组编译为恒真根；
- 将日历范围、星期、时区以及窗口/时间点编译为时间条件；
- 将 Control/Report 定义转换为运行时 Action；
- 收集所需 `DeviceEventKey`，供安装或恢复后重放当前设备状态。

`RuntimePlan` 再次检查动作组引用和对象身份，避免手工构造非法计划绕过编译器。

## 3. 增量求值森林

### 3.1 节点结构与共享

`EvalForest` 是全局、按字段索引驱动的观察者网络：

```text
EventSourceNode(deviceType, deviceId, field)       全局共享
  └─ PredicateNode(eventKey, operator, target)     相同谓词共享
      └─ LeafTransformNode                         表达式位置私有
          └─ CompositeNode ...                     平衡复合树私有
              └─ RootNode(runtimeId, groupId)      条件组出口
```

事件源和完全相同的谓词可跨 Runtime 共享；叶、复合节点和 Root 属于具体表达式。关闭注册会解除观察关系，并在没有引用时回收共享节点，避免策略更新后留下悬挂拓扑。

### 3.2 保持顺序语义的平衡树

混合 `AND`/`OR` 不是普通可交换归约。每个叶节点把谓词结果表示成一元布尔函数 `BooleanTransform(onFalse, onTrue)`，复合节点执行函数复合。函数复合具备结合律，因此既保持表单中的从左到右语义，又可构造平衡树，将传播深度控制为对数级。Root 以 `true` 作为 AND 折叠单位元得到最终布尔值。

字段比较由设备 Record 模型反射解析真实类型。数值统一用 `BigDecimal` 比较；布尔值接受 `true/false/1/0`；字符串和枚举按对应类型处理。类型解析或字段错误在求值时按谓词不成立处理，配置结构错误则在编译期拒绝。

### 3.3 增量传播

一个 `DeviceEvent` 只进入对应的唯一 `EventSourceNode`。只有值真正变化的节点继续通知下游；Forest 在本次传播的线程本地 batch 中归并 Root 抖动，只返回“传播前后最终值确实不同”的 Root。Engine 再按 `runtimeId` 和设备条件组反向索引收窄候选动作组，不进行全量策略扫描。

设备事件表达的是持续状态而非一次性触发：条件由假变真或由真变假都会产生候选推演，但动作只在推演时设备根与时间条件仍同时成立时执行。

## 4. 设备事件与当前状态恢复

`DeviceRecordChangeListener` 订阅设备快照契约，将每台设备的完整字段快照缓存在内存，只为首次快照的全部字段或后续发生变化的字段产生 `DeviceEvent`。

策略晚于设备上线、服务刚重启或 revision 刚替换时，不能等待下一条遥测才能得到正确根状态。因此安装完成后根据 `RuntimePlan.requiredEventKeys` 去重到设备维度，并重放当前完整状态：优先使用 listener 的内存快照；若内存尚空，则读取设备侧持续维护的 Redis record hash。找不到真实快照时不会伪造默认值，Root 保持初始不成立。

跨模块契约仅包括：设备模块发布 `DeviceRecordSnapshotEvent`；Redis 中可读取约定的 record hash。快照如何从 MQTT 报文产生、如何落库，不属于 Rule。

## 5. 时间与生命周期调度

### 5.1 Runtime 生命周期

有效期采用 `[activeFrom, activeUntil)`：开始时间包含、结束时间不包含。内存状态为 `PENDING → ACTIVE → EXPIRED`，被替换或删除则进入 `CANCELLED`。

`RuntimeLifecycleManager` 为每个 runtimeId 保存唯一 slot：未来生效的实例安排激活任务，存在结束时间的实例安排过期任务。回调前会核对 slot 仍为当前实例，旧 revision 的延迟任务不能误操作同 ID 新实例。

Runtime 在 PENDING 期间已经接入 Forest，因此设备状态持续更新；激活时初始化真实时间窗口并只对当时已经满足的动作组做一次完整推演。过期、删除或替换都会取消生命周期、时间和 Runtime mailbox，并释放 Forest 注册。

### 5.2 时间条件

- `WINDOW` 表示日历约束下的每日区间；支持跨午夜，星期归属于窗口开始日。
- `TIME_POINT` 表示日历约束下的瞬时脉冲，不写入长期窗口状态。
- 一个非空时间条件组中，任一窗口当前有效即可放行；时间点只在对应事件的本轮推演中成立。

`TimeScheduleService` 不做全局 Tick，而是为每个条件只计算并安排“下一次边界”。边界发出后从当前时钟继续安排下一次；回调迟到时跳过历史边界，采用 SKIP misfire 语义。窗口状态保存在 Runtime 的 `TimeConditionGroup`，服务只负责生产 `WINDOW_ENTER`、`WINDOW_EXIT`、`TIME_POINT` 事件。

## 6. 调度、并发与一致性

Engine 用读写锁保护拓扑替换与事件传播：安装/删除使用写锁，设备或时间事件使用读锁。`replaceRuntime` 先让旧网络保持当前共享源状态，完整注册新版后再释放旧版；新版编译/注册失败则恢复旧注册表。

`AsyncRuntimeScheduler` 的并发模型是：

- 不同 Runtime 可在线程池中并行；
- 同一 Runtime 从条件推演直到本轮所有异步 Action Future 完成都保持单飞；
- 执行期间的普通状态变化合并成一次补跑，候选动作组取并集；全量信号保持全量语义；
- `TIME_POINT` 是不可合并脉冲，进入独立 FIFO，并用有限 occurrence ledger 去重；
- Runtime 注销会清空待处理信号，阻止旧实例补跑。

这里提供的是进程内有序与单飞，不是分布式唯一执行。多个 Rule Engine 实例若同时消费同一事件，各实例都可能执行动作；控制动作也没有跨进程 exactly-once 保证。Root 读取的是持续更新的现实状态，不是跨多个设备字段的冻结快照，因此不同事件源可并发传播，保证的是各节点自身变化有序和最终贴近最新状态。

## 7. 动作、通知与告警

### 7.1 动作执行

`DefaultRuntimeExecutor` 当前支持：

- `ControlAction`：懒加载 MQTT Dubbo 引用，调用 `MqttRuleIo.asyncSend`，并把正常、RPC 错误和异常 Future 都归一为结构化 `ActionExecutionResult`。
- `ReportAction`：保留用户、SMS/SMTP 类型和内容，但实际通道尚未接入，明确返回 `NOT_IMPLEMENTED`。

同一动作组内动作会被逐个发起并异步等待；Runtime 若在发起过程中已失效，后续动作停止发起。`ActionExecutionTracker` 保存进程内成功/失败计数和最近 100 条失败诊断，它不是审计事实来源。

### 7.2 完成通知与告警日志

动作组命中后，Scheduler 等待本组已发起动作全部完成，生成唯一 `eventId` 的 `RuleExecutionNotice`。通知包含匹配/完成时间、条件组、traceId 以及每个动作的目标、受众、状态和消息。空动作组仍产生 `MATCHED` 通知。

Publisher 依次调用隔离失败的 Hook，再向 Report 动作中去重后的用户发布定向实时事件。持久化 Hook 将通知写入 `alert_log`，聚合状态为 `MATCHED`、`SUCCESS`、`FAILED`、`PARTIAL_FAILED` 或 `NOT_IMPLEMENTED`；`event_id` 唯一键是重复写入的最后防线。告警查询支持 Runtime、动作组、状态、匹配时间范围和分页，单页最多 100 条。

告警持久化和实时发布均在动作完成之后，失败只记录日志，不回滚已执行动作，也不阻塞其他 Hook。当前没有告警发布的事务 outbox 或自动重试保证。

跨模块边界为：MQTT 模块只承诺异步控制 RPC；Redis realtime channel 只承诺承载定向事件；Web 模块负责消费并推送会话。Rule 不依赖它们的内部实现。

## 8. 持久化恢复与补偿边界

应用就绪时，`RuntimePersistHelper` 读取所有当前 revision，跳过禁用、过期或无法反序列化/编译的策略，逐个注册可运行计划，最后按设备去重重放当前状态。单条坏数据不会阻断其他策略恢复。

恢复是“重建当前运行态”，不是“重放历史事件”：

- 设备条件通过当前完整快照恢复；
- 时间窗口按当前时钟重新计算；
- 已错过的时间点不会补发；
- 服务离线期间应执行的控制/通知不会自动补偿；
- 已完成动作不会从 `alert_log` 反向重放。

仓库中的 `compensation_task` / `compensation_task_log` 属于通用补偿基础设施，当前 Rule 源码没有注册规则专用 handler，也没有把动作失败写入补偿任务。因此不能把它描述为规则动作的现有可靠重试机制。若未来接入，应只通过“稳定 handler + 幂等业务键（建议 eventId/actionIndex）”契约衔接，补偿调度、抢锁和台账仍留在通用模块。

## 9. 权限与服务边界

策略 CRUD 和告警查询以 Dubbo 服务暴露，并通过 `@ActionAuthorized` 进入统一授权切面。Rule 只声明“该操作需要授权”；主体解析、关系模型和 Permify 判定属于 Auth 模块。持久化关闭时，相关持久化组件和服务不会创建。

Rule Engine 依赖 MySQL 保存 revision/告警，依赖 Nacos 发现 Dubbo 服务，Redis 用于设备状态恢复与实时事件；MQTT provider 在没有控制动作时不要求在线。上述均为端口契约，不应把外部模块内部结构复制进本文。

## 10. 代码导航

| 关注点 | 入口 |
| --- | --- |
| 对外策略/告警契约 | `api/.../SmartStrategyService.java`、`RuleAlertLogService.java` |
| 不可变规则定义 | `api/.../definition/RuntimeRevision.java` |
| 编译与计划 | `engine/.../definition/RuntimeRevisionCompiler.java`、`RuntimePlan.java` |
| 总入口与拓扑生命周期 | `engine/.../Engine.java` |
| 增量求值网络 | `engine/.../eval/v2/EvalForest.java` 及同包节点 |
| Runtime 与信号路由 | `engine/.../runtime/Runtime.java`、`RuntimeSignalRouter.java` |
| 单飞 mailbox 调度 | `engine/.../runtime/AsyncRuntimeScheduler.java` |
| 动作执行 | `engine/.../runtime/DefaultRuntimeExecutor.java`、`action/` |
| 时间计算与调度 | `engine/.../time/`、`RuntimeLifecycleManager.java` |
| 设备快照入口与恢复 | `engine/.../listener/DeviceRecordChangeListener.java` |
| Revision 持久化恢复 | `engine/.../definition/persistence/RuntimePersistHelper.java` |
| 通知与告警 | `engine/.../notification/`、`alert/persistence/` |
| 数据表 | `sql/schema.sql`、`sql/migrations/20260811_add_alert_log.sql` |
| 行为验证 | `engine/src/test/java/.../engine/` |

## 11. 当前明确限制

- Report 的 SMS/SMTP 仅建模，尚未实际发送。
- 规则执行没有跨实例唯一消费、事务 outbox 或 exactly-once 保证。
- 动作失败不自动进入通用补偿任务。
- 时间调度采用 SKIP misfire，不追赶离线期间的时间点或窗口边界。
- 数据库提交后的内存同步依靠进程继续执行或下次启动恢复，不是数据库与内存的原子提交。
- 设备多字段条件基于各字段最新可见值，不提供同一遥测批次的全局快照隔离。
