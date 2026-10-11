# OpenPets Android — Capability Gap Analysis & Implementation Roadmap

> Scope: compare this repo (`openpets-android`) against the wider OpenPetsHQ
> ecosystem, identify missing capabilities, and plan their implementation.
> Per the brief, capabilities that **fundamentally require a PC-side OpenPets
> host** are excluded from the build plan; everything the device can do on its
> own (including direct cloud calls over the device's own internet) stays in
> scope.
>
> Method note: "host-owned on desktop" is **not** the same as "needs a PC".
> Android can own conversations, providers, microphone sessions, storage and
> capability execution itself. Only capabilities whose data genuinely lives on
> the PC (agent/terminal process data, desktop-side plugin execution, PC-local
> files) are excluded.

---

## 1. Baseline & method

- **Android baseline:** local repo at `app/`, `protocol/`, `tools/`, `docs/`
  (see §3 for the current surface).
- **Upstream baseline:** `OpenPetsHQ/openpets` @ `main`
  (checked revision `7db3d8fe7f008f7feedea577bbd8cec908c1c669`, 2026-10-10),
  plus the org plugin repos and `@open-pets/cli` / `@open-pets/mcp` 4.0.0.
- **Classification axes** (used in every gap row):
  - **Dependency:** `device-local` · `direct-cloud` · `PC-dependent` · `multi-device`
  - **Relationship:** `upstream-parity` (same wire/API surface) ·
    `native-equivalent` (Android reimplementation of a desktop behavior) ·
    `Android-extension` (no upstream counterpart) · `deferred` (eligible but
    not scheduled now)
- **Priority** is assigned separately; "large" or "not recommended" is not the
  same as "out of scope".

---

## 2. Headline findings

1. **Protocol method parity already exists.** Upstream IPC v1 exposes the same
   11 methods and Remote v1 the same 3 methods the app implements. There is no
   missing IPC verb. The gaps are **semantics, security, and robustness**, not
   new method names.
2. **The real missing product surface is the companion/plugin feature set**
   (reminders, timers, focus, virtual pet, routines, wellness, fortune/8-ball,
   greeting, deadlines, calendar, and Android device resources) and the **v4
   assistant stack** (text/voice conversation over a typed capability registry).
   None of these inherently require a PC.
3. **Security/robustness debt is material** and should precede new features:
   lease renewal/targeting, transport budgets, bind/private enforcement, Remote
   pairing and secure token storage, and **unsafe pet import** (unbounded
   download/extraction, non-atomic replace).
4. **Several "known limitations" in the repo README are bugs, not design**:
   `pet.bubbleDismiss` unreachable, lease heartbeat/release no-ops, static GIF,
   unrendered `hud.icon`, no IPC rate limit, Remote not enforcing private IPv4.
5. **Catalog/asset format drift:** the app consumes `codex-pets.net` and a fixed
   8×9 / 192×208 sprite grid; upstream uses its own catalog (v2/v3) and a **V2
   8×11 atlas with version markers and neutral/gaze poses**.

---

## 3. Current Android surface (verified)

**Transport.** IPC `openpets-ipc` v1 (TCP 18420, line-delimited JSON, one
req/resp per socket); Remote `openpets-remote` v1 (TCP 18421), 4 KiB cap,
30 req/min/peer, disabled-by-default in upstream but **enabled by default here**.

**IPC methods:** `hello`, `status`, `pets.list`, `pets.install`,
`pets.install-local`, `lease.acquire`, `lease.heartbeat`, `lease.release`,
`pet.react`, `pet.say`, `pet.showMedia`.
**Remote methods:** `status`, `pet.react`, `pet.say`.

**UI/behavior.** Canvas spritesheet pet (8×9, 192×208), 11 reactions, idle
wandering, draggable; rich bubbles (markdown subset, ≤4 HUD bars, ≤4 actions
url/callbackUrl, pinned, forms ≤4 inputs → submitUrl); media (base64 inline or
HTTP(S) URL, clickUrl, caption, duration ≤30 s, ≤10 MiB); per-source accent
colors; 5 built-in pets + zip/folder import + online catalog; sound/haptics/TTS/
notifications; settings (server toggles, bind/ports/tokens, require-lease,
effect toggles, per-reaction overrides).

**Known defects / limitations:** `pet.bubbleDismiss` dispatcher case not
whitelisted (returns `unknown_method`); lease heartbeat does not renew and
release does not delete; leases are independent (no single-owner, no targeting);
Remote does not enforce private-IPv4; GIF decoded static; `hud.icon` parsed but
not drawn; no explicit `PowerManager.WakeLock` (only keep-screen-on); no IPC
rate limiter.

**Build:** minSdk 24, compile/targetSdk 34, Kotlin 2.0.21, AGP 8.7.2, Gson only,
Java sockets + `HttpURLConnection`.

---

## 4. Upstream surface (verified, pinned)

**Protocol/transports:** IPC v1 (same 11 methods), Remote v1 (same 3), LAN mode
(experimental HTTP polling coordinator + edge-based pet handoff; deliberately
does **not** relay speech/media/tool text).

**Plugin platform:** SDK/manifest v1/v2/v3, host-side sandbox, permission-checked
bridge, typed `configSchema` settings forms, commands, status, panels, network,
secrets, voice, AI, timers. Plugins run on the **host** and render via bubbles/
HUD/alerts/commands/schedules; they cannot inject UI into pet windows.

**Assistant capabilities** (typed, JSON-schema bounded, plugin-registered):
`focus.*`, `reminders.*`, `launch.greet`, `virtual-pet.*`, `resources.get/show/hide`.

**Official plugin catalog** (`docs/official-plugins.md`, `catalog.v2.json`):
13 official + 5 community.

| Official plugin | Dependency |
|---|---|
| Quick Reminders, Simple Timer, Focus Buddy, Virtual Pet, Launch Buddy, Daily Fortune Cookie, Magic 8-Ball, Mood Check-in, Morning & Evening Routine, Water Reminder | device-local |
| Anxiety Aid Tools (guided sessions + downloadable audio) | device-local + direct-cloud (media host `r2.anxietyaidtools.com`) |
| Calendar Airmail (Google Calendar reminders) | direct-cloud / local `CalendarContract` |
| System Resources (CPU/RAM/GPU/Disk; optional battery/network) | host metrics → Android device metrics |
| Deadline Buddy (manual deadlines) | device-local (GCal *not* included; Outlook out of scope) |

**Community:** Vocabulary Drag & Drop (device-local), Walkabout (pet roaming,
device-local), Usage Buddy (**PC-dependent**: reads a PC-local usage monitor).

**v4 assistant stack:** text composer, bounded conversation/tool execution,
capability discovery/routing, independently selected text/STT/TTS providers,
cancellation, authoritative action results, visible turn states, bounded
recent-history context, owner deletion controls; optional OpenAI Realtime.
Upstream assigns these responsibilities to its host, but they are **not
inherently PC-bound** — a mobile app can own them with direct cloud calls.

**MCP/CLI:** `@open-pets/mcp` exposes status/react/say; `@open-pets/cli` does
configure/doctor/status/pets/react/say/mcp/plugin scaffolding. Both are
host-side agent tooling.

---

## 5. Gap matrix

Legend — Dependency: **L**=device-local, **C**=direct-cloud, **P**=PC-dependent,
**M**=multi-device. Relationship: **PAR**=upstream-parity, **EQ**=native-
equivalent, **EXT**=Android-extension, **DEF**=deferred.

### 5.1 Protocol, transport & security

| # | Capability | Android status | Dep | Rel | Priority |
|---|---|---|---|---|---|
| P1 | IPC v1 method set (11) | Full parity | L | PAR | — |
| P2 | Remote v1 method set (3) | Full parity | L | PAR | — |
| P3 | Lease **renewal** on heartbeat + **delete** on release | Broken (no-op) | L | PAR | **High (defect)** |
| P4 | Lease idempotent re-acquire, session identity, requested-pet targeting | Validated then discarded | L | PAR | **High** |
| P5 | Concurrent agent pets / multi-pet pools & display | Single stage only | L | PAR | Med (large) |
| P6 | Remote **pairing/auth lifecycle**: named clients, per-client scopes, rotation/revocation, verifier-only token storage, explicit enablement | Single shared token; enabled by default | L | PAR | **High (security)** |
| P7 | Private-only bind enforcement (both servers) | Not enforced | L | PAR | **High** |
| P8 | Transport budgets: absolute connection deadline, byte/queue caps, pre-auth memory bounds; IPC rate limit separate from heartbeat | Inactivity timeout only; 16 MiB pre-auth; no IPC limit | L | PAR + EXT | **High** |
| P9 | `pet.showMedia` lease gating | Bypasses require-lease | L | PAR | Med |
| P10 | `pet.bubbleDismiss` | Half-implemented, unreachable | L | EXT | Low (cheap) |
| P11 | LAN mode (coordinator + handoff) | Absent | M | DEF | Deferred |
| P12 | Android-coordinated LAN variant | Absent | M | DEF | Deferred (large) |

### 5.2 Pets, catalog & assets

| # | Capability | Android status | Dep | Rel | Priority |
|---|---|---|---|---|---|
| A1 | Official OpenPets catalog + package identities | Uses `codex-pets.net` | L | PAR | Med |
| A2 | V2 8×11 atlas, version markers, neutral/gaze poses | Fixed 8×9, no metadata | L | PAR | **High** |
| A3 | Safe import: bounded download/extraction, file-count & decoded-image bounds, collision detection, atomic replace | Unbounded + non-atomic | L | PAR | **High (safety)** |
| A4 | GIF animation | Static bitmap | L | PAR | Med |
| A5 | `hud.icon` rendering | Parsed, not drawn | L | PAR | Low (cheap) |
| A6 | Independent HUD sizing / horizontal flip | Absent | L | PAR | Low |

### 5.3 Companion features (native equivalents of official plugins)

| # | Capability | Android status | Dep | Rel | Priority |
|---|---|---|---|---|---|
| C1 | Quick Reminders (create/snooze/done/status + notifications) | Absent | L | EQ | **High** |
| C2 | Simple Timer (countdown, HUD, pause, expiry alert) | Absent | L | EQ | **High** |
| C3 | Focus Buddy (sessions, pause/resume, skip break) | Absent | L | EQ | High |
| C4 | Virtual Pet (Tamagotchi state + actions + HUD) | Absent | L | EQ | Med |
| C5 | Launch Buddy (on-demand greeting) | Absent | L | EQ | Low (cheap) |
| C6 | Daily Fortune Cookie | Absent | L | EQ | Low (cheap) |
| C7 | Magic 8-Ball (command Q&A) | Absent | L | EQ | Low (cheap) |
| C8 | Water Reminder (cadence reminders) | Absent | L | EQ | Med |
| C9 | Morning & Evening Routine | Absent | L | EQ | Med |
| C10 | Mood Check-in | Absent | L | EQ | Low |
| C11 | Anxiety Aid Tools (guided sessions + audio download) | Absent | L+C | EQ | Med |
| C12 | Deadline Buddy (manual deadlines, TZ/DST-safe, restart recovery) | Absent | L | EQ | Med |
| C13 | Calendar Airmail (Google Calendar reminders) | Absent | C | EQ | Med (OAuth effort) |
| C14 | System Resources (device battery/mem/storage/net; CPU/GPU restricted → "unavailable") | Absent | L | EQ | Med |
| C15 | Walkabout (roaming/patrol) | Absent | L | EQ | Med |
| C16 | Vocabulary Drag & Drop | Absent | L | EQ | Low |
| C17 | Usage Buddy | Absent | P | — | **Excluded** |

### 5.4 Presentation foundation

| # | Capability | Android status | Dep | Rel | Priority |
|---|---|---|---|---|---|
| F1 | Durable authoritative state + display arbitration (owner/priority/pin/replace/cancel) across multiple producers | Ephemeral `PetBus`, single bubble/media | L | EQ | **High (foundation)** |
| F2 | Native local commands (invoke domain ops, not loopback POSTs) | Absent | L | EQ | **High (foundation)** |
| F3 | Pet movement/physics polish (gravity/bounce within stage) | Basic drag + idle wander | L | EQ | Low/Med |

### 5.5 Assistant & voice (v4 stack)

| # | Capability | Android status | Dep | Rel | Priority |
|---|---|---|---|---|---|
| V1 | Text composer + bounded conversation/tool loop | Absent | L+C | EQ | High (large) |
| V2 | Typed capability registry / discovery over native domain ops | Absent | L | EQ | High (large) |
| V3 | Provider adapters, profiles & credential storage (protected) | Absent | L+C | EQ | High (large) |
| V4 | Personality (tone/style; no permission/outcome impact) | Absent | L | EQ | Med |
| V5 | Terminal-text history + retention/deletion controls | Absent | L | EQ | Med |
| V6 | On-device STT (push-to-talk; API 31+ on-device, text fallback, network disclosure) | Absent | L (+C) | EQ | Med (large) |
| V7 | Optional Realtime/streaming voice | Absent | C | DEF | Deferred (large) |
| V8 | Teams cloud enrollment / Manager Check-ins | Absent | C | DEF | Deferred (validate) |

### 5.6 Hardening & ops

| # | Capability | Android status | Dep | Rel | Priority |
|---|---|---|---|---|---|
| H1 | Reboot/process-death/timezone alarm reconciliation for timers | Absent | L | PAR | High (with C1/C2) |
| H2 | Protected credential storage + `allowBackup` exclusion + redacted logs | Unaddressed | L | PAR | High |
| H3 | Bounded/redacted diagnostics | Absent | L | EXT | Med |
| H4 | Update notification | Absent | C | EXT | Low |
| H5 | Supported-lifecycle validation for screen-off listening (Doze/FGS `dataSync` limits) | Undefined | L | PAR | Med |

---

## 6. Explicitly excluded (PC-dependent)

These require the PC-side OpenPets host or its local data, so they are out of
scope for an Android-only app:

- **Usage Buddy** — reads a live Claude/Codex **PC-local usage-monitor endpoint**.
  (An Android usage dashboard or future direct vendor API would be a *different*
  capability, not parity.)
- **Desktop plugin host runtime as-is** — host-side JS sandbox executing plugins
  against PC machine data. Android ships **compiled-in native equivalents**
  instead (§5.3). A JS SDK *compatibility layer* is not PC-dependent in
  principle but is a large, low-value expansion (Phase 7).
- **Desktop-side agent tooling** — running/configuring the desktop agent, and
  the desktop MCP/CLI as *process controllers*.
- **Desktop window management** — floating/always-on-top desktop windows, etc.
- **Host-mediated conversation as a prerequisite** — Android owns its own
  conversation loop and calls providers directly (Phase 5–6); it does not need
  the desktop to relay text/audio.

Not excluded, but **deferred with a gated decision** (not "impossible"):
multi-pet pools, Android MCP endpoint, JS SDK compatibility, Teams, and
Android-coordinated LAN.

---

## 7. Implementation roadmap

Phases are dependency-ordered. Each phase = one coherent delivery boundary with
an `@oracle` gate where noted. **Recommended first release boundary: Phase 3.**

### Phase 0 — Contract baseline *(small–medium)*
- Pin upstream revision; record the classification (parity / equivalent /
  extension) in-repo.
- Resolve discrepancies: Deadline Buddy (GCal not included; Outlook out of
  scope) and catalog/asset format.
- Decide single-stage vs multi-pet policy and the supported Android lifecycle.
- Stand up **real upstream-client contract tests** (not just method-name checks).
- **Owners:** @explorer + @librarian · **Gate:** @oracle.

### Phase 1 — Existing companion correctness *(medium)*
- P3/P4 lease renewal, release, idempotency, session identity, requested-pet
  targeting (keep upstream concurrent-lease semantics; deprecate "single-owner"
  wording).
- P7/P8 bind policy, private enforcement, absolute connection deadline,
  byte/queue budgets, pre-auth memory caps, separate heartbeat vs expensive-work
  budgets. P9 media lease gating. P10 `pet.bubbleDismiss` decision.
- P6 Remote security: disable-by-default, pairing/auth lifecycle, verifier-only
  token storage. H2 `allowBackup`/credential/log redaction.
- A3 safe import (bounded download/extract, decoded-image bounds, collision
  detection, atomic replace).
- **Owner:** @fixer · **Gate:** @oracle (protocol/security).

### Phase 2 — Presentation foundation *(medium)*
- F1 authoritative state + display arbitration (source ownership, pin vs
  transient, priority, replacement, cancellation).
- F2 native local commands backed by domain ops (not loopback POSTs).
- A5 `hud.icon`, A4 bounded GIF (with API 24–27 strategy), A6 HUD sizing/flip.
- A1/A2 official catalog + V2 8×11 atlas / neutral-gaze metadata.
- **Owners:** @designer (surfaces) + @fixer (wiring) · **Gate:** @oracle.

### Phase 3 — Offline companion release *(medium–large)* — **first release boundary**
- C1 Quick Reminders + C2 Simple Timer first, incl. persistence, scheduling,
  notifications, actions, and H1 reboot/TZ/death reconciliation.
- **C3 Focus Buddy** next (sessions, pause/resume, skip-break), built on the same
  scheduling/reconciliation foundation as C1/C2 — focus is *not* delivered by
  Simple Timer. Its domain operations must exist before Phase 5 exposes
  `focus.*` through the assistant.
- Then C12 manual deadlines, C8 Water Reminder / C9 routine presets, C5 greeting,
  C4 Virtual Pet.
- **Owners:** @fixer (domain) + @designer (controls) · **Gate:** @oracle
  (scheduling/lifecycle).

### Phase 4 — Direct integrations *(medium–large)*
- C14 device System Resources with truthful `unavailable` fallback
  (no fabricated CPU/GPU).
- C13 calendar: local `CalendarContract` read/reminders first; direct Google
  OAuth where necessary (separate Android OAuth client, consent, refresh/revoke,
  recurrence/timezone). No calendar-write assistant promises without a separate
  safety/retry design.
- C11 Anxiety Aid Tools (guided sessions + audio cache), C15 Walkabout,
  C6/C7/C10 light behaviors.
- **Owners:** @librarian + @fixer · @designer for auth/status UX.

### Phase 5 — Text assistant *(large)*
- V2 small typed capability registry over proven domain operations; V1 text
  composer + bounded tool loop + authoritative results + visible turn states;
  V3 one provider adapter + protected credentials; V4 personality; V5
  terminal-text history + deletion controls.
- **Owners:** @oracle (architecture) + @fixer + @designer (conversation UX).

### Phase 6 — Voice *(large; Realtime separately)*
- V6 push-to-talk, availability-aware STT, shared assistant execution, TTS,
  cancellation, visible states; then network speech providers; V7 Realtime last.
- **Owners:** @librarian + @fixer + @designer · **Gate:** @oracle (lifecycle).

### Phase 7 — Separately gated expansions *(large–very large)*
- P5 multi-pet/pools, JS SDK compatibility, Android MCP endpoint, V8 Teams,
  P12 Android-coordinated LAN. Each needs its own value/feasibility decision
  before work starts. **Owner:** @oracle/@librarian feasibility first.

**Cheap wins (fold into nearest phase):** HUD icons (A5), clearer
diagnostics/docs (H3), greeting (C5), fortune/8-ball (C6/C7), plus lease
renewal/release (P3) once the contract is settled.

---

## 8. Risks & YAGNI guardrails

1. **Don't impose a global exclusive lease and call it compatibility.** Keep
   upstream concurrent-lease semantics; solve display contention with a
   separate presentation arbitration layer (F1).
2. **Don't persist every inbound event as "conversation history."** Inbound
   agent content must not silently become cloud prompt context (V5).
3. **Don't build a plugin platform to ship two timers.** Start with compiled-in
   capabilities and a small typed registry; add extensibility only when real
   consumers justify it.
4. **Don't build a generic panel schema, pathfinding engine, or universal
   confirmation framework without consumers.**
5. **Don't chase continuous CPU polling, indefinite wakelocks, or ambient
   microphone capture on repurposed old phones.** Keep-screen-on already covers
   the visible kiosk case; add real WakeLock only with a power-policy decision.
6. **Treat cloud credentials/history as sensitive**, not ordinary settings:
   protected storage, backup exclusion, redacted logs, explicit retention/
   deletion. Fix `allowBackup="true"`.
7. **Method-name parity ≠ wire compatibility.** Upstream uses PC-local absolute
   paths for `pets.install-local`/`pet.showMedia`; Android uses URL/base64.
   Document these as deliberate Android extensions.

---

## 9. Source references

- Android repo: `app/`, `protocol/`, `docs/`, `README.md` (local).
- Upstream protocol: `docs/ipc.md`, `docs/lan-mode.md`, `docs/plugins.md`,
  `docs/sdk.md`, `docs/desktop.md`, `docs/openpets_v4.md`,
  `docs/mcp.mdx`, `docs/cli.mdx`, `docs/official-plugins.md`,
  `web/public/plugins/catalog.v2.json`.
- Plugin repos: `Openpets-system-resources`, `openpets-plugin-usage-buddy`,
  `openpets-simple-timer`, `openpets-plugin-deadline-buddy`.
- Packages: `@open-pets/cli` 4.0.0, `@open-pets/mcp` 4.0.0.
