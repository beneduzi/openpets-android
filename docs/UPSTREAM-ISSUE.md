<!--
Ready-to-file upstream issue for the OpenPets desktop repo.
Produced from the Oracle deep-dive (RPC + upstream design review) and the app's
verified current behaviour. File with:
  gh issue create --repo <owner>/openpets --title "Feature: opt-in phone targets for validated plugin effects" --body-file docs/UPSTREAM-ISSUE.md
-->

# Title

```
Feature: opt-in phone targets for validated plugin effects
```

# Body

## Summary

Add an opt-in remote-target adapter to the OpenPets desktop host so compatible effects from existing desktop plugins can also appear on an OpenPets Android companion. Mirror validated display effects and their host-owned lifecycle—not SDK calls, DOM, or sandbox traffic. Start with one configured phone and a deliberately limited announcement/interaction subset; capability-gate richer synchronization rather than promising full desktop parity.

## Motivation

The [community Android companion](https://github.com/beneduzi/openpets-android) already accepts OpenPets IPC commands and provides an experimental bidirectional WebSocket. Today, desktop plugins must be ported or separately bridged to reach it. A host-owned target adapter would let existing compatible plugins use the phone without implementing their own networking, credentials, or callback routing.

## Scope and non-goals

- Additive and disabled by default; local desktop behavior remains unchanged.
- User-controlled target enablement and per-plugin/per-target sharing.
- Initially: one phone displaying effects for the default pet.
- No Android plugin runtime/store, arbitrary remote SDK execution, or sandbox forwarding.
- Defer forms, voice capture/routing, motion, multi-pet support, panels, deliveries, sessions, custom audio, and assistant capabilities.

## Proposed design

### 1. Target registry and effect boundary

Maintain a registry containing the local target and configured remote targets: stable ID, IPC/WS endpoints, host-only credentials, enablement, supported capabilities, and connection generation. Control Center owns configuration; plugins cannot supply arbitrary endpoints.

Fan out only effects accepted after existing permission, quota, content, and host-policy checks. Each adapter performs target-specific validation. An unsupported effect stays local with a bounded diagnostic; target failures must not block or fail the local plugin operation.

This requires a host-owned effect/lifecycle boundary, not interception of raw SDK requests.

### 2. Explicit translations

| Accepted desktop effect | Android translation and limits |
| --- | --- |
| `pet.speak`, `ui.bubble`, text `ui.alert` | `pet.say`: validated nonempty, single-line `message` ≤140 characters; Android also filters code-, URL/path-, and secret-like text. Project supported markdown from sanitized content; never send HTML. Unsupported text stays local. An alert remains a bubble; forward its notification side effect separately, only if accepted. |
| Bubble actions | Up to four `{id,label,style}` buttons; labels ≤40 characters. Generate opaque wire IDs matching `[A-Za-z0-9_-]{1,64}` and map back to logical action IDs. Desktop action IDs may contain additional punctuation. Do not create arbitrary `callbackUrl`s. |
| Sticky/pinned bubbles | Map supported persistent lifetime to `pinned:true`, while retaining host revocation. Phone pinning is one-slot persistence, not desktop pinned-slot parity. Current phone actions always dismiss; exclude `dismissesBubble:false` from the legacy interaction subset. |
| `bubble.update` | Merge the validated patch into a full descriptor. Re-`pet.say` with the same wire ID is currently an announced replacement: it resets lifetime and rebuilds forms. Use it for synchronization only when a silent-update capability exists. |
| `bubble.dismiss` / revoked handle | `pet.bubbleDismiss({id})`, scoped to the owned wire ID. Never send an unscoped dismiss. |
| Bubble HUD | Desktop `{items}` → phone `hud` array, ≤4 items, with explicit `max:100`; bounded labels ≤24 characters, deriving a safe fallback where absent. Map supported glyphs or omit desktop icon references. HUD-only bubbles need a phone message. Defer mutable HUDs until silent synchronization exists. |
| Bubble raster image asset | Host resolves approved assets to bytes → `pet.showMedia({data})`; never forward filesystem paths or desktop-local URLs. Decoded image bytes ≤10 MiB, duration 1–30 seconds; GIF is currently static. SVG/icon rasterization and full image-bubble dismissal lifecycle are follow-ups. |
| `pet.react` | Pass through the current eleven validated reactions. Additional names require an explicit mapping/capability. Preserve announcement policy; Android can alert for some reactions. |
| Accepted OS notification | `notify({title,body})`: single-line title ≤80, body ≤200. Per-request sound and independent notification identities are not currently preserved. |
| `status.set` / `clear` | Aggregate into the phone's single foreground line using `status.publish`, text ≤80; SDK `info` → wire `default`. An explicit reset contract is needed; `status.clear` does not currently exist and phone rendering does not preserve tone. Defer from MVP. |
| Motion / scale / visibility | Phone methods exist, but these are not direct SDK translations. Resolve desktop movement relative to its work area/pet centre before producing normalized phone coordinates; specify target scale/visibility and drag semantics separately. Deferred. |
| Host metrics | `system.metrics` is a read API, not a display effect. Optional host publication uses the special WS `status.host` frame with explicit units and freshness. Memory percentage alone is not RAM MiB totals. Deferred. |

Scheduling, storage, network, AI, and other host services stay host-side; their accepted display effects may still mirror. Phone TTS triggered by a bubble is not equivalent to `voice.speak`.

### 3. Inbound ownership and lifecycle

Keep explicit ownership maps:

- `(target, connection generation, wire bubble ID)` → live host handle, plugin generation, descriptor revision, and allowed wire/logical action IDs.
- `(target, connection generation, command source, wire command ID)` → current registered host command.

Require exact matches and current enablement/permissions. Reject missing, unknown, stale, or disallowed IDs. Bubble `source` is a display label, not ownership. Command `source` participates only as a registered namespace.

Route accepted actions through the existing host callback path. Consume dismissing actions once before invoking the callback; apply the action's host dismissal policy everywhere. Desktop timeout, replacement, dismissal, plugin disable/reload, or target disable immediately revokes remote callbacks.

Mirror simple registered commands via WS `commands.register`/`unregister`; invoke them through the existing host command path and timeout rules. Namespace registrations across host/plugin instances, respect the phone's global 50-command limit, and leave form-backed commands local. Registrations survive WS disconnect, so explicitly reconcile/clean up bridge-owned namespaces, including after host restart.

The phone has one bubble slot: reuse host priority, retain the incumbent on equal priority, and do not let periodic updates reclaim the slot. Alerts may preempt ambient state. Independent clients/native phone features remain possible; leases are liveness checks, not exclusive ownership.

Do not treat `say/react/media/hud/status/dismiss` broadcasts as user callbacks or mirror them back. Current phone manual dismissal/timeout is not reported as a genuine UI lifecycle event; do not synthesize SDK `onDismiss` from an intent echo.

### 4. Silent updates and reconnection

State updates and restoration must not beep, vibrate, speak, or create notifications. The companion's native silent HUD path is internal; a generic remote silent upsert/reconcile capability is still needed.

For existing companions, provide an explicit announcement-only mode:

- no periodic HUD/update replay;
- withdraw an interactive view if its descriptor changes and cannot be synchronized safely;
- on reconnect, clean up stale bridge-owned views and restore current command registrations—not old announcements.

A capable target may silently restore only currently live eligible display state, preserving its remaining lifetime. Never replay notifications, taps, command invocations, transcripts, or approvals. WS reconnection does not recover missed events.

Honor optional 15-second lease renewal where required, but do not rely on lease expiry to clear UI or callbacks.

### 5. Bounded transport and security

- Bound per-target queues by count and bytes, concurrent/in-flight work, deadlines, and inbound rate. Coalesce state by object/property; keep dismissal barriers and reject stale work so updates cannot resurrect dismissed objects.
- Isolate slow/unreachable targets. Await interaction-channel readiness before showing buttons, and install ownership maps before an early tap can arrive.
- Keep media off the small control stream: Android WS accepts 1 MiB frames; inline media uses capped IPC with base64 overhead accounted for. The companion's WS write path is now bounded (drop-on-overflow), but the host must still bound its own queues end-to-end.
- Validate inbound schemas, lengths, ownership, generation, and current authorization. Authenticated target frames never become arbitrary SDK calls.
- Keep credentials outside plugin sandboxes; protect discovery/config storage and redact token-bearing URLs.
- Raw TCP and `ws://` expose bearer tokens and content. Private-IP filtering is not encryption or strong peer authentication. Require a trusted/protected network; no public exposure.
- Resolve assets under existing host file/network policy, including URL/redirect and decoded-image bounds.

## OpenCode / host-client interoperability

This feature belongs in the OpenPets host and must not depend on OpenCode. An OpenCode integration could separately expose a typed `openpets` RPC broker for tools and clients, backed by supported OpenPets host entry points.

That RPC runs on the OpenCode server; it is not the phone protocol or the desktop plugin SDK. Current desktop IPC must not be assumed to support rich bubble callbacks. Direct-to-phone integrations remain an alternative, not a second simultaneous fan-out path.

## MVP

One manually configured phone/default pet; supported immutable text bubbles/alerts, dismissing actions, simple registered commands, and compatible notifications. Existing companions use announcement-only mode. Mutable HUDs, silent active-bubble restoration, and richer lifecycle parity require advertised companion capabilities before enablement.

Forms remain deferred: current phone forms POST to `submitUrl` (and now also emit an experimental WS `submit` event), whereas desktop plugins register callbacks. Voice remains deferred: `input.text` carries an optional `captureId` but no recipient selection. Pointer/power events need target-origin-aware subscriptions and permissions; they must not impersonate desktop input or battery state.

## Acceptance criteria

- Local-only behavior is unchanged; unplugging or slowing the phone never stalls plugins.
- Colliding logical IDs are safely mapped; unknown, malformed, stale, and disallowed actions/commands never invoke callbacks.
- Desktop dismissal/timeout/replacement and plugin disable/reload invalidate remote controls; duplicate dismissing taps invoke once.
- Arbitration prevents background updates from overwriting an unanswered interaction.
- Reconnect/restart reconciles owned registrations and UI without replaying historical announcements or interactions.
- Update storms stay within documented queue/in-flight limits and never alert per tick; legacy targets suppress unsupported synchronization.
- Capable targets restore current state silently without resetting lifetime; stale queued work cannot resurrect dismissed UI.
- Media/frame limits, premature disconnects, hostile input rates, and credential redaction are tested.

Suggested labels: enhancement, plugins, integration
