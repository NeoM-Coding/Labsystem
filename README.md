# lab-system-cloud

实验室综合管理系统后端工程。当前项目是 Spring Boot 3 / Dubbo 3 的多模块 Maven 工程，核心方向是将设备、网关、MQTT 通信、接口契约和 Web 入口拆分开，并使用独立 uid-generator 数据源生成全局主键。

## 模块结构

```text
lab-system-cloud
├── uid-springboot-starter  # 本地 uid-generator starter，支持独立数据源分配 workerId
├── common                  # 公共模型、命令协议、校验器、序列匹配、SetQueue、MyBatis-Plus ID 配置
├── api                     # 分布式接口契约和 DTO
├── mqtt                    # MQTT 网关客户端、轮询调度、任务队列、设备/网关 Mapper
├── web                     # Web 服务入口
├── edu                     # 教学业务服务占位模块
├── quartz                  # 定时任务服务占位模块
├── tools/mqtt-mock         # Node.js + TypeScript MQTT 下位机 mock
├── sql                     # MySQL schema
└── docs                    # MQTT 设计和设备请求/响应协议文档
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

## 核心设计

### Dubbo 契约

项目不使用 OpenFeign。分布式接口契约统一放在 `api` 模块，例如：

```text
api/src/main/java/xyz/jasenon/lab/api/mqtt/MqttIo.java
api/src/main/java/xyz/jasenon/lab/api/mqtt/dto/MqttTaskDto.java
```

服务实现模块依赖 `api` 和 `common`，由 Dubbo 负责服务暴露、发现和治理。

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
- `pollQueue` 使用 `SetQueue<Poll<MqttTask>>`，防止同一设备重复轮询。
- `PendingRequest` 代表当前正在等待响应的请求。
- `MqttCallback.messageArrived` 收到响应后交给 client 匹配当前请求并完成 future。

更完整的说明见 [docs/mqtt部分设计.md](docs/mqtt部分设计.md)。

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

注意：uid-generator 的 worker 表属于独立 uid 数据源，不属于业务库。H2 测试使用：

```text
mqtt/src/test/resources/db/uid-generator-schema.sql
```

## 配置

默认配置文件：

```text
common/src/main/resources/application-local.yml
mqtt/src/main/resources/application.yaml
web/src/main/resources/application.yaml
```

`mqtt` 模块默认配置：

```yaml
server:
  port: 3333

mqtt:
  url: tcp://localhost:1883
  mqtt-qos: at_least_once
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

启动 edu / quartz 占位模块：

```bash
./mvnw spring-boot:run -pl edu -am
./mvnw spring-boot:run -pl quartz -am
```

## Git 忽略约定

当前 `.gitignore` 忽略：

- Maven 构建输出：`target/`
- IDE 文件：`.idea/`、`*.iml`、`.vscode/`
- 本地系统文件：`.DS_Store`
- 设备资料目录：`docs/device_docs`
- Maven wrapper 文件：`mvnw`、`mvnw.cmd`、`.mvn`

