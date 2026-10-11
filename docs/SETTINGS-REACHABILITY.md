# Settings reachability checklist (contract freeze)

Phase 1 artifact for the settings-UI refactor (`SETTINGS-UI-REFACTOR.md`). Every control
and action currently reachable from `MainActivity.showSettings()` plus the per-feature
dialogs must remain reachable after the refactor. Use this as the acceptance list for
Phases 2–6.

## Apply semantics — frozen

Per the user's decision, **preserve current behaviour**:

- **Staged** (today: committed by the single Settings *Save*): `ipcEnabled`,
  `remoteEnabled`, `wsEnabled`, `hostStatusEnabled`, `autoStartOnBoot`, `soundEnabled`,
  `hapticsEnabled`, `ttsEnabled`, `notifyEnabled`, `requireLease`, `bindAddress`,
  `ipcPort`, `remotePort`, `wsPort`, `ipcToken`, `remoteToken`.
- **Immediate** (today: applied on selection): `customSoundUri` (picker / "Default sound"),
  `selectedPetId` (pet picker).
- Per-feature dialogs keep their own existing save semantics.

**Interpretation recorded:** the refactor keeps staged controls *staged* (nothing takes
effect until an explicit Save). To keep grouping coherent, staged controls live in
**transactional sections** (Connections & security; Appearance & output) with their own
Save/Discard, rather than mixed among immediate rows. `Start on boot` and `Show host
status` — both staged today — therefore sit in a transactional section rather than under
immediate Pet rows. Exact grouping is finalised in Phase 3 (Oracle gate).

## Reachability table

| # | Current control (where) | Destination after refactor | Apply |
|---|---|---|---|
| 1 | `Current pet: <name>` label | Pet › (info row) | — |
| 2 | `Change pet…` | Pet › Pets screen (selection, catalog, zip/folder import, delete) | immediate |
| 3 | `Animations…` | Pet › Animations screen | Save/Discard |
| 4 | `Walkabout…` | Pet › Walkabout screen (mode radios + speed) | Save/Discard |
| 5 | `Show host status` (toggle) | Appearance & output (transactional) | staged |
| 6 | `Timer…` | Companion features › Productivity › Timer | feature semantics |
| 7 | `Reminders…` | Companion features › Productivity › Reminders | feature |
| 8 | `Deadlines…` | Companion features › Productivity › Deadlines | feature |
| 9 | `Focus…` | Companion features › Productivity › Focus | feature |
| 10 | `Calendar…` | Companion features › Productivity › Calendar | feature |
| 11 | `Checklist…` | Companion features › Productivity › Launch checklist | feature |
| 12 | `Water Reminder…` | Companion features › Wellbeing › Water reminder | feature (Save/Discard config) |
| 13 | `Mood…` | Companion features › Wellbeing › Mood | feature |
| 14 | `Routine…` | Companion features › Wellbeing › Routine | feature (Save/Discard config) |
| 15 | `Breathe…` | Companion features › Wellbeing › Breathe | feature |
| 16 | `Virtual Pet…` | Companion features › Play & learning › Virtual pet | feature |
| 17 | `Fun…` | Companion features › Play & learning › Fun | feature |
| 18 | `Vocabulary…` | Companion features › Play & learning › Vocabulary | Save/Discard |
| 19 | `Voice input…` | Companion features › Play & learning › Voice input | feature |
| 20 | `Plugin commands…` | Advanced & diagnostics › Plugin commands | feature |
| 21 | `System resources…` (`Resources…`) | Advanced & diagnostics › System resources | feature |
| 22 | `Start on boot` (toggle) | Advanced & diagnostics (transactional startup group) | staged |
| 23 | `Device IP: <ip>` label | Connections & security › info row (copy / refresh) | — |
| 24 | `IPC enabled` (toggle) | Connections & security › Servers | staged |
| 25 | `Remote enabled` (toggle) | Connections & security › Servers | staged |
| 26 | `WebSocket enabled` (toggle) | Connections & security › Servers | staged |
| 27 | `WebSocket port` field | Connections & security › Servers | staged |
| 28 | `Bind address` field | Connections & security | staged |
| 29 | `IPC port` field | Connections & security › Servers | staged |
| 30 | `Remote port` field | Connections & security › Servers | staged |
| 31 | `IPC token` + Copy | Connections & security › Security (masked, copy) | staged |
| 32 | `Remote token` + Copy | Connections & security › Security (masked, copy) | staged |
| 33 | `Require lease` (toggle) | Connections & security › Security | staged |
| 34 | `Notification sound` (toggle) | Appearance & output | staged |
| 35 | `Test sound` (action) | Appearance & output / Sound screen | immediate action |
| 36 | `Vibration` (`Vibrar junto`) | Appearance & output | staged |
| 37 | `Speak bubble text (voice)` (TTS) | Appearance & output | staged |
| 38 | `System notifications` (toggle) | Appearance & output | staged |
| 39 | `Custom sound…` (picker) | Appearance & output › Sound file | immediate |
| 40 | `Default sound` (action) | Appearance & output › Sound file | immediate |
| 41 | (new) `Reset preferences…` | Advanced & diagnostics | prefs only, keep tokens |
| 42 | (new) `About OpenPets` | About | — |

Feature dialogs to migrate as feature screens (all 17 launchers above): Animations,
Pet picker, Timer, Reminders, Deadline, Focus, Virtual pet, Water, Fun, Mood, Routine,
Breathe, Resources, Calendar, Checklist, Walkabout, Vocabulary (settings + lookup),
Commands, Voice input.

## Action-level feature appendix

Each listed destination is within the feature screen named in the reachability table,
unless stated otherwise. “Immediate” means an action/configuration takes effect when
selected; “feature command” preserves the existing controller/action semantics.

| Feature | Existing action/control | Destination | Apply semantics / notes |
|---|---|---|---|
| Timer | 5/10/25/60-minute presets; custom duration + optional label; Start | Companion › Productivity › Timer | Starts immediately; presets and custom share the start command |
| Timer | Pause / Resume; +5 minutes; Cancel; Dismiss finished timer | Timer status screen | Immediate controller commands |
| Timer | Close/dismiss dialog | Timer | No-op on running session; session remains active |
| Reminders | Title + delay; Add | Companion › Productivity › Reminders | Add immediately; validates nonblank title/delay and 120-char title limit |
| Reminders | Done; Snooze; Delete | Reminders list | Immediate item commands |
| Reminders | Close dialog | Reminders | No-op |
| Deadlines | Title; date picker; time picker; Add | Companion › Productivity › Deadlines | Date/time are draft fields; Add commits immediately |
| Deadlines | Complete; Delete | Deadlines list | Immediate item commands |
| Deadlines | Close dialog | Deadlines | No-op |
| Focus | 25/5 and 50/10 presets; custom focus/break duration; Start | Companion › Productivity › Focus | Starts immediately |
| Focus | Pause; Resume; Skip break/phase; End (cancel) | Focus status screen | Immediate controller commands; skip acts on current phase |
| Focus | Close dialog | Focus | No-op on session |
| Virtual pet | Feed; Play; Pet/cuddle; Nap/rest | Companion › Play & learning › Virtual pet | Immediate; each action also requests Show on pet |
| Virtual pet | Initial Show on pet | Virtual pet screen / pet stage | Automatic when the screen opens (not a separate user action) |
| Water | Enabled; interval; from hour; to hour; Save | Companion › Wellbeing › Water reminder | Draft until Save; controller update applies all configuration together |
| Water | Close dialog | Water reminder | Discard unsubmitted draft |
| Fun | Greeting; Fortune | Companion › Play & learning › Fun | Immediate pet speech; dialog closes |
| Fun | 8-ball; enter question; submit answer | Fun › 8-ball question dialog | Question is collected but currently unused; answer speech is immediate |
| Mood | Note + one of five ratings | Companion › Wellbeing › Mood | Immediate record; note accompanies rating and action requests Show on pet |
| Mood | History / recent entries | Mood screen | Read-only; currently shown inline, not a separate action |
| Mood | Nudge; initial Show on pet | Mood screen / pet stage | Nudge is immediate; initial screen open also requests Show on pet |
| Routine | Morning/evening enabled; each hour/minute; Save | Companion › Wellbeing › Routine | Draft until Save; controller update applies both slots together |
| Routine | Close dialog | Routine | Discard unsubmitted draft |
| Breathe | Duration; Box / 4-7-8 start; Stop | Companion › Wellbeing › Breathe | Duration is draft; start/stop commands are immediate |
| Resources | Refresh; Show on pet | Advanced › System resources | Immediate actions |
| Calendar | Request calendar permission | Companion › Productivity › Calendar / OS permission prompt | OS-mediated; denial leaves feature unavailable and retryable |
| Calendar | Refresh; Show on pet | Calendar screen | Immediate actions |
| Calendar | Reminder toggle | Calendar screen | **Immediate** (`MainActivity.kt:922`); do not stage it with another editor |
| Checklist | Item text; Add | Companion › Productivity › Launch checklist | Add immediately; existing 80-character limit |
| Checklist | Done / Undo; Remove; Clear completed | Checklist list | Immediate controller commands |
| Checklist | Launch | Checklist screen | Immediate launch; also persists greeting field at launch |
| Checklist | Greeting edit / dialog dismissal | Checklist screen | **Currently persists on Launch and on dialog dismissal** (`MainActivity.kt:676,693`); preserve this behavior or explicitly document a deviation before changing it |
| Walkabout | Mode (off/roam/patrol/follow); speed; Save | Pet › Walkabout | Draft until Save; speed 0.5–3.0 |
| Walkabout | Close dialog | Walkabout | Discard draft |
| Vocabulary settings | Target language; Anki host; Save | Companion › Play & learning › Vocabulary settings | Save commits immediately on dialog Save; no separate preview |
| Vocabulary lookup | Define; Translate; Show on pet; **Add to Anki** | Vocabulary lookup screen | Network lookup actions immediate; Show on pet explicit; **Add to Anki** (`MainActivity.kt:767-770,779`) is an immediate network action and is disabled/absent when the Anki host is blank |
| Plugin commands | Command list; select a command to invoke it | Advanced › Plugin commands | Selecting a command **executes** it via `PetBus.invokeCommand(...)` (`MainActivity.kt:789`) — preserve this action; it is not a read-only list |
| Voice | Request microphone permission; Listen / Stop | Companion › Play & learning › Voice input / OS permission prompt | Permission OS-mediated; capture starts/stops immediately; dialog dismissal cancels |
| Animations | Per-reaction picker (Default or sprite state); waiting duration; Save/Cancel | Pet › Animations | Draft until Save; waiting duration is clamped by legacy UI (200–10,000 ms) |
| Pets | Select built-in/imported pet | Pet › Pets | Immediate selection |
| Pets | Online catalog search; previous/next pagination; select/download | Pet › Pets › Online catalog | Search/page immediate; selection downloads and imports immediately |
| Pets | Import ZIP; import folder | Pet › Pets | System picker then immediate import/selection |
| Pets | Delete imported pet | Pet › Pets | Immediate deletion; selecting fallback Tux if current pet deleted |

The catalog has search and pagination controls; there is no separate catalog action beyond
selecting a result to download/import. The 8-ball question is currently not passed into the
answer generator. Plugin commands are **executable** from Settings (the list buttons invoke
`PetBus.invokeCommand`), so the migrated screen must keep that action.

## Invariants (must hold at every phase)
- All rows above remain reachable; no dead ends.
- Staged controls never apply before Save; immediate controls never become staged.
- Tokens are never rotated or dropped (including by reset).
- Existing installations keep preferences, tokens, records and active sessions.
- Settings never stops the foreground service, companion sessions, timers or alarms.
