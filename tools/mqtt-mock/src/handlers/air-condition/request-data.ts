import { appendCrc16, appendSignedSum, verifyCrc16, verifySignedSum } from "../../protocol/checksum.js";
import type { CommandHandler } from "../types.js";

export const requestAirConditionDataHandler: CommandHandler = {
  commandLine: "REQUEST_AIR_CONDITION_DATA_RS485",
  handle(payload, context) {
    if (context.deviceType !== "AirCondition" || context.selfId === undefined) {
      return undefined;
    }

    if (isDocumentQuery(payload, context.address, context.selfId)) {
      return appendCrc16([
        context.address,
        context.selfId,
        0x0c,
        0x00,
        0x01,
        0x00,
        0x01,
        0x00,
        0x19,
        0x00,
        0x01,
        0x00,
        0x00,
        0x00,
        0x00
      ]);
    }

    if (isCurrentCommandLineQuery(payload, context.address, context.selfId)) {
      return appendSignedSum([context.address, context.selfId, 0x01, 0x02, 25, 0x03, 24, 0x00]);
    }

    return undefined;
  }
};

function isDocumentQuery(payload: readonly number[], address: number, selfId: number): boolean {
  return payload.length === 8
    && (payload[0] & 0xff) === address
    && (payload[1] & 0xff) === selfId
    && payload[2] === 0x00
    && payload[3] === 0x06
    && payload[4] === 0x00
    && payload[5] === 0x06
    && verifyCrc16(payload);
}

function isCurrentCommandLineQuery(payload: readonly number[], address: number, selfId: number): boolean {
  return payload.length === 10
    && (payload[0] & 0xff) === address
    && (payload[1] & 0xff) === selfId
    && payload.slice(2, 9).every((value) => value === 0xff)
    && verifySignedSum(payload);
}
