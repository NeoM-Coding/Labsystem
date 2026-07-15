import { appendUnsignedSum, verifyUnsignedSum } from "../../protocol/checksum.js";
import { ensureDevice, type AccessDeviceState } from "../../state/device-store.js";
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
    return appendUnsignedSum(accessStatus(ensureDevice("Access", context.address) as AccessDeviceState));
  }
};

function accessStatus(state: AccessDeviceState): number[] {
  return [
    state.address,
    0x03,
    0x01,
    state.opened ? 0xff : 0x00,
    lockStatusByte(state.lockStatus),
    state.locked ? 0xff : 0x00,
    state.delayTime & 0xff
  ];
}

function lockStatusByte(lockStatus: number): number {
  if (lockStatus === 1) {
    return 0xff;
  }
  if (lockStatus === 2) {
    return 0x11;
  }
  if (lockStatus === 3) {
    return 0x00;
  }
  return 0x00;
}
