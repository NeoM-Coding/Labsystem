import React, { useEffect, useMemo, useState } from "react";
import { createRoot } from "react-dom/client";
import "./styles.css";

type DeviceType = "Access" | "CircuitBreak" | "AirCondition" | "Light" | "Sensor";
type FieldType = "boolean" | "number" | "enum";

interface FieldSpec {
  name: string;
  label: string;
  type: FieldType;
  options?: string[];
  step?: number;
}

interface DeviceState {
  key: string;
  type: DeviceType;
  address: number;
  selfId?: number;
  updatedAt: string;
  [key: string]: unknown;
}

interface DevicesResponse {
  devices: DeviceState[];
  fieldSpecs: Record<DeviceType, FieldSpec[]>;
}

const deviceTypes: DeviceType[] = ["Access", "CircuitBreak", "AirCondition", "Light", "Sensor"];
const selfIdTypes = new Set<DeviceType>(["AirCondition", "Light", "Sensor"]);

function App(): React.ReactElement {
  const [devices, setDevices] = useState<DeviceState[]>([]);
  const [fieldSpecs, setFieldSpecs] = useState<Record<DeviceType, FieldSpec[]>>();
  const [drafts, setDrafts] = useState<Record<string, DeviceState>>({});
  const [type, setType] = useState<DeviceType>("Light");
  const [address, setAddress] = useState(41);
  const [selfId, setSelfId] = useState(1);
  const [status, setStatus] = useState("Ready");

  async function load(): Promise<void> {
    const response = await fetch("/api/devices");
    const body = await response.json() as DevicesResponse;
    setDevices(body.devices);
    setFieldSpecs(body.fieldSpecs);
    setDrafts(Object.fromEntries(body.devices.map((device) => [device.key, device])));
  }

  useEffect(() => {
    void load();
    const timer = window.setInterval(() => void load(), 3000);
    return () => window.clearInterval(timer);
  }, []);

  const grouped = useMemo(() => {
    return deviceTypes.map((deviceType) => ({
      type: deviceType,
      devices: devices.filter((device) => device.type === deviceType)
    }));
  }, [devices]);

  async function addDevice(event: React.FormEvent): Promise<void> {
    event.preventDefault();
    const payload = selfIdTypes.has(type) ? { type, address, selfId } : { type, address };
    const response = await fetch("/api/devices", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(payload)
    });
    if (!response.ok) {
      setStatus("Create failed");
      return;
    }
    setStatus("Device ready");
    await load();
  }

  async function saveDevice(key: string): Promise<void> {
    const draft = drafts[key];
    const response = await fetch(`/api/devices/${encodeURIComponent(key)}`, {
      method: "PATCH",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(draft)
    });
    setStatus(response.ok ? "Saved" : "Save failed");
    await load();
  }

  async function resetDevice(key: string): Promise<void> {
    const response = await fetch(`/api/devices/${encodeURIComponent(key)}/reset`, { method: "POST" });
    setStatus(response.ok ? "Reset" : "Reset failed");
    await load();
  }

  async function resetAll(): Promise<void> {
    const response = await fetch("/api/devices/reset", { method: "POST" });
    setStatus(response.ok ? "All reset" : "Reset failed");
    await load();
  }

  function updateDraft(key: string, name: string, value: unknown): void {
    setDrafts((current) => ({
      ...current,
      [key]: {
        ...current[key],
        [name]: value
      }
    }));
  }

  return (
    <main className="shell">
      <header className="topbar">
        <div>
          <h1>MQTT Mock</h1>
          <p>{devices.length} runtime devices</p>
        </div>
        <button className="ghost" type="button" onClick={() => void resetAll()}>Reset all</button>
      </header>

      <section className="toolbar">
        <form className="add-form" onSubmit={(event) => void addDevice(event)}>
          <label>
            Type
            <select value={type} onChange={(event) => setType(event.target.value as DeviceType)}>
              {deviceTypes.map((deviceType) => <option key={deviceType} value={deviceType}>{deviceType}</option>)}
            </select>
          </label>
          <label>
            Address
            <input type="number" min="1" max="255" value={address} onChange={(event) => setAddress(Number(event.target.value))} />
          </label>
          {selfIdTypes.has(type) && (
            <label>
              Self ID
              <input type="number" min="0" max="255" value={selfId} onChange={(event) => setSelfId(Number(event.target.value))} />
            </label>
          )}
          <button type="submit">Create</button>
        </form>
        <span className="status">{status}</span>
      </section>

      <section className="device-grid">
        {grouped.map((group) => (
          <div className="device-column" key={group.type}>
            <h2>{group.type}</h2>
            {group.devices.length === 0 && <div className="empty">No devices</div>}
            {group.devices.map((device) => (
              <DeviceCard
                key={device.key}
                device={drafts[device.key] ?? device}
                specs={fieldSpecs?.[device.type] ?? []}
                onChange={updateDraft}
                onSave={saveDevice}
                onReset={resetDevice}
              />
            ))}
          </div>
        ))}
      </section>
    </main>
  );
}

interface DeviceCardProps {
  device: DeviceState;
  specs: FieldSpec[];
  onChange(key: string, name: string, value: unknown): void;
  onSave(key: string): Promise<void>;
  onReset(key: string): Promise<void>;
}

function DeviceCard({ device, specs, onChange, onSave, onReset }: DeviceCardProps): React.ReactElement {
  return (
    <article className="device-card">
      <header>
        <strong>{device.key}</strong>
        <span>addr {device.address}{device.selfId !== undefined ? ` / ${device.selfId}` : ""}</span>
      </header>
      <div className="fields">
        {specs.map((spec) => (
          <FieldInput key={spec.name} device={device} spec={spec} onChange={onChange} />
        ))}
      </div>
      <footer>
        <small>{new Date(String(device.updatedAt)).toLocaleTimeString()}</small>
        <div className="actions">
          <button className="ghost" type="button" onClick={() => void onReset(device.key)}>Reset</button>
          <button type="button" onClick={() => void onSave(device.key)}>Save</button>
        </div>
      </footer>
    </article>
  );
}

function FieldInput({ device, spec, onChange }: {
  device: DeviceState;
  spec: FieldSpec;
  onChange(key: string, name: string, value: unknown): void;
}): React.ReactElement {
  const value = device[spec.name];
  if (spec.type === "boolean") {
    return (
      <label className="field boolean-field">
        <span>{spec.label}</span>
        <input
          type="checkbox"
          checked={Boolean(value)}
          onChange={(event) => onChange(device.key, spec.name, event.target.checked)}
        />
      </label>
    );
  }
  if (spec.type === "enum") {
    return (
      <label className="field">
        <span>{spec.label}</span>
        <select value={String(value)} onChange={(event) => onChange(device.key, spec.name, event.target.value)}>
          {spec.options?.map((option) => <option key={option} value={option}>{option}</option>)}
        </select>
      </label>
    );
  }
  return (
    <label className="field">
      <span>{spec.label}</span>
      <input
        type="number"
        step={spec.step ?? 1}
        value={Number(value)}
        onChange={(event) => onChange(device.key, spec.name, Number(event.target.value))}
      />
    </label>
  );
}

createRoot(document.getElementById("root")!).render(<App />);
