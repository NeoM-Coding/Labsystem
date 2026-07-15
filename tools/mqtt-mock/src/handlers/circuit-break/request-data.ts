import { appendCrc16 } from "../../protocol/checksum.js";
import type { Bytes } from "../../protocol/bytes.js";
import { ensureDevice, type CircuitBreakDeviceState } from "../../state/device-store.js";
import type { CommandHandler } from "../types.js";
import { floatLE } from "../shared/device-state.js";
import { matchesCheckedPrefix } from "../shared/match.js";

export const requestCircuitBreakDataHandler: CommandHandler = {
  commandLine: "REQUEST_CIRCUITBREAK_DATA",
  handle(payload, context) {
    if (context.deviceType !== "CircuitBreak") {
      return undefined;
    }
    if (!matchesCheckedPrefix(payload, [context.address, 0x03, 0x00, 0x18, 0x00, 0x74], 8, "CRC16")) {
      return undefined;
    }

    return appendCrc16(circuitBreakStatus(ensureDevice("CircuitBreak", context.address) as CircuitBreakDeviceState));
  }
};

function circuitBreakStatus(state: CircuitBreakDeviceState): Bytes {
  const body = new Array<number>(219).fill(0);
  body[0] = state.address;
  body[1] = 0x03;
  body[2] = 0xe8;
  body[3] = state.fixed ? 0x01 : 0x00;
  body[4] = (state.opened ? 0x01 : 0x00) | (state.locked ? 0x02 : 0x00);

  write(body, 7, floatLE(state.leakage));
  write(body, 11, floatLE(state.temperature));
  write(body, 55, floatLE(state.voltage));
  write(body, 119, floatLE(state.current));
  write(body, 151, floatLE(state.power));
  write(body, 215, floatLE(state.energy));
  return body;
}

function write(target: number[], offset: number, bytes: Bytes): void {
  bytes.forEach((value, index) => {
    target[offset + index] = value & 0xff;
  });
}
