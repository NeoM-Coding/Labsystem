# Observability

`observability` 是项目的日志追踪 starter，负责：

- HTTP 请求的 `X-Trace-Id`、`X-Request-Id` 接收、生成和响应回传。
- Dubbo consumer/provider 之间的 trace、request 和 `UserContext` 传播。
- `@Traced` 方法的参数、耗时、结果或异常日志。
- 线程池执行时复制完整 SLF4J MDC。
- 日志文件输出以及 Grafana Alloy、Loki、Grafana 本地日志平台。

## 使用注解

```java
@Traced(value = "laboratory-create", recordArgs = true)
public Laboratory create(Laboratory laboratory) {
    // ...
}
```

`recordResult` 默认关闭，避免返回大对象或敏感信息。参数中的 password、token、secret、authorization、cookie 等字段会被替换为 `***`，同时受深度、集合大小和总长度限制。

```yaml
lab:
  observability:
    tracing:
      enabled: true
      max-argument-length: 2048
      max-collection-size: 20
      max-depth: 3
```

## 启动日志平台

在项目根目录运行：

```bash
docker compose up -d loki alloy grafana
```

应用日志默认写入项目根目录的 `logs/`，也可通过 `LOG_PATH` 修改。打开 `http://localhost:3000`，使用 `admin/admin` 登录 Grafana，在 Explore 中查询：

```logql
{job="lab-system-cloud"} |= "trace_id=目标ID"
```

Alloy UI 位于 `http://localhost:12345`，Loki readiness 地址为 `http://localhost:3100/ready`。该 Compose 仅适合本地开发；Loki 未开启认证，不能直接暴露到公网。
