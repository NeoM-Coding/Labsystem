import { appendCrc16, appendSignedSum, verifyCrc16, verifySignedSum } from "../../protocol/checksum.js";
import { ensureDevice, type AirConditionDeviceState } from "../../state/device-store.js";
import type { CommandHandler } from "../types.js";

export const requestAirConditionDataHandler: CommandHandler = {
  commandLine: "REQUEST_AIR_CONDITION_DATA_RS485",
  handle(payload, context) {
    if (context.deviceType !== "AirCondition" || context.selfId === undefined) {
      return undefined;
    }

    if (isDocumentQuery(payload, context.address, context.selfId)) {
      const state = ensureDevice("AirCondition", context.address, context.selfId) as AirConditionDeviceState;
      return appendCrc16([
        context.address,
        context.selfId,
        0x0c,
        0x00,
        0x01,
        0x00,
        state.opened ? 0x01 : 0x00,
        0x00,
        state.temperature & 0xff,
        0x00,
        speedByte(state.speed),
        0x00,
        0x00,
        0x00,
        0x00
      ]);
    }

    if (isCurrentCommandLineQuery(payload, context.address, context.selfId)) {
      return appendSignedSum(airConditionStatus(ensureDevice("AirCondition", context.address, context.selfId) as AirConditionDeviceState));
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

function airConditionStatus(state: AirConditionDeviceState): number[] {
  return [
    state.address,
    state.selfId,
    state.opened ? 0x01 : 0x00,
    modeByte(state.mode),
    state.temperature & 0xff,
    speedByte(state.speed),
    state.roomTemperature & 0xff,
    state.errorCode & 0xff
  ];
}

function modeByte(mode: AirConditionDeviceState["mode"]): number {
  return { Heating: 0x01, Cooling: 0x02, AirSupply: 0x04, Dehumidification: 0x08 }[mode];
}

function speedByte(speed: AirConditionDeviceState["speed"]): number {
  return { Auto: 0x00, Low: 0x01, Middle: 0x02, High: 0x03 }[speed];
}

function isCurrentCommandLineQuery(payload: readonly number[], address: number, selfId: number): boolean {
  return payload.length === 10
    && (payload[0] & 0xff) === address
    && (payload[1] & 0xff) === selfId
    && payload.slice(2, 9).every((value) => value === 0xff)
    && verifySignedSum(payload);
}
