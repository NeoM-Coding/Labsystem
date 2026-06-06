import "dotenv/config";
import mqtt from "mqtt";
import { loadConfig } from "./config.js";
import { toBuffer, toHex, toUnsignedBytes } from "./protocol/bytes.js";
import { resolveResponse } from "./handlers/index.js";
import { decodeTopic } from "./topic/decoder.js";

const config = loadConfig();

const client = mqtt.connect(config.mqttUrl, {
  clientId: config.clientId,
  username: config.username,
  password: config.password,
  clean: true,
  reconnectPeriod: 1000
});

client.on("connect", () => {
  client.subscribe(config.subscribeTopic, { qos: config.qos }, (error) => {
    if (error) {
      console.error(`[mqtt-mock] subscribe failed topic=${config.subscribeTopic}`, error);
      return;
    }
    console.info(`[mqtt-mock] connected url=${config.mqttUrl} subscribe=${config.subscribeTopic}`);
  });
});

client.on("message", (topic, message) => {
  const request = toUnsignedBytes(message);
  const decodedTopic = decodeTopic(topic, config);

  try {
    const response = resolveResponse(topic, request);
    if (!response) {
      console.warn(`[mqtt-mock] unmatched topic=${topic} payload=${toHex(request)}`);
      return;
    }

    client.publish(decodedTopic.replyTopic, toBuffer(response.payload), { qos: config.qos }, (error) => {
      if (error) {
        console.error(`[mqtt-mock] publish failed topic=${decodedTopic.replyTopic}`, error);
        return;
      }
      console.info(
        `[mqtt-mock] ${response.commandLine} ${topic} -> ${decodedTopic.replyTopic} ` +
        `req=[${toHex(request)}] resp=[${toHex(response.payload)}]`
      );
    });
  } catch (error) {
    const message = error instanceof Error ? error.message : String(error);
    console.error(`[mqtt-mock] handle failed topic=${topic} payload=${toHex(request)} ${message}`);
  }
});

client.on("error", (error) => {
  console.error("[mqtt-mock] client error", error);
});

process.once("SIGINT", shutdown);
process.once("SIGTERM", shutdown);

function shutdown(): void {
  client.end(false, {}, () => {
    console.info("[mqtt-mock] stopped");
    process.exit(0);
  });
}
