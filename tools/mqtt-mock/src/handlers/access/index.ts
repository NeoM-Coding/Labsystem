import type { CommandHandler } from "../types.js";
import { accessControlAckHandler } from "./access-control-ack.js";
import { openAccessOnceHandler } from "./open-access-once.js";
import { requestAccessDataHandler } from "./request-access-data.js";

export const accessHandlers: CommandHandler[] = [
  requestAccessDataHandler,
  openAccessOnceHandler,
  accessControlAckHandler
];
