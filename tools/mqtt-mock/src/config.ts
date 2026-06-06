export interface MockConfig {
  mqttUrl: string;
  clientId: string;
  username?: string;
  password?: string;
  subscribeTopic: string;
  fallbackReplyTopic: string;
  topicRegex?: string;
  replyTopicTemplate?: string;
  qos: 0 | 1 | 2;
}

function optional(value: string | undefined): string | undefined {
  return value && value.trim().length > 0 ? value : undefined;
}

function qos(value: string | undefined): 0 | 1 | 2 {
  if (value === "1" || value === "2") {
    return Number(value) as 1 | 2;
  }
  return 0;
}

export function loadConfig(env: NodeJS.ProcessEnv = process.env): MockConfig {
  return {
    mqttUrl: env.MQTT_URL ?? "mqtt://localhost:1883",
    clientId: env.MQTT_CLIENT_ID ?? `lab-system-mqtt-mock-${Date.now()}`,
    username: optional(env.MQTT_USERNAME),
    password: optional(env.MQTT_PASSWORD),
    subscribeTopic: env.MQTT_SUBSCRIBE_TOPIC ?? "test/accept",
    fallbackReplyTopic: env.MQTT_REPLY_TOPIC ?? "test/send",
    topicRegex: optional(env.MQTT_TOPIC_REGEX),
    replyTopicTemplate: optional(env.MQTT_REPLY_TOPIC_TEMPLATE),
    qos: qos(env.MQTT_QOS)
  };
}
