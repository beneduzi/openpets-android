#!/usr/bin/env node
/**
 * openpets host-status sender
 *
 * Samples this PC's CPU / RAM / disk / network and pushes them to the OpenPets
 * Android app's experimental WebSocket endpoint, which shows them beside the
 * pet (Settings -> "Show host status on the pet").
 *
 * Requirements:
 *   - Node 22+ (built-in global WebSocket).
 *   - The app's WebSocket endpoint enabled (Settings -> "WebSocket (experimental)").
 *   - A discovery file (OPENPETS_DISCOVERY_FILE or ~/.config/opencode/openpets-discovery.json).
 *
 * Usage:
 *   node send-host-status.mjs
 *
 * Env:
 *   OPENPETS_DISCOVERY_FILE   path to the discovery file
 *   OPENPETS_WS_PORT          WebSocket port (default 18423)
 *   OPENPETS_WS_HOST          override host (else taken from the discovery endpoint)
 *   OPENPETS_STATUS_INTERVAL_MS  sample interval (default 5000)
 *
 * Linux is fully supported (via /proc); other platforms report what Node's
 * os/statfs expose and `null` for the rest (the app renders "unavailable").
 */
import { readFileSync } from "node:fs";
import { statfs } from "node:fs/promises";
import { loadavg, homedir } from "node:os";
import { join } from "node:path";

const WS_PORT = Number(process.env.OPENPETS_WS_PORT || 18423);
const INTERVAL_MS = Number(process.env.OPENPETS_STATUS_INTERVAL_MS || 5000);

function discovery() {
  const path = process.env.OPENPETS_DISCOVERY_FILE ||
    join(process.env.XDG_CONFIG_HOME || join(homedir(), ".config"), "opencode", "openpets-discovery.json");
  const d = JSON.parse(readFileSync(path, "utf8"));
  const m = /^tcp:\/\/([^:/]+):(\d+)$/.exec(d.endpoint || "");
  if (!m || typeof d.token !== "string") throw new Error(`invalid discovery file: ${path}`);
  return { host: process.env.OPENPETS_WS_HOST || m[1], token: d.token };
}

let prevCpu;
function cpuPct() {
  try {
    const parts = readFileSync("/proc/stat", "utf8").split("\n")[0].trim().split(/\s+/).slice(1).map(Number);
    const idle = (parts[3] || 0) + (parts[4] || 0);
    const total = parts.reduce((a, b) => a + b, 0);
    let pct = null;
    if (prevCpu && total - prevCpu.total > 0) {
      pct = Math.round(100 * (1 - (idle - prevCpu.idle) / (total - prevCpu.total)));
    }
    prevCpu = { idle, total };
    return pct;
  } catch {
    return null;
  }
}

function mem() {
  try {
    const t = readFileSync("/proc/meminfo", "utf8");
    const get = (k) => {
      const m = new RegExp(`^${k}:\\s+(\\d+) kB`, "m").exec(t);
      return m ? Number(m[1]) : null;
    };
    const totalKb = get("MemTotal");
    const availKb = get("MemAvailable");
    if (totalKb == null) return { used: null, total: null };
    return {
      used: availKb != null ? Math.round((totalKb - availKb) / 1024) : null,
      total: Math.round(totalKb / 1024),
    };
  } catch {
    return { used: null, total: null };
  }
}

let prevNet;
function netKbps() {
  try {
    const lines = readFileSync("/proc/net/dev", "utf8").split("\n").slice(2);
    let rx = 0;
    let tx = 0;
    for (const l of lines) {
      const m = /^\s*\S+:\s*(\d+)(?:\s+\d+){7}\s+(\d+)/.exec(l);
      if (m) {
        rx += Number(m[1]);
        tx += Number(m[2]);
      }
    }
    let out = { rx: null, tx: null };
    if (prevNet) {
      const secs = INTERVAL_MS / 1000;
      out = {
        rx: Math.round(((rx - prevNet.rx) * 8) / 1000 / secs),
        tx: Math.round(((tx - prevNet.tx) * 8) / 1000 / secs),
      };
    }
    prevNet = { rx, tx };
    return out;
  } catch {
    return { rx: null, tx: null };
  }
}

async function diskPct() {
  try {
    const s = await statfs("/");
    const total = s.blocks * s.bsize;
    const free = s.bfree * s.bsize;
    return total > 0 ? Math.round(100 * (1 - free / total)) : null;
  } catch {
    return null;
  }
}

async function sample() {
  const m = mem();
  const n = netKbps();
  const la = loadavg()[0];
  return {
    cpuPct: cpuPct(),
    ramUsedMb: m.used,
    ramTotalMb: m.total,
    diskUsedPct: await diskPct(),
    netRxKbps: n.rx,
    netTxKbps: n.tx,
    loadAvg: Number.isFinite(la) ? la : null,
    ts: Date.now(),
  };
}

function run() {
  let cfg;
  try {
    cfg = discovery();
  } catch (err) {
    console.error(String(err));
    process.exit(1);
  }
  const url = `ws://${cfg.host}:${WS_PORT}/?token=${encodeURIComponent(cfg.token)}`;
  const ws = new WebSocket(url);
  let timer;

  const push = async () => {
    try {
      ws.send(JSON.stringify({ type: "status.host", params: await sample() }));
    } catch {
      /* ignore */
    }
  };

  ws.addEventListener("open", () => {
    console.error(`host-status -> ${url} every ${INTERVAL_MS} ms`);
    timer = setInterval(push, INTERVAL_MS);
    void push();
  });
  ws.addEventListener("error", () => {});
  ws.addEventListener("close", () => {
    clearInterval(timer);
    console.error("disconnected; retrying in 3s");
    setTimeout(run, 3000);
  });
}

run();
