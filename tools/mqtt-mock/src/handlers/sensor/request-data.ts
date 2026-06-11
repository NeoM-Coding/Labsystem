import { appendUnsignedSum, verifyUnsignedSum } from "../../protocol/checksum.js";
import type { CommandHandler } from "../types.js";
import { u16, u32, vary } from "../shared/device-state.js";

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

    const temperatureTenths = vary(245, context.address, context.selfId);
    const humidityTenths = vary(558, context.address, context.selfId);
    const lightTenths = 1000 + context.address * 10 + context.selfId;
    const smoke = vary(12, context.address, context.selfId);

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
