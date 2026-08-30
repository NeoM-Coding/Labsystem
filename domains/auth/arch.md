# Auth 模块核心设计
> 本文描述 `domains/auth` 当前代码所实现的授权边界。权限名称、关系写入规则与故障语义以源码和 `service/schema/lab-system-v2.perm` 为准；`lab-system.perm` 是另一版模型草案，不应与当前 Java 枚举混用。

## 1. 模块定位

Auth 是系统的授权基础设施，采用「应用内编排 + Permify 关系图」的设计：Java 代码负责把业务命令转换为授权检查、约束关系变更范围并维护会话权限快照，Permify 负责保存关系和计算权限表达式。

它负责：

- 定义稳定的授权公共类型：资源类型、关系、动作、授权操作端口和用户上下文。
- 对标记了 `@ActionAuthorized` 的业务方法执行前置授权。
- 管理 `app:global` 上的系统级角色关系，以及用户对实验室的直接 `viewer` 关系。
- 为实验室领域提供初始化、删除、成员对齐和可见范围反查端口。
- 通过 Redis 保存可跨 HTTP、Dubbo 和实时连接恢复的 `UserContext` 快照。
- 把 Permify SDK、分页、schema version 和查询深度等细节封装在 `AuthClient` 内。

它不负责：

- 用户登录、令牌验签或当前用户 ID 的解析；调用侧必须先完成认证。
- 用户、实验室等业务实体的 MySQL 生命周期和事务。
- 决定某个业务 Command 对应哪个动作；每个业务域只注册自己的 `ActionCommandHandler`。
- 替业务查询自动拼接数据范围。业务域从 `UserContext` 读取实验室 ID 后自行约束查询。
- 以 Redis 代替 Permify。Redis 存放会话快照，Permify 才是授权关系和权限计算来源。

## 2. 结构与依赖方向

```text
业务域 Command
  └─ 自有 ActionCommandHandler ──> ActionCommand
                                  │
@ActionAuthorized 业务方法 <─ ActionAuthorizationAspect
                                  │
                                  v
                              AuthService
                                  │
                     AuthorizationOperations（端口）
                                  │
                                  v
                              AuthClient ──> Permify

认证/网关边界 ──> UserContextStore ──> Redis
                         │
                         └─ 请求或 RPC 入口恢复 ──> UserContextHolder(ThreadLocal)
```

`api` 子模块只提供可被其他模块依赖的类型，不依赖 Permify 实现。`service` 子模块实现切面、编排、Redis 存储与 Permify 适配器，并通过 Spring Boot AutoConfiguration 装配。业务域只依赖这些端口或扩展点，不应直接调用 Permify SDK。

## 3. UserContext：请求内身份与数据范围

### 3.1 快照内容

`UserContext` 包含用户 ID、账号名、显示名、登录时间，以及两种实验室范围表示：

- `laboratoryIds`：授权可见的实验室 ID 快照，是业务查询的直接过滤集合。
- `laboratoryScopes`：附带实验室名、楼宇和组织名称的结构化快照，用于在已授权集合内继续筛选。

`filterLaboratoryIds(...)` 只做集合收窄，不发起远程授权查询。楼宇与组织两个维度之间是 AND，同一维度多个值之间是 OR；空过滤返回完整快照。`canViewLaboratory` 用于关系写入前的越权防护。

### 3.2 传播契约

`UserContextHolder` 是同步调用栈上的 `ThreadLocal`，生命周期必须由入口适配器管理：

1. HTTP 入口解析已认证用户 ID，从 `UserContextStore` 取快照并写入 Holder。
2. Dubbo consumer 只传播用户 ID；provider 再从自己的 `UserContextStore` 恢复完整快照。
3. 业务服务、授权切面、审计与数据范围组件在当前线程读取 Holder。
4. 无论成功还是异常，入口都必须在 `finally` 中 `clear()`，避免线程池复用造成身份串线。

Auth 只定义 Holder/Store 契约。HTTP 过滤器、Dubbo 过滤器和 WebSocket 握手属于相应基础设施模块；它们不得把完整、可伪造的上下文当作客户端可信输入。

### 3.3 Redis 快照与刷新通知

`RedisUserContextStore` 使用键 `auth:user-context:{userId}` 保存字段级 JSON，不设置 TTL。权限变更或注销必须显式 `save/delete`，否则旧快照会持续存在。序列化忽略计算 getter，反序列化忽略历史未知字段，以兼容旧快照。

写入或删除成功后，会向 `RealtimeChannels.USER_CONTEXT_CHANGED` 发布 UPSERT/DELETE 事件。通知用于让实时连接刷新状态，但发布失败被有意吞掉：Redis 中的快照是事实结果，通知是尽力而为，不能反向令登录或权限刷新失败。

## 4. 方法授权：注解、切面与 Handler

业务方法使用无参数注解 `@ActionAuthorized` 标记。权限目标不写在注解字符串里，而由业务域注册的 `ActionCommandHandler<T>` 从真实 Command/Query 转换得到：

```text
业务参数 T + 当前 UserContext
        -> ActionCommand(entityType, entityId, action, subjectType, subjectId)
        -> Permify check
```

这样，资源 ID 的提取规则留在拥有该 Command 的业务域，Auth 不需要了解其内部模型。

切面执行规则：

- 先要求 Holder 中存在非空 `userId`，否则抛出 401 `AuthenticationRequiredException`。
- 遍历方法的所有参数，按参数的**精确运行时 Class**查找 Handler；不按父类或接口匹配。
- 一个方法可产生多个 `ActionCommand`，全部通过才进入业务方法；任一拒绝抛出 403 `PermissionDeniedException`。
- 同一种 Command Class 注册两个 Handler 时启动失败，避免权限规则不确定。
- 若所有参数都没有 Handler，当前实现记录 `authorization_handler_missing ... action=allow` 并放行。

最后一条是明确的 fail-open 边界。因此新增 `@ActionAuthorized` 方法时，必须同时提供 Handler 和映射测试；不能把“加了注解”视为已经受保护。切面顺序为 `@Order(90)`，与事务、审计等切面的相对顺序变更时需要重新验证。

## 5. Permify 模型

当前 Java 枚举与测试对应 `lab-system-v2.perm`。模型包含三个实体：

### 5.1 `user`

仅作为授权主体，不包含关系或属性。

### 5.2 `app:global`

系统级单例资源，关系直接授予用户：

| 能力组 | 关系 | 推导动作 |
| --- | --- | --- |
| 超级管理 | `super_admin` | 所有 app 动作，并通过实验室的 `app` 关系获得全实验室能力 |
| 用户管理 | `user_manager` / `user_viewer` | 创建、编辑、删除、列表和权限列表（按 DSL 组合） |
| 教学 | `edu_semester_manager/viewer`、`edu_timetable_manager/viewer` | 学期管理/查看、课表管理/查看 |
| 实验室 | `laboratory_manager` | `manage_laboratory` |
| 智控 | `smart_manager/viewer/keeper` | 策略管理、状态变更、列表 |
| 日志 | `log_viewer` | 审计日志与告警日志列表 |
| 分析 | `data_analyst` | 教学、空调和空开数据分析 |

代码固定使用 `AuthService.GLOBAL_APP_ID = "global"`。写入其他 app ID 会作为配置错误拒绝。

### 5.3 `laboratory:{id}`

每个实验室关系包括：

- `app @app`：指向 `app:global`，让 DSL 能展开全局超级管理员。
- `owner @user`：实验室拥有者。
- `manager @user`：实验室管理者。
- `viewer @user`：直接查看者。
- `can_view = app.super_admin or owner or manager or viewer`。
- `laboratory_manage = app.super_admin or owner or manager`。

`lookupEntityIds`/`lookupSubjectIds` 查询的是展开后的 permission，适合回答“用户能看哪些实验室”和“哪些用户能看该实验室”；`subjectIdsOf` 查询直接关系，适合成员编辑界面。二者不可互换，否则会把继承得到的超级管理员误写成直接成员。

## 6. 关系写入规则

### 6.1 通用用户授权

`AuthService` 在写 Permify 前执行防扩权校验：

- app 关系只能写到 `app:global`，且必须是 `RelationShip.App`。
- `super_admin` 只能由现有超级管理员授予或回收。
- 非超级管理员只能授予/回收自己已经拥有的 app 关系。
- 通用入口对 laboratory 只允许写 `viewer`，且目标实验室必须在操作者 `UserContext` 的可见范围中。
- 其他资源类型或关系组合作为 500 `AuthorizationConfigurationException` 拒绝。

`synchronize(UserAuthorizationCommand)` 先读取目标用户当前 app 关系和实验室 viewer 集合，计算差集，并在任何写入之前验证完整变更集，从而避免“后半段才发现越权”导致的局部写入；实际写入顺序为回收 app、回收 lab、授予 app、授予 lab。

`removeUser` 仅清理代码已知的 app 枚举关系和 laboratory viewer 关系。若 schema 新增关系却没有同步 Java 枚举/清理逻辑，可能残留授权数据。

### 6.2 实验室生命周期端口

`LaboratoryAuthorization` 是实验室业务事实与授权图之间的窄端口：

- `initialize`：先写 `laboratory#app@app:global`，再写创建者 `owner`；第二步失败时尝试删除该实验室全部授权数据作为补偿。
- `reconcile`：根据调用方提供的 owner、manager 集合，以差集方式对齐 Permify 直接关系。
- `replaceViewers`：只替换 viewer；owner 已天然可见，因此会从请求 viewer 集合中剔除。
- `remove`：删除该实验室的 tuple 和 attribute 数据。
- `members`：分别读取直接 owner/viewer，不展开间接权限。

Auth 不读取实验室数据库，也不判断谁是业务上的 owner/manager；业务域提供事实，Auth 只将其投影到授权图。

## 7. 一致性、故障与恢复边界

系统没有跨 MySQL、Permify、Redis 的分布式事务，必须按以下边界理解：

- Permify 的单次 grant/revoke/delete 是远程调用；批量同步由多次调用组成，中途失败可能留下部分完成状态。预校验只能防止权限校验导致的部分写，不能防网络故障。
- `initialize` 仅对第二次授权失败提供尽力补偿，补偿失败会以 suppressed exception 保留；调用方仍需安排重试或对账。
- `reconcile` 和 `replaceViewers` 采用当前集与目标集差集，天然适合重试，是跨存储恢复的首选入口。
- 关系读取和 permission lookup 都遍历 Permify continuous token，避免大集合只返回第一页；lookup 页大小可配置，直接关系读取固定每页 100。
- schema version 为空时，Client 首次需要版本的操作会查询 schema 列表并缓存第一个版本。运行期间不会自动跟随新模型；生产环境应发布并固定已验证版本，模型升级后重启或显式刷新 Client。
- Permify 不可达、schema 缺失或 SDK 异常时，检查与写入均抛异常，不降级为允许；只有“注解方法没有 Handler”这一配置缺口当前会放行。
- Redis 快照可能暂时落后于 Permify。授权关系变化后，拥有业务事实的调用方负责重建并保存相关用户上下文；实时通知只能减少连接侧陈旧窗口，不能保证投递。

## 8. 配置与装配

配置前缀为 `lab.auth.permify`：

| 配置 | 默认值 | 说明 |
| --- | --- | --- |
| `base-url` | `http://localhost:3476` | Permify HTTP 地址 |
| `tenant-id` | `t1` | 租户 |
| `schema-version` | 空 | 空值触发首次查询并缓存；生产建议固定 |
| `depth` | `20` | 权限图展开深度，小于 3 时回退 20 |
| `lookup-page-size` | `100` | permission lookup 页大小，非正数回退 100 |

`PermifyAuthAutoConfiguration` 提供 `AuthorizationOperations`、`Auth`、`LaboratoryAuthorization`、Handler Registry 和授权切面，均允许调用方用自定义 Bean 覆盖。`UserContextStoreAutoConfiguration` 仅在存在 `RedisBus` 且没有自定义 Store 时启用 Redis 实现。

## 9. 扩展规则

新增授权能力时按以下顺序维护，避免 schema、枚举和业务映射漂移：

1. 在 `lab-system-v2.perm` 增加关系或动作，并上传到目标 Permify tenant。
2. 同步 `RelationShip` 或 `Action` Java 枚举；名称必须与 DSL 完全一致。
3. 若是业务方法授权，在拥有 Command 的业务域注册唯一 `ActionCommandHandler`，不要让 Auth 引入该业务域模型。
4. 为 handler 映射、切面拒绝路径和 schema 定义补测试。
5. 若新增可写关系，显式更新 `AuthService` 的防扩权和清理规则；默认拒绝未知组合。
6. 若关系影响实验室可见范围，在变更成功后重建受影响用户的 `UserContext` 并写 Store。
7. 跨 MySQL 与 Permify 的业务流程应设计可重试的 reconcile/补偿任务，不依赖调用顺序“通常成功”。

禁止在业务域直接构造 Permify SDK 请求、复制完整权限 DSL，或通过扩大 UserContext 范围绕过 action check。

## 10. 代码导航

| 入口 | 路径 |
| --- | --- |
| 公共授权端口 | `api/src/main/java/xyz/jasenon/lab/auth/client/AuthorizationOperations.java` |
| 用户上下文模型/Holder/Store | `api/src/main/java/xyz/jasenon/lab/auth/context/` |
| 动作与关系枚举 | `api/src/main/java/xyz/jasenon/lab/auth/permission/` |
| 方法授权注解与切面 | `service/src/main/java/xyz/jasenon/lab/auth/annotation/`、`aspect/` |
| Command 与 Handler 扩展点 | `service/src/main/java/xyz/jasenon/lab/auth/command/`、`handler/` |
| 通用授权编排 | `service/src/main/java/xyz/jasenon/lab/auth/service/AuthService.java` |
| 实验室授权投影 | `service/src/main/java/xyz/jasenon/lab/auth/service/LaboratoryAuthorizationService.java` |
| Permify 适配器 | `service/src/main/java/xyz/jasenon/lab/auth/client/AuthClient.java` |
| Redis 上下文快照 | `service/src/main/java/xyz/jasenon/lab/auth/context/RedisUserContextStore.java` |
| 自动配置 | `service/src/main/java/xyz/jasenon/lab/auth/config/` |
| 当前权限模型 | `service/schema/lab-system-v2.perm` |
| 关键行为测试 | `api/src/test/`、`service/src/test/` |
