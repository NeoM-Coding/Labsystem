import type { Bytes } from "../../protocol/bytes.js";

export function vary(base: number, address: number, selfId = 0): number {
  return (base + ((address & 0xff) % 7) + ((selfId & 0xff) % 5)) & 0xffff;
}

export function u16(value: number): [number, number] {
  return [(value >> 8) & 0xff, value & 0xff];
}

export function u32(value: number): [number, number, number, number] {
  return [
    (value >> 24) & 0xff,
    (value >> 16) & 0xff,
    (value >> 8) & 0xff,
    value & 0xff
  ];
}

export function floatLE(value: number): Bytes {
  const buffer = Buffer.allocUnsafe(4);
  buffer.writeFloatLE(value, 0);
  return Array.from(buffer.values());
}
