# Shared 共享基础设施设计

`shared` 是各服务复用的技术底座。它统一进程内基础类型、跨进程契约、持久化约定、Redis 访问、分布式 UID 和链路日志，但不承载实验室、设备、规则、教学等业务域的状态机与业务编排。

本文描述共享层自身的稳定边界。业务模块只作为消费者出现；业务规则应留在对应模块中，不能为了“复用”下沉到 `shared`。

## 1. 模块边界

| 子模块 | 负责 | 明确不负责 |
| --- | --- | --- |
| `common` | 轻量通用类型、去重队列、异步执行器、RPC 与实时事件传输契约 | Spring 自动装配、数据库实体、具体业务 DTO、消息投递实现 |
| `persistence-core` | MyBatis-Plus 公共配置、基础实体字段、业务事务管理器绑定、持久化 JSON 一致性、UID 接入 | 表结构设计、Mapper 扫描、领域仓储、数据源地址与迁移脚本 |
| `redis` | Jedis 连接池自动装配、带命名空间的字符串/Hash/PubSub 操作 | 业务 Key 结构、对象序列化协议、分布式锁、可靠消息与消费确认 |
| `uid-springboot-starter` | UID 算法、Worker ID 分配、DB/Redis/本地模式和生成器自动装配 | 业务主键语义、业务库事务、跨版本 UID 参数迁移 |
| `observability` | HTTP/Dubbo 链路上下文、RPC 错误归一化、注解日志、参数脱敏、本地日志采集配置 | 指标/告警平台治理、业务审计、认证与授权判定、全功能 OpenTelemetry |

依赖方向保持单向：业务服务可以依赖一个或多个共享子模块；`common` 不反向依赖业务模块。`observability` 只依赖认证 API 中的公开上下文契约，用于传播身份标识，不进入认证域内部。

## 2. `common`：轻量共享内核

### 2.1 队列抽象

`AbstractIndexedQueue` 用集合索引约束队列内元素唯一性，并将 `offer/poll/remove/clear` 对队列和索引的更新放在同步临界区内。`UniqueQueue` 提供“排队中唯一”；`ActiveQueue` 额外维护 active 索引，区分“仍在处理集合中”和“当前正在队列中”：

- `poll` 只将元素移出 queued 索引，active 状态仍保留；
- `returnToQueue` 仅允许 active 元素重新排队；
- `remove/discard` 才同时结束排队和 active 生命周期；
- 调用方注入底层 `Queue` 与 `Set`，因此排序、公平性和集合实现由消费者决定。

这些类型只解决单 JVM 内的唯一排队，不提供跨进程互斥、持久化或崩溃恢复。

### 2.2 RPC 契约

`RpcResult<T>` 强制一次响应只能含 `data` 或 `error`，`RpcError` 使用稳定错误码、错误类型、HTTP 风格状态码、面向调用方的消息和 `traceId` 描述失败。`RpcErrorType` 是跨服务分类协议，不应塞入某一业务域专属枚举。

Provider 通过 `observability` 将异常归一化为 `RpcResult.failure`，Consumer 通过 `RpcClient` 恢复为本地 `BusinessException`。这样业务异常不依赖 Java 异常序列化跨网络传播。

### 2.3 实时事件契约

实时消息分三层：

1. `RealtimeEvent` 描述版本、事件标识、类型、发生时间、来源、trace、资源和扩展数据；
2. `RealtimeResource` 只携带资源类型、资源 ID 和可选实验室 ID；
3. `RealtimeMessage` 增加 `USER`、`LABORATORY`、`BROADCAST` 受众及受众 ID。

`RealtimeChannels` 和 `RealtimeEventTypes` 提供公共频道与稳定事件名。生产者只能发布接收方可兼容的字段；新增可选 `data` 字段优于改变既有字段语义。该模块只定义信封，不决定由 Redis、WebSocket 或其他设施投递。

`UserContextChangedEvent` 是共享的上下文缓存失效通知，仅表达 `UPSERT/DELETE`，不暴露认证模块内部模型。

### 2.4 异步上下文

`AsyncExecutor` 提供固定的 IO、CPU 和调度线程池，队列有界并采用 `CallerRunsPolicy` 形成回压。提交任务时复制完整 SLF4J MDC，执行后恢复线程原上下文，从而延续 trace。它是进程级静态资源：消费者应正确区分 IO/CPU 任务，不应提交永久阻塞任务，也不能把 Future 超时等同于底层操作一定停止。

## 3. `persistence-core`：持久化一致性

### 3.1 自动装配

Spring Boot imports 注册三个配置：

- `MybatisPlusConfig`：注册 MySQL 分页拦截器，页大小上限为 100；把 `UidGenerator` 适配成 MyBatis-Plus `IdentifierGenerator`。
- `PersistenceJacksonConfiguration`：确保 `JavaTimeModule` 注册到应用 `ObjectMapper`，并将同一 mapper 交给 `JacksonTypeHandler`。
- `BusinessTransactionAutoConfiguration`：当名为 `dataSource` 的业务数据源存在且没有其他事务管理器时，创建名为 `transactionManager` 的主事务管理器。

自动装配允许服务提供同类型 Bean 覆盖部分默认行为，但覆盖者必须继续维持业务数据源与 UID 数据源隔离。

### 3.2 实体与主键流

`BaseEntity` 统一字符串 `id` 及 `createAt/updateAt/deleteAt` 字段；时间戳如何填充、软删除是否启用仍由业务实体和服务配置决定。

典型插入路径为：

```text
BaseMapper.insert
  -> IdType.ASSIGN_ID
  -> MyBatis-Plus IdentifierGenerator
  -> UidGenerator.getUID()
  -> 数字 UID 转为实体 String id
  -> 业务 dataSource / transactionManager 写库
```

UID Worker 数据源不参与业务事务。共享层不提供跨库原子事务；任何同时修改业务库与外部系统的流程都必须在业务模块显式选择最终一致性方案。

## 4. `redis`：最小 Redis 访问面

`JedisAutoConfiguration` 在 `lab.redis.enabled=true`（默认）时创建 `JedisPool` 与 `RedisBus`，且二者都允许消费者用同类型 Bean 替换。配置前缀为 `lab.redis`，包含主机、端口、用户名、密码、DB、超时、Key 前缀和连接池参数。

`RedisBus` 对 Key 与频道统一调用 `namespaced`，默认形成 `lab:<key>`，防止不同环境或系统直接共享裸 Key。它提供：

- String：`get/set/set+ttl/delete`；
- Hash：单字段或批量写、TTL、读取、批量 pipeline 读取、存在判断与删除；
- Pub/Sub：`publish/subscribe`，订阅运行在守护线程池，返回 `RedisSubscription` 管理生命周期。

约束与失败边界：

- value/message 均为字符串，序列化格式属于调用方契约；
- 非正 TTL 被视为永久写入，正但不足一秒的 TTL 会按具体方法至少取一秒或由 Jedis 秒级语义处理；
- Hash 写入与设置 TTL 使用 Redis transaction，但不等于业务数据库事务；
- Pub/Sub 是瞬时广播，没有落盘、重放、ack 或消费组保证，断线期间消息可能丢失；
- Jedis 获取连接、网络和处理器异常直接暴露给调用方，不会静默降级；
- `RedisSubscription` 和 `RedisBus` 应随组件/应用关闭，否则会遗留订阅任务。

业务消费者应在自己的模块集中声明 Key/频道与 JSON schema，不要把领域常量逐步堆入 `RedisBus`。

## 5. `uid-springboot-starter`：分布式标识

### 5.1 两个正交选择

配置前缀为 `fun.uid`：

| 维度 | 模式 | 行为 |
| --- | --- | --- |
| Generator | `none` | `DefaultUidGenerator` 按秒生成 |
| Generator | `memory` | `CachedUidGenerator` 预生成到 RingBuffer |
| Assigner | `none` | `DefaultWorkerIdAssigner`，仅适合单机或测试 |
| Assigner | `db` | 独立数据库登记节点并取得 Worker ID |
| Assigner | `redis` | 通过 Spring Data Redis 自增并记录节点 Worker ID |

`UidGeneratorAutoConfigure` 根据 generator mode 创建生成器；若没有 `WorkerIdAssigner`，会回退到本地 assigner。正式多实例必须显式配置 `db` 或 `redis`，并保证相应 Bean 和依赖可用，不能依赖本地回退。

### 5.2 DB 与 Redis 分配

DB 模式创建独立的 `uidDataSource` 和 `uidSqlSessionFactory`，使用 `fun.uid.datasource.*`，通过 `tf_ap_worker_node` 按主机和端口登记节点。分配过程使用原生 MyBatis session 手动 commit/rollback，失败抛出 `UidGenerateException`，不会加入业务事务管理器。

Redis assigner 依赖 Spring Data 的 `RedisConnectionFactory/RedisTemplate`，与 `shared/redis` 的 Jedis `RedisBus` 是两套不同抽象，不能因项目存在 `RedisBus` 就假定 UID Redis 模式已满足装配条件。

### 5.3 位分配与缓存

UID 由时间差、Worker ID 和同秒序列组成。仓库当前约定可参考模块 `README.md` 与 `uid-config.example.yml`；`time-bits + worker-bits + seq-bits`、epoch、实例数量和峰值速率必须整体容量评审。系统产生数据后改变位分配或 epoch 会改变 ID 解释，不能作为普通配置热切换。

memory 模式用 `boost-power` 扩大 RingBuffer，并可用 `schedule-interval` 周期填充。它提高本地取号吞吐，但增加预生成、时钟和进程退出时未消费 ID 的权衡；唯一性仍依赖正确的 Worker ID 分配。

## 6. `observability`：上下文与失败可观测性

### 6.1 自动装配与链路数据流

`TracingAutoConfiguration` 默认开启，创建安全参数渲染器与 `@Traced` 切面。`TraceHttpAutoConfiguration` 仅在 Servlet Web 应用存在时注册最高优先级过滤器，因此纯 RPC 服务无需 Servlet API。

一次 HTTP 到 RPC 的上下文流为：

```text
HTTP X-Trace-Id / X-Request-Id
  -> TraceHttpFilter 校验或生成 ID，写入 MDC 与响应头
  -> 认证层在 UserContextHolder 建立身份上下文
  -> TraceDubboFilter 将 trace-id / request-id 写入 attachment
  -> UserContextDubboFilter 仅传播 user-id
  -> Provider 从 UserContextStore 重新加载当前上下文
  -> 调用完成后恢复/清理 MDC 与 UserContextHolder
```

只传播 `user-id` 是刻意的信任边界：Provider 不接受 Consumer 直接传入的角色和权限快照，而是从公开 `UserContextStore` 重新读取；上下文不存在或已撤销时拒绝调用。该过滤器提供传播与恢复，不负责认证策略本身。

`TraceContext` 接受至多 128 位的安全字符 ID，否则生成 32 位十六进制 trace ID 和 24 位 request ID；`Scope.close()` 恢复进入前 MDC，适配线程复用和嵌套调用。

### 6.2 RPC 错误边界

Provider 侧 `RpcResultDubboFilter` 同时处理同步和异步结果，把认证、授权、业务、参数和未知异常转换为 `RpcError`；未知异常仅向调用方返回安全消息，完整堆栈记录在 Provider 日志。Consumer 侧 `RpcClient` 将结构化错误转换成本地 `BusinessException`，传输失败映射为 503，空或非法响应映射为 502。

RPC 接口应统一返回 `RpcResult<T>`。不要同时依赖“远程抛业务异常”和结构化结果两套协议，也不要把内部堆栈、SQL 或密钥放入 `RpcError.message`。

### 6.3 方法日志与脱敏

`@Traced` 可标在类型或方法上，记录 operation、耗时与异常；参数默认记录，结果默认关闭。`SafeArgumentRenderer` 对 password、token、secret、authorization、cookie、credential、privateKey 等名称脱敏，并限制总长度、集合项数、递归深度和循环引用。

脱敏是防御措施而非数据分级系统：含敏感信息的方法应优先设置 `recordArgs=false`，返回大对象或个人数据时保持 `recordResult=false`。

日志默认可由仓库的 Alloy 采集到 Loki，并在 Grafana 查询。随附 Docker 配置只面向本地开发，未启用认证，不应直接公网暴露。

## 7. 跨模块组合规则

共享能力的组合必须在边界处收敛：

1. 业务模块通过 `persistence-core` 获取统一主键与事务基础，但表、Mapper 和业务一致性仍归业务模块。
2. 业务事件使用 `common` 的信封，并由边缘/实时组件选择 `RedisBus` 等投递设施；`common` 不依赖 Redis。
3. trace 通过 HTTP、Dubbo 和 `AsyncExecutor` 延续；自建线程池或消息消费者必须自行打开/恢复上下文，不能假定 MDC 自动跨线程。
4. 认证上下文只通过公开接口和 user ID 跨服务恢复；共享可观测模块不得复制权限模型。
5. UID 的 Redis assigner 使用 Spring Data Redis，通用 RedisBus 使用 Jedis；除非专门重构并验证，不应把二者混为同一个连接配置。

新增共享能力前应满足三个条件：至少两个模块存在相同的技术需求；抽象不包含业务名词或业务状态机；失败语义和生命周期能够由基础设施层独立说明。否则保留在业务模块。

## 8. 配置索引

```yaml
lab:
  redis:
    enabled: true
    host: localhost
    port: 6379
    database: 0
    timeout-millis: 2000
    key-prefix: lab
    pool:
      max-total: 16
      max-idle: 8
      min-idle: 0
      max-wait-millis: 2000
      test-on-borrow: true
  observability:
    tracing:
      enabled: true
      max-argument-length: 2048
      max-collection-size: 20
      max-depth: 3

fun:
  uid:
    generator-mode: none
    assigner-mode: db
    datasource:
      driver-class-name: com.mysql.cj.jdbc.Driver
      url: jdbc:mysql://localhost:3306/fun_cloud_base
      username: root
      password: change-me
```

生产配置应通过环境变量或密钥系统注入口令，不要提交真实凭据。

## 9. 失败边界总览

| 故障 | 共享层行为 | 消费者责任 |
| --- | --- | --- |
| Redis 不可用 | Jedis 操作抛错，订阅任务结束或异常 | 决定重试、降级或失败，不假定可靠投递 |
| UID Worker 分配失败 | 初始化/首次分配失败，不应生成可能冲突的 ID | 修复 DB/Redis 与实例身份配置 |
| 业务库与 UID 库并存 | 默认事务显式绑定业务 `dataSource` | 不把 UID 库操作纳入业务事务假设 |
| RPC 业务失败 | Provider 返回结构化 `RpcError` | Consumer 使用 `RpcClient` 统一解包 |
| RPC 传输失败 | Consumer 映射为 503 | 在业务边界决定幂等重试 |
| 非法 trace header | 替换为安全随机 ID | 使用响应 trace ID 排障 |
| 异步跨线程 | 仅 `AsyncExecutor` 自动复制 MDC | 自定义执行器显式传播并清理上下文 |

## 10. 扩展与验证规则

- 修改 `common` 的序列化 record、事件名或 RPC 错误类型时，按跨服务协议变更评审并保持向后兼容。
- 扩展 `RedisBus` 仅加入通用 Redis 原语；可靠队列、锁、缓存策略应先形成独立抽象与测试。
- 替换事务管理器、ObjectMapper 或 UID 生成器时，验证自动装配退让条件及多数据源隔离。
- 修改 UID 参数必须做寿命、Worker 容量、每秒序列容量和现存 ID 兼容性计算。
- 新增上下文字段必须明确可信来源、跨线程/跨 RPC 传播方式及 finally 清理路径。
- 日志字段新增前检查敏感性与基数，禁止将完整 token、密码或大型 payload 记录到日志。

重点测试位于各子模块 `src/test`：队列索引一致性、事务管理器绑定、Jackson Java Time、trace scope、HTTP/Dubbo 传播、UserContext 撤销、RPC 错误归一化及日志脱敏。`redis` 与 UID 的多实例/外部依赖行为还需要在集成环境验证。

## 11. 代码导航

```text
shared/
├── common/
│   └── src/main/java/xyz/jasenon/lab/common/
│       ├── AbstractIndexedQueue.java, UniqueQueue.java, ActiveQueue.java
│       ├── realtime/                 # 实时事件与上下文变更契约
│       ├── rpc/                      # RpcResult / RpcError
│       └── util/AsyncExecutor.java   # 有界线程池与 MDC 传播
├── persistence-core/
│   └── src/main/java/xyz/jasenon/lab/persistence/
│       ├── config/                   # MP、Jackson、业务事务自动装配
│       └── model/BaseEntity.java
├── redis/
│   └── src/main/java/xyz/jasenon/lab/redis/
│       ├── config/                   # lab.redis 配置与 Jedis 自动装配
│       └── core/                     # RedisBus 与订阅生命周期
├── uid-springboot-starter/
│   └── src/main/java/io/github/sunjieyi60/uid/starter/
│       ├── config/                   # Generator / DB / Redis 自动装配
│       ├── worker/                   # Worker ID 分配
│       ├── component/                # 默认与缓存生成器
│       └── buffer/                   # RingBuffer 填充
└── observability/
    ├── src/main/java/xyz/jasenon/lab/observability/
    │   ├── context/                  # MDC TraceContext
    │   ├── http/                     # Servlet 入口
    │   ├── dubbo/                    # trace、身份与结果过滤器
    │   ├── rpc/                      # 错误分类与客户端解包
    │   ├── aspect/, annotation/      # @Traced
    │   └── log/                      # 安全参数渲染
    └── docker/                       # Alloy / Loki / Grafana 本地配置
```

更细的运行参数可继续参考 [`observability/README.md`](observability/README.md)、[`uid-springboot-starter/README.md`](uid-springboot-starter/README.md) 和 [`persistence-core/uid-config.example.yml`](persistence-core/uid-config.example.yml)。
