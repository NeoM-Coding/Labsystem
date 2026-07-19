# Auth Modules

鉴权代码按契约和实现拆分：

- `auth-api`：权限、关系、Permify 操作契约、`UserContext` 和异常，不依赖 Permify SDK。
- `auth`：Permify Java Client、授权服务、Command Handler、AOP、配置开关和 DSL Schema。

## 配置

```yaml
lab:
  auth:
    permify:
      enabled: true
      base-url: http://localhost:3476
      tenant-id: t1
      schema-version: ""
```

`enabled=false` 时不会创建 `AuthClient` 和鉴权切面。`schema-version` 为空时，客户端会在第一次访问 Permify 时查询最新版本。

## 业务鉴权

业务方法使用 `@ActionAuthorized`，切面从方法参数中查找对应的 `ActionCommandHandler`：

```java
@ActionAuthorized
public Laboratory update(LaboratoryEdit command) {
    // business logic
}
```

每种业务 Command 在服务模块提供 Handler，把它转换成 Permify `ActionCommand`：

```java
public final class LaboratoryEditActionHandler
        extends AbstractAppActionCommandHandler<LaboratoryEdit> {

    public LaboratoryEditActionHandler() {
        super(LaboratoryEdit.class, Action.App.manage_laboratory);
    }
}
```

`ActionAuthorizationAspect` 从 `UserContextHolder` 获取当前用户，聚合全部参数产生的 `ActionCommand`，逐个调用 Permify 检查。没有已注册 Handler 的受保护方法会被视为授权配置错误。
