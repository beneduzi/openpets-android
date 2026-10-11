# Host-side plugins for OpenPets Android

The Android app is the **target** (the screen and the pet). Extensibility lives
on the **host** — a small program on your PC that drives the phone over its LAN
protocol. This is the Android analogue of a desktop OpenPets plugin: the plugin
runs on the host and the phone renders it.

## Why host-side

- No in-app plugin runtime, signing, or store is required (installing plugins
  *into the APK* would need a sandboxed runtime — a separate large effort).
- You get the full protocol: reactions, speech, rich bubbles/forms, media, host
  metrics, and — with the experimental WebSocket — a **bidirectional** channel
  for taps, voice transcripts, and approvals.
- It matches the OpenPets ecosystem, where plugins run host-side and drive the
  target.

## Architecture

```
  Host plugin (PC)                              OpenPets Android (phone)
  ┌──────────────┐   IPC  tcp://<ip>:18420      ┌────────────────────────┐
  │ your plugin  │ ───────────────────────────▶ │ pet · bubbles · media  │
  │ (Node)       │   WS   ws://<ip>:18423       │ companion features     │
  │              │ ◀──────────────────────────▶ │ (experimental)         │
  └──────────────┘   events ⇄ commands          └────────────────────────┘
```

- **IPC** (`openpets-ipc`, port 18420): request/response. Discovery file supplies
  `endpoint` + `token`. Best for outbound (`pet.say`, `pet.react`, screens).
- **WebSocket** (`openpets-ws`, port 18423, experimental, opt-in): persistent and
  **bidirectional** — events out, commands in. Needed for taps, approvals,
  voice (`input.text`), and host feeds.

## Prerequisites

1. A **discovery file** (`OPENPETS_DISCOVERY_FILE`, default
   `~/.config/opencode/openpets-discovery.json`) — see the app's agent guide and
   `tools/write-discovery.sh`.
2. For inbound events: enable **WebSocket (experimental)** in the app's Settings.

## Install & run a plugin

Host plugins are just programs on your PC. Choose whichever fits:

- **OpenCode plugin** — drop a `.ts` in `~/.config/opencode/plugins/` (see
  `integrations/opencode-plugin/`). Loads with OpenCode.
- **Node script** — `node your-plugin.mjs` (this folder's client is a plain ESM
  module). Run under a process manager if you want it always-on.
- **Daemon** — anything that can speak the IPC/WS protocol.

There is no app-side install step; "installing" a plugin = running it on the host.

## Use the client

```js
import { react, say, showMedia, sendHostStatus, ipc, connectWs, FEATURE } from "./openpets-client.mjs";

await react("working");
await say("Building…", { reaction: "running", source: "my-plugin" });
await showMedia({ url: "https://example.com/shot.png", message: "preview", durationMs: 8000 });

// Host metrics appear when the app's "Show host status on the pet" is enabled:
await sendHostStatus({ cpuPct: 42, ramUsedMb: 8192, ramTotalMb: 16384, usagePct: 30, usageLabel: "Claude" });

// Bidirectional: receive events (needs the app's WebSocket enabled).
connectWs({ onEvent: (e) => console.log(e.event, e) });
// Use reconnect: false for one-shot sockets, or stop a persistent socket with:
const ws = connectWs({ onEvent: handleEvent });
ws?.stopReconnect();
```

## What a plugin can do

| Capability | Channel | Notes |
| --- | --- | --- |
| Reactions, speech, media | IPC / WS | `pet.react`, `pet.say`, `pet.showMedia` |
| Rich bubbles (HUD, buttons, forms) | IPC / WS | `pet.say` with `hud`/`actions`/`inputs`/`submitUrl` |
| Receive taps / approvals | WS | `event:"action"` (bubbleId + actionId) |
| Dictated voice input | WS | `event:"input.text"` (from the app's Voice input) |
| Host metrics / usage | WS | `status.host` frame (opt-in in the app) |
| Leases (multi-agent) | IPC | `lease.acquire` / `heartbeat` / `release` |
| Companion control | IPC | `pet.*` plus the app's features via their controllers |

## Worked example

`template/plugin.mjs` shows the full loop: show a pinned bubble with **Approve /
Deny**, receive the tap over WS, and react. Run it with `node plugin.mjs`.

## Protocol reference

- IPC methods and wire format: `docs/AGENT-INTEGRATION.md`.
- WebSocket frames (experimental): `docs/AGENT-INTEGRATION.md` → *Experimental
  WebSocket*, and `docs/WS-FEATURES.md`.

## Security

- Private/local peers only; every request is authenticated with the app's **IPC
  token** (keep it secret; the discovery file contains it).
- Traffic is **unencrypted** — use a trusted LAN/VPN.
- Sender-side: never send secrets in `pet.say` messages (they are validated to
  reject secret-like text) and treat notification/voice content as sensitive.

## Limitations

- No app-side plugin *store* or runtime; plugins are host programs.
- The WebSocket is **experimental**; its frame schema may change.
- Host plugins can't inject answers into an agent's `question` tool; they can
  notify and (for permissions) resolve via the approvals plugin.
