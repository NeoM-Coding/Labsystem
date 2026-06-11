import type { CommandHandler } from "../types.js";
import { airConditionControlHandler } from "./control.js";
import { requestAirConditionDataHandler } from "./request-data.js";

export const airConditionHandlers: CommandHandler[] = [
  requestAirConditionDataHandler,
  airConditionControlHandler
];
