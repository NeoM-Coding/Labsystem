# 管理员操作审计

`audit` 保存管理员可读的业务操作，和 `observability` 的技术 Trace 分离。

## 1. 当前模型

业务方法使用：

```java
@Audited("laboratory.create")
public RpcResult<Laboratory> create(LaboratoryCreate command) {
    // ...
}
```

切面只在业务方法成功返回后处理：

1. 从 `UserContextHolder` 取得操作人。
2. 遍历方法参数。
3. 按精确运行时类型查找 `AuditLogHandler`。
4. 生成一个或多个 `AuditFragment`。
5. 连同 operation、traceId、requestId 写入 `audit_operation_log`。

失败业务调用不写成功审计，由技术日志记录异常。

## 2. 审计片段

每个片段包含：

- `AuditAction`：CREATE、EDIT、DELETE 等动作。
- objectType：资源类型。
- objectId：资源 ID。
- eventType：Command 类型名称。
- detail：`Loggable.log()` 返回的可读摘要。

创建操作在调用前可能没有 ID，Handler 可重写 `objectId(event, result)` 从返回值补齐。

## 3. 接入步骤

1. API Command 实现 `Loggable`。
2. `log()` 返回简洁、无敏感信息的业务摘要。
3. 在 Provider 模块注册 `AuditLogHandler<Command>`。
4. 在成功写操作上添加 `@Audited("稳定操作名")`。
5. 增加 Handler 与 Aspect 测试。

未注册 Handler 的参数会被忽略；如果整个方法没有可处理参数，则不写审计记录。因此新增注解后必须验证数据库确实产生记录。

## 4. 配置

```yaml
lab:
  audit:
    enabled: true
```

`enabled` 缺省为 true。关闭后不会创建审计 Registry、Store 和 Aspect。

审计 Store 当前使用业务 MySQL，表为 `audit_operation_log`，不声明物理外键。审计写入失败会记录 error，但不会回滚已经成功的核心业务。

## 5. 当前覆盖

已接入：

- 系统用户创建/更新。
- 联系人创建。
- 实验室创建/修改/删除。
- 学期创建/修改/删除。
- 课表创建/修改/删除/清空/导入。

待补：

- 设备和网关 CRUD。
- 轮询启停。
- 单台/批量控制。
- 智能策略创建、更新、启停和删除。

## 6. 分页查询

Web 层通过 `GET /api/audit-logs` 暴露管理员审计查询。接口使用 MyBatis Plus 的 `Page`，分页插件统一复用 `shared/persistence-core`，默认每页 20 条，单页最多 100 条。

支持的筛选字段包括：

- 操作人 ID、用户名、姓名。
- operation、action、objectType、objectId、eventType。
- 描述关键词、traceId、requestId。
- `occurredFrom` 至 `occurredTo` 的发生时间范围，采用 ISO 日期时间格式。

结果按发生时间、审计 ID 倒序排列。查询受全局 `list_audit_logs` 权限保护，系统超级管理员或 `log_viewer` 可使用该能力。

## 7. 安全边界

审计摘要不得包含：

- 明文或摘要密码。
- Session Token、Cookie、Authorization。
- AES Key、MQTT/Nacos/Permify 凭据。
- 完整二进制 payload。
- Excel 文件完整内容。

控制和批量操作只记录目标、指令名称、数量与结果摘要。
