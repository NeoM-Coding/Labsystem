# uid-springboot-starter 分布式发号器

基于 [百度 UIDGenerator](https://github.com/baidu/uid-generator) 的 **Spring Boot 3** Starter，为分布式系统提供高性能、高可用的唯一 ID 生成服务。

代码fork自 [uid-springboot-starter](https://gitee.com/mrdjun/uid-springboot-starter/tree/master)，并在此基础上添加了额外的数据源配置，再次感谢 [mrdjun](https://gitee.com/mrdjun) 。

> 本项目已针对 **Spring Boot 3.x / JDK 17+** 环境进行深度适配与改造。

---

## 核心改进（相比原版）

| 改进项 | 说明 |
|--------|------|
| **Spring Boot 3 自动配置** | 使用 `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` 替代 `spring.factories`，兼容 Spring Boot 3.x |
| **位分配默认值优化** | 默认 `timeBits=31 / workerBits=19 / seqBits=13`，支持约 **68 年**（原版 28/22/13 仅约 8.7 年，已过期） |
| **Epoch 起点更新** | 默认 `2026-04-30`，避免原版 `2016-05-20` 时间戳耗尽问题 |
| **独立数据源** | DB 模式下使用 `fun.uid.datasource` 前缀配置 **独立 DataSource**，不干扰主业务数据库 |
| **编程式事务** | `DatasourceWorkerIdAssigner` 采用 MyBatis 原生编程式事务（手动 `commit/rollback/close`），不依赖 Spring `@Transactional`，确保独立数据源场景下事务边界清晰可控 |

---

## 一、引入依赖

```xml
<dependency>
    <groupId>io.github.sunjieyi60</groupId>
    <artifactId>baidu-uidgenerator-spring-boot3-starter</artifactId>
    <version>1.0</version>
</dependency>
```

---

## 二、快速配置

### 最小配置（默认模式）

无需任何配置即可启动，默认使用 `none` 生成器 + `none` 分配器：

```yaml
fun:
  uid:
    # 生成器模式：none（默认）、memory
    generator-mode: none
    # 分配器模式：none（默认）、db、redis
    assigner-mode: none
```

### DB 模式（生产环境推荐）

DB 模式通过 MySQL 自增主键分配 `workerId`，适合多实例部署：

```yaml
fun:
  uid:
    assigner-mode: db
    generator-mode: none
    # 独立数据源配置（不与 spring.datasource 冲突）
    datasource:
      driver-class-name: com.mysql.cj.jdbc.Driver
      url: jdbc:mysql://localhost:3306/fun_cloud_base?useUnicode=true&characterEncoding=utf-8
      username: root
      password: your_password
```

> DB 模式下，启动时会自动检查并创建 `fun_cloud_base` 数据库和 `tf_ap_worker_node` 表。

### 位分配自定义

如需自定义 UID 位分配（默认 31/19/13）：

```yaml
fun:
  uid:
    # 时间位：31bit ≈ 68 年
    time-bits: 31
    # 机器位：19bit ≈ 52 万台机器
    worker-bits: 19
    # 序列位：13bit = 8192/s
    seq-bits: 13
    # Epoch 起点（建议设为项目启动年份）
    epoch-str: 2026-04-30
```

**位分配公式**：`1(sign) + timeBits + workerBits + seqBits = 64`

### Memory 模式（高吞吐场景）

基于 `RingBuffer` 的缓存生成器，可提供 **600万+/s** 的稳定吞吐量：

```yaml
fun:
  uid:
    generator-mode: memory
    assigner-mode: db
    # RingBuffer 扩容系数
    boost-power: 3
    # 定时填充间隔（秒），0 表示消耗至 50% 后触发填充
    schedule-interval: 0
```

---

## 三、使用方式

### 方式一：直接注入 UidGenerator

```java
@Service
public class OrderService {
    
    @Autowired
    private UidGenerator uidGenerator;
    
    public Long createOrder() {
        long orderId = uidGenerator.getUID();
        // 解析 UID：{"timestamp":"2026-04-30 12:00:00","workerId":"1","sequence":"0"}
        String parsed = uidGenerator.parseUID(orderId);
        return orderId;
    }
}
```

### 方式二：MyBatis Plus 全局 ID 生成器

在引入 `shimi-common` 后，MyBatis Plus 自动使用 `IdGenerator` 作为全局 `IdentifierGenerator`：

```java
@Entity
public class User {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
}
```

插入时自动调用 `UidGenerator.getUID()` 填充 ID。

### 方式三：JPA IdentityGenerator（保留兼容）

```java
import javax.persistence.*;

@Entity
@Table(name = "t_user")
public class UserEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO, generator = "uid-id")
    @GenericGenerator(name = "uid-id", strategy = UidGenerator.JPA_ID)
    private Long id;
}
```

---

## 四、WorkerIdAssigner 模式说明

| 模式 | 机制 | 适用场景 |
|------|------|----------|
| **none** | 随机生成 0~1000 的 workerId | 单机测试，不推荐生产环境 |
| **db** | MySQL 自增主键分配 workerId，支持自动建库建表 | 多实例生产环境（推荐） |
| **redis** | Redis 原子递增分配 workerId | 已有 Redis 基础设施的场景 |

---

## 五、注意事项

1. **Epoch 过期问题**：原版默认 `2016-05-20` + 28bit 时间戳约 8.5 年，已过期。请务必配置 `epoch-str` 为近期日期，或增大 `time-bits`。
2. **位分配校验**：`1 + time-bits + worker-bits + seq-bits` 必须严格等于 64，否则启动时报 `allocate not enough 64 bits`。
3. **DB 模式数据源独立**：`fun.uid.datasource` 是 UidGenerator 专属数据源，与业务数据源完全隔离。
4. **Spring Boot 3 兼容性**：确保使用 `spring-boot-starter` 3.x 版本，自动配置通过 `imports` 文件注册。

---

## 许可证

Apache License 2.0
