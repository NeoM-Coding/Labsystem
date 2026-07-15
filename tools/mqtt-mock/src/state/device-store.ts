import type { DeviceType } from "../protocol/device-type.js";

export type DeviceFieldType = "boolean" | "number" | "enum";

export interface DeviceFieldSpec {
  name: string;
  label: string;
  type: DeviceFieldType;
  options?: string[];
  step?: number;
}

interface DeviceBase {
  key: string;
  type: Exclude<DeviceType, "Unknown">;
  address: number;
  selfId?: number;
  updatedAt: string;
}

export interface AccessDeviceState extends DeviceBase {
  type: "Access";
  opened: boolean;
  locked: boolean;
  lockStatus: number;
  delayTime: number;
}

export interface AirConditionDeviceState extends DeviceBase {
  type: "AirCondition";
  selfId: number;
  opened: boolean;
  mode: "Cooling" | "Heating" | "Dehumidification" | "AirSupply";
  temperature: number;
  speed: "Low" | "Middle" | "High" | "Auto";
  roomTemperature: number;
  errorCode: number;
}

export interface CircuitBreakDeviceState extends DeviceBase {
  type: "CircuitBreak";
  opened: boolean;
  fixed: boolean;
  locked: boolean;
  voltage: number;
  current: number;
  power: number;
  energy: number;
  leakage: number;
  temperature: number;
}

export interface LightDeviceState extends DeviceBase {
  type: "Light";
  selfId: number;
  opened: boolean;
  locked: boolean;
}

export interface SensorDeviceState extends DeviceBase {
  type: "Sensor";
  selfId: number;
  temperature: number;
  humidity: number;
  light: number;
  smoke: number;
}

export type ManagedDeviceState =
  | AccessDeviceState
  | AirConditionDeviceState
  | CircuitBreakDeviceState
  | LightDeviceState
  | SensorDeviceState;

export const deviceFieldSpecs: Record<Exclude<DeviceType, "Unknown">, DeviceFieldSpec[]> = {
  Access: [
    { name: "opened", label: "Opened", type: "boolean" },
    { name: "locked", label: "Locked", type: "boolean" },
    { name: "lockStatus", label: "Lock status", type: "number", step: 1 },
    { name: "delayTime", label: "Delay time", type: "number", step: 1 }
  ],
  AirCondition: [
    { name: "opened", label: "Opened", type: "boolean" },
    { name: "mode", label: "Mode", type: "enum", options: ["Cooling", "Heating", "Dehumidification", "AirSupply"] },
    { name: "temperature", label: "Temperature", type: "number", step: 1 },
    { name: "speed", label: "Speed", type: "enum", options: ["Low", "Middle", "High", "Auto"] },
    { name: "roomTemperature", label: "Room temperature", type: "number", step: 1 },
    { name: "errorCode", label: "Error code", type: "number", step: 1 }
  ],
  CircuitBreak: [
    { name: "opened", label: "Opened", type: "boolean" },
    { name: "fixed", label: "Fixed", type: "boolean" },
    { name: "locked", label: "Locked", type: "boolean" },
    { name: "voltage", label: "Voltage", type: "number", step: 0.1 },
    { name: "current", label: "Current", type: "number", step: 0.1 },
    { name: "power", label: "Power", type: "number", step: 0.1 },
    { name: "energy", label: "Energy", type: "number", step: 0.1 },
    { name: "leakage", label: "Leakage", type: "number", step: 0.001 },
    { name: "temperature", label: "Temperature", type: "number", step: 0.1 }
  ],
  Light: [
    { name: "opened", label: "Opened", type: "boolean" },
    { name: "locked", label: "Locked", type: "boolean" }
  ],
  Sensor: [
    { name: "temperature", label: "Temperature", type: "number", step: 0.1 },
    { name: "humidity", label: "Humidity", type: "number", step: 0.1 },
    { name: "light", label: "Light", type: "number", step: 0.1 },
    { name: "smoke", label: "Smoke", type: "number", step: 1 }
  ]
};

const devices = new Map<string, ManagedDeviceState>();

export function deviceKey(type: DeviceType, address: number, selfId?: number): string {
  return selfId === undefined ? `${type}:${address}` : `${type}:${address}:${selfId}`;
}

export function listDevices(): ManagedDeviceState[] {
  return Array.from(devices.values()).sort((left, right) => {
    if (left.type !== right.type) {
      return left.type.localeCompare(right.type);
    }
    if (left.address !== right.address) {
      return left.address - right.address;
    }
    return (left.selfId ?? 0) - (right.selfId ?? 0);
  });
}

export function ensureDevice(type: DeviceType, address: number, selfId?: number): ManagedDeviceState {
  if (type === "Unknown") {
    throw new Error(`unsupported device address: ${address}`);
  }
  const key = deviceKey(type, address, selfId);
  const existing = devices.get(key);
  if (existing) {
    return existing;
  }
  const device = defaultDevice(type, address, selfId);
  devices.set(key, device);
  return device;
}

export function updateDevice(key: string, patch: Record<string, unknown>): ManagedDeviceState {
  const current = devices.get(key);
  if (!current) {
    throw new Error(`device not found: ${key}`);
  }

  const next = applyPatch(current, patch);
  devices.set(key, touch(next));
  return devices.get(key)!;
}

export function resetDevice(key: string): ManagedDeviceState {
  const current = devices.get(key);
  if (!current) {
    throw new Error(`device not found: ${key}`);
  }
  const next = defaultDevice(current.type, current.address, current.selfId);
  devices.set(key, next);
  return next;
}

export function resetAllDevices(): ManagedDeviceState[] {
  for (const current of Array.from(devices.values())) {
    devices.set(current.key, defaultDevice(current.type, current.address, current.selfId));
  }
  return listDevices();
}

function defaultDevice(type: Exclude<DeviceType, "Unknown">, address: number, selfId?: number): ManagedDeviceState {
  const now = new Date().toISOString();
  if (type === "Access") {
    return {
      key: deviceKey(type, address),
      type,
      address,
      opened: true,
      locked: true,
      lockStatus: 1,
      delayTime: 5,
      updatedAt: now
    };
  }
  if (type === "AirCondition") {
    const resolvedSelfId = requireSelfId(type, selfId);
    return {
      key: deviceKey(type, address, resolvedSelfId),
      type,
      address,
      selfId: resolvedSelfId,
      opened: true,
      mode: "Cooling",
      temperature: 25,
      speed: "High",
      roomTemperature: 24,
      errorCode: 0,
      updatedAt: now
    };
  }
  if (type === "CircuitBreak") {
    return {
      key: deviceKey(type, address),
      type,
      address,
      opened: true,
      fixed: true,
      locked: address % 2 === 0,
      voltage: round1(220 + (address % 5)),
      current: round1(1.2 + (address % 4) / 10),
      power: round1(260 + address),
      energy: round1(1234.5 + address),
      leakage: round3(0.12 + address / 1000),
      temperature: round1(26.5 + (address % 3)),
      updatedAt: now
    };
  }
  if (type === "Light") {
    const resolvedSelfId = requireSelfId(type, selfId);
    return {
      key: deviceKey(type, address, resolvedSelfId),
      type,
      address,
      selfId: resolvedSelfId,
      opened: true,
      locked: true,
      updatedAt: now
    };
  }

  const resolvedSelfId = requireSelfId(type, selfId);
  return {
    key: deviceKey(type, address, resolvedSelfId),
    type: "Sensor",
    address,
    selfId: resolvedSelfId,
    temperature: round1(vary(245, address, resolvedSelfId) / 10),
    humidity: round1(vary(558, address, resolvedSelfId) / 10),
    light: round1((1000 + address * 10 + resolvedSelfId) / 10),
    smoke: vary(12, address, resolvedSelfId),
    updatedAt: now
  };
}

function applyPatch(current: ManagedDeviceState, patch: Record<string, unknown>): ManagedDeviceState {
  const specs = deviceFieldSpecs[current.type];
  const allowed = new Map(specs.map((spec) => [spec.name, spec]));
  const next: Record<string, unknown> = { ...current };

  for (const [name, value] of Object.entries(patch)) {
    const spec = allowed.get(name);
    if (!spec) {
      continue;
    }
    if (spec.type === "boolean") {
      next[name] = Boolean(value);
    } else if (spec.type === "number") {
      next[name] = Number(value);
    } else if (spec.options?.includes(String(value))) {
      next[name] = String(value);
    }
  }

  return next as unknown as ManagedDeviceState;
}

function touch<T extends ManagedDeviceState>(device: T): T {
  return { ...device, updatedAt: new Date().toISOString() };
}

function requireSelfId(type: DeviceType, selfId: number | undefined): number {
  if (selfId === undefined) {
    throw new Error(`${type} requires selfId`);
  }
  return selfId;
}

function vary(base: number, address: number, selfId = 0): number {
  return (base + ((address & 0xff) % 7) + ((selfId & 0xff) % 5)) & 0xffff;
}

function round1(value: number): number {
  return Math.round(value * 10) / 10;
}

function round3(value: number): number {
  return Math.round(value * 1000) / 1000;
}
