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
该组件使用独立 SDK 和已有 HTTP/Dubbo 过滤器，不抢占 JVM 全局 OpenTelemetry，
可避免 MySQL 驱动等第三方组件提前读取全局实例造成启动冲突。

## 启动日志平台

在项目根目录运行：

```bash
docker compose up -d loki tempo alloy grafana
```

应用日志默认写入项目根目录的 `logs/`，也可通过 `LOG_PATH` 修改。打开 `http://localhost:3000`，使用 `admin/admin` 登录 Grafana，默认首页为 **Lab System 可观测性**。看板由 Compose 自动导入，也可以从 Dashboards → Lab System 打开。

- **HTTP 请求数**统计经过应用 HTTP 过滤器的请求。服务端错误指 HTTP 5xx 或抛出到请求处理链之外的异常。每个请求计一次，异常错误率为服务端错误请求数除以总请求数。HTTP 4xx 仍计入请求总数。
- **服务端错误与异常日志**汇总各服务的异常。Web、Base、MQTT、规则引擎、教务、BI 各有一个日志面板。红色级别标记和 `⚠` 前缀用于定位异常行。展开日志可通过 TraceID 打开 Tempo。看板中的 **Tempo 错误链路**列出已采样的错误 trace，顶部链接可以打开 Explore 搜索其他链路。

可预见的业务失败通过 `BusinessException` 上报。Dubbo 将失败转换成 `RpcResult`，Web 返回对应的 HTTP 状态、业务码、提示及 Trace ID。数据库唯一键冲突、完整性约束、并发修改、数据服务不可用和其他读写失败有独立业务码。网关主题占用会进一步说明是发送还是接收主题。BI 和补偿任务在任务结果中记录业务码与原因。未分类的异常在服务端记录堆栈，调用端收到通用错误。已采样的失败 RPC span 带有 `rpc.business.code` 和 `rpc.business.type`。

已有部署需要重新构建并重启 Java 服务，应用才会写入 `http_request_completed` 日志。请求指标从新版服务开始产生数据。也可以在 Explore 中按 Trace ID 查询日志：

```logql
{job="lab-system-cloud"} |= "trace_id=目标ID"
```

查看链路时在 Grafana 的 Explore 中选择 `Tempo`，可以按服务名、Span 名或 Trace ID
查询。Compose 默认让 Java 服务经 Alloy 的 `4318` 端口导出，采样率为 25%；
Tempo 数据保留 24 小时。可通过 `OTEL_TRACES_SAMPLER_ARG` 和 Tempo 配置调整。

Alloy UI 位于 `http://localhost:12345`，Tempo readiness 地址为
`http://localhost:3200/ready`，Loki readiness 地址为 `http://localhost:3100/ready`。
该 Compose 仅适合本地开发；这些服务未开启认证，不能直接暴露到公网。
