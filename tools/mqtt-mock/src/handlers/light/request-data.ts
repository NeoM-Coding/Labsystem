import { appendUnsignedSum, verifyUnsignedSum } from "../../protocol/checksum.js";
import type { CommandHandler } from "../types.js";

export const requestLightDataHandler: CommandHandler = {
  commandLine: "REQUEST_LIGHT_DATA",
  handle(payload, context) {
    if (context.deviceType !== "Light" || context.selfId === undefined || payload.length !== 7) {
      return undefined;
    }
    if (payload[1] !== 0x03 || (payload[2] & 0xff) !== context.selfId) {
      return undefined;
    }
    if (!verifyUnsignedSum(payload)) {
      throw new Error("REQUEST_LIGHT_DATA checksum failed");
    }

    return appendUnsignedSum([context.address, 0x03, context.selfId, 0xff, 0xff, 0x00]);
  }
};
