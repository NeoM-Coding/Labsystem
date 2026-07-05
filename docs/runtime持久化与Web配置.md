# Runtime 持久化与 Web 配置

本项目采用 **MySQL metadata + JSON revision**。MySQL 元数据负责列表查询、状态和生命周期索引；JSON revision 保存一次完整、不可变的规则配置。

## 为什么条件组必须独立

旧模型把设备条件树和时间条件直接放在 ActionGroup 内：

```mermaid
flowchart LR
    R["Runtime"] --> A1["ActionGroup A"]
    R --> A2["ActionGroup B"]
    A1 --> D1["DeviceCondition 副本"]
    A1 --> T1["TimeCondition 副本"]
    A2 --> D2["DeviceCondition 副本"]
    A2 --> T2["TimeCondition 副本"]
```

这种结构对代码构造方便，但不适合 Web 表单：相同条件会被复制，编辑时容易产生多个不一致版本，运行时也会重复索引和调度。

当前模型把条件组提升为 Runtime 内可复用对象，ActionGroup 只保存引用：

```mermaid
flowchart LR
    R["Runtime"] --> DG["DeviceConditionGroup registry"]
    R --> TG["TimeConditionGroup registry"]
    R --> AG["ActionGroup registry"]

    AG --> A1["ActionGroup A"]
    AG --> A2["ActionGroup B"]
    A1 -->|"deviceConditionGroupId"| D["共享设备条件组"]
    A2 -->|"deviceConditionGroupId"| D
    A1 -->|"timeConditionGroupId"| T["共享时间条件组"]
    A2 -->|"timeConditionGroupId"| T
```

亮点：

- 前端可以独立创建、编辑和选择条件组。
- 一个条件组只编译、索引和调度一次。
- 条件根结果变化后，通过反向引用找到所有关联 ActionGroup。
- TimePoint 发生一次，可以扇出触发所有引用该时间组的 ActionGroup。

## Revision JSON

`RuntimeRevision` 是 Web DTO 与引擎运行对象之间的边界。建议 JSON 结构如下：

```json
{
  "runtimeId": "runtime-lab-101",
  "activeFrom": "2026-07-01T00:00:00Z",
  "activeUntil": "2026-12-31T16:00:00Z",
  "deviceConditionGroups": [
    {
      "groupId": "room-too-hot",
      "conditions": [
        {
          "conditionId": "temperature-over-26",
          "deviceType": "AirCondition",
          "deviceId": "ac-1",
          "field": "roomTemperature",
          "operator": "GT",
          "value": "26",
          "logicToPrevious": "AND"
        }
      ]
    }
  ],
  "timeConditionGroups": [
    {
      "groupId": "weekday-office-hours",
      "conditions": [
        {
          "conditionId": "office-window",
          "type": "WINDOW",
          "startDate": "2026-07-01",
          "endDate": "2026-12-31",
          "weekdays": ["MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY"],
          "zoneId": "Asia/Shanghai",
          "startTime": "08:30:00",
          "endTime": "18:00:00",
          "timePoint": null
        }
      ]
    }
  ],
  "actionGroups": [
    {
      "actionGroupId": "notify-user",
      "deviceConditionGroupId": "room-too-hot",
      "timeConditionGroupId": "weekday-office-hours",
      "actions": [
        {
          "type": "Report",
          "control": null,
          "userIds": ["user-1"],
          "reportTypes": ["SMTP"],
          "content": "实验室温度过高"
        }
      ]
    },
    {
      "actionGroupId": "control-air-condition",
      "deviceConditionGroupId": "room-too-hot",
      "timeConditionGroupId": "weekday-office-hours",
      "actions": []
    }
  ]
}
```

这里两个 ActionGroup 复用了同一组设备条件和时间条件。JSON 中不嵌套复制条件组。

## 编译与注册

```mermaid
sequenceDiagram
    participant Web as "Web Controller"
    participant DB as "MySQL"
    participant Compiler as "RuntimeRevisionCompiler"
    participant Engine as "Engine"
    participant Runtime as "Runtime"

    Web->>DB: 读取 published revision JSON
    DB-->>Web: RuntimeRevision
    Web->>Compiler: compile(revision)
    Compiler->>Compiler: 校验重复 ID 和悬空引用
    Compiler->>Runtime: 每个 groupId 构造一个条件组实例
    Compiler->>Runtime: ActionGroup 按 ID 引用条件组
    Compiler-->>Web: Runtime
    Web->>Engine: register(runtime)
    Engine->>Runtime: 建立事件索引、生命周期和时间任务
```

`RuntimeRevisionCompiler` 承担：

- 校验 Runtime 生命周期。
- 校验设备/时间条件组 ID 唯一。
- 校验 ActionGroup ID 唯一。
- 拒绝不存在的条件组引用。
- 将设备条件列表编译成严格左结合的 `EvalTreeNode`。
- 将时间 DTO 编译成窗口或 TimePoint。
- 将动作 DTO 编译成 `ControlAction` 或 `ReportAction`。
- 保证同一个条件组对象被所有引用它的 ActionGroup 共享。

## 数据表

`rule_runtime` 保存可检索元数据：

- 规则名称、所有者和发布状态。
- 当前发布版本号。
- `active_from/active_until`，用于启动恢复和后台扫描。

`rule_runtime_revision` 保存不可变版本：

- `(runtime_id, revision_no)` 唯一。
- `definition` 保存完整 JSON。
- `schema_version` 用于未来 JSON 迁移。
- `checksum` 用于审计和幂等发布。

发布建议放在一个数据库事务内：

1. 锁定 `rule_runtime` 当前行。
2. 分配下一个 `revision_no`。
3. 插入新的 `rule_runtime_revision`，历史 revision 不更新。
4. 更新 `rule_runtime.published_revision_no`、生命周期和状态。
5. 事务提交后发布 RuntimeReload 消息；rule-engine 加载 revision、编译并原子替换旧 Runtime。

当前仓库已经落地表结构、revision DTO、编译器和内存替换能力。Web Controller、Repository、发布事务和跨服务 RuntimeReload 契约仍需在后续接入。

## 运行时扇出

```mermaid
flowchart LR
    E["DeviceEvent"] --> L["EventKey -> DeviceConditionLeaf"]
    L --> D["共享 DeviceConditionGroup<br/>只刷新一次"]
    D -->|"根结果变化"| Reverse["deviceGroupId -> actionGroupIds"]
    Reverse --> Signal["StateChanged(candidateActionGroupIds)"]
    Signal --> Mailbox["Runtime mailbox<br/>候选集合取并集"]
    Mailbox --> Eval["ActionGroupEvaluator"]
    Eval --> A1["ActionGroup A actions"]
    Eval --> A2["ActionGroup B actions"]
```

时间条件对应链路：

```mermaid
flowchart LR
    TS["TimeScheduleService"] -->|"每个共享组只注册一次"| TE["TimeEvent<br/>timeConditionGroupId"]
    TE --> TG["TimeConditionGroup.apply"]
    TG --> Reverse["timeGroupId -> actionGroupIds"]
    Reverse --> Scheduler["AsyncRuntimeScheduler"]
    Scheduler --> A1["ActionGroup A"]
    Scheduler --> A2["ActionGroup B"]
```

状态事件可合并，但合并时必须保留候选 ActionGroup ID 的并集；TimePoint 仍按 `occurrenceId` 去重并进入 FIFO，不能被状态合并吞掉。
