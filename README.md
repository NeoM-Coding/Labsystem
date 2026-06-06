# lab-system-cloud

实验室综合管理系统后端工程，当前采用 Spring Boot / Spring Cloud 多模块 Maven 结构，按业务能力拆分为公共能力、MQTT 网关通信、Web API、教学业务和定时任务模块。

## 模块结构

```text
lab-system-cloud
├── common   # 公共命令模型、校验、序列匹配、SetQueue 等基础能力
├── mqtt     # MQTT 网关客户端、任务队列、轮询调度、响应匹配
├── web      # Web 服务入口，当前服务名 lab-system-web，默认端口 8989
├── edu      # 教学业务服务
├── quartz   # 定时任务服务
└── docs     # 设计文档和设备协议资料
```

## 技术栈

- Java 17
- Spring Boot 3.5.13
- Spring Cloud 2025.0.0
- Spring Cloud Alibaba 2025.0.0.0
- Nacos Discovery
- OpenFeign / LoadBalancer
- Eclipse Paho MQTT Client 1.2.5
- Hutool 5.8.40
- JUnit 5

## 核心设计

### MQTT 通信

MQTT 模块围绕 `AbstractSysClient`、`MqttClient`、`MqttCallback` 和 `SysClientMananger` 构建。

当前设计要点：

- 每个网关对应一个客户端实例。
- 每个客户端内部有一个 worker 线程，保证同一网关请求串行发送。
- 用户主动请求进入 `userQueue`。
- 后台轮询请求进入 `pollQueue`。
- `userQueue` 优先级高于 `pollQueue`。
- `pollQueue` 使用 `SetQueue<Poll<MqttTask>>`，防止同一设备重复开启轮询。
- 响应通过 `MqttCallback.messageArrived` 进入 `AbstractSysClient.recevive`，只有匹配当前 `PendingRequest` 时才完成其 `Future`。

更完整的 MQTT 设计说明见 [docs/mqtt部分设计.md](docs/mqtt部分设计.md)。

### 命令和响应匹配

`MqttTask` 根据 `CommandLine` 和参数生成底层 payload，并按命令配置追加校验位。

请求和响应的匹配依赖 `SeqGeneratorManager` 中加载的序列规则，规则文件位于：

```text
common/src/main/resources/seq-rules.seq
mqtt/src/main/resources/seq-rules.seq
```

## 环境准备

需要安装：

- JDK 17
- Maven
- Nacos，默认配置示例使用 `127.0.0.1:8848`
- MQTT Broker，用于运行真实 MQTT 集成测试

本仓库忽略 `mvnw` 和 `mvnw.cmd`，推荐使用本机 Maven：

```bash
mvn -version
```

## 构建和测试

构建全部模块：

```bash
mvn clean package
```

运行全部测试：

```bash
mvn test
```

运行指定模块测试：

```bash
mvn test -pl mqtt -am
```

运行 MQTT 真实链路测试中的单个方法：

```bash
mvn test -pl mqtt -am -Dtest=MqttClientSendIntegrationTests#pollQueueAcceptsMultipleAccessDevicesAndSendsEachSerially -Dsurefire.failIfNoSpecifiedTests=false
```

注意：`MqttClientSendIntegrationTests` 会连接真实 MQTT Broker。运行前需要确认测试中的 broker 地址、topic 和认证配置可用。

## 启动服务

Web 模块：

```bash
mvn spring-boot:run -pl web -am
```

MQTT 模块：

```bash
mvn spring-boot:run -pl mqtt -am
```

Edu 模块：

```bash
mvn spring-boot:run -pl edu -am
```

Quartz 模块：

```bash
mvn spring-boot:run -pl quartz -am
```

## Git 忽略约定

当前 `.gitignore` 忽略：

- Maven 构建输出：`target/`
- IDE 文件：`.idea/`、`*.iml`、`.vscode/`
- 本地系统文件：`.DS_Store`
- 设备资料目录：`docs/device_docs`
- Maven wrapper 文件：`mvnw`、`mvnw.cmd`、`.mvn`

如果本地需要 Maven wrapper，可以自行生成或保留在工作区，但不要提交到仓库。

