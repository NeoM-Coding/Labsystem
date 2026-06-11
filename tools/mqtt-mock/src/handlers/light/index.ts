import type { CommandHandler } from "../types.js";
import { lightControlHandler } from "./control.js";
import { requestLightDataHandler } from "./request-data.js";

export const lightHandlers: CommandHandler[] = [
  requestLightDataHandler,
  lightControlHandler
];
