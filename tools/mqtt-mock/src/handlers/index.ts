import { toHex, type Bytes } from "../protocol/bytes.js";
import { deviceTypeByAddress } from "../protocol/device-type.js";
import type { CommandHandler } from "./types.js";
import { accessHandlers } from "./access/index.js";
import { airConditionHandlers } from "./air-condition/index.js";
import { circuitBreakHandlers } from "./circuit-break/index.js";
import { lightHandlers } from "./light/index.js";
import { sensorHandlers } from "./sensor/index.js";

const handlers: CommandHandler[] = [
  ...accessHandlers,
  ...circuitBreakHandlers,
  ...airConditionHandlers,
  ...lightHandlers,
  ...sensorHandlers
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
  const context = { topic, deviceType, address, selfId: resolveSelfId(deviceType, payload) };

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

function resolveSelfId(deviceType: string, payload: Bytes): number | undefined {
  if (deviceType === "AirCondition") {
    return payload.length > 1 ? payload[1] & 0xff : undefined;
  }
  if (deviceType === "Light" || deviceType === "Sensor") {
    return payload.length > 2 ? payload[2] & 0xff : undefined;
  }
  return undefined;
}
