import type { CommandHandler } from "../types.js";
import { circuitBreakControlHandler } from "./write-control.js";
import { requestCircuitBreakDataHandler } from "./request-data.js";

export const circuitBreakHandlers: CommandHandler[] = [
  requestCircuitBreakDataHandler,
  circuitBreakControlHandler
];
