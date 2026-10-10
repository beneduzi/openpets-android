/**
 * openpets-client — tiny host-side client for the OpenPets Android companion.
 *
 * Host "plugins" run on the PC (as an OpenCode plugin, a Node script, or a
 * daemon) and drive the phone over its LAN protocol. Use this helper instead of
 * re-implementing discovery/socket/WS handling.
 *
 *   import { say, react, ipc, connectWs, readDiscovery } from "./openpets-client.mjs";
 *
 * Outbound (host -> phone): `react`, `say`, `showMedia`, `ipc`, `sendHostStatus`.
 * Inbound (phone -> host, needs the experimental WS enabled): `connectWs`.
 *
 * Node 22+ (uses the built-in global WebSocket). Never throws into the host.
 */
import { readFileSync } from "node:fs";
import { connect } from "node:net";
import { randomUUID } from "node:crypto";
import { basename, join } from "node:path";
import { homedir, hostname } from "node:os";

const WS_PORT = Number(process.env.OPENPETS_WS_PORT || 18423);

export const FEATURE = {
  timer: "simple-timer",
  focus: "focus-buddy",
  reminder: "quick-reminder",
  hostStatus: "host-status",
  vocad: "vocab",
  launch: "launch-checklist",
};

export function discoveryPath() {
  if (process.env.OPENPETS_DISCOVERY_FILE) return process.env.OPENPETS_DISCOVERY_FILE;
  const base = process.env.XDG_CONFIG_HOME || join(homedir(), ".config");
  return join(base, "opencode", "openpets-discovery.json");
}

/** { host, port, token } from the discovery file, or undefined. */
export function readDiscovery() {
  try {
    const d = JSON.parse(readFileSync(discoveryPath(), "utf8"));
    const m = /^tcp:\/\/([^:/]+):(\d+)$/.exec(d.endpoint ?? "");
    if (!m || typeof d.token !== "string") return undefined;
    return { host: m[1], port: Number(m[2]), token: d.token };
  } catch {
    return undefined;
  }
}

/** A short project label for per-project accent colors. */
export function projectSource(dir = process.cwd()) {
  return (basename(dir) || hostname()).slice(0, 64);
}

/**
 * Send one IPC request and resolve with the parsed response.
 * `params.source` defaults to the current project folder; pass `{ source }` to override.
 */
export function ipc(method, params = {}, { source, timeoutMs = 1500 } = {}) {
  return new Promise((resolve) => {
    const disc = readDiscovery();
    if (!disc) return resolve(undefined);
    const id = randomUUID();
    let buffer = "";
    let done = false;
    const finish = (value) => {
      if (done) return;
      done = true;
      try {
        socket.destroy();
      } catch {
        /* ignore */
      }
      resolve(value);
    };
    const socket = connect({ host: disc.host, port: disc.port });
    socket.setTimeout(timeoutMs);
    socket.on("connect", () => {
      const request = {
        id,
        version: 1,
        token: disc.token,
        method,
        params: { ...params, ...(source === undefined ? { source: projectSource() } : { source }) },
      };
      socket.write(JSON.stringify(request) + "\n");
    });
    socket.on("data", (chunk) => {
      buffer += chunk.toString("utf8");
      const nl = buffer.indexOf("\n");
      if (nl >= 0) {
        try {
          finish(JSON.parse(buffer.slice(0, nl)));
        } catch {
          finish(undefined);
        }
      }
    });
    socket.on("error", () => finish(undefined));
    socket.on("timeout", () => finish(undefined));
  });
}

export const react = (reaction, opts) => ipc("pet.react", { reaction }, opts);

export const say = (message, { reaction, source, hud, actions, pinned, bubbleId } = {}) =>
  ipc("pet.say", {
    message,
    ...(reaction ? { reaction } : {}),
    ...(source ? { source } : {}),
    ...(hud ? { hud } : {}),
    ...(actions ? { actions } : {}),
    ...(pinned !== undefined ? { pinned } : {}),
    ...(bubbleId ? { id: bubbleId } : {}),
  });

export const showMedia = ({ url, data, message, reaction, durationMs, clickUrl } = {}) =>
  ipc("pet.showMedia", {
    ...(url ? { url } : {}),
    ...(data ? { data } : {}),
    ...(message ? { message } : {}),
    ...(reaction ? { reaction } : {}),
    ...(durationMs ? { durationMs } : {}),
    ...(clickUrl ? { clickUrl } : {}),
  });

/**
 * Push host metrics (shown when the app's "Show host status on the pet" is on).
 * `usagePct`/`usageLabel` add a 4th bar (Usage-Buddy style).
 */
export const sendHostStatus = (
  { cpuPct, ramUsedMb, ramTotalMb, diskUsedPct, netRxKbps, netTxKbps, loadAvg, usagePct, usageLabel } = {},
) => {
  const ws = connectWs({});
  const frame = JSON.stringify({
    type: "status.host",
    params: { cpuPct, ramUsedMb, ramTotalMb, diskUsedPct, netRxKbps, netTxKbps, loadAvg, usagePct, usageLabel, ts: Date.now() },
  });
  return new Promise((resolve) => {
    if (!ws) return resolve(false);
    ws.addEventListener("open", () => {
      try {
        ws.send(frame);
      } catch {
        /* ignore */
      }
      setTimeout(() => {
        try {
          ws.close();
        } catch {
          /* ignore */
        }
        resolve(true);
      }, 200);
    });
    ws.addEventListener("error", () => resolve(false));
  });
};

/**
 * Open the experimental WebSocket (bidirectional) and forward events.
 * Requires the app's "WebSocket (experimental)" setting to be enabled.
 *
 *   const ws = connectWs({ onEvent: (e) => { if (e.event === "action") … } });
 *
 * Events: { type:"event", event:"react"|"say"|"media"|"dismiss"|"hud"|"action"|"input.text", ... }
 * Send commands with `ws.send(JSON.stringify({ id, method, params }))`.
 */
export function connectWs({ onEvent, onOpen, onClose, onError } = {}) {
  const disc = readDiscovery();
  const GlobalWebSocket = globalThis.WebSocket;
  if (!disc || !GlobalWebSocket) return undefined;
  const url = `ws://${disc.host}:${WS_PORT}/?token=${encodeURIComponent(disc.token)}`;
  let ws;
  const open = () => {
    ws = new GlobalWebSocket(url);
    ws.addEventListener("open", () => onOpen?.(ws));
    ws.addEventListener("message", (event) => {
      try {
        const msg = JSON.parse(typeof event.data === "string" ? event.data : "");
        onEvent?.(msg);
      } catch {
        /* ignore malformed frames */
      }
    });
    ws.addEventListener("error", () => onError?.());
    ws.addEventListener("close", () => {
      onClose?.();
      setTimeout(open, 3000);
    });
  };
  open();
  return ws;
}
