# OpenCode plugin — OpenPets companion (host side)

The companion Android app is the **target**; this plugin is the **host-side
half** for [OpenCode](https://opencode.ai). It drives the pet from your coding
agent and, most importantly, **notifies when the assistant is waiting on you**.

## What it does

| Trigger | Pet behavior |
| --- | --- |
| A prompt is submitted | `thinking` reaction |
| `edit` / `write` / `patch` tools run | `editing` reaction |
| test commands run in a shell tool | `testing` reaction |
| **the `question` tool runs (assistant asks you something)** | `waiting` + "Your input is needed" bubble |
| **`permission.asked` (assistant needs approval)** | `waiting` + "Your input is needed" bubble |
| session error | `error` reaction + bubble |
| session becomes idle | `success` reaction |

Each event is tagged with `source` (the current project folder) so the companion
colors/labels it per project.

## Why it does not need the MCP

The plugin speaks the OpenPets **IPC** protocol directly to the device using the
discovery file (`OPENPETS_DISCOVERY_FILE`, or
`~/.config/opencode/openpets-discovery.json`). Notifications therefore keep
working even when no OpenPets MCP client is connected. (The MCP remains the
recommended path for agent-issued `say`/`react` tool calls.)

## Install

1. Make sure a discovery file exists (see the app's
   [agent integration guide](../../docs/AGENT-INTEGRATION.md) and
   `tools/write-discovery.sh`). It looks like:

   ```json
   { "endpoint": "tcp://192.168.0.163:18420", "token": "…" }
   ```

2. Copy the plugin to your OpenCode plugins directory:

   ```bash
   # global (all projects)
   mkdir -p ~/.config/opencode/plugins
   cp openpets.ts ~/.config/opencode/plugins/openpets.ts

   # or project-local
   mkdir -p .opencode/plugins
   cp openpets.ts .opencode/plugins/openpets.ts
   ```

   Alternatively, reference this folder from your `opencode.json(c)`:

   ```jsonc
   { "plugins": ["/absolute/path/to/integrations/opencode-plugin"] }
   ```

3. Restart the OpenCode service so plugins reload:

   ```bash
   opencode service restart
   ```

## Configuration

- `OPENPETS_DISCOVERY_FILE` — path to the discovery file (optional; defaults to
  `~/.config/opencode/openpets-discovery.json`, honoring `XDG_CONFIG_HOME`).

## Notes

- Cooldowns: reactions 3–10 s, speech 20 s per category, so the pet is not
  spammed.
- The plugin never throws into the host; every socket error is swallowed.
- This is the host-side companion for the **stable IPC** path. The app's
  experimental `ws://` endpoint (see the app README) is a separate, opt-in
  transport and is not required by this plugin.

## `openpets-approvals.ts` — host-side approval flow (experimental)

An **optional, experimental** second plugin that finishes the "interactive
approvals on the pet" idea. It requires the app's experimental WebSocket
endpoint to be enabled (Settings → *WebSocket (experimental)*, default
`ws://<device>:18423`).

- When a **permission** request is raised, it shows a pinned pet bubble with
  **Approve / Always / Deny** buttons.
- It holds a WebSocket connection to the app and receives your tap as
  `{"type":"event","event":"action","bubbleId":…,"actionId":…}`.
- On a matching tap it calls `ctx.permission.reply(...)` to resolve the request.

Caveats:

- The OpenCode permission-event payload is not in the public schema, so the
  request/session ids are parsed defensively; adjust `extractRequest(...)` if
  your build differs.
- **Question-tool answers cannot be injected back into the tool** by a plugin,
  so this covers permissions only. Questions are still *notified* by
  `openpets.ts`.
- If the WS endpoint is disabled/unreachable, it is a no-op and approvals are
  answered normally in the TUI.

Install the same way as `openpets.ts`, then restart the OpenCode service:

```bash
cp openpets-approvals.ts ~/.config/opencode/plugins/openpets-approvals.ts
```

## `openpets-voice.ts` — host-side voice input (experimental)

Consumes the app's **Voice input**. It connects to the experimental WebSocket
endpoint and, on `{"type":"event","event":"input.text","text":…}` (your phone
dictation transcript), injects the text into OpenCode as a user message so the
coding agent receives it.

- Target session: `OPENPETS_VOICE_SESSION` (env) pins a session; otherwise the
  most recently listed session is used (best effort).
- Requires the app's WebSocket endpoint **and** the app's Voice input to be used
  (microphone is opt-in on the phone).
- Experimental; the frame schema may change.

```bash
cp openpets-voice.ts ~/.config/opencode/plugins/openpets-voice.ts
```

## `openpets-interact.ts` — answer on the pet (experimental)

Registers two OpenCode tools that make the pet a real **feedback path** from the
phone back to the agent (the built-in `question` tool can't be answered from the
pet; a plugin-registered tool can):

- **`openpets_ask`** `{ question, options: string[] (≤4) }` → shows the options as
  a pinned pet bubble; returns **the selected option** as the tool result.
- **`openpets_confirm`** `{ message }` → shows Approve / Deny; returns
  **approved/denied**.

Requirements: the app's **experimental WebSocket** enabled (to receive the tap)
and a discovery file. Awaits the tap with a timeout (default 60 s;
`OPENPETS_ASK_TIMEOUT_MS`).

```bash
cp openpets-interact.ts ~/.config/opencode/plugins/openpets-interact.ts
```

## `openpets-media.ts` — display images on the pet (experimental)

Registers the **`media`** tool so the agent can put an image on the pet from a
session — just say *"show it on the pet"*:

- `media { path }` — a **local file** (png/jpg/jpeg/webp/gif); read on the host and
  sent inline as base64 (no network on the device).
- `media { url }` — an **http(s) image URL** the device fetches.
- optional `caption`, `durationMs` (1–30 s).

The app caps media at 10 MB and shows it in a transient bubble (with the caption,
optional actions/click) — see `pet.showMedia` in the agent guide.

```bash
cp openpets-media.ts ~/.config/opencode/plugins/openpets-media.ts
```

## `openpets-move.ts` — puppet the pet (experimental)

Registers two tools to control the pet's position and animation:

- **`move`** `{ x, y?, durationMs? }` — walk the pet to a position (`x`/`y` are
  fractions 0..1 of the screen). It plays the **directional walk animation** and
  waits for the walk to finish, so chained moves play in order.
- **`react`** `{ reaction }` — play any reaction animation.

So a session can do *"move right, then left, then right, then idle, then
celebrate."*

```bash
cp openpets-move.ts ~/.config/opencode/plugins/openpets-move.ts
```



## How the round-trip works (server side)

```
OpenCode (agent)                       OpenPets Android
  MCP tools (status/say/react) ──IPC──▶ renders
  plugin (openpets.ts) event-driven ──IPC──▶
  plugin tool execute ──IPC pet.say (actions)──▶ bubble buttons
                                          │ user taps
  plugin WS client ◀── WS event:"action" ─┘
       │
       ├─ permission.asked → ctx.permission.reply (openpets-approvals)
       ├─ input.text       → ctx.session.prompt   (openpets-voice)
       └─ action on our bubble → resolve the tool result (openpets-interact)
```

Auth = the discovery file's **IPC token** (LAN, private peers). The app never
calls OpenCode; a **host-side WS subscriber plugin** is what receives its events.



