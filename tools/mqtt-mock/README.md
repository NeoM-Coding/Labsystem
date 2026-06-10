# MQTT Mock

Node.js + TypeScript 下位机 mock。它订阅后端发送主题，解析二进制 payload，按设备地址区分设备类型，再由指令 handler 生成响应 payload 并发布到响应主题。

## 使用

```bash
cd tools/mqtt-mock
npm install
npm run dev
```

`npm run dev` 使用 `tsx watch`，修改 TypeScript 源码后会自动重启。启动入口会先通过 `dotenv` 载入当前目录下的 `.env`，再创建 MQTT client。

默认配置对应当前集成测试：

```text
MQTT_URL=mqtt://localhost:1883
MQTT_SUBSCRIBE_TOPIC=test/accept/+
MQTT_REPLY_TOPIC=test/send
MQTT_TOPIC_REGEX=^test/accept/(?<topicKey>[^/]+)$
MQTT_REPLY_TOPIC_TEMPLATE=test/send/${topicKey}
```

这表示 mock 会订阅 `test/accept/+`，收到 `test/accept/1` 时回复到 `test/send/1`，收到 `test/accept/gateway-a` 时回复到 `test/send/gateway-a`。

如果习惯写 `test/accept/*`，工具会自动把 `*` 转为 MQTT 标准单层通配符 `+`。

如果真实主题里带更复杂的网关 id，可以使用正则提取并构造回复主题：

```text
MQTT_SUBSCRIBE_TOPIC=gateway/+/accept
MQTT_TOPIC_REGEX=^gateway/(?<gatewayId>[^/]+)/accept$
MQTT_REPLY_TOPIC_TEMPLATE=gateway/${gatewayId}/send
```

## 扩展指令

每条指令建议独立放在 `src/handlers/<device-type>/` 下，并在对应 `index.ts` 导出。handler 只负责：

- 判断是否匹配当前 payload
- 校验 checksum
- 构造响应 payload

公共能力放在 `src/protocol` 和 `src/topic`。
