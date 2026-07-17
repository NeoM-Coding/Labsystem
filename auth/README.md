# Auth Modules

鉴权代码按契约和实现拆分：

- `auth-api`：`PreAuth`、`PostAuth`、权限名称、`UserContext` 和异常，不依赖 Permify SDK。
- `auth`：Permify Java Client、AOP、配置开关和 DSL Schema。

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

`enabled=false` 时不会创建 `AuthClient` 和鉴权切面。`schema-version` 为空时，客户端会在第一次 `grant` 或 `check` 时向 Permify 查询最新版本。

## 注解

常量实体 ID：

```java
@PreAuth(
        entityType = SourceType.laboratory,
        entityId = "lab-1",
        permission = "view"
)
```

从方法参数解析实体 ID：

```java
@PreAuth(
        entityType = SourceType.laboratory,
        entityId = "#laboratoryId",
        idMode = Mode.Sqel,
        permission = "update"
)
```

SpEL 支持参数名、`#p0`、`#a0` 以及参数对象属性，例如 `#command.laboratoryId`。表达式只基于被拦截方法的参数求值。

切面默认使用 `user` 作为 subject type，并从 `UserContextHolder` 中读取 `userId`。`PreAuth` 在方法执行前检查；`PostAuth` 仅在方法正常返回后检查。
