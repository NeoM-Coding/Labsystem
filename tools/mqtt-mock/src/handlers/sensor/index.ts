import type { CommandHandler } from "../types.js";
import { requestSensorDataHandler } from "./request-data.js";

export const sensorHandlers: CommandHandler[] = [
  requestSensorDataHandler
];
