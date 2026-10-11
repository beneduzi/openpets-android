# RFC: a Phone Target for OpenPets (effect mirror)

Status: **draft** · Target: OpenPets desktop host (`OpenPetsHQ/openpets`) · Related:
`docs/WORKS-ON-BOTH.md`, `docs/PLUGIN-PARITY.md` in the Android companion repo.

## Summary

Let the desktop host treat a connected **OpenPets Android companion** (or any
protocol target) as a first-class **remote display**, and **fan a plugin's
effects out** to it — so existing plugins light up the phone with no changes.

## Motivation

- The Android companion already implements the effect surface plugins produce:
  `pet.say` (Markdown, HUD, actions, forms, pinned/id), `pet.react`,
  `pet.showMedia`, `pet.move`/`setScale`/`hide`/`show`, `notify`,
  `status.publish`, plus the experimental WebSocket for bidirectional events.
- Today a plugin only drives the **local** desktop pet; users who want the pet
  on a spare phone must re-implement each plugin.
- Plugins already "describe effects" and the host owns rendering — so the host
  can render those effects on **more than one target**.

## Design

### 1. Target registry
The host maintains a list of targets: the local pet plus zero or more **remote
targets**, each identified by a discovery file (IPC) or WS endpoint + token.
Add/remove/enable is user-controlled (Control Center).

### 2. Effect fan-out
When a plugin emits an effect through `ctx.*`, the host applies it locally **and**
maps it to the protocol for each enabled remote target:

| `ctx.*` | Phone protocol |
| --- | --- |
| `ctx.pet.speak` | `pet.say` (message) |
| `ctx.pet.react` | `pet.react` |
| media / `ui` image | `pet.showMedia` |
| `ui.bubble` (text/actions/inputs/sticky/id) | `pet.say` (`hud`/`actions`/`inputs`/`submitUrl`/`id`/`pinned`) |
| `bubble.update` / `bubble.dismiss` | re-`pet.say` same `id` / `pet.bubbleDismiss` |
| `pet.move` / `setScale` / `show` / `hide` | `pet.move` / `pet.setScale` / `pet.show` / `pet.hide` |
| `ctx.notify` | `notify` |
| `ctx.status` | `status.publish` |

### 3. Inbound routing (phone → proving plugin)
Subscribe to each target's **WebSocket** and route events back to the plugin that
produced the corresponding bubble, as if they were local:
`action` (bubble tap), `command`, `input.text`, `pointer` (tap/drag), `power`,
`status`.

### 4. Opt-in, scope, and identity
- Per **plugin** and per **target** enablement.
- Optional per-target view: mirror all effects, or only bubbles/notifications.
- Carry the plugin's `source` so the phone keeps its per-project accent.

### 5. Degrade gracefully
Effects with **no phone analogue** stay local: `ui.panel`, `ui.delivery`,
`ui.session`, `ctx.bus`, `assistant.registerCapability`, host `ctx.ai`. The host
may optionally surface a small "not shown on the phone" hint.

### 6. Reliability & safety
- **Failure isolation:** an unreachable target never blocks a plugin; sends are
  best-effort with a bounded queue.
- **Coalescing:** high-frequency effects (HUD ticks, movement) are coalesced per
  target to avoid flooding.
- **Security:** the phone already enforces private peers + token; the host must
  not forward secrets, and should treat target traffic as untrusted.

## Compatibility

- **No phone-side protocol changes required** — the effect surface already
  exists (including the experimental WebSocket).
- Additive and opt-in on the host; default behavior unchanged (local only).

## Open questions

1. Naming: "target" vs "display" vs "device".
2. Coalescing/rate-limit policy and defaults.
3. Control Center UX for adding/removing targets.
4. Multi-target semantics (several phones; per-target accents/labels).
5. Whether `pet.move` mirroring is desirable (phones are small) or bubbles-only.

## Ready-to-file issue

**Title:** Feature: mirror plugin effects to a remote OpenPets target (phone)

**Body:**
Plugins currently drive only the local desktop pet. The Android companion
implements the same effect surface (`pet.say`/`pet.react`/`pet.showMedia`/
`pet.move`/…, plus an experimental WebSocket for events/commands). Proposal:
add a **remote target registry** to the host and **fan out** plugin effects
(`ctx.pet.speak/react`, `ctx.ui.bubble`, `ctx.notify`, `ctx.status`, movement) to
enabled targets, mapping `ctx.*` to the protocol; subscribe to each target's
WebSocket and route `action`/`command`/`input.text`/`pointer` back to the
producing plugin. Opt-in per plugin/target, graceful degradation for
desktop-only surfaces (panels/deliveries/sessions/assistant/bus), bounded queues
with coalescing, and failure isolation. No phone-side changes required. Design
and mapping: see the companion repo `docs/WORKS-ON-BOTH.md` (§ Part 2).

Labels: `enhancement`, `plugins`, `integration`.
