export type Bytes = readonly number[];

export function toUnsignedBytes(payload: Buffer): number[] {
  return Array.from(payload.values()).map((value) => value & 0xff);
}

export function toBuffer(bytes: Bytes): Buffer {
  return Buffer.from(bytes.map((value) => value & 0xff));
}

export function toHex(bytes: Bytes): string {
  return bytes.map((value) => (value & 0xff).toString(16).padStart(2, "0").toUpperCase()).join(" ");
}
