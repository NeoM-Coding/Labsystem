import { createReadStream } from "node:fs";
import { stat } from "node:fs/promises";
import { createServer, type IncomingMessage, type Server, type ServerResponse } from "node:http";
import path from "node:path";
import { deviceFieldSpecs, ensureDevice, listDevices, resetAllDevices, resetDevice, updateDevice } from "./state/device-store.js";
import type { DeviceType } from "./protocol/device-type.js";
import type { MockConfig } from "./config.js";

export interface WebServerHandle {
  close(callback?: () => void): void;
}

export function startWebServer(config: MockConfig): WebServerHandle | undefined {
  if (!config.webEnabled) {
    return undefined;
  }

  const server = createServer((request, response) => {
    void handleRequest(request, response);
  });

  server.listen(config.webPort, config.webHost, () => {
    console.info(`[mqtt-mock] web ui http://${config.webHost}:${config.webPort}`);
  });

  return {
    close(callback) {
      server.close(callback);
    }
  };
}

async function handleRequest(request: IncomingMessage, response: ServerResponse): Promise<void> {
  const url = new URL(request.url ?? "/", "http://localhost");

  try {
    if (url.pathname.startsWith("/api/")) {
      await handleApi(request, response, url);
      return;
    }
    await serveStatic(url.pathname, response);
  } catch (error) {
    const message = error instanceof Error ? error.message : String(error);
    sendJson(response, 500, { error: message });
  }
}

async function handleApi(request: IncomingMessage, response: ServerResponse, url: URL): Promise<void> {
  if (request.method === "GET" && url.pathname === "/api/devices") {
    sendJson(response, 200, { devices: listDevices(), fieldSpecs: deviceFieldSpecs });
    return;
  }

  if (request.method === "POST" && url.pathname === "/api/devices") {
    const body = await readJson(request);
    const type = String(body.type ?? "") as DeviceType;
    const address = Number(body.address);
    const rawSelfId = body.selfId === undefined || body.selfId === "" ? undefined : Number(body.selfId);
    const device = ensureDevice(type, address, rawSelfId);
    sendJson(response, 201, device);
    return;
  }

  if (request.method === "POST" && url.pathname === "/api/devices/reset") {
    sendJson(response, 200, { devices: resetAllDevices() });
    return;
  }

  const deviceMatch = url.pathname.match(/^\/api\/devices\/([^/]+)(\/reset)?$/);
  if (deviceMatch) {
    const key = decodeURIComponent(deviceMatch[1]);
    const isReset = Boolean(deviceMatch[2]);
    if (request.method === "PATCH" && !isReset) {
      sendJson(response, 200, updateDevice(key, await readJson(request)));
      return;
    }
    if (request.method === "POST" && isReset) {
      sendJson(response, 200, resetDevice(key));
      return;
    }
  }

  sendJson(response, 404, { error: "not found" });
}

async function readJson(request: IncomingMessage): Promise<Record<string, unknown>> {
  const chunks: Buffer[] = [];
  for await (const chunk of request) {
    chunks.push(Buffer.isBuffer(chunk) ? chunk : Buffer.from(chunk));
  }
  if (chunks.length === 0) {
    return {};
  }
  return JSON.parse(Buffer.concat(chunks).toString("utf8")) as Record<string, unknown>;
}

async function serveStatic(pathname: string, response: ServerResponse): Promise<void> {
  const publicDir = path.resolve("dist/public");
  const normalizedPath = pathname === "/" ? "/index.html" : pathname;
  const candidate = path.resolve(publicDir, `.${normalizedPath}`);
  const target = candidate.startsWith(publicDir) ? candidate : path.join(publicDir, "index.html");

  try {
    const file = await stat(target);
    if (file.isFile()) {
      streamFile(target, response);
      return;
    }
  } catch {
    // Fall through to the SPA entry.
  }

  streamFile(path.join(publicDir, "index.html"), response);
}

function streamFile(filePath: string, response: ServerResponse): void {
  response.writeHead(200, { "Content-Type": contentType(filePath) });
  createReadStream(filePath).pipe(response);
}

function sendJson(response: ServerResponse, status: number, body: unknown): void {
  response.writeHead(status, { "Content-Type": "application/json" });
  response.end(JSON.stringify(body));
}

function contentType(filePath: string): string {
  if (filePath.endsWith(".html")) {
    return "text/html; charset=utf-8";
  }
  if (filePath.endsWith(".css")) {
    return "text/css; charset=utf-8";
  }
  if (filePath.endsWith(".js")) {
    return "text/javascript; charset=utf-8";
  }
  return "application/octet-stream";
}
