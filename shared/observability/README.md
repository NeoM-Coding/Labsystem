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

## 可复用 Span 边界

源码位于 `shared/observability`。业务模块只依赖 `Tracing`，不需要引用 OpenTelemetry
的 Span、Scope、Context，也不需要手动调用 start/end。普通 Spring Bean 方法优先使用
`@Traced`（通过代理调用才生效，自调用不生效）；消息边界、队列和合并调度使用下列显式包装：

```java
Tracing.operation("device.update").attribute("device.id", deviceId).run(() -> update());
var result = Tracing.operation("rule.evaluate").get(() -> evaluate());
var value = Tracing.operation("io.read").call(() -> readWithCheckedException());
var future = Tracing.operation("device.send").async(() -> sendAsync());
executor.execute(Tracing.capture().wrap(() -> handle()));
```

每次调用创建一个 Operation，不要共享可变 builder。`run/get/call` 管理同步生命周期；
`async` 在 CompletionStage 完成、失败或取消时结束 span，而不是提交任务时结束。
它返回派生 stage，不向上游转发取消；`@Traced` 则保留原返回对象身份。
异步回调使用 `Tracing.capture().get(...)` 或 `wrap(...)` 恢复上下文与完整 MDC，
执行完成（包括抛异常）后恢复工作线程原上下文。

发布消息在 `.producer().run(...)` 内调用 `Tracing.headers()` 放入消息元数据；
消费消息使用 `.consumer(headers).run(...)` 提取 W3C 上下文。
多条事件合并执行时使用 `.linked(capturedContexts)` 创建新根 span 并关联起因，
最多保留 128 个链接。仅附加诊断所需 ID、状态与原因，不附加完整消息或令牌。
业务捕获异常并转成失败结果时，可调用 `Tracing.failure(error)` 标记当前 span。

SDK 导出默认关闭，不影响原有日志追踪。启用时配置：

```properties
lab.observability.tracing.otlp-enabled=true
```

通过标准环境变量 `OTEL_EXPORTER_OTLP_ENDPOINT`、`OTEL_EXPORTER_OTLP_PROTOCOL`、
`OTEL_TRACES_SAMPLER`、`OTEL_TRACES_SAMPLER_ARG` 设置采集端与采样。
该组件使用 SDK 和已有 HTTP/Dubbo 过滤器，不应再同时初始化另一套全局 SDK。
必须另行配置可用的 OTLP 接收端和 Grafana tracing 数据源；现有 Loki 只存储日志。

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
