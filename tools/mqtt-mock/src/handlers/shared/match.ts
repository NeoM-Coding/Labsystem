import type { Bytes } from "../../protocol/bytes.js";
import type { CheckType } from "../../protocol/checksum.js";
import { verifyCheck } from "../../protocol/checksum.js";

export function hasPrefix(payload: Bytes, prefix: Bytes): boolean {
  if (payload.length < prefix.length) {
    return false;
  }
  return prefix.every((value, index) => (payload[index] & 0xff) === (value & 0xff));
}

export function hasLengthAndCheck(payload: Bytes, length: number, checkType: CheckType): boolean {
  return payload.length === length && verifyCheck(checkType, payload);
}

export function matchesCheckedPrefix(
  payload: Bytes,
  prefix: Bytes,
  length: number,
  checkType: CheckType
): boolean {
  return payload.length === length && hasPrefix(payload, prefix) && verifyCheck(checkType, payload);
}
