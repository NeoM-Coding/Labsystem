import { appendCrc16, appendSignedSum, verifyCrc16, verifySignedSum } from "../../protocol/checksum.js";
import { ensureDevice, updateDevice, type AirConditionDeviceState } from "../../state/device-store.js";
import type { CommandHandler } from "../types.js";

export const airConditionControlHandler: CommandHandler = {
  commandLine: "AIR_CONDITION_CONTROL",
  handle(payload, context) {
    if (context.deviceType !== "AirCondition" || context.selfId === undefined) {
      return undefined;
    }

    if (isDocumentControl(payload, context.address, context.selfId)) {
      const state = ensureDevice("AirCondition", context.address, context.selfId);
      updateDevice(state.key, { opened: payload[5] === 0x01 });
      return [...payload];
    }

    if (isCurrentCommandLineControl(payload, context.address, context.selfId)) {
      const state = ensureDevice("AirCondition", context.address, context.selfId);
      const next = updateDevice(state.key, airConditionPatch(payload)) as AirConditionDeviceState;
      return appendSignedSum(airConditionStatus(next));
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

function airConditionPatch(payload: readonly number[]): Record<string, unknown> {
  const patch: Record<string, unknown> = {};
  if (payload[2] !== 0xff) {
    patch.opened = payload[2] === 0x01;
  }
  if (payload[3] !== 0xff) {
    patch.mode = modeName(payload[3] & 0xff);
  }
  if (payload[4] !== 0xff) {
    patch.temperature = payload[4] & 0xff;
  }
  if (payload[5] !== 0xff) {
    patch.speed = speedName(payload[5] & 0xff);
  }
  return patch;
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

function modeName(value: number): AirConditionDeviceState["mode"] | undefined {
  if (value === 0x01) {
    return "Heating";
  }
  if (value === 0x04) {
    return "AirSupply";
  }
  if (value === 0x08) {
    return "Dehumidification";
  }
  return "Cooling";
}

function speedByte(speed: AirConditionDeviceState["speed"]): number {
  return { Auto: 0x00, Low: 0x01, Middle: 0x02, High: 0x03 }[speed];
}

function speedName(value: number): AirConditionDeviceState["speed"] | undefined {
  if (value === 0x00) {
    return "Auto";
  }
  if (value === 0x01) {
    return "Low";
  }
  if (value === 0x02) {
    return "Middle";
  }
  return "High";
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
