# WebSocket-enabled features (plan)

The experimental `openpets-ws` endpoint (opt-in, disabled by default, private
peers + IPC token) is bidirectional, which unlocks features that were previously
out of scope for a phone-only app. This document plans three of them.

> Status: **planning**. None of these are built yet. WS is experimental, so the
> message schema below is a proposal and may change.

## Transport recap

- `ws://<device>:18423/?token=<IPC_TOKEN>`, opt-in in Settings, private peers only.
- Server → client: `hello`, `status`, and `event` frames (`react`, `say`,
  `media`, `dismiss`, `hud`, `action`).
- Client → server: `{ "id", "method", "params" }` dispatched through the shared
  IPC dispatcher (`react`/`say`/`showMedia`/…), returning `{ "id", "ok", … }`.
- New WS-native frames (below) are handled by the bridge, not the IPC dispatcher.

---

## A. Host → device status (PC metrics on the phone)

**Goal:** show the *host* (PC) CPU / RAM / network / disk beside the pet, the
mirror image of the on-device **System Resources** feature.

- **Flow:** a small host-side sender (PC) samples metrics and pushes
  `{ "type": "status.host", "params": { "cpu": 42, "ramUsedMb": …, "ramTotalMb": …,
  "netRxKbps": …, "diskUsedPct": …, "ts": … } }`.
- **App:** a new "Host status" feature (source `host`, bubbleId `host-status`)
  renders it via the existing silent feature-HUD path (`PetBus.hud`), with an
  opt-in toggle and a staleness timeout (drop/blank if no frame for N seconds).
- **Sender:** build a tiny host helper in this repo
  (`integrations/host-status/`) — Node (built-in `WebSocket`) or shell — with
  platform samplers (Linux/macOS/Windows best-effort; return `null` when a
  metric is unavailable).
- **Cost/risk:** low (read-only, display only). Recommended **first**.

Open questions: which metrics; sample cadence (e.g. 5 s); who owns the sender;
whether to also accept these metrics over the IPC path.

## B. Android notifications → host (pipe notifications out)

**Goal:** forward the phone's notifications to connected WS clients (a PC
dashboard, a logger, or the agent).

- **Requires** an Android `NotificationListenerService` — a **special access**
  permission the user grants in system settings (not a normal runtime
  permission). Must be strictly opt-in and clearly disclosed.
- **App:** the listener forwards `{ "type": "event", "event": "notification",
  "package": …, "app": …, "title": …, "text": …, "postedAt": … }` to clients.
- **Privacy:** default to a package allow/deny list, cap text length, and offer
  a "no content, metadata only" mode. Never forward OTP/secret-looking text by
  default.
- **Cost/risk:** higher (sensitive permission, privacy). Recommended **last**.

Open questions: direction (phone→host only, or also host→phone); which apps;
content vs metadata; retention.

## C. STT — voice input to the agent

**Goal:** capture speech on the device, transcribe it, and send the text over WS
to the host so the coding agent can use it as input (voice control).

- **Requires** `RECORD_AUDIO`; use the Android `SpeechRecognizer`
  (on-device when available on API 31+, else network) with a text fallback and a
  clear "may use network" disclosure. Push-to-talk, not always-listening.
- **App:** a push-to-talk affordance (e.g. a button in Settings + an optional
  long-press gesture) → recognized text → WS
  `{ "type": "command", "method": "input.text", "params": { "text": … } }`.
- **Host:** the OpenCode plugin receives `input.text` and injects it as a
  session message (`ctx.session.prompt`) so the agent sees it as user input.
- **Cost/risk:** medium. High user value (talk to the agent from the phone).

Open questions: on-device vs cloud STT (privacy/quota); push-to-talk vs a
wake-word (avoid always-listening); whether the pet should echo the transcript.

---

## Cross-cutting

- **Protocol:** keep `openpets-ws` versioned (`v1`); new frame types are additive.
  Document in `docs/AGENT-INTEGRATION.md`.
- **Security:** private peers + IPC token already enforced; add per-feature
  opt-in toggles; never put secrets in frames; redact notification content.
- **Permissions added over time:** `RECORD_AUDIO` (C), notification-listener
  special access (B). Keep them off by default and disclose at enable time.
- **Reuse:** the silent feature-HUD path (`PetBus.hud`) already supports multiple
  sources, so A/C can render without clobbering agent bubbles.

## Suggested order

1. **A — host status** (read-only, low risk; also validates the new WS-native
   frame path end to end).
2. **C — STT voice input** (high value; introduces `input.text` + host injection).
3. **B — notification piping** (sensitive permission; do carefully).

## Explicitly out of scope

Anything that requires the desktop OpenPets host as a *prerequisite* (e.g. the
desktop plugin runtime, PC-local agent process control). These WS features are
phone-owned or phone↔generic-WS-client and do not need the desktop app.
