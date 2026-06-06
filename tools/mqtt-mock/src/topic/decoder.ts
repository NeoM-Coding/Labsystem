import type { MockConfig } from "../config.js";

export interface DecodedTopic {
  requestTopic: string;
  replyTopic: string;
  groups: Record<string, string>;
}

export function decodeTopic(topic: string, config: MockConfig): DecodedTopic {
  if (!config.topicRegex || !config.replyTopicTemplate) {
    return {
      requestTopic: topic,
      replyTopic: config.fallbackReplyTopic,
      groups: {}
    };
  }

  const regex = new RegExp(config.topicRegex);
  const match = regex.exec(topic);
  if (!match) {
    return {
      requestTopic: topic,
      replyTopic: config.fallbackReplyTopic,
      groups: {}
    };
  }

  const groups = match.groups ?? {};
  return {
    requestTopic: topic,
    replyTopic: renderTemplate(config.replyTopicTemplate, groups),
    groups
  };
}

function renderTemplate(template: string, groups: Record<string, string>): string {
  return template.replace(/\$\{(?<name>[A-Za-z_][A-Za-z0-9_]*)}/g, (_, name: string) => {
    return groups[name] ?? "";
  });
}
