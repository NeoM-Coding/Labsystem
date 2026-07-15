import { appendCrc16 } from "../../protocol/checksum.js";
import { ensureDevice, updateDevice } from "../../state/device-store.js";
import type { CommandHandler } from "../types.js";
import { matchesCheckedPrefix } from "../shared/match.js";

export const circuitBreakControlHandler: CommandHandler = {
  commandLine: "CIRCUIT_BREAK_CONTROL",
  handle(payload, context) {
    if (context.deviceType !== "CircuitBreak") {
      return undefined;
    }

    const open = matchesCheckedPrefix(
      payload,
      [context.address, 0x10, 0x00, 0x19, 0x00, 0x01, 0x02, 0x00, 0x01],
      11,
      "CRC16"
    );
    const close = matchesCheckedPrefix(
      payload,
      [context.address, 0x10, 0x00, 0x19, 0x00, 0x01, 0x02, 0x00, 0x00],
      11,
      "CRC16"
    );

    if (!close && !open) {
      return undefined;
    }

    const state = ensureDevice("CircuitBreak", context.address);
    updateDevice(state.key, { opened: open });

    return appendCrc16([context.address, 0x10, 0x00, 0x19, 0x00, 0x01]);
  }
};
