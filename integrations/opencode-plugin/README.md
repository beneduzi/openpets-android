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
