# Lab System 微服务重构方案

> 基于 Spring Cloud Alibaba + Nacos + Dubbo 的内部 RPC 架构，OpenFeign 仅用于外部系统对接

---

## 一、现状分析

### 1.1 当前架构

当前项目采用 Maven 多模块结构，各模块职责如下：

| 模块 | 端口 | 职责 | 可启动 |
|------|------|------|--------|
| `common` | - | 公共实体、DTO、工具类 | 否（库模块） |
| `cache` | - | 缓存抽象（Caffeine/Redis） | 否（库模块） |
| `service` | 8088 | 主业务 API + Quartz 定时任务 | 是 |
| `mqtt` | 8089 | MQTT 通信服务、设备指令下发 | 是 |
| `class-time-table-rsocket-core` | - | 智慧班牌核心协议 | 否（库模块） |
| `class-time-table-rsocket-server` | 7001,8090 | 智慧班牌服务器 | 是 |

### 1.2 现存问题

1. **模块边界模糊**：`service` 既承载 REST API，又内嵌 Quartz 定时任务，未来业务膨胀后难以独立扩缩容。
2. **调用方式原始**：`service` 通过 HTTP 直连 `mqtt` 的 `/task/add` 接口（`HttpUtil.createPost(Host)`），无服务发现、无负载均衡、无熔断降级。
3. **同步能力缺失**：MQTT 是天然异步协议，当前仅能在 `mqtt` 模块内部通过 `MqttNx`（Redisson 锁 + CountDownLatch）等待设备 ACK，`service` 层无法感知指令是否真正被设备执行成功。
4. **扩展性差**：新增 `edu` 等其他业务模块时，无法复用统一的 RPC 基础设施。

---

## 二、目标架构

### 2.1 设计原则

1. **单一职责**：`web` 模块负责 **REST 网关、路由转发和 WebSocket 统一接入**，**不承担任何业务编排**；`quartz`、`mqtt`、`edu`、`device` 各自独立为可启动服务，业务逻辑下沉到对应领域服务。
2. **通信分层**：
   - **Dubbo（Triple/gRPC）**：**内部服务间调用的唯一协议**。高性能、低延迟，统一超时、重试、熔断策略。
   - **OpenFeign（HTTP）**：**仅用于与外部第三方系统对接**（如学校统一身份认证、教务系统、外部开放平台）。内部服务间不再使用 Feign。
3. **服务注册统一**：全部服务注册到 **Nacos**，作为注册中心和配置中心。
4. **渐进式重构**：先完成核心链路（MQTT 控制）的 Dubbo 化，再逐步迁移查询类接口。
5. **WebSocket 中心化**：`lab-web` 作为唯一对外暴露 HTTP 的服务，统一维护前端 WebSocket 长连接。其他服务（如 `lab-mqtt-service`）通过 Dubbo RPC 调用 `lab-web` 的推送接口，将消息主动送达前端。

### 2.2 模块拆分方案

```
lab-system/
├── lab-parent/                          # 父 POM（依赖管理）
│
├── lab-api/                             # API 契约模块（纯接口 + DTO）
│   ├── src/main/java/
│   │   ├── xyz.jasenon.lab.api.mqtt/    # MqttRemoteService (Dubbo)
│   │   ├── xyz.jasenon.lab.api.edu/     # EduRemoteService (Dubbo)
│   │   ├── xyz.jasenon.lab.api.cache/   # CacheRemoteService (Dubbo)
│   │   ├── xyz.jasenon.lab.api.quartz/  # QuartzRemoteService (Dubbo)
│   │   └── xyz.jasenon.lab.api.device/  # DeviceRemoteService (Dubbo)
│   └── pom.xml
│
├── lab-common/                          # 现有 common 保留（实体、工具类）
├── lab-cache-starter/                   # 现有 cache 升级为自动配置 starter
│
├── lab-web/                             # 新的 REST 网关层（端口 8080）
│   ├── 依赖：lab-api、Dubbo Consumer、Sa-Token、（可选）OpenFeign（仅用于外部对接）
│   ├── 职责：接收所有前端 HTTP/WebSocket 请求，鉴权，参数校验，通过 Dubbo 透传到下游服务；维护 WebSocket Session 映射，为其他服务提供消息推送能力
│   └── 不直接连接数据库，不写 @Service / Facade（纯网关层）
│
├── lab-mqtt-service/                    # 现有 mqtt 升级（端口 8089）
│   ├── 依赖：lab-api、Dubbo Provider、Paho、Redisson
│   ├── 职责：MQTT 连接管理、设备指令下发、状态上报处理
│   └── 暴露 Dubbo 接口：MqttRemoteService
│
├── lab-quartz-service/                  # 从 service 拆分（端口 8087）
│   ├── 依赖：lab-api、Dubbo Consumer、Quartz、MySQL
│   ├── 职责：定时任务调度、条件判断（SpEL）、动作执行
│   └── 动作执行时通过 Dubbo 调用 mqtt / edu / cache / device
│
├── lab-edu-service/                     # 新增教务服务（端口 8086）
│   ├── 依赖：lab-api、Dubbo Provider、MySQL
│   ├── 职责：学期、课程、教师、排课管理
│   └── 暴露 Dubbo 接口：EduRemoteService
│
└── lab-device-service/                  # 现有 service 中的设备管理拆分（端口 8088）
    ├── 依赖：lab-api、Dubbo Provider + Dubbo Consumer（调用 mqtt）、MySQL
    ├── 职责：设备基础数据、RS485/Socket 网关管理、设备控制业务编排
    └── 暴露 Dubbo 接口：DeviceRemoteService
```

### 2.3 服务端口规划

| 服务 | HTTP 端口 | Dubbo 端口 | 对外暴露 HTTP | 说明 |
|------|-----------|------------|---------------|------|
| `lab-web` | 8080 | - | ✅ | 唯一对外 REST 入口 |
| `lab-mqtt-service` | 8089 | 20889 | ❌ | 仅 Dubbo + 内部 MQTT |
| `lab-quartz-service` | 8087 | 20887 | ❌ | 仅 Dubbo |
| `lab-edu-service` | 8086 | 20886 | ❌ | 仅 Dubbo |
| `lab-device-service` | 8088 | 20888 | ❌ | 仅 Dubbo |

> 注：开发调试期可临时开启各服务的 Swagger，生产环境仅 `lab-web` 暴露 HTTP。

---

## 三、技术选型：内部调用统一 Dubbo，外部对接才用 OpenFeign

### 3.1 选型矩阵

| 场景 | 推荐方案 | 理由 |
|------|----------|------|
| 内部服务间所有调用（查询、CRUD、控制、调度） | **Dubbo** | 统一协议，避免 HTTP 的序列化与连接开销；与数据库 IO 相比，RPC 链路应尽量做到零额外损耗 |
| 设备控制（MQTT 下发） | **Dubbo** | 需要同步等待 ACK，适合用 `CompletableFuture` + 自定义超时；Triple 协议性能更高 |
| 定时任务执行动作 | **Dubbo** | 高频、低延迟、需要稳定调用 |
| 缓存读写 | **Dubbo** | 高频、低延迟 |
| 与第三方系统对接（学校 SSO、外部教务平台） | **OpenFeign** | 外部系统通常只暴露 HTTP 接口，Feign 在此场景是必要适配层 |
| 跨模块事务（最终一致） | **Dubbo + Seata** | 若未来有分布式事务需求，Dubbo 集成 Seata 更成熟 |

### 3.2 业务编排下沉到领域服务示例

**业务编排（如"先查设备详情，再下发 MQTT 指令"）不属于 `lab-web` 的职责**，应下沉到 `lab-device-service`：

```java
// lab-device-service 中负责业务编排
@Service
public class DeviceControlFacade {

    // Dubbo：用于需要同步等待 ACK 的 MQTT 控制
    @DubboReference(check = false, timeout = 10000)
    private MqttRemoteService mqttRemoteService;

    // Dubbo：查询设备信息也走内部 RPC
    @DubboReference(check = false)
    private DeviceRemoteService deviceRemoteService;

    public R<MqttSendResult> controlDeviceSync(ControlDeviceRequest request) {
        // 1. Dubbo 查询设备详情
        DeviceVo device = deviceRemoteService.getDevice(request.getDeviceId());
        // 2. Dubbo 同步下发 MQTT 指令并等待设备 ACK
        MqttSendResult result = mqttRemoteService.sendSync(buildMqttRequest(device, request));
        return R.success(result);
    }
}
```

`lab-web` 只做透传：

```java
// lab-web 中只做参数校验和 Dubbo 透传
@RestController
@RequestMapping("/device")
public class DeviceController {

    @DubboReference(check = false, timeout = 10000)
    private DeviceRemoteService deviceRemoteService;

    @PostMapping("/control/sync")
    public R<MqttSendResult> controlSync(@RequestBody @Validated ControlDeviceRequest req) {
        return deviceRemoteService.controlDeviceSync(req);
    }
}
```

---

## 四、Nacos 服务发现配置

### 4.1 父 POM 增加 Spring Cloud Alibaba 依赖管理

```xml
<properties>
    <spring-cloud-alibaba.version>2023.0.1.0</spring-cloud-alibaba.version>
    <spring-cloud.version>2023.0.1</spring-cloud.version>
    <dubbo.version>3.2.14</dubbo.version>
</properties>

<dependencyManagement>
    <dependencies>
        <!-- Spring Cloud Alibaba BOM -->
        <dependency>
            <groupId>com.alibaba.cloud</groupId>
            <artifactId>spring-cloud-alibaba-dependencies</artifactId>
            <version>${spring-cloud-alibaba.version}</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
        <!-- Dubbo BOM -->
        <dependency>
            <groupId>org.apache.dubbo</groupId>
            <artifactId>dubbo-bom</artifactId>
            <version>${dubbo.version}</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>
```

### 4.2 各服务通用 bootstrap.yaml

```yaml
spring:
  application:
    name: lab-web          # 或 lab-mqtt-service / lab-quartz-service 等
  cloud:
    nacos:
      discovery:
        server-addr: 127.0.0.1:8848
        namespace: dev
        group: DEFAULT_GROUP
      config:
        server-addr: 127.0.0.1:8848
        namespace: dev
        group: DEFAULT_GROUP
        file-extension: yaml
        shared-configs:
          - data-id: lab-common.yaml
            group: DEFAULT_GROUP
            refresh: true

# Dubbo 配置（仅 Provider/Consumer 服务需要）
dubbo:
  application:
    name: ${spring.application.name}
    qos-enable: false
  protocol:
    name: tri            # Triple 协议，兼容 gRPC
    port: -1             # 自动分配，或固定 20880+
  registry:
    address: nacos://127.0.0.1:8848?namespace=dev
  consumer:
    check: false
    timeout: 5000
```

---

## 五、核心场景：MQTT 同步调用方案

### 5.1 需求回顾

- `web` 调用 `mqtt` 模块发送消息（QoS1）。
- 嵌入式设备收到后，在**统一订阅主题**回复应答。
- 应答特征：**地址、操作码、自身编号**。
- **Sync 模式**：消息到达时需要回溯到是哪个请求下发的任务，感知操作是否成功。
- **Async 模式**：正常异步请求，不等待结果。
- 目标：把**天然异步的 MQTT 链路**借助 NIO/异步编程思想，变成**同步可感知**的链路。

### 5.2 核心思路：CompletableFuture + 请求-应答映射表

当前 `mqtt` 模块内部已通过 `MqttNx`（Redisson 分布式锁 + CountDownLatch）实现了单进程内的发送-等待。微服务化后，只需在 `mqtt` 模块外再包一层 **Dubbo 服务接口**，将等待结果通过 Dubbo 返回给调用方即可。

**用户要求"借助 NIO 思想"**，因此推荐采用以下设计：

1. **每个请求生成全局唯一 `traceId`**（如 UUID 或雪花算法）。
2. 在 `mqtt` 模块维护一个内存映射表：
   ```java
   ConcurrentHashMap<String, CompletableFuture<MqttResponse>> pendingSyncRequests
   ```
3. `lab-device-service` 通过 **Dubbo 同步调用** `MqttRemoteService.sendSync(request)`。
4. `mqtt` 模块内部：
   - 将 `traceId` 写入 MQTT Payload 的扩展字段（或关联到设备地址+操作码+编号）。
   - 发送 MQTT 消息。
   - 用 `CompletableFuture.get(timeout)` 等待设备 ACK。
5. 当 `MqttAcceptCallback` 收到设备应答时：
   - 解析出 `traceId`（或根据地址+操作码+编号反查）。
   - 从 `pendingSyncRequests` 取出对应的 `CompletableFuture`，调用 `complete()`。
6. 若超时未收到 ACK，则 `completeExceptionally()`，Dubbo 调用返回超时异常。

> 该方案本质上是 **NIO Reactor 模式**在应用层的体现：发送线程不阻塞在 IO 上，而是注册一个 Callback（`CompletableFuture`），由消息到达事件驱动完成。

### 5.3 为什么不用 ThreadLocal 回溯线程？

在微服务 + Dubbo 场景下，**请求线程与处理线程通常不在同一进程**，ThreadLocal 无法跨进程传递。正确的"回溯"方式是：

- **TraceId 全链路传递**：通过 Dubbo Attachment / MDC / OpenTelemetry 传递 `traceId`。
- **请求上下文映射**：用 `traceId` 关联请求和响应，而非依赖操作系统线程 ID。

### 5.4 接口设计

#### 5.4.1 `lab-api` 中的 Dubbo 接口

```java
package xyz.jasenon.lab.api.mqtt;

import java.util.concurrent.CompletableFuture;

public interface MqttRemoteService {

    /**
     * 同步发送：阻塞等待设备 ACK，超时抛出异常
     */
    MqttSendResult sendSync(MqttSendRequest request);

    /**
     * 异步发送：立即返回，不等待 ACK
     */
    void sendAsync(MqttSendRequest request);

    /**
     * Dubbo 原生异步支持：返回 CompletableFuture
     * 调用方可以选择 await 或链式处理
     */
    default CompletableFuture<MqttSendResult> sendAsyncFuture(MqttSendRequest request) {
        return CompletableFuture.supplyAsync(() -> sendSync(request));
    }
}
```

#### 5.4.2 请求/响应 DTO

```java
@Data
public class MqttSendRequest implements Serializable {
    /** 全局唯一追踪 ID，由调用方或网关生成 */
    private String traceId;
    /** 设备类型 */
    private DeviceType deviceType;
    /** 设备 ID */
    private Long deviceId;
    /** RS485 网关 ID */
    private Long rs485Id;
    /** 指令 */
    private CommandLine commandLine;
    /** 参数 */
    private Integer[] args;
    /** 超时时间（毫秒），默认 6000 */
    private Long timeoutMs = 6000L;
}

@Data
public class MqttSendResult implements Serializable {
    private String traceId;
    /** 是否成功收到设备 ACK */
    private boolean success;
    /** 设备返回的原始负载（可选） */
    private byte[] responsePayload;
    /** 耗时毫秒 */
    private Long elapsedMs;
    /** 错误信息 */
    private String errorMsg;
}
```

### 5.5 `lab-mqtt-service` 实现详解

#### 5.5.1 请求-应答映射表（核心）

```java
@Component
@Slf4j
public class MqttPendingRequestManager {

    /** traceId -> CompletableFuture 映射 */
    private final ConcurrentHashMap<String, CompletableFuture<MqttResponseContext>> pendingMap =
            new ConcurrentHashMap<>();

    /**
     * 注册一个待等待的同步请求
     */
    public CompletableFuture<MqttResponseContext> register(String traceId, long timeoutMs) {
        CompletableFuture<MqttResponseContext> future = new CompletableFuture<>();
        pendingMap.put(traceId, future);

        // 注册超时清理，防止内存泄漏
        ScheduledExecutorService cleaner = MqttScheduleConfig.getCleaner();
        cleaner.schedule(() -> {
            CompletableFuture<MqttResponseContext> removed = pendingMap.remove(traceId);
            if (removed != null && !removed.isDone()) {
                removed.completeExceptionally(
                    new TimeoutException("MQTT 设备应答超时，traceId: " + traceId));
                log.warn("[MQTT-SYNC] 请求超时，traceId: {}", traceId);
            }
        }, timeoutMs, TimeUnit.MILLISECONDS);

        return future;
    }

    /**
     * 当收到设备应答时调用
     */
    public void complete(String traceId, MqttResponseContext context) {
        CompletableFuture<MqttResponseContext> future = pendingMap.remove(traceId);
        if (future != null && !future.isDone()) {
            future.complete(context);
            log.info("[MQTT-SYNC] 请求完成，traceId: {}", traceId);
        } else {
            log.warn("[MQTT-SYNC] 收到未知或已超时的应答，traceId: {}", traceId);
        }
    }
}
```

#### 5.5.2 Dubbo Provider 实现

```java
@DubboService(interfaceClass = MqttRemoteService.class, timeout = 10000)
@Service
@Slf4j
public class MqttRemoteServiceImpl implements MqttRemoteService {

    @Autowired
    private TaskProcessorsManage taskProcessorsManage;
    @Autowired
    private MqttTaskExplainer mqttTaskExplainer;
    @Autowired
    private MqttPendingRequestManager pendingManager;

    @Override
    public MqttSendResult sendSync(MqttSendRequest request) {
        long start = System.currentTimeMillis();
        String traceId = request.getTraceId();
        if (StrUtil.isBlank(traceId)) {
            traceId = IdUtil.fastSimpleUUID();
            request.setTraceId(traceId);
        }

        // 1. 构建 MqttTask，将 traceId 附加到任务上下文
        Task task = convertToTask(request);
        task.setSendThreadName(Thread.currentThread().getName());
        MqttTask mqttTask = mqttTaskExplainer.explainTask(task);
        mqttTask.setTraceId(traceId);   // 扩展 MqttTask 字段

        // 2. 注册等待（NIO 思想：先注册回调，再发送）
        CompletableFuture<MqttResponseContext> future =
                pendingManager.register(traceId, request.getTimeoutMs());

        // 3. 提交到任务队列发送
        taskProcessorsManage.addTask(mqttTask);

        try {
            // 4. 阻塞等待设备应答（对外表现为同步，对内是 Future 事件驱动）
            MqttResponseContext context = future.get(request.getTimeoutMs(), TimeUnit.MILLISECONDS);

            long elapsed = System.currentTimeMillis() - start;
            return MqttSendResult.builder()
                    .traceId(traceId)
                    .success(true)
                    .responsePayload(context.getPayload())
                    .elapsedMs(elapsed)
                    .build();
        } catch (TimeoutException | InterruptedException | ExecutionException e) {
            long elapsed = System.currentTimeMillis() - start;
            log.error("[MQTT-SYNC] 同步调用异常，traceId: {}", traceId, e);
            return MqttSendResult.builder()
                    .traceId(traceId)
                    .success(false)
                    .elapsedMs(elapsed)
                    .errorMsg(e.getMessage())
                    .build();
        }
    }

    @Override
    public void sendAsync(MqttSendRequest request) {
        Task task = convertToTask(request);
        MqttTask mqttTask = mqttTaskExplainer.explainTask(task);
        taskProcessorsManage.addTask(mqttTask);
    }

    private Task convertToTask(MqttSendRequest request) {
        // ... 字段转换逻辑
    }
}
```

#### 5.5.3 设备应答回调改造

收到设备应答时，需要把 `traceId` 提取出来，完成对应的 `CompletableFuture`。

假设当前 `MqttAcceptCallback.messageArrived()` 收到消息，解析后进入 `MqttMessageDispatcher`，最终到达具体 Handler（如 `AirConditionMessageHandler`）。

**改造点**：在 Handler 处理完业务逻辑后，检查是否为同步请求的应答：

```java
@Component
public class MqttResponseRouter {

    @Autowired
    private MqttPendingRequestManager pendingManager;

    /**
     * 由各个 MessageHandler 在处理完业务后调用
     *
     * @param payload 设备返回的原始负载
     * @param rs485Id 网关 ID
     */
    public void routeResponse(byte[] payload, Long rs485Id) {
        // 从 payload 中解析 traceId
        // 如果协议不支持直接携带 traceId，可通过 "地址+操作码+自身编号" 反查最近发出的请求
        String traceId = extractTraceId(payload);

        if (StrUtil.isNotBlank(traceId)) {
            pendingManager.complete(traceId,
                    MqttResponseContext.builder()
                            .payload(payload)
                            .rs485Id(rs485Id)
                            .receivedAt(System.currentTimeMillis())
                            .build());
        }
    }

    private String extractTraceId(byte[] payload) {
        // 示例：假设 payload 末尾 16 字节为 traceId
        // 具体实现取决于你的设备通信协议
        // 如果设备协议不支持 traceId，可用 Redis/ZSet 维护 "地址+操作码+编号 -> traceId" 的映射
    }
}
```

> **协议兼容性提示**：如果现有嵌入式设备固件无法在 Payload 中携带 `traceId`，可采用 **"地址 + 操作码 + 自身编号 + 时间窗口"** 作为关联键，在 `mqtt` 模块维护一个 **LRU 映射表**（如 Caffeine `expireAfterWrite(5s)`），发送时记录关联键 -> `traceId`，收到应答时反查。

### 5.6 两种 Sync 模式的对比

| 方案 | 实现方式 | 优点 | 缺点 | 推荐度 |
|------|----------|------|------|--------|
| **A. Dubbo 同步阻塞** | `MqttRemoteService.sendSync()` 内部 `future.get()` | 对调用方完全透明，代码最简单 | Dubbo 线程会被占用到 MQTT ACK 返回 | ⭐⭐⭐ 推荐 |
| **B. Dubbo Async + Web Async** | Dubbo Provider 返回 `CompletableFuture`，`web` 层用 `DeferredResult` / WebFlux | 不阻塞 Dubbo/Web 线程，并发最高 | 代码复杂度稍高，需要前端配合异步回调 | ⭐⭐ 高并发场景推荐 |

**默认推荐方案 A**，因为：
- 当前 `mqtt` 模块内部已经用 `MqttNx` 阻塞等待 ACK，说明业务上能接受这种时延（约 6 秒）。
- `web` 层的设备控制接口调用频率不高，阻塞几条 Dubbo 线程是可以接受的。
- 代码侵入最小，最容易理解和维护。

如果未来设备数量暴增、并发控制请求很高，再升级到 **方案 B**。

---

## 六、WebSocket 统一推送架构

### 6.1 设计背景

在微服务拆分后，只有 `lab-web` 对外暴露 HTTP 端口。前端页面需要实时接收设备状态、MQTT 轮询结果等消息。最合理的做法是：

- **前端 WebSocket 统一连接到 `lab-web`**（如 `ws://gateway.lab-system/ws`）。
- **`lab-web` 维护 `userId -> WebSocketSession` 的映射**。
- **下游服务通过 Dubbo 调用 `lab-web` 的推送接口**，将消息推送到指定用户或广播到所有在线用户。

### 6.2 核心思路：Dubbo Provider in `lab-web`

`lab-web` 虽然是对外的网关层，但它可以同时作为 **Dubbo Provider**，暴露一个 `WebSocketPushService` 接口，供内部服务调用：

```java
// lab-api 中定义
public interface WebSocketPushService {
    /** 推送给指定用户 */
    void pushToUser(String userId, String message);
    /** 推送给指定会话 */
    void pushToSession(String sessionId, String message);
    /** 广播给所有在线用户 */
    void broadcast(String message);
}
```

### 6.3 `lab-web` 中的 WebSocket 与 Dubbo Provider 实现

```java
@Component
@Slf4j
public class WebSocketSessionManager {

    /** userId -> Session */
    private final ConcurrentHashMap<String, Session> userSessionMap = new ConcurrentHashMap<>();

    public void register(String userId, Session session) {
        userSessionMap.put(userId, session);
        log.info("[WebSocket] 用户上线，userId: {}", userId);
    }

    public void remove(String userId) {
        userSessionMap.remove(userId);
        log.info("[WebSocket] 用户下线，userId: {}", userId);
    }

    public void sendToUser(String userId, String message) {
        Session session = userSessionMap.get(userId);
        if (session != null && session.isOpen()) {
            try {
                session.getBasicRemote().sendText(message);
            } catch (IOException e) {
                log.error("[WebSocket] 推送消息失败，userId: {}", userId, e);
            }
        }
    }

    public void broadcast(String message) {
        userSessionMap.values().forEach(session -> {
            if (session.isOpen()) {
                try {
                    session.getBasicRemote().sendText(message);
                } catch (IOException e) {
                    log.error("[WebSocket] 广播消息失败", e);
                }
            }
        });
    }
}
```

```java
@ServerEndpoint("/ws/{userId}")
@Component
public class LabWebSocketHandler {

    @Autowired
    private WebSocketSessionManager sessionManager;

    @OnOpen
    public void onOpen(Session session, @PathParam("userId") String userId) {
        sessionManager.register(userId, session);
    }

    @OnClose
    public void onClose(Session session, @PathParam("userId") String userId) {
        sessionManager.remove(userId);
    }

    @OnMessage
    public void onMessage(String message, Session session) {
        // 可选：处理前端心跳或订阅指令
    }
}
```

```java
@DubboService(interfaceClass = WebSocketPushService.class)
@Component
public class WebSocketPushServiceImpl implements WebSocketPushService {

    @Autowired
    private WebSocketSessionManager sessionManager;

    @Override
    public void pushToUser(String userId, String message) {
        sessionManager.sendToUser(userId, message);
    }

    @Override
    public void pushToSession(String sessionId, String message) {
        // 若按 sessionId 维护映射，可同理实现
    }

    @Override
    public void broadcast(String message) {
        sessionManager.broadcast(message);
    }
}
```

### 6.4 `lab-mqtt-service` 主动推送示例

当 `lab-mqtt-service` 收到设备轮询消息（如传感器数据上报）时，通过 Dubbo 调用 `lab-web` 的 `WebSocketPushService`，将数据实时推送给前端：

```java
@Service
@Slf4j
public class SensorMessageHandler {

    // Dubbo 调用 lab-web 的推送服务
    @DubboReference(check = false)
    private WebSocketPushService webSocketPushService;

    public void handle(SensorRecord record) {
        // 1. 保存数据到数据库（或转发给其他服务）
        // ...

        // 2. 构造推送消息（例如 JSON）
        String payload = JSONUtil.toJsonStr(new SensorRealtimeVo(record));

        // 3. 推送给关注该实验室的所有在线用户（或广播）
        // 简化示例：广播；实际可按实验室 ID 查找在线用户列表后点对点推送
        webSocketPushService.broadcast(payload);
        log.info("[MQTT] 传感器数据已推送到 WebSocket，deviceId: {}", record.getDeviceId());
    }
}
```

### 6.5 集群环境下的 Session 共享（可选）

如果 `lab-web` 部署多实例，前端 WebSocket 连接会分散到不同节点。此时需要：

1. **按用户 ID 做粘性会话（Session Affinity）**：负载均衡器（如 Nginx、Gateway）按 `userId` 哈希路由，确保同一用户总是连到同一 `lab-web` 实例。
2. **或引入 Redis Pub/Sub**：`lab-web` 各实例订阅 Redis 频道，当某个实例收到 Dubbo 推送调用时，若本地无该用户 Session，则通过 Redis 广播让持有该 Session 的实例代为发送。

**初始阶段建议采用方案 1（粘性会话）**，实现最简单，不引入额外中间件。

---

## 七、`lab-web` 层调用示例（变薄，只做透传）

### 7.1 同步控制（透传到 lab-device-service）

```java
@RestController
@RequestMapping("/device")
public class DeviceControlController {

    @DubboReference(check = false, timeout = 10000)
    private DeviceRemoteService deviceRemoteService;

    @PostMapping("/control/sync")
    public R<MqttSendResult> controlSync(@RequestBody @Validated ControlDeviceRequest req) {
        // lab-web 只做参数校验和 Dubbo 透传，不编排业务
        return deviceRemoteService.controlDeviceSync(req);
    }
}
```

### 7.2 异步控制（透传到 lab-device-service）

```java
@PostMapping("/control/async")
public R<Void> controlAsync(@RequestBody @Validated ControlDeviceRequest req) {
    deviceRemoteService.controlDeviceAsync(req);
    return R.success(null, "控制任务已异步下发");
}
```

### 7.3 教务查询（透传到 lab-edu-service）

```java
@RestController
@RequestMapping("/edu")
public class EduController {

    @DubboReference(check = false)
    private EduRemoteService eduRemoteService;

    @GetMapping("/course-schedule/{id}")
    public R<CourseScheduleVo> getCourseSchedule(@PathVariable Long id) {
        return eduRemoteService.getCourseSchedule(id);
    }
}
```

> `lab-web` 中**不再出现** `@Autowired private SomeFeignClient`，内部查询统一走 Dubbo。

---

## 八、迁移路线图

### Phase 1：基础设施搭建（1 周）

1. 创建 `lab-api` 模块，迁移公共 DTO 和 RPC 接口定义。
2. 引入 Nacos 依赖，搭建 Nacos Server（单机或集群）。
3. 父 POM 引入 `spring-cloud-alibaba-dependencies` 和 `dubbo-bom`。
4. 将 `cache` 模块改造为 `lab-cache-starter`（自动配置）。

### Phase 2：MQTT 服务改造（1 周）

1. 将现有 `mqtt` 模块重命名为 `lab-mqtt-service`。
2. 删除 HTTP 接口 `/task/add`（或标记为 `@Deprecated`）。
3. 实现 `MqttRemoteService` Dubbo Provider。
4. 引入 `MqttPendingRequestManager` 和 `MqttResponseRouter`。
5. 自测：用 Dubbo 泛化调用测试 `sendSync` / `sendAsync`。

### Phase 3：Web 网关层搭建（1 周）

1. 新建 `lab-web` 模块。
2. 迁移 `service` 中所有 `Controller` 到 `lab-web`。
3. 将 Controller 中的 Service 调用替换为 **Dubbo 透传调用**；如有业务编排逻辑，下沉到对应的 `lab-device-service` / `lab-edu-service`。
4. 配置 Sa-Token 网关鉴权（或保留在 `lab-web` 中做统一鉴权）。

### Phase 4：业务服务拆分（2 周）

1. 新建 `lab-quartz-service`，将 `service` 中的 `quartz` 包整体迁移。
2. 新建 `lab-device-service`，将 `service` 中的设备管理、用户管理、实验室管理迁移，同时将需要 MQTT 同步控制的业务编排下沉到此服务。
3. 新建 `lab-edu-service`，将课表相关逻辑迁移。
4. `service` 模块逐步下线，最终删除。

### Phase 5：优化与灰度（1 周）

1. 接入 Sentinel 做流量控制（可选）。
2. 配置 Dubbo 线程池、连接数优化。
3. 完善链路追踪（SkyWalking / Micrometer Tracing）。

---

## 九、风险与应对

| 风险 | 应对措施 |
|------|----------|
| 设备固件不支持 traceId | 采用 "地址+操作码+编号+时间窗口" 作为关联键，用 Caffeine 缓存映射 |
| Dubbo 同步调用超时导致线程池耗尽 | 设置合理的 `timeout`（如 8s），并限制前端并发；必要时升级到 Async 模式 |
| Nacos 单点故障 | 生产环境部署 Nacos 集群（3 节点） |
| 微服务拆分后本地调试困难 | 保留 `spring.profiles.active=local` 的直连模式，开发期绕过 Nacos |
| 现有 HTTP 调用链过多 | 渐进式重构，先改核心链路（MQTT 控制），查询类接口逐步迁移到 Dubbo |

---

## 十、总结

本次重构的核心目标是：**通过 Spring Cloud Alibaba + Nacos + Dubbo 将现有单体多模块系统演化为真正的微服务体系**。

针对 **MQTT 同步调用**这一核心场景，推荐采用以下设计：

1. **`lab-web` 变薄**：只做 REST 网关（鉴权、校验、路由转发），**不写任何业务编排代码**。
2. **内部通信统一 Dubbo**：查询、CRUD、控制、调度全部走 Dubbo，避免 HTTP 的额外序列化和连接开销。
3. **OpenFeign 仅用于外部对接**：学校 SSO、第三方开放平台等必须走 HTTP 的场景才保留 Feign。
4. **Dubbo 作为 RPC 层**：`lab-device-service` -> `lab-mqtt-service` 走 Dubbo，利用其高性能和完善的超时机制。
5. **CompletableFuture + 请求映射表**：在 `mqtt` 模块内用内存映射表注册等待中的同步请求，收到设备 ACK 后 `complete()`。
6. **对外表现为同步**：`MqttRemoteService.sendSync()` 内部阻塞等待，对调用方完全透明。
7. **TraceId 全链路传递**：替代 ThreadLocal 实现跨进程、跨线程的"任务回溯"。

这样，你就能把 MQTT 这条"天然异步"的链路，优雅地改造成**同步可感知、可追溯、可超时控制**的稳定链路，同时让微服务的职责边界真正清晰。
