> 历史设计草案：包含已删除的 TimePoint 方案。当前实现见 [Runtime v3](runtime-v3.md)。

# Runtime 命令执行模型设计草案

状态：设计提案，尚未替换现有实现。

## 1. 目标与边界

以 Runtime 为状态所有权、命令排序和版本切换的单位。同一 Runtime 的设备条件、时间状态、生命周期、动作组触发历史只能由所属
worker 修改。多个 Runtime 共享固定数量的 worker；第一版按 runtimeId 固定分区，不做跨 worker 迁移。

每个 worker 使用有界命令队列，批量消费时限制批次，避免长期独占。推理线程不执行数据库、设备通信或通知等阻塞操作。队列和快照发布仍需同步；“免锁”仅指独占的
Runtime 内部状态。

## 2. 定义、计划、实例分离

```text
RuntimeRevision（可持久化的用户定义）
    → RuntimeCompiler（纯校验、编译）
RuntimePlan（深度不可变，可重复实例化）
    → RuntimeInstaller（构建运行实例）
RuntimeInstance（generation + 可变状态，归 worker）
```

当前 RuntimePlan 的 Map.copyOf 只复制容器；EvalNode 和 TimeConditionGroup 仍可变。新计划不保存这两类运行对象，也不保存任务句柄、观察者、执行器或执行状态。

建议结构（概念契约，非最终 Java API）：

```text
RuntimePlan
  runtimeId / revision
  lifecycle: LifecyclePlan
  conditionGroups: Map<ConditionGroupId, ConditionGroupPlan>
  timeGroups: Map<TimeGroupId, TimeGroupPlan>
  actionGroups: Map<ActionGroupId, ActionGroupPlan>
  bindings: List<RuleBindingPlan>
  dependencies: DependencyPlan（编译器生成）

RuleBindingPlan
  bindingId
  conditionGroupId
  timeGroupId
  actionGroupId
  triggerPolicy
  activationPolicy
```

RuleBindingPlan 是“何时执行哪些动作”的语义单位。ActionGroupPlan 只保存动作和顺序/失败策略；条件、时间和触发策略放在
binding。多个 binding 可复用同一动作组，默认每个 binding 独立触发，不隐式合并执行。

第一阶段兼容现有定义：每个旧 ActionGroupDefinition 编译为一个同名 binding 和一个 action group，不要求同步修改前端及持久化格式。若未来不需要独立复用
binding，可保留组合门面，但内部仍明确区分动作与触发状态。

所有引用按强类型 ID 绑定，编译时验证引用存在；不使用对象身份判断。无设备限制编译为 ConstantTrueConditionGroup，无时间限制编译为
AlwaysTimeGroup，不用 null 表示特殊语义。DependencyPlan 从引用和条件自动生成，不能由调用方单独维护。

## 3. 条件与时间语义

ConditionGroupPlan 首期承载设备谓词的严格左折叠表达式，保留现有 AND/OR 顺序语义。编译为不可变谓词和连接关系；安装时创建私有求值数组或树。先接入
v0 传播算法，求值内核可以替换，不暴露 v0/v1/v2 类型给 Runtime。

时间不强行塞入永久布尔叶子：

| 内容            | 类型与职责                                       |
|---------------|---------------------------------------------|
| LifecyclePlan | Runtime 的绝对有效区间 `[activeFrom, activeUntil)` |
| CalendarSpec  | 本地日期范围（两端包含）、星期、ZoneId；内部约束 AND             |
| WindowSpec    | CalendarSpec + 本地起止时间；产生持续门控状态              |
| PointSpec     | CalendarSpec + 本地发生时间；产生单次 occurrence       |
| TimeGroupPlan | 完整时间条件之间 OR；空组为 always                      |

持续时间资格：always 或任一窗口有效。Point 只在对应 occurrence 的本次推理中满足该时间分支，不写入持续资格，不污染后续设备事件。混合窗口与时间点保留当前
OR 语义；窗口有效期间每次 point 仍是独立发生。

建议边界：窗口采用 `[start, end)`；跨午夜窗口按开始日匹配 CalendarSpec。start=end
的现有行为必须核对后决定是全天还是非法。夏令时空缺/重叠、时间点迟到和恢复补发必须成为明确策略，不能依赖默认日期转换行为。

RuntimeInstance 保存设备值/已知性、求值状态、有效窗口集合、binding 持续资格历史、occurrence 去重状态、生命周期和已提交决策序号。计划中不保存这些状态。

## 4. 事件与命令契约

区分外部事实 EngineEvent 与内部操作 RuntimeCommand。

外部事实：DeviceObservation、未来的 ManualTrigger / WebhookEvent 等。外部事实携带
eventId、occurredAt、receivedAt、源信息；有可靠源序号时附带 sourceSequence。没有序号时不能声称能还原源端顺序。

内部命令：InstallRuntime、RemoveRuntime、ActivateRuntime、ExpireRuntime、ApplyEvent、TimeDue、ExecutionCompleted、SnapshotQuery。

RuntimeEnvelope 携带 runtimeId、generation、worker 接收顺序及追踪信息。TimeDue 还带
timerToken、conditionId、scheduledAt、occurrenceId；旧 generation 或旧 token 一律拒绝。设备事件可扇出到多个 Runtime，时间回调直接寻址
Runtime。无需给所有事件伪造同一种订阅 key。

第一版推理按命令接收顺序执行，不按 occurredAt 重排。单条命令使用一致的 evaluationInstant；生命周期检查和窗口校准使用该时间。时间点的
scheduledAt 用于识别发生和应用迟到策略，不读取“未来”历史状态来模拟过去。事件时间重放属于后续独立能力。

## 5. 消费路径

```text
输入适配器 → EventIngress → 类型对应路由器
  DeviceRouter：eventKey → RuntimeTarget(generation, condition slots)
  Time callback：runtimeId + generation + timerToken
  ManualRouter：授权后定位 runtime / binding
      → WorkerQueue[hash(runtimeId)]
      → RuntimeProcessor
      → ExecutionRequest → ActionDispatcher → ExecutionCompleted
```

路由器只选择目标，不修改条件或筛选 EDGE。编译生成局部反向索引：eventKey → 谓词位置 → conditionGroup →
binding；timeCondition → timeGroup → binding。CandidateBindingSet 是依赖收窄结果，不代表匹配或应该执行。

RuntimeProcessor 处理一次命令：

1. 校验 generation、生命周期、timerToken 和必要的去重信息。
2. 固定本次时间；若已到期先转为 EXPIRED，不能等到期回调才能阻止决策。
3. 更新所有命中谓词；完成全部传播后读取根结果，屏蔽单次事件中间变化。
4. 校准相关时间窗口，求出 candidate binding。
5. 计算候选的完整资格与触发策略，更新触发历史。
6. 形成不可变执行请求并提交到有界待发区，发布只读状态快照。

RECONCILE 需要收到相关观测的事实，即使条件真值没变也要进入决策。多个命中路径在同一 Runtime 命令内按 bindingId 去重；跨
Runtime 默认不提供原子快照。

## 6. 触发与执行

持续资格 `eligible = active && deviceSatisfied && persistentTimeAllowed`。

| 驱动                 | 决策                                                                       |
|--------------------|--------------------------------------------------------------------------|
| 设备观测 / 窗口变化        | EDGE 在完整持续资格 false→true 时执行；RECONCILE 在相关驱动且资格为 true 时产生协调请求             |
| TimePoint          | 有效 occurrence + 设备满足 + 生命周期有效，产生一次决策；不改写持续资格历史                           |
| Activation         | 按 activationPolicy 建立基线；建议支持 BASELINE_ONLY 和 EVALUATE_CURRENT，兼容旧行为时显式选择 |
| ExecutionCompleted | 更新执行状态；不默认再次触发，否则容易形成反馈循环                                                |

定期 RECONCILE 必须有显式周期驱动。设备离线/未知值如何参与求值应定义为策略；首期可兼容当前行为，但快照应区分未知和已观测
false。

ExecutionRequest 包含
runtimeId、generation、bindingId、actionGroupId、decisionId、源事件/occurrenceId、触发原因、动作参数快照。异步执行器不重新读取条件做业务判断；开始前可以执行
generation 有效性检查。

执行并发策略（顺序、合并、取消待执行任务、重试）与 EDGE/RECONCILE 分开。执行结果以命令回传。内存去重不等于重启后
exactly-once；需要可靠交付时引入持久 outbox 和下游幂等键。

## 7. 安装、替换与注销的提交协议

采用单一 ingress 排序控制操作和事实路由；各 Runtime worker 独立消费。这样可以定义一个 Runtime 替换前后的事件归属，不要求整个系统暂停。

1. 在入口之外编译、验证并构建私有候选实例，失败不触碰旧版本。
2. ingress 到达替换屏障时，对目标 Runtime 暂存后续事件，其他 Runtime 正常路由。
3. worker 在已排队旧命令之后处理安装：准备初始化状态及 timer intents；准备失败保留旧版本。提交时切换实例和 generation。
4. ingress 收到提交确认后切换订阅索引，按顺序投递暂存事件，返回注册成功。订阅必须从此刻状态重新计算，不能按旧路由过滤暂存事件。

屏障期间为该 Runtime
暂存的是可重新按新订阅匹配的事实流，包含新订阅所需事件；可用有界全局入口日志及游标实现，不能仅保存旧订阅命中的事件。缓冲耗尽必须背压或使安装显式失败，不能静默丢失。此机制有复杂度，应首先以简单、有界且可验证的入口协议实现。

初始化设备快照也要与入口序列对齐：快照游标之前的数据用于建基线，之后事件按序重放，避免快照和实时事件覆盖顺序反转。Pending
期间保持订阅更新，激活时使用最新已知状态。

定时器是可重建的派生资源。worker 记录下一次 timer intent，调度器确认或重试安装；投递到期消息仍校验时间和
token。安装成功的契约需明确区分“实例已提交”和“定时资源已就绪”，不能在资源失败时假装全部成功。

Remove 同样经过入口屏障。确认后不再产生该 generation 的新决策，待执行请求失效；已经开始的外部操作尽力取消，不能承诺撤回。注册注销原子性只覆盖单
Runtime，不覆盖数据库发布与外部设备操作的分布式事务。

## 8. 包结构

```text
xyz.jasenon.lab.engine
  api/                  EngineFacade、安装/移除结果、只读快照
  definition/           持久化定义模型及版本契约
  plan/                 深度不可变 RuntimePlan 与 binding/condition/time/action plan
  compiler/             校验、引用解析、依赖索引和求值程序编译
  event/                事实契约及 device/time/manual 子类型
  routing/              ingress、订阅索引、目标解析、拓扑屏障
  runtime/              RuntimeInstance、RuntimeProcessor、RuntimeSnapshot
    command/            命令及 envelope
    lifecycle/          状态转移与有效期判定
    evaluation/         Runtime 内的增量更新协调
    trigger/            binding 状态、触发策略、决策
  eval/                 求值内核、类型比较；不依赖 runtime 或执行设施
  time/                 Calendar/Window/Point 语义、下一次发生计算
  scheduling/           worker、队列、定时器适配与 timer intent
  execution/            ExecutionRequest、执行策略、调度、结果
    action/             Control/Report/Poll 执行实现
  infrastructure/       持久化、设备消息接入、通知、Spring 装配
```

依赖方向：compiler 依赖 definition 和 plan；runtime 消费 plan 并调用 eval/time；routing/scheduling 驱动 runtime；execution
消费不可变请求并回传结果。plan 不依赖 runtime，eval/time 不依赖 Engine、Spring 或执行器。避免每个类各建一层接口，只为可替换内核、外部设施及测试边界定义端口。

当前 Engine 收敛为门面；RuntimeRevisionCompiler 拆到 compiler；RuntimeActionGroup 的绑定职责迁到
RuleBindingPlan；RuntimeSignalRouter 拆为 routing 与 runtime.trigger；AsyncRuntimeScheduler
分离决策、执行排队和结果反馈；TimeScheduleService / RuntimeLifecycleManager 的线程设施进入 scheduling，时间数学与生命周期判定保留纯逻辑。

## 9. 实施顺序与验收

1. 用现有测试列出现行语义，补齐上述待决边界，形成兼容/变更清单。
2. 引入深度不可变 plan 和 binding，旧 revision 通过编译器适配，不迁动 API。
3. 实现单 worker RuntimeProcessor、v0 内核和确定性命令回放，验证设备/时间/激活完整路径。
4. 实现 generation、入口屏障、快照序列和安装故障处理，再扩展多 worker。
5. 拆分动作执行，保留现有动作适配；切换 Engine 门面后再整理旧包和旧版本实现。

关键验收：同一事件多个叶子只提交最终结果；不变观测可驱动 RECONCILE；窗口进入产生完整
EDGE；重叠窗口不误触发；时间点重复/迟到有确定行为；激活与到期边界一致；替换失败保留旧版本；新订阅不漏切换事件；旧定时器及执行回调不能污染新版本；过载不会静默丢事件；动作阻塞不阻塞推理。

性能测试统计接收到决策完成的吞吐、P99、队列深度和分配量，并覆盖热点 Runtime、大扇出和长表达式。比较完整路径，不能以入队耗时替代推理耗时。
