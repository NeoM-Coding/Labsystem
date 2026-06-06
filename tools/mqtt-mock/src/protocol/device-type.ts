export type DeviceType = "Access" | "CircuitBreak" | "AirCondition" | "Light" | "Sensor" | "Unknown";

export function deviceTypeByAddress(address: number): DeviceType {
  if (address >= 1 && address <= 10) {
    return "Access";
  }
  if (address >= 11 && address <= 30) {
    return "CircuitBreak";
  }
  if (address >= 31 && address <= 40) {
    return "AirCondition";
  }
  if (address >= 41 && address <= 60) {
    return "Light";
  }
  if (address >= 61 && address <= 80) {
    return "Sensor";
  }
  return "Unknown";
}
