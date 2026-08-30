# Web 模块核心设计

`web` 是实验室系统面向浏览器的接入层。它把 HTTP/WebSocket 请求转换为稳定的 Dubbo API 调用，建立当前用户上下文，并把下游业务结果统一映射为 HTTP 响应。这里不承载实验室、设备、规则、排课或审计领域的业务实现。

## 1. 职责与边界

### 负责

- 暴露 `/api/**` REST API、`/ws/events` WebSocket 端点和 OpenAPI 文档。
- 使用 Sa-Token 识别登录会话，并在每次请求进入时恢复 `UserContext`。
- 将 URL、查询参数、JSON 或上传文件适配成可序列化的 RPC command/query。
- 通过 Dubbo consumer 调用业务服务，并统一解包 `RpcResult`。
- 将业务错误、认证授权错误、RPC 不可用映射为一致的 `R` 响应和 HTTP 状态码。
- 订阅 Redis 实时事件，在本实例内按用户及实验室可见范围分发 WebSocket 消息。
- 在 HTTP → Dubbo 调用链上建立追踪，并保留实时事件携带的 `traceId`。

### 不负责

- 不直接访问业务数据库，也不拥有领域仓储；启动类明确排除了数据源自动配置。
- 不实现权限判定、实验室可见范围计算、设备通信、规则执行、排课冲突检查或审计存储。
- 不在 WebSocket 连接上接受客户端订阅条件；事件范围只能来自可信的 `UserContext`。
- 不复制下游服务的数据模型和业务规则。跨模块依赖以各 `*-api` 中的接口、command/query、view/model 为边界。

## 2. 请求主链路

```text
浏览器请求
  -> Sa-Token Servlet 上下文
  -> UserContextRequestFilter
       -> 从 Sa-Token 取 userId
       -> 从 UserContextStore 恢复可信上下文
       -> 写入线程级 UserContextHolder
  -> Controller 参数适配
  -> RpcClient.call/run
  -> Dubbo API consumer
  -> 下游业务服务
  -> RpcResult 解包
  -> R + DiyResponseEntity（业务码同时作为 HTTP 状态）
  -> finally 清理 UserContextHolder
```

Controller 应保持薄：只处理传输层约束和身份适配，例如以 path ID 覆盖 body ID、把 multipart 文件变成 `byte[]`，不得在这里重做下游业务校验。

## 3. HTTP API 分组

| 分组 | 路径 | 消费的 API 契约 | Web 层处理 |
| --- | --- | --- | --- |
| 会话 | `/api/sessions` | `UserService` | 登录后创建 Sa-Token cookie；查询当前会话；登出下游上下文及本地会话 |
| 用户 | `/api/users` | `UserService` | 用户查询、权限查询、创建、修改、删除；path ID 组装为 RPC command |
| 联系人 | `/api/contacts` | `UserService` | 创建不可登录联系人，返回前执行用户脱敏 |
| 实验室 | `/api/laboratories` | `LaboratoryService` | 可见实验室、筛选选项、成员和 viewer 管理；path ID 优先 |
| MQTT 网关 | `/api/mqtt/gateways` | `MqttGatewayCRUD` | 网关查询、登记、修改、删除 |
| MQTT 设备 | `/api/mqtt/devices` | `MqttDeviceCRUD`、`MqttPollCo`、`MqttTelemetryQuery` | 设备与遥测查询、设备管理、轮询启停 |
| MQTT 任务 | `/api/mqtt/tasks` | `MqttIo` | 同步任务与批量任务；同步调用超时单独放宽到 120 秒 |
| 智能策略 | `/api/smart-strategies` | `SmartStrategyService`、`RuleAlertLogService` | 策略版本管理、启停和告警日志查询 |
| 学期 | `/api/edu/semesters` | `SemesterService` | 学期查询和增删改；更新时 path ID 覆盖 body ID |
| 课表 | `/api/edu/timetables` | `TimetableService` | 课表查询和增删改、清空、Excel 导入；上传先做空文件/大小/读取检查，RPC 只传可序列化字节 |
| 审计日志 | `/api/audit-logs` | `AuditLogService` | 把分页、操作人、资源、链路和时间条件组装为查询对象 |

API 的业务语义由对应 API 模块定义。Web 只保证入参转换、调用和错误边界；例如“可见实验室取交集”“排课冲突”“规则版本刷新”都由下游完成。

## 4. 会话与 UserContext

登录是唯一跳过 `UserContextRequestFilter` 的业务入口：`POST /api/sessions` 先调用 `UserService.authenticate`，再用返回的用户 ID 创建 Sa-Token 会话。token 通过 `HttpOnly`、`SameSite=Lax`、`Path=/` cookie 返回，因此同源 WebSocket 握手也能携带它；生产 HTTPS 应设置 `SESSION_COOKIE_SECURE=true`。

除登录和 OpenAPI/Swagger 路径外，请求过滤流程为：

1. 入口先清空 `UserContextHolder`，防止 Servlet 线程复用导致身份串请求。
2. `CurrentUserIdResolver` 从 Sa-Token 获取登录 ID；未登录时继续请求，由下游认证边界决定是否拒绝。
3. 已有 token 时，从 `UserContextStore` 按 userId 取上下文；找不到则立即返回 401，要求重新登录。
4. 上下文写入 `UserContextHolder`，供 Dubbo 调用传播用户身份和授权范围。
5. `finally` 无条件清理线程上下文。

`UserContextStore` 及其权限内容属于 auth 契约。Web 不推导、扩展或信任客户端提交的实验室范围。

## 5. Dubbo consumer 与失败边界

- consumer 通过 `@DubboReference(check = false)` 注入，使 Web 可以在提供者暂未启动时完成启动；实际请求仍可能失败。
- 全局默认调用超时 5 秒、重试 0 次，避免非幂等写操作被隐式重复执行。课表导入为 30 秒，MQTT 同步任务为 120 秒。
- 所有业务 RPC 都经 `RpcClient.call/run`，由该边界解包 `RpcResult` 并把下游失败转换为本地异常。
- RPC command/query 必须可序列化。Controller 不应把 `MultipartFile`、Servlet request、Spring 类型或本地 lambda 作为远程参数。
- 资源 ID 以 URL path 为权威。更新/删除接口应重新组装下游 command，避免 body 中的 ID 改写目标资源。
- `RpcException` 统一返回 503“下游服务暂不可用”；不能把连接、注册中心或提供者堆栈暴露给客户端。

Web 对 base、mqtt、rule、edu、audit 的依赖止于 API jar。新增跨模块能力时，应先在所属 API 模块形成明确契约，再由 Web 消费，禁止直接依赖实现模块中的 service、repository 或 handler。

## 6. 响应与异常

Controller 的成功响应统一为 `DiyResponseEntity.of(R.success(data))`。`DiyResponseEntity` 使用 `R.code` 作为 HTTP status，保证 HTTP 层与响应体业务码一致。

全局异常映射：

| 异常 | 对外结果 |
| --- | --- |
| `BusinessException` | 保留业务 code、message、data |
| `AuthenticationRequiredException` | 401 |
| `PermissionDeniedException` | 403 |
| `AuthorizationConfigurationException` | 500 |
| `RpcException` | 503，隐藏内部 RPC 细节 |
| 其他异常 | 500 通用提示，完整异常只写服务端日志 |

扩展 Controller 时不要自行捕获并吞掉这些异常，也不要返回另一套 envelope。

## 7. WebSocket 实时事件

实时链路与业务写请求解耦：业务模块把标准 `RealtimeMessage` 发布到 Redis，所有 Web 实例订阅，再只向本实例持有的连接投递。

```text
业务模块 -> Redis RealtimeChannels.EVENTS
                    -> 每个 Web 实例 RealtimeRedisSubscriber
                    -> RealtimeSessionRegistry
                    -> USER / LABORATORY / BROADCAST 收件人计算
                    -> 二次授权检查
                    -> 该用户在本实例上的全部 WebSocket 会话
```

- 端点为 `/ws/events`。握手必须同时具有有效 Sa-Token 用户 ID 和匹配的 `UserContext`，否则返回 401。
- 建连后首先发送 `system.connected`，包含用户 ID、连接 ID 和协议版本。
- 客户端文本和 pong 只更新活跃时间，不改变订阅范围。
- `RealtimeSessionRegistry` 按 user、session、laboratory 建立本机索引；同一用户的多个页面/设备都会收到消息。
- `LABORATORY` 消息先按实验室索引选候选用户，再用最新上下文执行 `canViewLaboratory` 二次检查。`USER` 和 `BROADCAST` 仍只作用于本实例的已认证连接。
- Redis 的用户上下文变更事件会刷新本地索引；上下文删除或无法重新加载时，以 1008（policy violation）关闭该用户连接，避免旧权限继续生效。
- 发送使用固定 4 线程池和 `ConcurrentWebSocketSessionDecorator`。超过发送时间/缓冲限制或发送失败时，以 1013 关闭慢连接。
- 心跳定时发送 ping；超过空闲阈值或传输失败时移除并关闭连接。应用销毁时关闭订阅、调度器、发送线程和全部本地连接。
- 非法 Redis 消息只记录警告并丢弃，不能阻断订阅线程。

`allowed-origins` 中的 `*` 会被主动过滤，并不会开启任意源。新增部署域名必须显式加入白名单。

## 8. 调用追踪

Controller 类或关键方法使用 `@Traced` 建立 Web span，部分 MQTT 调用开启结果记录。RPC 必须继续通过 `RpcClient`，以维持 HTTP → Dubbo 的 trace 上下文传播和一致的结果处理。实时事件使用公共事件协议中的 `traceId`：业务事件保留生产方链路，`system.connected` 使用当前握手/连接上下文可获得的 trace ID。

追踪参数受 `lab.observability.tracing` 的长度、集合大小和深度限制。不要在追踪标签、异常消息或实时 payload 中加入密码、token、完整用户隐私或不受限的大对象。

## 9. 安全边界

- 认证凭据只由 Sa-Token cookie/header 解析；客户端 JSON 中的 userId 不构成身份。
- 授权由下游 auth 拦截与 `UserContext` 负责，Controller 文案不是安全措施。
- path ID 是资源定位的唯一权威；不得直接信任 body 内重复的 ID。
- WebSocket audience 由服务端 Redis 消息和可信上下文决定，客户端不能订阅任意用户或实验室。
- 文件上传在进入 RPC 前受 Servlet 限制与 `lab.edu.import-max-bytes` 双重限制。
- 对外错误隐藏 RPC 拓扑、堆栈、注册中心信息和配置细节。
- OpenAPI 申明的认证 header 名为 `satoken`；Swagger 文档路径免上下文恢复，但这不应扩展为其他匿名业务路径。

## 10. 配置与运行依赖

主要配置位于 `src/main/resources/application.yaml`：

| 配置 | 默认值/作用 |
| --- | --- |
| `WEB_SERVER_PORT` | `8989`，HTTP/WebSocket 端口 |
| `NACOS_HOST` / `NACOS_PORT` | `localhost:8848`，Dubbo 注册和配置中心 |
| `DUBBO_CONSUMER_TIMEOUT` | `5000` ms，全局 RPC 超时 |
| `REDIS_HOST` / `REDIS_PORT` / `REDIS_DATABASE` | Sa-Token、UserContext 与实时总线依赖 |
| `SESSION_COOKIE_SECURE` | 默认 `false`；HTTPS 部署必须设为 `true` |
| `EDU_IMPORT_MAX_BYTES(_VALUE)` | Servlet 与 Controller 两层导入上限 |
| `WEBSOCKET_ALLOWED_ORIGINS` | WebSocket 显式来源白名单 |
| `WEBSOCKET_SEND_TIME_LIMIT_MILLIS` | 单连接发送时间限制 |
| `WEBSOCKET_SEND_BUFFER_BYTES` | 单连接发送缓冲上限 |
| `WEBSOCKET_PING_INTERVAL_MILLIS` | ping 周期，默认 25 秒 |
| `WEBSOCKET_IDLE_TIMEOUT_MILLIS` | 连接空闲上限，默认 60 秒 |
| `PERMIFY_*` | auth 契约所需的权限服务连接参数 |
| `TRACING_*` | 调用追踪开关与参数采样边界 |

Web 自身无数据库。运行时至少需要 Nacos、Redis、Permify（由 auth 使用）及对应 Dubbo provider；`check=false` 仅放宽启动顺序，不代表依赖可缺失。

OpenAPI UI 位于 `/swagger-ui.html`，规范位于 `/v3/api-docs`。`api-docs` Maven profile 可在不检查 provider 的情况下启动 Web 并生成 `target/api.yaml`。

## 11. 扩展规则

新增或修改接口时遵循以下约束：

1. Controller 只依赖所属模块的 `*-api` 和公共契约；跨领域编排应收敛到明确的应用服务，而不是在 Web 中串接多个实现细节。
2. 所有 Dubbo 调用使用可序列化 command/query，并经 `RpcClient.call/run`。
3. 更新/删除资源时以 path ID 重组 command；不要让请求体选择目标资源。
4. 业务错误交给统一异常处理；新增异常类型时补充明确的 HTTP 映射。
5. 新实时事件复用公共 `RealtimeEvent`/`RealtimeMessage` 协议，明确 audience，实验室事件必须经过上下文二次授权。
6. 改变会话、权限索引、慢连接或心跳行为时同步增加对应测试。
7. 新 API 补充 `@Tag`、`@Operation`，并确保 OpenAPI 生成不强依赖在线 provider。

## 12. 代码导航与验证重点

| 位置 | 作用 |
| --- | --- |
| `WebApplication.java` | 无数据源的 Spring Boot / Dubbo consumer 入口 |
| `context/` | Sa-Token 用户解析、请求级 `UserContext` 生命周期 |
| `user/SessionController.java`、`SaTokenSessionManager.java` | 登录、当前会话、登出与 cookie |
| `response/` | 统一响应状态与异常映射 |
| `realtime/` | 握手、连接索引、Redis 订阅、授权分发、心跳 |
| `mqtt/`、`rule/`、`edu/`、`laboratory/`、`user/`、`audit/` | 各 API 的传输层适配 Controller |
| `config/OpenApiConfiguration.java` | API 元数据和 Sa-Token security scheme |
| `src/test/.../contract/DubboSerializationContractTests.java` | RPC API 入参、返回值及嵌套类型的序列化契约 |
| `src/test/.../context`、`realtime`、`user`、`edu` | 上下文清理、握手授权、事件范围、cookie、path ID 和上传边界 |

修改后的最低验证应覆盖模块测试。若改动 RPC DTO，必须保留序列化契约测试；若改动实时分发，至少验证实验室隔离、多会话投递和上下文变更后旧索引失效。
