# RFC: a Phone Target for OpenPets (validated effect mirror)

Status: **draft v2** (incorporates review) · Target: OpenPets desktop host
(`OpenPetsHQ/openpets`) · Related: `docs/WORKS-ON-BOTH.md`, `docs/PLUGIN-PARITY.md`.

## Summary

Let the desktop host treat a connected **OpenPets Android companion** (or any
protocol target) as a first-class **remote display**, and **mirror validated,
host-owned plugin effects and lifecycle** to it — so existing plugins light up the
phone without rewriting them and without exposing raw SDK calls.

Mirror **effects**, not SDK calls, DOM, or sandbox traffic. The phone is a
target, not a plugin host.

## Motivation

- The Android companion already renders the effect surface plugins produce:
  `pet.say` (Markdown, ≤4 HUD bars, ≤4 actions, ≤4 inputs, `submitUrl`, `id`,
  `pinned`), `pet.react`, `pet.showMedia`, `pet.move`/`setScale`/`show`/`hide`,
  `notify`, `status.publish`, `pet.bubbleDismiss`; plus an experimental
  bidirectional WebSocket for events and commands.
- Today a plugin drives only the **local** desktop pet. Users who want the pet on
  a spare phone must port each plugin.

## Design

### 1. Target registry
Host keeps the local pet plus zero or more **remote targets** (discovery file or
WS endpoint + token). Add/remove/enable is user-controlled (Control Center).

### 2. Effect mirroring — with explicit translations
The host already validates and owns effects; mirror the **validated** effect to
each enabled target. The mapping is **not** 1:1 — each row lists the translation.

| `ctx.*` | Phone | Translation / limits |
| --- | --- | --- |
| `pet.speak` | `pet.say` | single-line ≤140, validated (no URL/path/code). Distinguish **silent** vs **announced** (see §5). |
| `pet.react` | `pet.react` | map desktop-only reaction names to the 11 phone reactions. |
| `ui` image / asset | `pet.showMedia` | host must **resolve** assets/paths to bytes or a reachable URL; phone media has no `source`; 10 MiB cap; GIF is static today. |
| `ui.bubble` | `pet.say` | desktop HUD is `{items}` 0–100; phone `hud` needs `label`+`max` (≤4). Desktop `input` is **singular**; phone `inputs` is an array, text/number only, needs labels, no select/defaults. Desktop action `id`s/phone ids differ in charset/length. `duration`/`priority`/dismissal policy are **not** preserved. |
| `bubble.update` | re-`pet.say` same `id` | the adapter must **merge the patch into a full descriptor**; re-sending resets lifetime and rebuilds forms. |
| `bubble.dismiss` | `pet.bubbleDismiss(id)` | |
| `pet.move` | `pet.move` | desktop screen coordinates → phone **normalized fractions** (0–1). |
| `pet.setScale`/`show`/`hide` | same names | direct. |
| `ctx.notify` | `notify` | |
| `ctx.status` | `status.publish` | phone has **one** global status line (its foreground notification); map SDK tone `info`→`default`; define aggregation and a `status.clear`. |
| panels / deliveries / sessions | — | **deferred** (no phone analogue). |
| bus / AI / storage / network | — | **host services**; not mirrored as calls, but their resulting *display* effects still mirror. |
| `system.metrics` | `status.host` | a **read** API, not an emitted effect; the host may publish it to the phone. |
| `voice.speak` | phone TTS on `pet.say` | **approximation** only. |

### 3. Ownership & inbound routing (replace "producing plugin")
Inbound events do **not** carry a plugin identity. The host must maintain explicit
ownership maps:

- **Bubbles/actions:** the host generates a **phone-compatible opaque wire id**
  per logical bubble and keeps `(target connection, wire bubble id) → (plugin
  generation, logical bubble, allowed action ids)`. On an inbound
  `{bubbleId, actionId}` (the phone sends nothing else), reject absent/unknown/
  stale ids and actions not in the allowed set. `source` is a **display label**,
  never ownership.
- **Commands:** route `{source, commandId}` through the host's existing
  registered-command invocation; namespace registrations across instances;
  revoke on disable/reload; restore current registrations after reconnect;
  commands needing forms stay local.
- **Forms:** phone forms POST values to `submitUrl` and emit **no submit event**.
  Existing plugins supply callbacks, not URLs. Either specify an authenticated,
  reachable **host callback endpoint** (or add a WS submit frame), or **defer
  forms**.
- **Voice `input.text`:** carries no recipient/capture id and cannot satisfy
  `ctx.voice.listen` or pick a plugin automatically. Require **explicit
  user-selected ownership**, or defer.
- **Pointer / power:** **device-wide subscription events**, not bubble callbacks.
  Preserve target origin and existing permissions; never present phone battery as
  desktop battery; pointer needs drag-start/end semantics.
- **Observations vs callbacks:** `say`/`react`/`media`/`hud`/`status`/`dismiss`
  broadcasts are **echoes** of host-side intent, not user callbacks. Keep them
  out of the inbound callback path and prevent feedback loops.

### 4. Lifecycle & arbitration
- Define whether a phone action **dismisses the logical bubble everywhere**.
- When a desktop timeout/replacement revokes a bubble handle, its **remote
  callbacks must become invalid** too (no orphaned buttons).
- The phone has **one bubble slot**: specify **arbitration** between plugins'
  periodic updates (not just per-bubble coalescing).
- Honor the optional **15 s leases** as validity/liveness, **not** exclusive
  ownership or multi-pet isolation.

### 5. Silent updates & transport bounding
- **Silent semantics:** a mirrored `pet.say` can currently beep, vibrate, notify,
  and speak. Non-announcement updates (HUD ticks, movement) must use a **silent
  update path** (the phone already has a silent feature-HUD channel), not a full
  announced `pet.say`.
- **Bound queues** by **bytes and count**, plus in-flight work, timeouts and
  inbound rates. Coalesce by target/object/property; preserve dismissal ordering;
  prevent stale updates resurrecting dismissed bubbles. On reconnect, restore the
  **current** state and command registrations — not historical notifications or
  actions.
- The phone's WS server currently uses an **unbounded write executor** and a
  **1 MiB** frame cap (vs 10 MiB media). Reconcile: cap writes and use capped IPC
  media transfer or explicitly smaller WS payloads.

### 6. Security
- Review every inbound frame: schema, lengths, **registered ownership**, and
  current enablement. Authenticated target traffic must **never** become arbitrary
  SDK calls.
- `ws://` and raw TCP expose the **bearer token and content** on the wire; private
  IP checks are **not** encryption or strong target auth. Keep credentials
  host-only, protect discovery storage, redact token-bearing URLs, and require a
  trusted/protected network.

## Compatibility (narrowed)

- **Phone-side protocol changes are not required for the MVP** (bubbles, actions,
  simple commands, notifications) — the surface exists.
- **Known gaps** that are *not* covered without follow-up: **forms** (no submit
  event), **generic voice routing** (no capture id), **motion semantics**, and a
  **silent update** path on the desktop side. The "unchanged plugins, no phone
  changes" guarantee is therefore narrowed to the verified MVP below.
- Additive and opt-in on the host; default behavior unchanged.

## MVP scope & non-goals
- **MVP:** one configured phone / default pet; bubbles/alerts; actions; simple
  commands; notifications.
- **Defer:** pet motion, generic voice routing, forms, panels/deliveries/sessions.

## Acceptance tests (required before filing)
- colliding/unknown/stale bubble or action ids are rejected;
- remote callbacks invalidated on desktop replacement/timeout;
- disconnect/reconnect restores current state without replaying history;
- HUD/movement storms are coalesced/silent and never beep/vibrate/speak per tick;
- malformed/hostile target frames are rejected without reaching the SDK.

## Ready-to-file issue

**Title:** Feature: mirror validated plugin effects to a remote OpenPets target (phone)

**Body:** Add a **remote target registry** to the desktop host and **mirror
validated, host-owned effects and lifecycle** to enabled targets (phone). Map
`ctx.pet.speak/react`, `ctx.ui.bubble` (with the translation/limits noted), media
(host-resolved bytes), `ctx.notify`, `ctx.status`, and movement to the phone
protocol; keep desktop-only surfaces (panels/deliveries/sessions, bus/AI/storage/
network) host-side and mirror only their display effects. Maintain **ownership
maps** so inbound `{bubbleId, actionId}`, `{source, commandId}`, and pointer/power
events route to the right plugin with lifecycle/arbitration rules; add **silent
update** semantics and **bounded** queues; and specify the known gaps (forms have
no submit event; voice has no capture id; motion semantics). MVP = one phone,
bubbles/alerts, actions, simple commands, notifications. Design + mapping +
acceptance tests: see the companion repo `docs/WORKS-ON-BOTH.md` and this RFC.

Labels: `enhancement`, `plugins`, `integration`.
