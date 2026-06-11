import { appendCrc16 } from "../../protocol/checksum.js";
import type { Bytes } from "../../protocol/bytes.js";
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

    return appendCrc16(circuitBreakStatus(context.address));
  }
};

function circuitBreakStatus(address: number): Bytes {
  const body = new Array<number>(219).fill(0);
  body[0] = address;
  body[1] = 0x03;
  body[2] = 0xe8;
  body[3] = 0x01;
  body[4] = address % 2 === 0 ? 0x03 : 0x01;

  write(body, 7, floatLE(0.12 + address / 1000));
  write(body, 11, floatLE(26.5 + (address % 3)));
  write(body, 55, floatLE(220 + (address % 5)));
  write(body, 119, floatLE(1.2 + (address % 4) / 10));
  write(body, 151, floatLE(260 + address));
  write(body, 215, floatLE(1234.5 + address));
  return body;
}

function write(target: number[], offset: number, bytes: Bytes): void {
  bytes.forEach((value, index) => {
    target[offset + index] = value & 0xff;
  });
}
