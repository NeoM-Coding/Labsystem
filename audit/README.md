# 管理员操作审计

`audit` 记录管理员可读的业务操作，与 `observability` 中面向运维的链路日志分离。

每条日志表达为“主语-谓语-宾语”：

- 主语：当前 `UserContext` 中的用户。
- 谓语：`AuditAction` 定义的 `CREATE`、`EDIT` 或 `DELETE`。
- 宾语：业务 `AuditLogHandler` 返回的资源类型和资源 ID。

## 接入步骤

1. 在 API 模块中定义实现 `Loggable` 的命令 DTO，`log()` 返回面向管理员的中文描述。
2. 在业务实现模块中添加继承 `AuditLogHandler<T>` 的 Spring Bean，声明谓语和宾语。
3. 在业务方法上添加 `@Audited` 。切面仅在方法成功返回后写入审计日志。

切面会遍历方法的所有实参，根据精确运行时类型查找 Handler。未注册的参数会被忽略，多个可审计参数会聚合到同一条操作记录中。Handler 还可以通过可选的方法返回值补齐创建类命令在调用前尚未生成的资源 ID。

## 运行配置

```yaml
lab:
  audit:
    enabled: true
```

可通过 `AuditLogService` 查询指定用户、动作或资源类型的最近操作。持久化表为 `audit_operation_log`，表结构不包含物理外键。
