import { appendCrc16, appendSignedSum, verifyCrc16, verifySignedSum } from "../../protocol/checksum.js";
import type { CommandHandler } from "../types.js";

export const airConditionControlHandler: CommandHandler = {
  commandLine: "AIR_CONDITION_CONTROL",
  handle(payload, context) {
    if (context.deviceType !== "AirCondition" || context.selfId === undefined) {
      return undefined;
    }

    if (isDocumentControl(payload, context.address, context.selfId)) {
      return [...payload];
    }

    if (isCurrentCommandLineControl(payload, context.address, context.selfId)) {
      const open = payload[2] === 0x01;
      const mode = payload[3] === 0xff ? 0x02 : payload[3] & 0xff;
      const speed = payload[4] === 0xff ? 0x03 : payload[4] & 0xff;
      return appendSignedSum([context.address, context.selfId, open ? 0x01 : 0x00, mode, 25, speed, 24, 0x00]);
    }

    return undefined;
  }
};

function isDocumentControl(payload: readonly number[], address: number, selfId: number): boolean {
  if (payload.length !== 8 || !verifyCrc16(payload)) {
    return false;
  }
  if ((payload[0] & 0xff) !== address || (payload[1] & 0xff) !== selfId) {
    return false;
  }
  if (payload[2] !== 0x0f || payload[3] !== 0xa4 || payload[4] !== 0x00) {
    return false;
  }
  return payload[5] === 0x00 || payload[5] === 0x01;
}

function isCurrentCommandLineControl(payload: readonly number[], address: number, selfId: number): boolean {
  if (payload.length !== 10 || !verifySignedSum(payload)) {
    return false;
  }
  if ((payload[0] & 0xff) !== address || (payload[1] & 0xff) !== selfId) {
    return false;
  }
  return payload[2] === 0x00 || payload[2] === 0x01 || payload[2] === 0xff;
}
