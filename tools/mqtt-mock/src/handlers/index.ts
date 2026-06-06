import { toHex, type Bytes } from "../protocol/bytes.js";
import { deviceTypeByAddress } from "../protocol/device-type.js";
import type { CommandHandler } from "./types.js";
import { accessHandlers } from "./access/index.js";

const handlers: CommandHandler[] = [
  ...accessHandlers
];

export interface MockResponse {
  commandLine: string;
  payload: Bytes;
}

export function resolveResponse(topic: string, payload: Bytes): MockResponse | undefined {
  if (payload.length === 0) {
    return undefined;
  }

  const address = payload[0] & 0xff;
  const deviceType = deviceTypeByAddress(address);
  const context = { topic, deviceType, address };

  for (const handler of handlers) {
    try {
      const response = handler.handle(payload, context);
      if (response) {
        return {
          commandLine: handler.commandLine,
          payload: response
        };
      }
    } catch (error) {
      const message = error instanceof Error ? error.message : String(error);
      throw new Error(`${handler.commandLine} failed for ${toHex(payload)}: ${message}`);
    }
  }

  return undefined;
}
