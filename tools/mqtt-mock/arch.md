# MQTT 下位机 Mock 核心设计
本文描述 `tools/mqtt-mock` 的实现边界、运行链路和扩展约束。它是面向本地开发与集成验证的下位机模拟器，而不是生产设备网关；跨模块内容仅保留与 Java MQTT 模块对接所需的主题和二进制协议契约。

## 1. 模块职责

MQTT Mock 以 Node.js + TypeScript 实现一组有状态的虚拟下位机，主要完成四件事：

1. 连接 MQTT Broker，订阅后端下发主题，并将匹配结果发布到对应上行主题。
2. 按二进制 payload 的设备地址、子设备编号和指令特征选择 handler。
3. 校验及生成 `UNSIGN_SUM`、`SIGN_SUM`、`CRC16` 校验位，构造与 Java 端当前协议兼容的响应帧。
4. 在内存中维护虚拟设备状态，并通过 Web 页面/API 观察和修改状态。

### 非职责

- 不实现 Java MQTT 模块内部的任务调度、重试、消息关联、记录持久化或 Redis 访问。
- 不充当 MQTT Broker；运行前仍需有可连接的 Broker。
- 不模拟网络抖动、设备离线、响应延迟、并发冲突或完整的真实硬件时序。
- 不持久化设备状态，进程退出后状态丢失。
- 不根据数据库自动发现设备；设备在首次收到合法指令或通过管理页创建时进入内存。

## 2. 进程结构与启动

`src/index.ts` 是唯一运行入口。启动后，同一 Node.js 进程中包含两个协作部分：

```text
                     ┌──────────────────────────────┐
MQTT Broker ────────►│ MQTT client                  │
                     │ subscribe -> decode -> handle│
                     │                    -> publish│
                     └──────────────┬───────────────┘
                                    │
                                    ▼
                           内存 Device Store
                                    ▲
                                    │
                     ┌──────────────┴───────────────┐
Browser ── HTTP ────►│ 静态 Web 页面 + JSON API     │
                     └──────────────────────────────┘
```

启动顺序如下：

1. `dotenv/config` 从当前工作目录加载 `.env`。
2. `loadConfig()` 解析 MQTT、主题、QoS 和 Web 服务配置。
3. 若启用 Web 管理页，HTTP server 开始监听。
4. MQTT client 使用 clean session 连接 Broker；断线后每秒尝试重连。
5. 连接成功后订阅配置的下发主题。
6. 收到 `SIGINT` 或 `SIGTERM` 时，关闭 Web server 和 MQTT client 后退出。

开发模式与生产式运行：

```bash
cd tools/mqtt-mock
npm install

# 构建 Web 资源并监听 TypeScript 源码变化
npm run dev

# 完整构建后启动 dist 产物
npm run build
npm start
```

`npm run build:web` 使用 esbuild 将 React 页面打包到 `dist/public`；`tsc` 只编译 `src/**/*.ts` 到 `dist`，页面的 TSX 由独立 Web 构建脚本处理。因此直接执行 `npm start` 前必须已有完整的 `dist` 产物。

## 3. 配置与主题路由

主要配置见 `.env.example`：

| 配置 | 默认值 | 作用 |
| --- | --- | --- |
| `MQTT_URL` | `mqtt://localhost:1883` | Broker 地址 |
| `MQTT_CLIENT_ID` | 带时间戳的 ID | MQTT 客户端标识 |
| `MQTT_USERNAME` / `MQTT_PASSWORD` | 空 | 可选认证信息 |
| `MQTT_SUBSCRIBE_TOPIC` | `test/accept/+` | 后端向下位机发送指令的主题 |
| `MQTT_REPLY_TOPIC` | `test/send` | 无法动态解析时的兜底响应主题 |
| `MQTT_TOPIC_REGEX` | `^test/accept/(?<topicKey>[^/]+)$` | 从实际请求主题提取命名分组 |
| `MQTT_REPLY_TOPIC_TEMPLATE` | `test/send/${topicKey}` | 使用命名分组生成响应主题 |
| `MQTT_QOS` | `0` | 订阅和发布共同使用的 QoS |
| `MQTT_MOCK_WEB_ENABLED` | 启用 | 是否启动管理服务 |
| `MQTT_MOCK_WEB_HOST` / `PORT` | `127.0.0.1:8787` | 管理服务监听地址 |

订阅主题中完整段的 `*` 会被归一化为 MQTT 标准单层通配符 `+`。此转换只发生在订阅主题，不改变正则或模板。

主题解码由 `src/topic/decoder.ts` 独立完成：正则匹配成功时，把命名捕获组替换进响应模板；未配置正则/模板或匹配失败时，使用 `MQTT_REPLY_TOPIC`。例如：

```text
请求主题：test/accept/gateway-a
捕获结果：topicKey = gateway-a
响应主题：test/send/gateway-a
```

主题只决定消息从哪里接收、向哪里回复，不参与设备类型和指令识别。设备路由完全基于 payload。

## 4. 消息处理主链路

每条 MQTT 消息按以下顺序处理：

```text
Buffer
  -> 转为 0..255 无符号字节数组
  -> payload[0] 映射设备类型
  -> 按设备类型解析可选 selfId
  -> 顺序遍历 handler 注册表
  -> 首个返回响应的 handler 胜出
  -> 响应字节转 Buffer
  -> 发布到主题解码所得 replyTopic
```

空 payload 或没有 handler 匹配时不回复，只输出 unmatched 日志。handler 内抛出的校验或处理错误会被补充指令名和十六进制 payload 后记录，也不会发布响应。单条消息失败不会主动终止进程。

### 设备类型与寻址

设备类型按首字节地址固定映射：

| 地址范围 | 类型 | `selfId` 位置 |
| --- | --- | --- |
| 1–10 | `Access` | 无 |
| 11–30 | `CircuitBreak` | 无 |
| 31–40 | `AirCondition` | `payload[1]` |
| 41–60 | `Light` | `payload[2]` |
| 61–80 | `Sensor` | `payload[2]` |
| 其他 | `Unknown` | 无，不会被现有 handler 处理 |

`selfId` 的位置是设备协议的一部分，不是统一帧头。增加设备类型时必须明确它是否存在以及如何解析。

### Handler 注册与匹配

`src/handlers/index.ts` 将各设备目录导出的数组展平为有序注册表。每个 handler 实现同一接口：

- `commandLine`：日志及诊断使用的稳定指令名。
- `handle(payload, context)`：不匹配时返回 `undefined`，匹配时返回完整响应字节。

handler 应依次检查设备类型、帧长、固定字节/指令字节及校验位。注册表采用“首个响应胜出”，所以规则可能重叠时应把更具体的 handler 放在前面。校验失败的策略目前并不完全统一：部分 handler 对形状匹配但校验失败的帧抛错，公共前缀匹配工具则直接判定为不匹配；扩展时应避免让损坏帧意外落入后续 handler。

当前 handler 分组：

| 设备 | 查询/控制能力 | 校验方式 |
| --- | --- | --- |
| 门禁 | 状态查询、单次开门、开关/锁状态/延时控制确认 | 无符号和 |
| 空气调节器 | 状态查询、开关及模式/温度/风速控制；兼容两种现有帧形态 | 有符号和或 CRC16 |
| 断路器 | 状态查询、分合闸 | CRC16 |
| 灯光 | 状态查询、开关、锁定/解锁 | 无符号和 |
| 传感器 | 温度、湿度、光照、烟雾查询 | 无符号和 |

## 5. 二进制协议边界

Mock 与 Java MQTT 模块共享的是线上字节契约，不共享内部类型或调用流程。兼容边界包括：

- 首字节为设备地址，并通过上述地址段识别设备种类。
- 部分设备使用额外 `selfId` 标识同一地址下的子设备。
- 固定帧长度、指令/功能码、字段偏移与字节序必须与 Java 端编解码保持一致。
- `UNSIGN_SUM` 对齐 Java 端无符号字节求和实现，`SIGN_SUM` 对齐有符号字节求和实现；当前实现均以 `% 0xff` 得到校验字节。
- CRC16 使用初始值 `0xffff`、多项式 `0xa001`，并以低字节在前、高字节在后追加。
- 断路器浮点指标使用 4 字节小端 IEEE 754；传感器整数采用大端 `u16/u32`。

协议变化应同时核对请求匹配和响应生成。只让 handler “能识别请求”并不足够，响应帧长度、状态位、数值缩放、字节序和校验尾也属于契约。

## 6. 设备内存状态

`src/state/device-store.ts` 是 MQTT handler 与管理页面唯一共享的数据源。底层为进程级 `Map<string, ManagedDeviceState>`：

```text
无 selfId：<Type>:<address>
有 selfId：<Type>:<address>:<selfId>
```

查询或控制 handler 调用 `ensureDevice()`：设备不存在时按类型生成确定性默认值，存在时复用当前值。控制指令通过 `updateDevice()` 修改状态，后续查询响应从更新后的状态编码。因此可以用“后端下发控制 -> 再查询 -> 检查回读”的方式验证完整交互。

各类型的可管理状态为：

| 类型 | 状态字段 |
| --- | --- |
| `Access` | `opened`, `locked`, `lockStatus`, `delayTime` |
| `CircuitBreak` | `opened`, `fixed`, `locked`, `voltage`, `current`, `power`, `energy`, `leakage`, `temperature` |
| `AirCondition` | `opened`, `mode`, `temperature`, `speed`, `roomTemperature`, `errorCode` |
| `Light` | `opened`, `locked` |
| `Sensor` | `temperature`, `humidity`, `light`, `smoke` |

默认值会结合 `address`/`selfId` 产生小幅差异，便于同时观察多台设备。`updateDevice()` 只接受该设备 `deviceFieldSpecs` 中声明的字段，忽略 key、类型、地址、更新时间等身份字段；数值会经 `Number()` 转换，枚举仅接受声明的选项。当前没有范围校验，编码响应时通常再截断或舍入到协议宽度。

单设备 reset 与 reset all 都按原身份重建默认状态。所有有效更新会刷新 `updatedAt`，但状态不会写入磁盘或外部存储。

## 7. Web 管理页

Web server 与 MQTT client 在同一进程运行，因此页面修改立即影响下一次协议响应。服务提供 React 单页应用和少量 JSON API：

| 方法与路径 | 作用 |
| --- | --- |
| `GET /api/devices` | 返回设备列表与字段规格 |
| `POST /api/devices` | 按 type/address/selfId 确保设备存在 |
| `PATCH /api/devices/:key` | 修改允许的状态字段 |
| `POST /api/devices/:key/reset` | 重置单台设备 |
| `POST /api/devices/reset` | 重置所有已创建设备 |

页面每 3 秒刷新一次设备列表，按类型分栏展示真实内存状态，可创建设备、编辑字段和重置状态。`AirCondition`、`Light`、`Sensor` 创建时必须提供 `selfId`。

HTTP 层是本地调试工具，不包含鉴权、跨域策略、并发版本控制和持久化。服务默认只监听 `127.0.0.1`；若改为对外网卡监听，应明确其调试性质和访问风险。

静态文件从运行时工作目录下的 `dist/public` 提供。当前路径解析使用 `path.resolve("dist/public")`，因此应在 `tools/mqtt-mock` 目录执行启动命令。

## 8. 扩展新指令

扩展既有设备的一条指令时，建议按以下顺序：

1. 在 `src/handlers/<device-type>/` 新增独立 handler 文件。
2. 明确请求帧长度、固定前缀、可变字段、校验算法及响应字节布局。
3. 先检查 `context.deviceType` 和必要的 `selfId`，不匹配返回 `undefined`。
4. 使用 `src/protocol/checksum.ts` 与 `handlers/shared` 中的公共字节工具，避免复制校验或字节序算法。
5. 需要状态时调用 `ensureDevice()`；控制类指令先更新状态，再按协议生成确认或状态响应。
6. 从设备目录的 `index.ts` 导出，并检查它在数组中的匹配优先级。
7. 用真实请求字节验证：合法帧能回复、错误长度/功能码不匹配、错误校验不产生合法响应、控制后的查询反映新状态。

增加新设备类型还需要同步修改：

- `protocol/device-type.ts` 的类型联合和地址映射；
- `handlers/index.ts` 的 `selfId` 解析规则与 handler 注册；
- `state/device-store.ts` 的状态模型、默认值和字段规格；
- Web 页面的设备类型列表，以及是否要求 `selfId`；
- 本文中的地址和协议说明。

不要为了实现一条新指令引入 Java MQTT 模块的内部服务或调度对象。以捕获的协议样本和明确的线上字节契约作为 Mock 的输入边界。

## 9. 已知限制

- 状态仅存在于单进程内存中；多实例之间不共享。
- handler 没有请求关联 ID；回复顺序由单个 Node.js 事件循环和 MQTT client 行为决定。
- 订阅与发布共用同一个 QoS，且没有 retained message 配置。
- topic 模板缺失的命名分组会替换为空字符串，配置错误不会在启动阶段失败。
- 配置的主题正则在每条消息上重新构造，非法正则会在处理消息时抛错。
- Web API 对设备类型、地址范围、数值范围和 JSON Content-Type 的校验有限。
- `ensureDevice()` 依赖调用方提供与设备类型一致的地址/selfId；Web 页面可创建与地址段不一致的类型，但 MQTT 路由仍以地址段为准。
- 当前没有自动化测试目录；协议变更应至少执行 TypeScript 构建，并用 MQTT 请求/响应样本做回归。

## 10. 代码导航

| 路径 | 作用 |
| --- | --- |
| `src/index.ts` | 进程入口、MQTT 生命周期、主消息链路和退出处理 |
| `src/config.ts` | 环境变量解析、默认值和订阅通配符归一化 |
| `src/topic/decoder.ts` | 请求主题捕获与响应主题模板渲染 |
| `src/protocol/bytes.ts` | Buffer、无符号字节和十六进制日志转换 |
| `src/protocol/checksum.ts` | 三类校验算法及追加/验证函数 |
| `src/protocol/device-type.ts` | 地址到设备类型的映射 |
| `src/handlers/index.ts` | handler 总注册表、设备上下文和首匹配分发 |
| `src/handlers/types.ts` | handler 契约 |
| `src/handlers/<device-type>/` | 各设备请求识别、状态变更和响应编码 |
| `src/handlers/shared/` | 公共匹配、整数/浮点编码辅助函数 |
| `src/state/device-store.ts` | 内存状态模型、默认值、更新与重置 |
| `src/web-server.ts` | HTTP API 和静态资源服务 |
| `src/web/main.tsx` | 管理页面及其轮询、编辑交互 |
| `scripts/build-web.mjs` | React 页面构建与静态入口复制 |
| `.env.example` | 可用运行配置示例 |
