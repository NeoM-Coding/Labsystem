import { appendUnsignedSum, verifyUnsignedSum } from "../../protocol/checksum.js";
import { ensureDevice, updateDevice } from "../../state/device-store.js";
import type { CommandHandler } from "../types.js";

const commandNames = new Map<string, string>([
  ["255:17", "OPEN_LIGHT"],
  ["0:17", "CLOSE_LIGHT"],
  ["17:255", "LOCK_LIGHT"],
  ["17:0", "UNLOCK_LIGHT"]
]);

export const lightControlHandler: CommandHandler = {
  commandLine: "LIGHT_CONTROL",
  handle(payload, context) {
    if (context.deviceType !== "Light" || context.selfId === undefined || payload.length !== 7) {
      return undefined;
    }
    if (payload[1] !== 0x0a || (payload[2] & 0xff) !== context.selfId) {
      return undefined;
    }
    if (!commandNames.has(`${payload[3] & 0xff}:${payload[4] & 0xff}`)) {
      return undefined;
    }
    if (!verifyUnsignedSum(payload)) {
      throw new Error("LIGHT_CONTROL checksum failed");
    }

    const state = ensureDevice("Light", context.address, context.selfId);
    updateDevice(state.key, lightPatch(payload));

    return appendUnsignedSum([
      context.address,
      0x0a,
      context.selfId,
      payload[3] & 0xff,
      payload[4] & 0xff
    ]);
  }
};

function lightPatch(payload: readonly number[]): Record<string, unknown> {
  const patch: Record<string, unknown> = {};
  if (payload[3] === 0xff) {
    patch.opened = true;
  } else if (payload[3] === 0x00) {
    patch.opened = false;
  }
  if (payload[4] === 0xff) {
    patch.locked = true;
  } else if (payload[4] === 0x00) {
    patch.locked = false;
  }
  return patch;
}
