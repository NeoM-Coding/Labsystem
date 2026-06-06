import { appendUnsignedSum, verifyUnsignedSum } from "../../protocol/checksum.js";
import type { CommandHandler } from "../types.js";

export const requestAccessDataHandler: CommandHandler = {
  commandLine: "REQUEST_ACCESS_DATA",
  handle(payload, context) {
    if (context.deviceType !== "Access" || payload.length !== 7) {
      return undefined;
    }
    if (payload[1] !== 0x03 || payload[2] !== 0x01) {
      return undefined;
    }
    if (!verifyUnsignedSum(payload)) {
      throw new Error("REQUEST_ACCESS_DATA checksum failed");
    }
    return appendUnsignedSum([context.address, 0x03, 0x01, 0xff, 0xff, 0xff, 0x05]);
  }
};
