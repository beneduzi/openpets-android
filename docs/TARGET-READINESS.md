# Target-readiness plan — app changes if the upstream phone-target RFC is approved

The upstream proposal (`docs/UPSTREAM-PHONE-TARGET.md`) makes the **desktop host**
mirror validated plugin effects to this app. The app is already the target, so
the host carries the bulk (target registry, effect mapping, ownership maps,
coalescing). The app only needs a few **optional hooks** to make mirroring
correct.

## Verdict: nothing is dropped, and defaults don't change

Every change below is **additive and opt-in**. Existing agent usage and every
standalone companion feature behave exactly as today.

| Change | Type | Default / existing behavior |
| --- | --- | --- |
| `silent` flag on `pet.say` | additive flag (default off) | unchanged — an agent `pet.say` still beeps/speaks |
| WS `submit` event | additive event | unchanged — forms still POST to `submitUrl` |
| WS write-queue bounding | internal hardening | no external change (only avoids memory blow-up under flooding) |
| media frame-size reconciliation | docs/cap | unchanged — large media keeps using **IPC** |
| pointer drag start/end | additive events | unchanged — tap/drag points still emitted |
| voice `captureId` | additive optional field | unchanged — `input.text` still works anonymously |
| host-provided accent/label | additive optional params | unchanged — current per-source accent still applies |
| `status.clear` | additive method | unchanged — `status.publish` still updates the notification |
| feature advertisement in `hello`/`status` | additive fields | clients ignore unknown fields |

Only realistic behavior delta: if the **host** opts into `silent`, mirrored HUD
ticks stop alerting (that's the point); a plain agent `pet.say` is unaffected.

## Phased plan

Each phase: **@fixer**, one coherent increment, verified with `:protocol:test`,
`:app:testDebugUnitTest`, `:app:assembleDebug`, committed at the boundary.

**Phase 1 — Silent updates (highest value).**
Add an optional `silent` boolean to `pet.say` (validator + sink + stage/service
path that renders with **no** sound, haptics, TTS, or notification). Tests:
default behavior unchanged; `silent:true` suppresses effects.

**Phase 2 — WS form-submit event.**
When a form is submitted, **emit** `{ "type":"event","event":"submit",
"bubbleId":…, "values":{…} }` to WS clients (in addition to the existing
`submitUrl` POST) so the host can receive it without an HTTP endpoint.

**Phase 3 — WS transport hardening.**
Bound `WebSocketServer`'s per-connection write executor (bounded queue; drop/coalesce
oldest) so a chatty host can't balloon memory; document WS-vs-IPC media sizing
(keep large media on IPC). No change under normal load.

**Phase 4 — Pointer drag semantics + voice capture id.**
Emit `pointer` with a phase (`start`/`move`/`end`); add an optional `captureId`
to `input.text` and accept a host-supplied one. Additive.

**Phase 5 — Interop polish (optional).**
Accept optional `accent`/`label` on `pet.say` (so phone colors match desktop);
add `status.clear`; advertise supported features/limits in `hello`/`status`.

## Explicitly NOT touched

Standalone companion features (timer/reminders/focus/…); the existing IPC/WS
method set and their defaults; leases; Remote; media-over-IPC; settings; the
whole agent flow.

## Reconcile with the Oracle

The Oracle deep-dive (RPC + upstream issue) may refine items 3–5 and the RPC
interop note; fold accepted findings here before implementing.
