import { appendUnsignedSum, verifyUnsignedSum } from "../../protocol/checksum.js";
import { ensureDevice, type SensorDeviceState } from "../../state/device-store.js";
import type { CommandHandler } from "../types.js";
import { u16, u32 } from "../shared/device-state.js";

export const requestSensorDataHandler: CommandHandler = {
  commandLine: "REQUEST_SENSOR_DATA",
  handle(payload, context) {
    if (context.deviceType !== "Sensor" || context.selfId === undefined || payload.length !== 7) {
      return undefined;
    }
    if (payload[1] !== 0x03 || (payload[2] & 0xff) !== context.selfId) {
      return undefined;
    }
    if (!verifyUnsignedSum(payload)) {
      throw new Error("REQUEST_SENSOR_DATA checksum failed");
    }

    const state = ensureDevice("Sensor", context.address, context.selfId) as SensorDeviceState;
    const temperatureTenths = Math.round(state.temperature * 10);
    const humidityTenths = Math.round(state.humidity * 10);
    const lightTenths = Math.round(state.light * 10);
    const smoke = Math.round(state.smoke);

    return appendUnsignedSum([
      context.address,
      0x03,
      context.selfId,
      ...u16(temperatureTenths),
      ...u16(humidityTenths),
      ...u32(lightTenths),
      ...u16(smoke)
    ]);
  }
};
