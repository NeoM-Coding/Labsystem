import { appendUnsignedSum, verifyUnsignedSum } from "../../protocol/checksum.js";
import { ensureDevice, updateDevice } from "../../state/device-store.js";
import type { CommandHandler } from "../types.js";

const supportedControlCodes = new Set([0x01, 0x02, 0x03]);

export const accessControlAckHandler: CommandHandler = {
  commandLine: "ACCESS_CONTROL_ACK",
  handle(payload, context) {
    if (context.deviceType !== "Access" || payload.length !== 7) {
      return undefined;
    }
    if (payload[1] !== 0x0a || !supportedControlCodes.has(payload[2] & 0xff)) {
      return undefined;
    }
    if (!verifyUnsignedSum(payload)) {
      throw new Error("ACCESS_CONTROL_ACK checksum failed");
    }
    const state = ensureDevice("Access", context.address);
    if (payload[2] === 0x03) {
      updateDevice(state.key, { delayTime: payload[3] & 0xff });
      return appendUnsignedSum([context.address, 0x0a, 0x03, payload[3] & 0xff]);
    }

    updateDevice(state.key, accessPatch(payload));

    return appendUnsignedSum([context.address, 0x0a, payload[2] & 0xff, payload[3] & 0xff]);
  }
};

function accessPatch(payload: readonly number[]): Record<string, unknown> {
  const patch: Record<string, unknown> = {};
  if (payload[3] === 0xff) {
    patch.opened = true;
  } else if (payload[3] === 0x00) {
    patch.opened = false;
  }
  if (payload[4] === 0xff) {
    patch.lockStatus = 1;
  } else if (payload[4] === 0x11) {
    patch.lockStatus = 2;
  } else if (payload[4] === 0x00) {
    patch.lockStatus = 3;
  }
  if (payload[5] === 0xff) {
    patch.locked = true;
  } else if (payload[5] === 0x00) {
    patch.locked = false;
  }
  return patch;
}
