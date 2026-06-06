import { appendUnsignedSum, verifyUnsignedSum } from "../../protocol/checksum.js";
import type { CommandHandler } from "../types.js";

const supportedControlCodes = new Set([0x01, 0x02, 0x03]);

export const accessControlAckHandler: CommandHandler = {
  commandLine: "ACCESS_CONTROL_ACK",
  handle(payload, context) {
    if (context.deviceType !== "Access" || payload.length !== 7) {
      return undefined;
    }
    if (payload[1] !== 0x0a || !supportedControlCodes.has(payload[2] & 0xff)) {
      return undefined;
    }
    if (!verifyUnsignedSum(payload)) {
      throw new Error("ACCESS_CONTROL_ACK checksum failed");
    }
    return appendUnsignedSum([context.address, 0x0a, payload[2] & 0xff]);
  }
};
