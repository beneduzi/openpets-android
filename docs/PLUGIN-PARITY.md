# How OpenPets plugins work — and APK/desktop parity

Source: `OpenPetsHQ/openpets` (`docs/plugins.md`, `docs/sdk.md`,
`packages/sdk/src/index.ts`, `apps/desktop/src/plugin-service.ts`).

## How a desktop plugin works

A plugin is a **validated package plus code that describes effects**; the desktop
**host** owns rendering and side effects. Plugins never touch the DOM/host
directly.

1. **Manifest** `openpets.plugin.json` (SDK/manifest v3): `id`, `name`, `version`,
   `sdkVersion`, `runtime`, `entry`, `permissions`, `configSchema`
   (`text|number|boolean|select|time|date|secret|sound`), `assets`, and optional
   `commands`, `status`, `panels`, `network.hosts`, declarative timers.
2. **Install paths**: bundled official (seeded, can't uninstall), catalog
   (ZIP + SHA-256 + constrained extract → `userData/plugins/{id}`), or local dev
   (snapshot + watch).
3. **Sandbox**: a hidden Electron window, per-plugin session partition, tokened
   SDK bridge; permissions/quotas validated per call.
4. **Permissions**: declared in the manifest, user-approved at install,
   re-checked on every call (e.g. `pet:speak`, `pet:reaction`, `pet:move`,
   `ui:panel`, `notify`, `schedule`, `storage`, `network`, `network:local`,
   `voice:listen`, `secrets`, `auth`, `files`, `system:metrics`).
5. **Runtime API `ctx.*`**: `pet`/`pets` (speak/react/setAnimation/move/getState),
   `ui` (`bubble`→handle with update/dismiss/pin/onAction/onSubmit; `alert`,
   `toast`, `panel`, `delivery`, `session`), `schedule`
   (`once`/`every`/`daily`/`cron`/`at`), `storage` (get/set/keys/subscribe),
   `config`, `events` (pet click/drag/drop, idle, display, power…), `bus`,
   `audio`, `voice` (speak + push-to-talk listen), `notify`, `ai`
   (available/complete/stream), `secrets`, `auth`, `net` (fetch/stream),
   `files`, `system`, `assets`, `commands` (register/unregister), `status`,
   `assistant.registerCapability`, `t`, `log`.
6. **Commands** surface in the pet right-click menu (dynamic, verb-first,
   `placement: "top"` for live controls, `priority`, ~5 s timeout default).
7. **Assistant capabilities** are typed operations (`id`, `description`,
   JSON-schema `input`, bounded JSON result) invoked via host-internal routing —
   distinct from menu commands, and they grant no authority.
8. **Schedules** are wall-clock aware and re-arm after sleep; **storage**
   persists across restarts; state lives in `openpets-plugin-state.json`.

**Key idea:** plugins emit effects; the host renders them. The plugin API is a
host contract, not a target contract.

## Why the APK is different

The Android app is a **protocol target** (IPC + experimental WS), not a plugin
host. It has **no JS runtime, no `ctx.*`, no plugin store**. It renders effects
that arrive over the protocol and runs native companion features. So "parity" is
about the **effect surface**, not the plugin API.

## Parity map (desktop `ctx.*` → APK)

| Desktop plugin capability | APK path | Status |
| --- | --- | --- |
| `pet.speak` (`pet.say`) | IPC `pet.say` (message) | ✅ |
| `pet.react` | IPC `pet.react` | ✅ |
| `ui.bubble` (text, actions, inputs, sticky, duration) | `pet.say` with `hud`/`actions`/`inputs`/`submitUrl`/`id`/`pinned` | ✅ (subset: markdown subset, ≤4 actions/inputs/HUD, no priority) |
| `bubble.update` / `dismiss` | re-`pet.say` same `id` / `pet.bubbleDismiss` | ✅ |
| `bubble.onAction` | `callbackUrl` POST **or** WS `event:"action"` | 🟡 |
| `ui.alert` / `ui.toast` | — (app toasts not exposed) | ❌ |
| `ui.panel` / `delivery` / `session` | — (no such surfaces) | ❌ |
| `pet.move` / `setScale` / `hide` / `show` | IPC `pet.move` / `pet.setScale` / `pet.hide` / `pet.show`; WS `pointer` events | ✅ (follow/physics still ❌) |
| `schedule.*` | host-side (plugin schedules itself) + native features | ✅ (host) |
| `storage` / `config` | host-side persistence | ✅ (host) |
| `events` (click, drag, drop, idle, power, display) | WS emits react/say/media/dismiss/hud/action/input.text **and `pointer` (tap/drag)**; drop/idle/power still not emitted | 🟡 |
| `bus` | — | ❌ |
| `audio.play` | app plays sound on notify/reaction (not addressable) | 🟡 |
| `voice.speak` | app TTS on `pet.say` | ✅ |
| `voice.listen` (push-to-talk) | app **Voice input** → WS `input.text` (app-initiated) | 🟡 |
| `notify` | IPC/WS `notify` → phone notification | ✅ |
| `ai.complete` | host-side (plugin runs on the PC) | ✅ |
| `secrets` / `auth` / `net` / `files` | host-side | ✅ |
| `system.metrics` | device metrics native; **host** metrics via WS `status.host` | 🟡 |
| `commands.register` (pet menu) | WS `commands.register`/`unregister` → Settings → *Plugin commands*; invoke → WS `event:"command"` | ✅ (list UI, not the pet menu) |
| `status` | — | ❌ |
| `assistant.registerCapability` | app has no assistant | ❌ |

## How to make it seamless

1. **Host-side plugins targeting the phone (near-term, recommended).** Plugins run
   on the PC (desktop runtime, an OpenCode plugin, or a Node process) and emit
   effects over IPC/WS — see `docs/HOST-PLUGINS.md` and
   `integrations/host-plugins/`. Programmatic effects (speak/react/media/bubbles/
   actions) are near-parity. A single plugin can target **desktop or phone** if
   written against a small adapter (the `openpets-client`).
2. **Effect-forwarding bridge (true seamlessness).** The desktop **host** mirrors
   its own plugin effects to the phone (a "phone target" in the desktop app), so
   existing desktop plugins appear on the phone unchanged. This is an **upstream**
   feature (desktop side), not something the APK can add alone.
3. **On-device runtime (full parity + store).** Run the same JS plugins in the APK
   inside a sandbox (QuickJS/WASM) behind an SDK bridge + permission UI. Largest
   effort; required only if an in-app plugin store is the goal.

## Gap-closing (done this round)

- ✅ `pet.move` / `pet.setScale` / `pet.hide` / `pet.show` (IPC + WS).
- ✅ `notify` (IPC/WS) → phone notification.
- ✅ Plugin command registry: WS `commands.register`/`unregister`; Settings →
  *Plugin commands*; invoke → WS `event:"command"`.
- ✅ Pet interaction `pointer` (tap/drag) events over WS.

Still open: a `status` surface, richer events (drop/idle/power), `follow`/physics,
and a common-effect "works on both" plugin guide.

Surfaces with **no meaningful phone analogue**: panels, deliveries, sessions,
bus, assistant capabilities, host AI.

## Recommendation

- **Now:** treat host-side plugins (option 1) as the extension model; document the
  common effect subset so one plugin can drive either target; keep the native
  features for standalone use.
- **Upstream:** propose a **phone target / effect-mirror** in the desktop app
  (option 2) — that's what makes desktop plugins show up on the APK with no
  rewrite.
- **Later (optional):** on-device runtime (option 3) if an APK plugin store
  becomes a goal.
