# lab-system-cloud

实验室综合管理系统后端工程。当前项目是 Spring Boot 3 / Dubbo 3 的多模块 Maven 工程，核心方向是将设备、网关、MQTT 通信、接口契约、规则引擎和 Web 入口拆分开，并使用独立 uid-generator 数据源生成全局主键。

## 模块结构

```text
lab-system-cloud
├── uid-springboot-starter  # 本地 uid-generator starter，支持独立数据源分配 workerId
├── common                  # 轻量通用类型、异常、队列和工具
├── mqtt-api                # MQTT Dubbo 契约、DTO 和二进制命令协议
├── mqtt                    # MQTT 网关客户端、轮询调度、任务队列、设备/网关 Mapper
├── redis                   # Jedis 自动配置、RedisBus、Pub/Sub 和 hash 能力
├── rule-engine             # 事件驱动规则引擎，基于设备字段事件增量推演 runtime/action group
├── web                     # Web 服务入口
├── edu                     # 教学业务服务占位模块
├── tools/mqtt-mock         # Node.js + TypeScript MQTT 下位机 mock
├── sql                     # MySQL schema
└── docs                    # 架构、MQTT、规则引擎和设备请求/响应协议文档
```

## 技术栈

- Java 17
- Spring Boot 3.5.12
- Apache Dubbo 3.3.6
- Nacos，作为 Dubbo 注册中心和配置中心
- MyBatis / MyBatis-Plus 3.5.16
- MySQL Connector/J，业务库和 uid-generator 生产配置使用 MySQL
- H2，单元测试中验证 uid 主键生成和数据源隔离
- Baidu uid-generator，本仓库内置 `uid-springboot-starter`
- Eclipse Paho MQTT Client 1.2.5
- Hutool 5.8.40
- Lombok
- JUnit 5 / Spring Boot Test
- Node.js + TypeScript，用于 MQTT 设备 mock

## 本地基础设施

根目录的 `compose.yml` 提供项目开发所需的完整基础设施：

- MySQL 8.0.44：自动创建 `lab_sys`、`fun_cloud_base`，并导入 `sql/schema.sql`。
- Redis 8.8、EMQX 5.8、Permify 1.6、Nacos 3.2。
- Grafana、Loki、Alloy 日志检索与采集链路。

首次启动建议使用一键部署脚本。脚本会创建 `.env`、启动 Compose、导入数据库
schema、上传 Permify DSL，并初始化默认 super admin 用户与关系：

```bash
cp .env.example .env
./scripts/deploy.sh
```

默认登录账号和预编译 BCrypt 密码位于 `.env`，非本地环境必须修改。
完整说明见 [docs/一键部署与初始化.md](docs/一键部署与初始化.md)。

只需要操作原生 Compose 时仍可执行 `docker compose up -d`，但该方式不会运行
MySQL 用户和 Permify schema/relation bootstrap。

常用入口：

| 服务 | 地址 | 默认凭据 |
| --- | --- | --- |
| EMQX Dashboard | `http://localhost:18083` | `admin/public123` |
| Nacos Console | `http://localhost:8080` | 本地模式关闭鉴权 |
| Permify HTTP | `http://localhost:3476` | 无 |
| Grafana | `http://localhost:3000` | `admin/admin` |
| Alloy | `http://localhost:12345` | 无 |

停止容器使用 `docker compose down`。需要连同本地数据彻底重建时使用 `docker compose down -v`；该命令会删除数据库和日志平台卷。

## 核心设计

### Dubbo 契约

项目不使用 OpenFeign。MQTT 分布式接口契约和命令协议统一放在 `mqtt-api` 模块，例如：

```text
mqtt-api/src/main/java/xyz/jasenon/lab/api/mqtt/MqttIo.java
mqtt-api/src/main/java/xyz/jasenon/lab/api/mqtt/dto/MqttTaskDto.java
```

服务实现模块依赖 `mqtt-api`，由 Dubbo 负责服务暴露、发现和治理。

### 全局 ID

`common` 中的 `MybatisPlusConfig` 接入 uid-generator，为 MyBatis-Plus 的 `ASSIGN_ID` 提供项目级 ID 生成能力。

uid-generator 使用独立数据源，配置前缀为：

```yaml
fun:
  uid:
    assigner-mode: db
    generator-mode: none
    datasource:
      driver-class-name: com.mysql.cj.jdbc.Driver
      url: jdbc:mysql://localhost:3306/fun_cloud_base?useUnicode=true&characterEncoding=utf-8
      username: root
      password: your-password
```

业务数据源仍使用 `spring.datasource`。这两个数据源需要保持隔离，相关测试位于：

```text
mqtt/src/test/java/xyz/jasenon/lab/mqtt/db/UidGeneratorDataSourceIsolationTests.java
```

### MQTT 通信

MQTT 模块围绕 `AbstractSysClient`、`MqttClient`、`MqttCallback`、`SysClientMananger` 和 `SysPollingManager` 构建。

当前设计要点：

- 一个 RS485 网关对应一个 MQTT client。
- 每个 client 内部串行处理请求，保证同一网关链路不会并发发送。
- `userQueue` 处理用户主动请求。
- `pollQueue` 处理后台轮询请求。
- `userQueue` 优先级高于 `pollQueue`。
- `pollQueue` 使用 `ActiveQueue<Poll<MqttTask>>`，防止同一设备重复轮询，并保留 poll 任务 active 状态。
- `PendingRequest` 代表当前正在等待响应的请求。
- `MqttCallback.messageArrived` 收到响应后交给 client 匹配当前请求并完成 future。

更完整的说明见 [docs/mqtt部分设计.md](docs/mqtt部分设计.md)。

### 规则引擎

`rule-engine` 模块实现事件驱动规则推演。它的核心目标是：设备状态变化后，只驱动受影响的表达式叶子节点，并通过独立调度器异步推演命中的 runtime。

当前链路：

1. MQTT 模块解码设备 record 后，将最新状态写入 Redis hash。
2. MQTT 模块发布完整设备快照到 Redis Pub/Sub。
3. rule-engine listener 缓存上一条快照。
4. 首次看到设备时，为快照内所有字段生成事件。
5. 后续只为新增字段或值变化字段生成事件。
6. `Engine` 只为 ACTIVE runtime 路由设备事件，并刷新命中的 `EvalTreeNode` 叶子。
7. `RuntimeLifecycleManager` 按用户配置的有效期主动激活或注销 runtime。
8. `TimeScheduleService` 计算时间窗口边界和 TimePoint，并投递定向 `TimeEvent`。
9. 设备条件组和时间条件组可被多个 ActionGroup 复用；根结果变化后按反向引用生成候选动作组。
10. 调度器保证同一 runtime 单飞；状态事件的候选集合取并集，TimePoint 按 occurrence 保序且不会丢失。
11. `ActionGroupEvaluator` 综合设备条件、时间条件和生命周期，再执行组内 `List<Action>`。
12. `ControlAction` 调用 `MqttIo.asyncSend()`，记录成功/失败数量和最近失败摘要；`ReportAction` 当前保留通知骨架。

当前表达式模型：

- `EvalNode` 是链式原始条件。
- `EvalTreeNode` 是可增量刷新的平衡 transformer 表达式树。
- `fromChain()` 严格按链表顺序左结合计算，不使用 `AND` / `OR` 运算符优先级；内部用 segment tree 压缩高度，避免链式规则形成左倾树。
- 例如 `A OR B AND C` 会被计算为 `(A OR B) AND C`。
- 表达式右值仍以字符串保存，但求值时会根据 `DeviceType + field` 还原为 boolean、数字、enum 或 string。

当前边界：

- `ActionGroup` 通过 ID 引用 Runtime 内可复用的设备条件组和时间条件组。
- `Runtime` 支持 `PENDING / ACTIVE / EXPIRED / CANCELLED` 生命周期。
- `RuntimeRevisionCompiler` 可将 Web JSON revision 校验并编译为共享条件组对象图。
- `RuntimePersistHelper` 通过 MyBatis/MyBatis-Plus Mapper 持久化不可变 revision，并在 rule-engine 重启后自动恢复 enabled Runtime。
- TimeConditionGroup 支持日期范围、星期、普通/跨午夜窗口以及 TimePoint。
- `ControlAction` 已接入 MQTT 异步控制；`ReportAction` 保存用户、通知形式和内容，通知服务接入前使用完整日志展示。
- Action 重试、冷却时间和失败持久化还未实现。
- 时间任务的持久化恢复、misfire 策略和多实例选主尚未实现。

更完整的说明见 [docs/engine设计.md](docs/engine设计.md)、[docs/engine_condition_group改造.md](docs/engine_condition_group改造.md) 和 [docs/runtime持久化与Web配置.md](docs/runtime持久化与Web配置.md)。

`rule-engine` 内置一个配置开关控制的真实链路演示，当前配置已开启。设置
`lab.rule-engine.simple-test.enabled=true` 后，它会等待 MQTT 模块发布的真实 Redis
设备快照，并通过真实 Dubbo `MqttIo.asyncSend()` 执行控制动作。具体设备参数和
链路 Mermaid 见 [docs/engine设计.md](docs/engine设计.md#simpletest-真实链路)。

### 设备协议

设备请求和响应协议见 [docs/设备请求及响应.md](docs/设备请求及响应.md)。

命令模型和校验能力主要在：

```text
common/src/main/java/xyz/jasenon/lab/common/command/
common/src/main/java/xyz/jasenon/lab/common/command/checker/
```

序列匹配规则文件：

```text
common/src/main/resources/seq-rules.seq
mqtt/src/main/resources/seq-rules.seq
mqtt/src/test/resources/seq-rules.seq
```

## 数据库

业务库 schema 位于：

```text
sql/schema.sql
```

当前 schema 目标为 MySQL 8.x，包含：

- `gateway`：网关表，支持 RS485 / Socket 类型。
- `device`：设备表，按 `device_type` 区分 Access、AirCondition、Sensor、CircuitBreak、Light。
- `rule_runtime`：规则元数据、发布状态和生命周期索引。
- `rule_runtime_revision`：带 `enabled` 的不可变完整 JSON revision，条件组与 ActionGroup 通过 ID 关联。

两张规则表的实体均继承 `BaseEntity`：数据库 `id` 由
uid-springboot-starter 和 MyBatis-Plus `ASSIGN_ID` 自动生成，`runtime_id`
单独作为 Engine/Web 使用的业务标识。

注意：uid-generator 的 worker 表属于独立 uid 数据源，不属于业务库。H2 测试使用：

```text
mqtt/src/test/resources/db/uid-generator-schema.sql
```

## 配置

默认配置文件：

```text
common/src/main/resources/application-local.yml
mqtt/src/main/resources/application.yaml
rule-engine/src/main/resources/application.yaml
web/src/main/resources/application.yaml
```

`mqtt` 模块默认配置：

```yaml
server:
  port: 3333

mqtt:
  connect:
    url: tcp://localhost:1883
    qos: at_least_once
  poll:
    interval-millis: 2_000
    timeout-millis: 5_000
    watchdog-interval-millis: 60_000
  gateway:
    watchdog-interval-millis: 60_000
```

本地启动前通常需要确认：

- MySQL 地址、账号、密码
- uid-generator 独立数据库
- Nacos 地址和账号
- MQTT Broker 地址
- RS485 网关 topic 配置

## MQTT Mock

`tools/mqtt-mock` 是 Node.js + TypeScript 的下位机 mock。它订阅后端发送主题，解析 payload，按设备地址区分设备类型，并将固定响应发布回响应主题。

启动方式：

```bash
cd tools/mqtt-mock
npm install
npm run dev
```

`npm run dev` 使用 `tsx watch`，会自动载入 `tools/mqtt-mock/.env` 并监听 TypeScript 源码变化。

常用环境变量：

```text
MQTT_URL=mqtt://localhost:1883
MQTT_SUBSCRIBE_TOPIC=test/accept
MQTT_REPLY_TOPIC=test/send
```

如果 topic 中包含网关 id，可以配置正则提取和回复 topic 模板：

```text
MQTT_SUBSCRIBE_TOPIC=gateway/+/accept
MQTT_TOPIC_REGEX=^gateway/(?<gatewayId>[^/]+)/accept$
MQTT_REPLY_TOPIC_TEMPLATE=gateway/${gatewayId}/send
```

## 构建和测试

项目保留 Maven wrapper，但 `.gitignore` 已忽略 `mvnw`、`mvnw.cmd` 和 `.mvn`。如果本地没有 wrapper，可以使用系统 Maven。

构建全部模块：

```bash
./mvnw clean package
```

运行全部测试：

```bash
./mvnw test
```

运行 MQTT 模块测试：

```bash
./mvnw -pl mqtt -am test
```

运行 rule-engine 模块测试：

```bash
./mvnw -pl rule-engine -am test
```

运行 uid-generator 数据源隔离测试：

```bash
./mvnw -pl mqtt -am -Dtest=UidGeneratorDataSourceIsolationTests -Dsurefire.failIfNoSpecifiedTests=false test
```

运行 MQTT 真实链路集成测试：

```bash
./mvnw -pl mqtt -am -Dtest=MqttClientSendIntegrationTests -Dsurefire.failIfNoSpecifiedTests=false test
```

注意：`MqttClientSendIntegrationTests` 依赖真实 MQTT Broker、真实 topic 配置以及可回复的 mock/设备。

## 启动服务

启动 MQTT 模块：

```bash
./mvnw spring-boot:run -pl mqtt -am
```

启动 Web 模块：

```bash
./mvnw spring-boot:run -pl web -am
```

启动 rule-engine 模块：

```bash
./mvnw spring-boot:run -pl rule-engine -am
```

启动 edu 占位模块：

```bash
./mvnw spring-boot:run -pl edu -am
```

## Git 忽略约定

当前 `.gitignore` 忽略：

- Maven 构建输出：`target/`
- IDE 文件：`.idea/`、`*.iml`、`.vscode/`
- 本地系统文件：`.DS_Store`
- 设备资料目录：`docs/device_docs`
- Maven wrapper 文件：`mvnw`、`mvnw.cmd`、`.mvn`
