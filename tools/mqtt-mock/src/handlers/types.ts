import type { Bytes } from "../protocol/bytes.js";
import type { DeviceType } from "../protocol/device-type.js";

export interface HandlerContext {
  topic: string;
  deviceType: DeviceType;
  address: number;
}

export interface CommandHandler {
  commandLine: string;
  handle(payload: Bytes, context: HandlerContext): Bytes | undefined;
}
