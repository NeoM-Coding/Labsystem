import type { Bytes } from "./bytes.js";

export type CheckType = "CRC16" | "UNSIGN_SUM" | "SIGN_SUM";

export function unsignedSum(bytes: Bytes): number {
  const sum = bytes.reduce((acc, value) => acc + (value & 0xff), 0);
  return sum % 0xff;
}

export function signedSum(bytes: Bytes): number {
  const sum = bytes.reduce((acc, value) => acc + toSignedByte(value), 0);
  return sum % 0xff;
}

export function appendUnsignedSum(bytes: Bytes): number[] {
  return [...bytes, unsignedSum(bytes)];
}

export function appendSignedSum(bytes: Bytes): number[] {
  return [...bytes, signedSum(bytes)];
}

export function verifyUnsignedSum(bytes: Bytes): boolean {
  if (bytes.length < 2) {
    return false;
  }
  return unsignedSum(bytes.slice(0, -1)) === (bytes[bytes.length - 1] & 0xff);
}

export function verifySignedSum(bytes: Bytes): boolean {
  if (bytes.length < 2) {
    return false;
  }
  return (signedSum(bytes.slice(0, -1)) & 0xff) === (bytes[bytes.length - 1] & 0xff);
}

export function appendCrc16(bytes: Bytes): number[] {
  const crc = crc16(bytes);
  return [...bytes, crc & 0xff, (crc >> 8) & 0xff];
}

export function verifyCrc16(bytes: Bytes): boolean {
  if (bytes.length < 3) {
    return false;
  }
  const actual = ((bytes[bytes.length - 1] & 0xff) << 8) | (bytes[bytes.length - 2] & 0xff);
  return crc16(bytes.slice(0, -2)) === actual;
}

export function appendCheck(type: CheckType, bytes: Bytes): number[] {
  if (type === "CRC16") {
    return appendCrc16(bytes);
  }
  if (type === "SIGN_SUM") {
    return appendSignedSum(bytes);
  }
  return appendUnsignedSum(bytes);
}

export function verifyCheck(type: CheckType, bytes: Bytes): boolean {
  if (type === "CRC16") {
    return verifyCrc16(bytes);
  }
  if (type === "SIGN_SUM") {
    return verifySignedSum(bytes);
  }
  return verifyUnsignedSum(bytes);
}

function crc16(bytes: Bytes): number {
  let crc = 0xffff;
  for (const byte of bytes) {
    crc ^= byte & 0xff;
    for (let i = 0; i < 8; i++) {
      crc = (crc & 0x0001) !== 0 ? (crc >> 1) ^ 0xa001 : crc >> 1;
    }
  }
  return crc & 0xffff;
}

function toSignedByte(value: number): number {
  const unsigned = value & 0xff;
  return unsigned > 127 ? unsigned - 256 : unsigned;
}
