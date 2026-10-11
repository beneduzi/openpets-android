# Agent integrations

The Android app is the OpenPets *target/destination*. Agents (the OpenCode plugin, the OpenPets MCP server, and the `@open-pets` CLI) point at the device's IP instead of a desktop app.

## How it works

- **IPC:** `openpets-ipc` v1 on TCP port `18420`.
- **Remote:** `openpets-remote` v1 on TCP port `18421`.
- **Preferred path:** Agents use IPC via the discovery file. Remote is a fallback for MCP/CLI.

## MCP Server Configuration

### 1. Generate a discovery file

Run `tools/write-discovery.sh <DEVICE_IP> <IPC_TOKEN> [IPC_PORT] [OUT_PATH]`. The default `OUT_PATH` is `~/.config/opencode/openpets-discovery.json`.

Example JSON shape:

```json
{
  "protocol": "openpets-ipc",
  "protocolVersion": 1,
  "endpoint": "tcp://192.168.0.14:18420",
  "token": "<IPC token>",
  "appVersion": "0.1.0",
  "pid": 12345,
  "platform": "linux"
}
```

**Constraints:** The endpoint TCP address must be private/local IPv4 (`192.168.x`, `10.x`, `172.16–31.x`, `169.254.x`, `127.x`, or `100.64–127.x`). The token must be 16–256 characters. `platform` must match the consuming process (`linux`, `darwin`, or `win32`), and `pid` must be greater than 0.

### 2. Point the MCP server at the device

Set `OPENPETS_DISCOVERY_FILE`; it covers both the OpenCode plugin and MCP.

**Generic MCP client config:**

```json
{
  "mcpServers": {
    "openpets": {
      "type": "stdio",
      "command": "npx",
      "args": ["-y", "@open-pets/mcp@latest", "--pet", "default"]
    }
  }
}
```

**Canonical CLI form:** `npx @open-pets/cli mcp`

**OpenCode-specific** (`~/.config/opencode/opencode.jsonc`):

```jsonc
"openpets": {
  "type": "local",
  "command": ["npx", "-y", "@open-pets/cli@4.0.0", "mcp", "--pet", "default"],
  "environment": {
    "OPENPETS_DISCOVERY_FILE": "/home/<user>/.config/opencode/openpets-discovery.json"
  },
  "enabled": true
}
```

## Available MCP tools

| Tool | What it does |
| --- | --- |
| `openpets_status` | Reports the target pet ID and connectivity. |
| `openpets_react` | Plays a reaction animation. |
| `openpets_say` | Shows a short speech bubble. |

## OpenCode integration

There are two ways to drive the device from OpenCode:

1. **MCP (one-way, recommended first step).** The `openpets` MCP server
   (`npx @open-pets/cli mcp`) exposes `openpets_status` / `openpets_react` /
   `openpets_say`. It uses the discovery file; `export
   OPENPETS_DISCOVERY_FILE="$HOME/.config/opencode/openpets-discovery.json"`
   before starting OpenCode. Local/discovery mode only (no remote push).

2. **Host plugins (two-way).** Copy the plugins bundled in this repo to your
   OpenCode plugins dir and restart:

   ```bash
   tools/install-opencode-plugins.sh
   opencode service restart
   ```

   | Plugin | Adds |
   | --- | --- |
   | `openpets.ts` | reactions/speech from OpenCode activity; input-needed notices |
   | `openpets-interact.ts` | `ask` / `confirm` tools — answered **on the pet** |
   | `openpets-media.ts` | `media` tool — show an image on the pet |
   | `openpets-approvals.ts` | permission approvals on the pet |
   | `openpets-voice.ts` | on-device speech → session text |

   Two-way features require the app's **WebSocket (experimental)** endpoint.
   Full details: [`integrations/opencode-plugin/README.md`](../integrations/opencode-plugin/README.md)
   and [`integrations/host-plugins/README.md`](../integrations/host-plugins/README.md).

## Remote mode (MCP / CLI)

```bash
export OPENPETS_REMOTE_ENDPOINT="tcp://192.168.0.14:18421"
export OPENPETS_REMOTE_TOKEN="<remote token from the app>"
export OPENPETS_REMOTE_CLIENT_ID="android-companion"   # optional
```

The OpenCode plugin does **not** support remote mode. Remote constraints: raw TCP, one request per socket, 4 KB cap, private/local IPv4 only, token 43–256 base64url characters, and rate limited to 30 requests / 60 s.

## Experimental WebSocket (`openpets-ws`, opt-in)

### Plugin commands

Experimental: a client sends `{ "id":"…", "method":"commands.register", "params":{"source":"plugin", "commands":[{"id":"refresh", "title":"Refresh", "description":"Optional description"}]}}`. The app lists registered commands in Settings → *Plugin commands*. Invoking one emits `{ "type":"event","event":"command","source":"plugin","commandId":"refresh" }` to connected clients.

Voice input is experimental and microphone access is opt-in. When the user dictates, the app can send `{ "type":"event", "event":"input.text", "text": … }`; a host client may inject this as user input.

An optional `ws://` endpoint (default port `18423`) that is **disabled by default** and marked **experimental**. Enable it in Settings → *WebSocket (experimental)*. It:

- authenticates with the **IPC token** and only accepts **private/local IPv4** peers;
- sends `hello` + `status` on connect, then streams pet/companion events as JSON frames;
- accepts the same command surface as IPC: send `{ "id", "method", "params" }` and receive `{ "id", "ok", "result" }` or `{ "id", "ok", "error" }`.

Connect with `ws://<device-ip>:18423/?token=<IPC_TOKEN>`. Because this is experimental, the message schema may change between versions.

### Host status

A client can send `{ "type":"status.host", "params":{ cpuPct, ramUsedMb, ramTotalMb, diskUsedPct, netRxKbps, netTxKbps, loadAvg, ts, usagePct, usageLabel } }`. A client may include usage percentages (for example, quota usage) and an optional short label. When enabled in Settings, the app shows these metrics as a pinned HUD on the pet and clears it after about 30 seconds without updates.

### Interactive actions

A client can send `pet.say` with `actions` (each `{ "id", "label" }`). When the user taps an action on the pet, connected clients receive `{ "type":"event", "event":"action", "bubbleId":…, "actionId":… }` and can react, for example by sending another command. This interaction is experimental.

## Diagnose your setup

```bash
npx @open-pets/cli status
npx @open-pets/cli doctor
```

Direct test scripts:

```bash
node tools/send-reaction.mjs 192.168.0.14 18420 <IPC_TOKEN> react editing
node tools/send-reaction.mjs 192.168.0.14 18420 <IPC_TOKEN> say "Running tests" testing
node tools/send-media.mjs    192.168.0.14 18420 <IPC_TOKEN> ./print.png "screenshot" 8000
```

## Media (`pet.showMedia`)

- **Parameters:** `data` (base64 image, pushed inline—preferred and needs no network) or `url` (http/https; the app downloads it); optional `message`, `reaction`, `durationMs` (1000–30000, default 8000), and `clickUrl`.
- **Types and size:** png/jpg/jpeg/webp/gif; about 10 MB limit.

## Reaction → animation map

| Reaction | Animation |
| --- | --- |
| `idle` | `idle` |
| `thinking` | `review` |
| `working` / `editing` / `running` | `running` |
| `testing` / `waiting` | `waiting` |
| `waving` | `waving` |
| `success` / `celebrating` | `jumping` |
| `error` | `failed` |

## IPC method reference

Requests use `{ id, version:1, token, method, params }`. Success responses use `{ id, ok:true, result }`; errors use `{ id, ok:false, error:{code,message} }`.

**Accepted methods:** `hello`, `status`, `pets.list`, `pets.install`, `pets.install-local`, `lease.acquire`, `lease.heartbeat`, `lease.release`, `pet.react`, `pet.say`, `pet.showMedia`, `pet.bubbleDismiss`, `pet.move`, `pet.setScale`, `pet.hide`, `pet.show`, `notify`, `status.publish`.

`pet.say` accepts optional `silent: true` to suppress sound, haptics, TTS, and notification (for mirrored HUD/state updates).

Clients can call `notify` with a title and optional body to post a phone notification.

Clients can use `pet.move` (`x`, optional `y` as 0..1 fractions; optional `durationMs` 0..10000), `pet.setScale` (`scale` 0.25..3), `pet.hide`, and `pet.show`. Over WebSocket, clients also receive `pointer` events for pet taps and drags.
WebSocket `status` and `power` events are experimental. `status.publish` accepts single-line text (1..80 characters) and optional tone (`default`, `success`, `warning`, `error`).

The lease TTL is 15 s; `lease.heartbeat` renews the TTL and `lease.release` deletes the lease.

**Known limitation:** `pet.bubbleDismiss` is an accepted Android extension and is not present in upstream desktop IPC.

The Remote server only accepts private/local IPv4 peers.
