# Works-on-both plugins, and the upstream phone-target proposal

Two things that make the desktop app and the APK feel seamless:

1. a **common effect subset** a single plugin can target on either side, and
2. a proposal for the desktop host to **mirror plugin effects to the phone**.

## Part 1 — The common effect subset

A host plugin should emit only effects that **both** the desktop pet and the
Android target understand. Code against the shared subset via
`integrations/host-plugins/openpets-client.mjs`:

| Effect | Desktop (`ctx.*`) | Phone (protocol) | Notes |
| --- | --- | --- | --- |
| speak | `pet.speak` | `pet.say` | single-line, ≤140 chars, no URLs/paths/code |
| react | `pet.react` | `pet.react` | 11 reactions |
| media | `ui`/image | `pet.showMedia` | url or base64; png/jpg/webp/gif; ≤10 MB; duration ≤30 s |
| bubble | `ui.bubble` | `pet.say` (`hud`/`actions`/`inputs`/`submitUrl`/`id`/`pinned`) | phone subset: markdown subset, ≤4 HUD/actions/inputs |
| update/dismiss bubble | `bubble.update/dismiss` | re-`pet.say` same `id` / `pet.bubbleDismiss` | |
| action tap | `bubble.onAction` | WS `event:"action"` (or `callbackUrl`) | |
| notify | `ctx.notify` | `notify` | phone posts a system notification |
| move/scale/hide | `pet.move`/`setScale`/`show`/`hide` | `pet.move`/`pet.setScale`/`pet.show`/`pet.hide` | |
| status line | `ctx.status` | `status.publish` | phone updates its foreground notification |
| commands | `ctx.commands.register` | WS `commands.register` + `event:"command"` | phone lists them in Settings |
| pointer | `pet:interact`/events | WS `event:"pointer"` (tap/drag) | |
| host metrics | `ctx.system.metrics` | WS `status.host` | opt-in on the phone |
| voice in | `ctx.voice.listen` | app Voice input → WS `event:"input.text"` | app-initiated on the phone |

**Not in the subset** (desktop-only or no phone analogue): `ctx.bus`, `ui.panel`,
`ui.delivery`, `ui.session`, `assistant.registerCapability`, `ctx.ai`, and
desktop pet physics/follow-cursor.

### Example

```js
import { react, say, connectWs } from "../host-plugins/openpets-client.mjs";

export async function announceBuild(n) {
  await react("working");
  await say(`Build #${n} running`, { reaction: "running", source: "ci" });
  const ws = connectWs({ onEvent: (e) => {
    if (e.event === "action" && e.bubbleId === "ci-build") void handle(e.actionId);
  }});
  await say("Cancel build?", {
    bubbleId: "ci-build", pinned: true,
    actions: [{ id: "cancel", label: "Cancel", style: "danger" }],
  });
}
```

The same function runs against the desktop host (swap the client for `ctx.*`) or
the phone (this client) — the **effects** are identical.

## Part 2 — Upstream proposal: a phone target / effect mirror

**Goal:** existing desktop plugins appear on the phone with no rewrite.

**Idea:** the desktop host gains a **remote target** (the phone) and fans a
plugin's effects out to it, mapping `ctx.*` to the phone protocol.

- **Target registry:** the host tracks targets (local pet + zero or more remote
  phones indentified via their discovery files / WS endpoints).
- **Effect fan-out:** when a plugin calls `ctx.pet.speak` / `ctx.ui.bubble` /
  `ctx.notify` / `ctx.status`, the host renders locally **and** forwards the
  mapped effect to each remote target over IPC (`pet.say`, `pet.react`,
  `pet.showMedia`, `pet.move`, `notify`, `status.publish`) / WS (events).
- **Mapping:** the common-effect table above is the fan-out contract. Effects
  outside it (panels, deliveries, sessions, assistant, bus, AI) stay local; the
  host can optionally surface a "not shown on phone" notice.
- **Inbound (phone → host):** the host subscribes to the phone's WS and routes
  `action`, `command`, `input.text`, `pointer`, `power` back to the owning plugin.
  Routing requires host-side ownership maps keyed by the wire IDs the host
  generated — the phone's `source` label is display metadata, not ownership.
- **Opt-in & per-target safety:** per plugin and per target; the phone already
  enforces private peers + token.
- **Failure isolation:** an unreachable phone never blocks a plugin.

**Clean mapping:** speak, react, media, dismissible bubbles/actions, notify,
move/scale/hide, status, commands, pointer, host metrics.

**Needs host-side work (not "clean" today):**

- **HUD** — the phone requires a message and defaults an omitted `max` to 1; send
  `max:100` and labels ≤24 chars, and map or omit desktop icon glyphs.
- **Forms** — the phone POSTs to `submitUrl` (and emits an experimental WS `submit`
  event); desktop plugins register callbacks, so parity is not clean.
- **Voice** — `input.text` carries an optional `captureId` but no recipient
  selection; do not auto-inject transcripts into a session.
- **Mutable / `bubble.update` synchronization** — requires the silent-update path
  (the phone's `silent` HUD channel is internal) so syncing does not re-announce.
- **Motion** — desktop `moveBy`/`moveTo`/`wander` are not a direct mapping to the
  phone's `pet.move`; resolve work-area/pet-centre transforms first.

**Degrade gracefully:** panels/deliveries/sessions → local only; assistant
capabilities/AI → local only.

**Deliverable:** an upstream RFC/issue proposing the phone target, the effect
fan-out table above, inbound routing, and the opt-in semantics. The app now
exposes the effects and events this needs, plus optional `silent` `pet.say`,
WS `submit`, pointer `phase`, and voice `captureId`; the host still owns mapping,
ownership, and coalescing. See `docs/UPSTREAM-ISSUE.md`.

## Recommendation

- **Now:** author host plugins against the common subset (Part 1); they work on
  both targets.
- **Upstream:** drive Part 2 so existing desktop plugins light up the phone with
  no changes — that is the real "seamless APK vs desktop" outcome.
