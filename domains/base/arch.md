# Base 模块核心设计

> 本文以 `domains/base/api`、`domains/base/service`、当前测试及 `sql/schema.sql` 为准，面向需要修改用户、联系人、实验室和授权同步逻辑的维护者。

## 1. 模块定位

Base 是系统的基础资料域与身份上下文装配者，负责：

- 维护系统用户和不可登录联系人；
- 校验账号密码，并创建、刷新或删除登录用户上下文；
- 维护实验室基础资料、创建人和负责人快照；
- 将用户的应用权限、实验室范围以及实验室成员变更同步到授权服务；
- 在 RPC 边界隐藏密码，并对手机号进行落库加密；
- 为关键写操作提供授权检查、审计事件和链路追踪接入点；
- 运行通用数据库驱动的补偿任务，目前用于实验室授权关系对账。

Base 不负责：

- 签发或解析 Web 登录令牌；它只校验凭证并维护 `UserContextStore`；
- 定义授权模型、存储授权关系或执行权限判定；这些能力通过 Auth 模块契约调用；
- 保存审计日志或决定审计投递策略；这里只定义可审计命令和对象映射；
- 管理课程、设备、规则、消息等业务数据；它们只通过实验室 ID、用户 ID 等稳定标识引用本域；
- 提供 HTTP Controller；外部入口由 Web 层调用这里暴露的 Dubbo API。

## 2. 代码分层

```text
domains/base
├── api
│   └── .../base/api
│       ├── dto            RPC 命令/查询；写命令同时提供审计描述
│       ├── model          User、Laboratory、补偿任务持久化模型
│       ├── service        LaboratoryService、UserService RPC 契约
│       ├── vo             面向调用方的组合视图
│       ├── persistence    密码/手机号 MyBatis TypeHandler
│       └── validation     领域输入错误集合
└── service
    └── .../base
        ├── service.impl   用户与实验室用例编排、事务边界
        ├── mapper         MyBatis-Plus 与定制查询
        ├── handler
        │   ├── authorization  命令到授权检查的映射
        │   └── audit          命令到审计对象的映射
        ├── context        UserContext 装配
        └── compensation   数据库驱动的补偿调度与授权对账
```

`api` 是跨进程契约，应保持小而稳定；`service` 才持有数据库、授权客户端、缓存和审计实现依赖。新增能力时不要把 Web 请求对象或其他领域内部模型带入 `api`。

## 3. 核心模型与不变量

### 3.1 User：用户与联系人共表

`user` 表同时存两类主体：

- 普通用户：`username`、`password` 同时存在，可登录；
- 联系人：`username`、`password` 同时为空，只供负责人、通知接收人等业务引用。

普通用户必须有姓名、用户名、密码，并至少有手机号或邮箱；联系人必须有姓名，并至少有手机号或邮箱。数据库进一步保证姓名、用户名、手机号、邮箱唯一，以及用户名和密码成对为空或成对存在。

密码由 `BCryptoHandler` 在写入时 BCrypt 哈希，读取的是摘要；手机号由带 `aes.key` 的 `AESCryptoHandler` 加密成 Base64 文本并在读取时解密。所有跨 RPC 返回用户数据的路径必须调用 `User.mask()`；当前列表、当前用户、认证、实验室创建人和负责人视图都遵守此约束。

注意：姓名并发检查中的 `String.intern()` 只对单 JVM 有效，真正的多实例唯一性依赖数据库唯一索引。手机号采用随机加密模式时，密文唯一索引不能等价替代明文去重，维护时不可假定它能识别相同明文。

### 3.2 Laboratory：基础资料与授权资源的双重身份

`laboratory` 表保存楼栋、组织、名称、动态 `extra`、负责人 `manager` JSON 快照和 `createBy`。创建人是稳定用户 ID；负责人列表保存 `User` 快照，但授权同步只抽取其中非空的用户 ID。

实验室同时是外部授权系统中的资源。MySQL 是基础资料事实源，授权关系是派生状态：

- 创建人同步为 owner；
- `manager` 中的用户 ID 同步为管理关系；
- viewers 由独立成员接口整体替换；
- 用户可见实验室按授权服务的 `can_view` 结果反查，而不是只读取直接 viewer 关系，因此可包含超级管理员等继承权限。

`LaboratoryVO` 将 `createBy` 解析成脱敏用户，并对负责人逐个脱敏。`extra` 是无固定 schema 的扩展字段，修改其结构前应先确认所有消费方。

### 3.3 UserContext：查询范围缓存

`UserContextFactory` 将用户信息和当前可见实验室投影为上下文，其中每个 scope 只包含实验室 ID、名称、楼栋和组织。实验室列表与筛选项先读取当前上下文中的可见范围，再在范围内按楼栋/组织收窄；筛选条件不能扩大权限边界。

上下文是 Redis 中的派生缓存，不是权限事实源。登录、用户授权更新、实验室资料/成员变化后会重建；用户删除和登出会删除。刷新保留原 `loginAt`，当前线程对应同一用户时也同步更新 `UserContextHolder`。

## 4. 关键业务流程

### 4.1 登录与登出

1. 按用户名读取用户，Mapper result map 解密手机号但保留密码摘要。
2. 拒绝不存在用户、无登录凭证的联系人和密码错误。
3. 从授权服务查询该用户所有可见实验室，再从 MySQL 装配仍存在的实验室 scope。
4. 保存 `UserContext`，返回脱敏用户。
5. 登出只删除当前用户的上下文；令牌生命周期由调用方负责。

### 4.2 创建和修改用户

普通用户创建流程为“领域校验 → 姓名存在性检查 → MySQL 插入 → 同步应用权限与实验室范围”。更新会先拒绝把联系人当普通用户授权，随后更新 MySQL、同步完整授权集合，并在事务提交后刷新上下文。联系人创建只写用户表，不分配登录和授权信息。

删除用户禁止删除当前登录者，顺序为 MySQL 逻辑删除、清理该用户的外部授权关系、提交后清理上下文。授权调用失败会使本地事务回滚，但跨系统已经发生的远端写入不受 MySQL 回滚控制。

### 4.3 实验室生命周期

创建实验室时记录当前用户为 `createBy`，保存资料后立即调用 `LaboratoryAuthorization.reconcile` 初始化创建人与负责人关系；更新资料时保留原创建人并再次对账。两者都在提交后刷新所有可见用户的 scope。

查看或整体替换 viewers 需要实验室级管理权限。替换前验证每个用户 ID 都存在，并合并变更前后的可见用户集合进行缓存刷新。

删除时必须先查询受影响用户，再逻辑删除 MySQL 记录并删除授权资源，最后在提交后刷新上下文。先查用户这一顺序不可颠倒，否则授权资源删除后将无法确定缓存影响范围。

## 5. 权限、审计与可观测性接入

服务方法上的 `@ActionAuthorized` 只声明需要鉴权，具体动作由 DTO 类型对应的 handler 决定：

| 用例 | 授权目标 |
| --- | --- |
| 创建普通用户/联系人 | `app:global#create_user` |
| 用户列表 | `app:global#list_user` |
| 查看用户权限 | `app:global#list_user_permissions` |
| 修改用户及授权 | `app:global#edit_user` |
| 删除用户 | `app:global#delete_user` |
| 创建、编辑、删除实验室 | `app:global#manage_laboratory` |
| 查看/替换实验室成员 | `laboratory:{id}#laboratory_manage` |

实验室普通列表不走动作鉴权，而是强制要求登录上下文并按上下文 scope 隔离数据。

写方法通过 `@Audited` 发布审计语义；DTO 实现 `Loggable` 生成不含密码、手机号等敏感字段的描述，审计 handler 映射 `CREATE/EDIT/DELETE`、对象类型和对象 ID。创建场景的 ID从持久化返回对象提取。审计模块的存储和投递属于外部契约，不在 Base 内展开。

`@Traced` 覆盖用户和实验室服务；认证、登出显式关闭参数记录，避免密码进入 trace。新增敏感接口时应沿用这一策略。

## 6. 持久化与运行依赖

Base 使用 MySQL、MyBatis-Plus 和逻辑删除字段 `delete_at`。核心表为：

- `user`：用户与联系人；
- `laboratory`：实验室资料和负责人 JSON 快照；
- `compensation_task`：任务定义、下次触发时间和租约；
- `compensation_task_log`：每次执行台账。

服务通过 Dubbo Triple 暴露，默认端口 `50051`，在 Nacos 注册和读取配置。Redis 保存用户上下文；Permify 提供授权查询与关系写入。`AES_KEY` 必须提供且符合底层 AES 密钥长度要求，否则手机号 TypeHandler 无法可靠初始化或工作。UID 使用独立的 `fun_cloud_base` 数据源分配 worker 节点。

数据库 schema 不建立用户—实验室外键，跨表和跨系统引用完整性由服务校验与补偿任务维护。

## 7. 补偿与一致性边界

`CompensationTaskRegistrar` 在应用就绪后扫描实现 `CompensationTaskHandler` 且标注 `@CompensationJob` 的 Bean，按唯一 `code` 首次登记任务。多实例同时登记由数据库唯一索引收敛。

Scheduler 默认每 30 秒查询最多 20 个到期任务，以带条件更新争抢 10 分钟租约。Cron 按任务 `zoneId` 计算；迟到超过 5 分钟时，`SKIP` 记录跳过，`FIRE_ONCE` 执行一次。执行状态和最多 2000 字符的错误/消息写回任务并进入日志表。

当前 `laboratory-auth-reconcile` 每日零点遍历未删除实验室，以 MySQL 中的创建人和负责人重新对账授权资源。它修复实验室 owner/manager 派生关系，不负责恢复用户应用权限或 viewers，也不刷新 `UserContext`。如果授权关系已修复但缓存仍旧，需由后续登录/写操作重建，或另行增加受控刷新策略。

需要明确的故障边界：

- MySQL 事务不能原子提交 Permify 或 Redis；
- 远端授权写成功后本地事务回滚，可能留下暂时的多余关系；每日实验室对账只覆盖其职责范围；
- Redis 刷新被安排在 `afterCommit`，避免数据库回滚时提前发布新 scope，但刷新失败不会回滚已提交业务数据；
- 任务租约超时后可由另一实例再次执行，handler 必须幂等；当前 reconcile 满足这一要求；
- Scheduler 的任务状态更新与日志写入不是一个显式事务，极端故障下二者可能不一致。

## 8. 扩展约定

### 新增 Base RPC 用例

1. 在 `api/dto` 定义专用命令或查询，不复用含义模糊的 Map。
2. 在 `api/service` 增加稳定契约，在 `service.impl` 编排事务。
3. 若需要权限，增加精确 DTO 类型的 `ActionCommandHandler`，不要在业务方法内散落字符串权限。
4. 若需要审计，让命令实现 `Loggable`，增加对应 `AuditLogHandler`，确保描述不含凭证和联系方式。
5. 返回 `User` 或嵌套用户时统一脱敏，并增加 RPC 边界测试。

### 新增补偿任务

实现 `CompensationTaskHandler` 并标注 `@CompensationJob`；`type()` 必须与注解 `code` 完全相同。处理器必须可重复执行，payload 变更要向后兼容数据库中的旧任务。调整已登记任务的注解 cron 不会自动覆盖数据库记录，需显式迁移或运维修改。

## 9. 代码导航与验证重点

| 关注点 | 入口 |
| --- | --- |
| 对外契约 | `api/.../service/UserService.java`、`LaboratoryService.java` |
| 用户/联系人模型 | `api/.../model/User.java` |
| 实验室模型与视图 | `api/.../model/Laboratory.java`、`vo/LaboratoryVO.java` |
| 用户主流程 | `service/.../service/impl/UserServiceImpl.java` |
| 实验室主流程 | `service/.../service/impl/LaboratoryServiceImpl.java` |
| 上下文投影 | `service/.../context/UserContextFactory.java` |
| 权限动作映射 | `service/.../handler/authorization/` |
| 审计对象映射 | `service/.../handler/audit/` |
| 补偿框架与对账 | `service/.../compensation/` |
| SQL 与字段约束 | `sql/schema.sql` 中 `user`、`laboratory`、`compensation_task*` |
| 运行配置 | `service/src/main/resources/application.yml` |

修改后至少覆盖：领域校验、用户脱敏和加解密、DTO 到权限动作映射、审计对象 ID、实验室可见范围不被筛选条件扩大、授权写入顺序、事务提交后缓存刷新，以及补偿任务的多实例争抢和幂等性。
