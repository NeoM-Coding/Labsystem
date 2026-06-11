import { appendUnsignedSum, verifyUnsignedSum } from "../../protocol/checksum.js";
import type { CommandHandler } from "../types.js";

export const openAccessOnceHandler: CommandHandler = {
  commandLine: "OPEN_ACCESS_ONCE",
  handle(payload, context) {
    if (context.deviceType !== "Access" || payload.length !== 7) {
      return undefined;
    }
    if (payload[1] !== 0x0a || payload[2] !== 0x02 || payload[3] !== 0xff) {
      return undefined;
    }
    if (!verifyUnsignedSum(payload)) {
      throw new Error("OPEN_ACCESS_ONCE checksum failed");
    }
    return appendUnsignedSum([context.address, 0x0a, 0x02, 0xff]);
  }
};
