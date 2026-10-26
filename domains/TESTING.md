# 领域模块单元测试

`src/test` 聚焦当前模块的功能和业务规则：直接实例化被测对象，使用 Mockito 或小型内存替身隔离数据库、缓存、RPC、消息发布等边界。不启动 Spring 容器，不连接外部服务。

- auth：授权决策、关系请求构造、用户上下文和缓存行为。
- base：实验室与用户管理、授权及审计处理、补偿调度和字段加解密。
- edu：课表冲突判断、Excel 导入和处理器规则。
- bi：看板统计与授权规则。
- audit：审计收集、处理器选择、查询过滤和分页。
- mqtt/api：命令参数、序列号规则、校验和及 CRC；协议算法的测试放在算法所属模块。
- mqtt/service：设备和网关管理、请求匹配、任务转换、报文解析及遥测发布。
- rule/engine：规则编译、求值、执行、调度以及持久化生命周期。持久化测试模拟 Mapper 和事务边界，验证版本追加、启停、删除、恢复及提交失败时的内存同步行为，不验证真实数据库事务或 SQL 执行。

保留直接调用配置方法、解析本模块 Mapper XML 等无需容器的测试；`ReflectionTestUtils` 仅用于注入测试依赖，不启动 Spring。

不在这里放置 `@SpringBootTest`、`ApplicationContextRunner`、应用启动冒烟测试、真实 MQTT 链路、数据库集成测试或部署配置与密码生成工具。测试应断言输出、状态变化或边界调用，避免只打印结果或断言类存在。

在 `lab-system-cloud` 下运行全部领域模块及其依赖的测试：

```sh
./mvnw -pl domains/auth/api,domains/auth/service,domains/base/api,domains/base/service,domains/audit/service,domains/edu/service,domains/bi/service,domains/mqtt/api,domains/mqtt/service,domains/rule/engine -am clean test
```

只验证规则持久化生命周期（依赖模块没有同名测试时允许跳过）：

```sh
./mvnw -pl domains/rule/engine -am test -Dtest=RuntimePersistHelperTests -Dsurefire.failIfNoSpecifiedTests=false
```
