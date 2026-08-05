# uid-springboot-starter

仓库内维护的 Baidu UID Generator Spring Boot 3 Starter。它提供 UID Generator、DB/Redis/本地 Worker ID 分配和 MyBatis-Plus 接入所需的基础 Bean。

## 1. 当前项目如何使用

业务服务的典型配置：

```yaml
fun:
  uid:
    assigner-mode: db
    generator-mode: none
    datasource:
      driver-class-name: com.mysql.cj.jdbc.Driver
      url: jdbc:mysql://localhost:3306/fun_cloud_base
      username: root
      password: change-me
```

- `spring.datasource` 连接业务库 `lab_sys`。
- `fun.uid.datasource` 连接 Worker 库 `fun_cloud_base`。
- 两个 DataSource 和 SqlSessionFactory 相互隔离。

`persistence-core.MybatisPlusConfig` 把 `UidGenerator` 注册为 MyBatis-Plus `IdentifierGenerator`。继承 `BaseEntity` 的实体使用：

```java
@TableId(type = IdType.ASSIGN_ID)
private String id;
```

通过 MyBatis-Plus `insert` 时自动获取 UID。业务 Service 不应再手工调用 `getUID()`。

## 2. 模式

### Generator

| `generator-mode` | 实现 | 说明 |
| --- | --- | --- |
| `none` | `DefaultUidGenerator` | 默认，按秒和序列生成 |
| `memory` | `CachedUidGenerator` | 使用 RingBuffer 预填充 |

### Worker Assigner

| `assigner-mode` | 实现 | 适用 |
| --- | --- | --- |
| `none` | `DefaultWorkerIdAssigner` | 单机/测试，本地随机 |
| `db` | `DatasourceWorkerIdAssigner` | 当前多服务部署 |
| `redis` | `RedisWorkerIdAssigner` | 已有 Redis 分配场景 |

正式多实例不能让可能冲突的进程使用 `none`。

## 3. DB Assigner

DB 模式使用独立 `uidDataSource` 和 `uidSqlSessionFactory`。Worker 表：

```text
tf_ap_worker_node
```

Assigner 在表不存在时尝试创建表；创建数据库也有兼容 SQL，但生产环境通常不应给应用账号建库权限。当前 Compose 在 MySQL 初始化阶段预先创建 `fun_cloud_base`，应用只需要连接并维护 Worker 表。

每次服务实例启动会登记节点并取得 Worker ID。`base`、`mqtt`、`rule-engine`、`edu` 的配置保留不同 server/Dubbo 端口，以便节点身份可区分。

## 4. 位分配

当前代码默认值：

```text
timeBits   = 31
workerBits = 19
seqBits    = 13
epoch      = 2026-04-30
```

满足：

```text
1 sign + timeBits + workerBits + seqBits = 64
```

需要覆盖时：

```yaml
fun:
  uid:
    time-bits: 31
    worker-bits: 19
    seq-bits: 13
    epoch-str: 2026-04-30
```

修改位数和 epoch 会改变 UID 可用年限、Worker 数量和单秒序列容量。已经产生业务 ID 后不要随意修改；确需修改时应做容量计算和跨版本兼容评审。

## 5. Memory Generator

```yaml
fun:
  uid:
    generator-mode: memory
    assigner-mode: db
    boost-power: 3
    schedule-interval: 300
```

- `boost-power` 控制 RingBuffer 扩容。
- `schedule-interval > 0` 时启用周期填充。
- 未配置周期时仍可按 buffer 阈值触发填充。

吞吐能力必须在目标硬件和实际位分配上压测，不在文档中承诺固定数字。

## 6. 直接使用

非 MyBatis 场景可以注入：

```java
@Service
public class ExampleService {

    private final UidGenerator uidGenerator;

    public ExampleService(UidGenerator uidGenerator) {
        this.uidGenerator = uidGenerator;
    }

    public long nextId() {
        return uidGenerator.getUID();
    }
}
```

项目业务实体优先使用 `persistence-core` 的统一 MyBatis-Plus 接入，避免同一服务混用多套 ID 策略。

## 7. 自动配置

主要配置类：

- `UidGeneratorAutoConfigure`
- `UidDatasourceAssignerConfigure`
- `UidRedisAssignerConfigure`

注册入口：

```text
META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports
```

`web` 不写业务实体，显式关闭 assigner/generator，并排除 UID 数据源自动配置。

## 8. 测试与排查

需要验证：

- 业务 DataSource 与 uid DataSource 隔离。
- 多实例获得不同 Worker ID。
- Worker 表不存在时初始化行为。
- `BaseMapper.insert` 自动填充字符串 ID。
- epoch/位分配非法时启动失败。
- Redis/DB Assigner 不可用时拒绝启动，而不是回退到随机 Worker。

仓库中的数据源隔离测试位于 MQTT/Persistence 相关测试目录。部署问题优先检查 `UID_DB_URL`、数据库权限和 `tf_ap_worker_node`。

## 9. 来源与许可证

实现基于 Baidu UID Generator 和早期 `uid-springboot-starter` 版本改造，当前适配 Spring Boot 3。许可证见模块/仓库 LICENSE。
