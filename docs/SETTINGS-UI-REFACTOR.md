# Settings / configuration UI refactor — plan

Status: **planned** (2026-10). Produced with the Oracle from two research passes:
Android dense-configuration best practice, and an inventory of the current settings
surface. Supersedes the sketch in `CAPABILITY-ROADMAP.md` §8. No code changed yet.

## 1. Architecture decision

**Use a custom RecyclerView-based Settings overview and configuration screens, hosted by fragments inside the existing `MainActivity`.** Feature workflows use the same screen scaffold, with ordinary Views or RecyclerView as appropriate.

Do **not** introduce `PreferenceFragmentCompat`, Compose, another Activity, or a generic configuration-schema renderer.

### Why this fits the actual code

- `MainActivity.kt:798–902` mixes navigation, configuration, credentials and diagnostics in one dialog. Categorization alone will not fix its transactional ambiguity.
- Controllers already expose usable APIs: `snapshot/settings`, `refresh`, `addListener/removeListener`, and commands. For example, `TimerController.kt:10–18,130–131` and `WaterController.kt:10–17,39–52` delegate to application-scoped instances. **Keep these controllers and stores.**
- Dialog-specific view references and lifecycle bookkeeping occupy `MainActivity.kt:115–202,270–330`. Move those responsibilities into feature fragments, not into rewritten controllers.
- Material Components and View Binding already exist in `app/build.gradle.kts:45–57`; the theme already inherits Material 3 DayNight. Add an explicit RecyclerView dependency compatible with compileSdk 34, such as **1.3.2**, and explicit compatible Fragment/ViewModel dependencies where needed.
- Bespoke credential rows, validated forms, live feature status and system-derived values make Preference customization more work than a small custom adapter.

### Proposed organization

```text
settings/
  AppSettings.kt                 Existing persistence API; retain keys/defaults
  SettingsValidators.kt          Pure validation functions
  SettingsActions.kt             Writes and existing side effects
  SettingsResetter.kt            Explicit, narrowly scoped resets
  ui/
    SettingsOverviewFragment.kt
    SettingsAdapter.kt
    SettingsRow.kt
    SettingsSearchIndex.kt
    ConnectionsFragment.kt
    ConnectionDraftViewModel.kt
    ...small configuration fragments...

companion/ui/
  FeatureScreenFragment.kt        Shared scaffold/lifecycle helpers
  ControllerBinding.kt           Adapts existing listener APIs
  ...feature fragments...
```

Use straightforward classes and explicit route IDs. Do not build a settings DSL, dependency-injection framework or replacement persistence layer.

---

## 2. Navigation and target information architecture

### Navigation model

Change `MainActivity`'s content from the stage alone into a root container containing:

1. The existing `PetStageView`.
2. A full-size settings fragment container above it.

Long-press opens the overview. Settings is an **opaque utility surface**, not a speech bubble or translucent collection of dialogs.

- Keep the stage attached and keep `MainActivity` registered with `PetBus`.
- Keep `AppState.foreground` true while Settings is visible.
- Opening Settings must not stop the foreground service, companion sessions or alarms.
- Disable touch and accessibility access to the covered stage.
- Use ordinary FragmentManager navigation and its back stack; no Navigation Component is necessary.
- Root Back returns to the pet. Child Back returns to the overview or previous workflow screen.
- Preserve overview scroll position and search query when returning.
- Dirty transactional forms intercept Back/Up with **"Discard changes?"**, offering **Keep editing / Discard**.
- Closing an editor dialog cancels that editor only; it must not close Settings.
- Settings shows system bars and handles keyboard/window insets. Restore immersive mode when returning to the stage. The unconditional immersive code at `MainActivity.kt:389–399` must become conditional.
- Retain the current manifest configuration-change behavior initially; do not combine this refactor with an Activity lifecycle policy change.

### Exact overview order

The overview contains compact rows under these six headings:

| Section | Overview rows | Destination/behavior |
|---|---|---|
| **Pet** | **Current pet**, summary containing the selected pet's name | **Pets screen:** selection, online catalog, ZIP import, folder import and imported-pet deletion |
| | **Animations** | Configuration screen: reaction mappings and waiting duration |
| | **Walkabout** | Configuration screen: mode and speed |
| | **Show host status** | Immediate switch; helper: "Requires a WebSocket host-status sender." Keep configurable even when WebSocket is off |
| **Appearance & sound** | **Notification sound** | Switch-with-detail: switch applies immediately; detail opens Sound screen |
| | **Vibration** | Immediate switch, independent of sound |
| | **Read bubble text aloud** | Immediate TTS switch |
| | **System notifications** | Immediate switch; summary explains that this controls background **agent-event** notifications and reports OS permission restrictions |
| **Companion features** | **Productivity:** Timer, Focus, Reminders, Deadlines, Calendar, Launch checklist | Each opens its existing workflow, migrated to a feature screen |
| | **Wellbeing:** Water reminder, Mood, Routine, Breathe | Each opens a feature screen |
| | **Play & learning:** Virtual pet, Fun, Vocabulary, Voice input | Each opens a feature screen |
| **Connections & security** | **Server settings** | One Connections screen, described below |
| | **Device IP** | Read-only best-effort LAN IPv4, with Copy/Refresh actions; "Unavailable" when absent |
| **Advanced & diagnostics** | **Start on boot** | Immediate switch |
| | **System resources** | Feature screen |
| | **Plugin commands** | Feature screen listing registered commands; no plugin management system |
| | **Reset preferences…** | Reset-scope selection dialog, followed by confirmation |
| **About** | **About OpenPets** | Screen with app version and external links to setup documentation, agent integration and source code |

This accounts for all **17 existing feature launchers**: Walkabout moves to Pet; Resources and Commands move to Advanced; the remaining 14 stay under Companion features.

### Destination structure

**Connections screen**, in this order:

1. Device IP and explanatory text.
2. Bind address.
3. **Servers**
   - IPC switch-with-detail and port.
   - Remote switch-with-detail and port.
   - WebSocket switch-with-detail and port.
   - WebSocket helper: experimental, uses the IPC token.
4. **Security**
   - IPC token.
   - Remote token.
   - Require lease switch; clarify that it applies to IPC/WebSocket commands, not Remote.
5. Read-only effective server status.
6. Persistent **Discard / Save** footer.

The whole screen is one transaction. Server-off states disable their port controls without erasing values. Credentials remain available for copying and maintenance.

**Sound screen:**

- Sound enabled switch.
- **Sound file** row, summary **Default sound** or the selected file's display name.
- Clicking Sound file opens a short choice dialog: **Default sound / Choose audio file…**.
- **Test sound** action with an explicit warning about its behavior.

**Feature screens:** place current state first, primary actions second, configuration or creation forms next, and history/items last. Keep every existing action and preset.

**Dialogs remain appropriate for:** animation selection, single-field editing, reset/delete confirmation, the 8-ball question, and native date/time pickers. Document pickers and permission prompts remain system UI. Multi-control workflows become screens, not bottom sheets.

Explicit **Show on pet** actions should reveal the stage after performing their existing command. Starting a timer or focus session should remain on its status screen. Preserve existing automatic controller announcements; do not rewrite their rules.

---

## 3. Components and explicit layout direction

### Utility-surface specification

Use existing Material 3 semantic colors:

- Background: `colorSurface`.
- Primary text: `colorOnSurface`.
- Secondary text: `colorOnSurfaceVariant`.
- Errors: Material error color **plus explanatory text**.
- Remove manually forced white/DKGRAY field styling.

Do not change the stage's black background or create ecosystem-specific design tokens.

Layout:

- Toolbar: **56dp minimum**, title 20sp, navigation target 48dp.
- Content: single column, **maximum width 600dp**, centered on larger devices.
- Horizontal content padding: **16dp**.
- Row vertical padding: **12dp**.
- Title: **16sp**; summary: **14sp**; gap: **4dp**.
- Navigation/switch rows: **64dp minimum** when showing summaries.
- Action targets: **48dp minimum**.
- Section spacing: **24dp above, 8dp below**.
- Forms use Material labeled text inputs, at least 56dp high.
- Save footer: 16dp padding, two buttons with 48dp minimum targets. Stack vertically when labels/font size no longer fit.
- Text may wrap; do not enforce fixed heights or two-line clipping at large font sizes.
- No decorative cards around every setting, illustrations, tabs or icon grid.

Copy rules:

- Sentence case.
- Titles name the setting; summaries explain its current value or consequence.
- No ellipsis on ordinary navigation rows.
- Ellipsis only for actions needing another selection or confirmation.
- Use **Save / Discard** for transactional screens; **Save / Cancel** for small editor dialogs.
- Do not show "Saved" after an action merely starts a timer or sends a command.

### Reusable row set

Use a sealed row model with stable IDs and a small adapter. Several variants can share a holder/layout.

| Row | Implementation and rules |
|---|---|
| Section header | Heading text; non-clickable |
| Navigation | Title, summary, trailing chevron; entire row clickable |
| Switch | Title/summary and `MaterialSwitch`; binding must not trigger writes |
| Switch-with-detail | Same visual base, but distinct accessible switch and detail targets; no ambiguous whole-row toggle plus navigation |
| Text/number field | Material labeled input, helper/error text; number keyboard for ports |
| Secret | Masked input/value, Show/Hide and Copy; reusable credential editor |
| Information | Read-only title/value; optional explicit Copy/Refresh action |
| Slider | Label, current numeric value and range; commit according to screen semantics |
| Choice | Segmented controls only when labels fit; otherwise radio choices |
| Action | Text action with ripple; destructive actions distinguished by copy and confirmation |

For Walkabout, use **four vertically arranged radio choices** and a speed slider from **0.5–3.0**, continuous rather than quantizing existing persisted floats. Provide a numeric editor for precise entry. Keep mode and speed in a Save/Discard draft.

RecyclerView form rules:

- Draft state lives outside recycled Views.
- Detach/rebind text and switch listeners carefully.
- Never replace a focused field's text on every snapshot.
- Preserve cursor, errors and invalid text.
- Use stable IDs and targeted updates; avoid full-list replacement during ticks.

### Shared feature scaffold and controller wiring

`FeatureScreenFragment` supplies toolbar, content slot, empty/error presentation, optional transaction footer and lifecycle cleanup. Use a RecyclerView for growing lists; a scrollable form is acceptable for a small fixed workflow.

`ControllerBinding<S>` adapts existing APIs:

```text
onStart:
  register one stable listener
  refresh controller
  render current snapshot

onStop:
  unregister listener
  stop UI-only ticker
  invalidate queued UI callbacks

onDestroyView:
  release view references
```

Callbacks must reach the main thread and verify that the current view lifecycle is active.

**Do not retain screen listeners or Views in MainActivity.** Remove each migrated feature's corresponding `*DialogListener`, `*DialogRender`, dialog and TextView fields.

Keep activity-owned controller refresh/HUD ticking separate from feature-screen presentation. Timer/Focus/Breathe screens can poll snapshots for their visible countdowns; they must not become responsible for keeping companion sessions alive.

Importantly, split editable forms from live rendering. Water/Routine listeners currently rebuild their entire forms (`MainActivity.kt:1472–1518,1553–1614`), which can overwrite drafts. Incoming snapshots should update effective status, not user edits.

Voice is an exception: create a view-scoped `SttController`, cancel on leaving and destroy with the view. Its constructor captures callbacks (`SttController.kt:17–20`); reusing the current Activity instance risks callbacks targeting an old dialog.

---

## 4. Applying changes, validation and reset

### Apply semantics

| Settings/workflow | Semantics |
|---|---|
| Sound, haptics, TTS, agent notifications, host-status display, start on boot | Immediate |
| Pet selection and custom/default sound | Immediate after successful selection; picker cancellation does nothing |
| Connections, including enables, bind, ports, tokens and lease requirement | One Save/Discard transaction |
| Animations and Walkabout | Save/Discard, preserving their existing explicit-save model |
| Water and Routine configuration | Save/Discard through existing controller `update()` APIs |
| Vocabulary configuration | Save/Discard |
| Reminder/deadline/checklist creation and session controls | Existing explicit commands apply immediately |
| Checklist greeting | Explicit single-field Save/Cancel editor; blank means the existing time-of-day greeting |

There is **no global Save button**. An immediately applied setting must never appear inside another screen's unsaved transaction.

Add a narrowly scoped batch-write method to `AppSettings` for connection configuration. Use **one SharedPreferences editor**, then **one service reload**. Keep the `"openpets"` preference file, key names, defaults and public properties.

Independent toggles already have live preference readers in the Activity/service; do not restart all servers for every toggle. Initially preserve the existing reload for custom-sound and pet selection, rather than combining this work with service architecture changes.

When host-status display is turned off, clear the visible metrics immediately; subsequent updates already check the preference in `HostStatus.kt:24–29`.

### Validation rules

**Ports**

- Integer **1–65535**, no silent fallback or clamping.
- Enabled servers must have distinct ports because they share the bind address and TCP transport.
- Disabled servers may retain duplicate ports.
- Opening the UI does not repair or rewrite existing invalid values.
- Validate changed fields and the enabled configuration on Save; explain any existing invalid active value that must be corrected.

**Tokens — preserve compatibility, not just generated formats**

The actual persistence contract is broader than the default generators:

- IPC getter accepts **16–256 characters** (`AppSettings.kt:50–58`); IPC protocol compares the token verbatim.
- Remote protocol requires **43–256 characters matching `[A-Za-z0-9_-]+`** (`RemoteProtocol.kt:82–83`).

Therefore:

- New/edited IPC tokens: 16–256 printable characters; reject control characters. **Do not require exactly 32 hex characters.**
- New/edited Remote tokens: reuse `isValidRemoteToken()`. **Do not require exactly 43 characters.**
- Trim edited values as the old Save does; never lowercase them.
- Preserve unchanged legacy tokens. Warn about an incompatible existing Remote token, but never silently rotate it.
- Do not change token getter generation behavior during this UI refactor.

**Bind address**

- Empty input explicitly means `0.0.0.0`; show **"All network interfaces"** before saving.
- Accept IPv4, IPv6—including scoped literals where supported—and valid hostnames/`localhost`.
- Reject URLs, paths, embedded ports and whitespace.
- Preserve hostname support: `LineServer.kt:45` uses `InetSocketAddress(bindAddress, port)`.
- Do not perform DNS resolution on the UI thread. Syntax validation and runtime bind failures are different concerns.

**Feature inputs**

Use current engine constraints rather than inventing new limits:

- Water: interval **15–240 minutes**, hours **0–23**, unequal start/end; retain overnight-window support.
- Routine: hours **0–23**, minutes **0–59**.
- Waiting animation duration: **200–10,000ms**, matching the current dialog's clamp, but now report errors instead of silently coercing.
- Reminder/deadline titles: existing 120-character limit; checklist items: existing 80-character limit.
- Session durations: existing positive-duration rules.

Show field-specific errors, retain invalid drafts and focus the first invalid field on Save. Never close the screen on failed validation.

### Effective values

Do not equate persisted "enabled" with "listening."

Add a small read-only, application-scoped connection-status publisher populated by `OpenPetsService` after startup/reload/stop:

- Disabled, applying, listening, stopped or failed.
- Actual bound port.
- Configured bind address, explicitly labeled as configured.
- Safe failure description.

Existing startup exceptions are swallowed at `OpenPetsService.kt:213–245`; WebSocket also swallows failures at `WebSocketBridge.kt:115`. Expose these outcomes without changing authentication or transport behavior.

Save means **preferences saved and reload requested**. A failed bind remains visible with corrective guidance; do not claim success or automatically rewrite/rollback configuration.

Also show:

- LAN IP as best-effort system information.
- Notification preference separately from OS permission/channel availability.
- TTS unavailability when known.
- Current/default sound source, including an inaccessible-file warning.

### Credential presentation

- Mask by default; no plaintext token in overview summaries.
- Reveal only by explicit action; re-mask on leaving/backgrounding.
- Copy only on explicit request; never include credentials in toast text.
- If copying a dirty token, say **"Copied unsaved token. Save to activate it."**
- Disable suggestions/autofill for secret inputs.
- Mark clipboard data sensitive on supported Android versions.
- Use `FLAG_SECURE` while credential editing/reveal is active, restoring the previous window state afterward.
- Keep unsaved secrets only in memory, not SavedStateHandle/Bundle, logs or search indexes.
- After process death, clearly report that unsaved token edits were cleared; require re-entry or explicit retention of the saved token.

### Reset policy

**Recommendation: reset preferences, not app data or pairings.** Product intent for "global reset" is otherwise undefined.

Provide reset scope selection from the overview and **Reset this section…** in configuration-screen overflow menus.

Reset scopes:

- **Pet:** Tux, default animation mappings/duration, Roam at 1.0, host-status display off. Retain imported packages.
- **Appearance & sound:** all four output switches on; custom sound cleared.
- **Connections:** current documented enable/bind/port/lease defaults; **retain both tokens**.
- **Companion preferences:** Water defaults, Routine defaults, Calendar reminders default, greeting blank, vocabulary language `en`, Anki host blank.
- **Startup:** start on boot on.
- **All preferences:** the union of those scopes.

Preserve timers/focus sessions, reminders, deadlines, checklist items, mood history, virtual-pet stats, notified-event history and unknown preference keys. The unused `launchChecklistEnabled` key should remain preserved; see risks below.

Use controller updates for Water/Routine/Calendar so alarms reconcile correctly. Do not clear their preference files.

Confirmation must enumerate consequences and state:

> "Pairing tokens, imported pets and companion records will be kept."

Connection/global confirmation must also warn that defaults enable IPC/Remote on all interfaces.

On transactional screens, section reset fills the draft; Save still activates it. Immediate-section and global resets apply after confirmation. Never use `SharedPreferences.clear()`.

---

## 5. Search and accessibility

### Search

**Include search.** Around 29 overview rows plus nested configuration justify it.

- Root toolbar search expands a labeled **Search settings** field.
- Search a small explicit index of localized titles, descriptive summaries and aliases such as IPC/WebSocket/vibration.
- Include nested settings and feature destinations, not just overview rows.
- Results are navigation rows with breadcrumbs, e.g. **Connections & security › IPC port**.
- Selecting a result opens the destination, scrolls to the target and highlights/focuses it briefly.
- Results never contain editable switches; edits happen at their normal destination.
- Preserve query/results when returning.
- Back closes search before exiting the overview.
- Do not index tokens, personal records, greeting text, sound URIs or downloaded catalog contents.
- No fuzzy-search dependency, database or external search service.

### Accessibility requirements

- All interactive targets ≥48dp.
- Give the pet an accessibility action **Open settings**; long-press must not be the only accessible entry. `PetStageView` currently implements custom touch handling without an equivalent accessibility action.
- Heading semantics for section headers.
- Switch labels and state announced together, without duplicate focus stops.
- Distinct labels such as **Copy IPC token**, not several anonymous "Copy" buttons.
- Proper form labels, keyboard actions and error announcements.
- Preserve focus across snapshot/timer updates; do not announce countdowns every second.
- Navigation announces screen titles and restores focus on return.
- Test 200% font size, landscape, RTL and TalkBack.
- Covered stage and bubbles must not remain in the Settings accessibility tree.

---

## 6. Localization pass

`res/values/strings.xml` is currently English-only. **Standardize the default UI on English and move all user-facing copy into resources.** Do not invent a partial Portuguese translation.

Cover:

- Settings labels/toasts at `MainActivity.kt:804–808,827–829,856–864,898`.
- Animation titles/options/buttons at `409–448`.
- Pet selection, catalog, import/download/deletion copy at `460–641`.
- Deadline/checklist placeholders at `650–669`.
- Walkabout errors and vocabulary hints at `701–748`.
- Resource labels, relative-time strings and feature validation messages.
- New headings, summaries, reset confirmations, accessibility descriptions and search aliases.

Use formatted resources, plurals and locale-aware dates/numbers. Display friendly localized reaction/mode names where useful, but keep persisted/protocol identifiers unchanged.

For known validation failures, show localized UI errors rather than raw English engine exception messages. Also move controller-generated notification/HUD copy into resources as those features are migrated; protocol error strings are a separate concern.

---

## 7. Incremental migration: six phases

| Phase | Changes | Verification before proceeding |
|---|---|---|
| **1. Freeze the contract** | Create a reachability checklist from every current launcher/action. Extract defaults, validation and batch-write helpers without changing the entry UI. Add unit tests. Start localization cleanup. | Existing settings and dialogs still work. Existing installation retains preference values, tokens, records and active sessions. |
| **2. Introduce the utility shell** | Add stage-plus-fragment container, grouped overview, basic rows and Back/inset handling. Route feature rows to existing dialog builders through a temporary host bridge. The connection destination may temporarily retain the old editor. | Long-press, tap-to-wave, incoming events, foreground state and service behavior remain intact. Every old destination is reachable. |
| **3. Migrate core configuration** | Implement Connections, Sound, Pets/catalog, Animations and Walkabout destinations. Add drafts, atomic connection saves, credential masking and effective server status. Stop routing users to the giant Settings dialog. | Invalid input cannot silently save. Cancel/discard does not apply connection changes. Custom sound survives restart; tokens remain stable; pet imports/selection work. |
| **4. Establish feature scaffold** | Migrate Resources, Commands, Virtual pet, Fun and Mood individually. Prove shared subscription cleanup and incremental rendering. | Repeated open/close causes no duplicate listeners or stale UI. Feature actions and existing pet output remain unchanged. |
| **5. Migrate workflow features** | Migrate Timer, Focus, Breathe, Reminders, Deadlines, Checklist, Water, Routine, Calendar, Voice and Vocabulary, one feature per small change. Keep controllers/stores unchanged. | Timers/HUDs continue off-screen; alarms survive background/restart; drafts survive ticks; permission denial/grant and repeated voice use work. |
| **6. Finish and remove bridges** | Add complete search/reset coverage, finish localization/a11y, remove legacy Settings/dialog plumbing and unused helpers, update setup docs/screenshots. | Full device acceptance suite, unit tests, debug/release builds and minSdk smoke tests pass. No migration-only UI remains. |

For custom sound, take the persistable document URI permission when available and validate readability before persisting. The existing callback at `MainActivity.kt:210–215` does not retain that permission.

---

## 8. Risks, uncertain points and non-goals

### Highest risks

1. **Lifecycle leaks:** application-scoped controllers can retain listeners capturing destroyed Views.
2. **Losing companion ticking:** separating presentation must not move session maintenance into visible screens.
3. **Credential compatibility:** exact generated-format validation would reject working custom IPC tokens.
4. **Draft loss:** snapshot rendering and RecyclerView rebinding must not erase user input.
5. **Misleading server status:** startup failures are currently swallowed; status must represent actual outcomes.
6. **Permission/document results:** restoration must return to the correct route without opening duplicate screens.

### Decisions requiring explicit acknowledgment

- **Immediate toggles intentionally change the old blanket-Save interaction**, while preserving their operational behavior. Recommend this change because there is no longer a misleading global transaction.
- **Global reset should mean preferences only**, not factory erase. Recommend preserving credentials and records unless product requirements explicitly say otherwise.
- `launchChecklistEnabled` appears only in `AppSettings`, with no consumer. **Preserve it; do not expose a nonfunctional switch or invent new launch behavior.**
- Vocabulary's hint currently allows `host:port`, but `VocabularyClient.kt:27–28` appends `:8765` unconditionally. Do not impose new endpoint validation or promise custom-port support during this refactor; track that existing integration defect separately.
- `SoundNotifier.playTest()` sets alarm volume to maximum and plays for about 2.5 seconds. Preserve it as a clearly warned diagnostic, **not** an ordinary custom-sound preview.

### Non-goals

- Compose migration, Preference framework or desktop Control Center layout.
- Dynamic `configSchema` rendering or plugin administration.
- DataStore/database migration.
- New theming/token system.
- Rewriting controllers, schedulers, authentication or networking protocols.
- Factory reset, automatic credential rotation or configuration export.
- Generic form engine, elaborate navigation framework or broad service rewrite.

---

## 9. Device-verifiable acceptance criteria

- Long-press opens Settings; accessible **Open settings** works; Back returns to the same pet stage.
- All former controls, imports, feature actions and presets remain reachable.
- Upgrading an existing installation preserves tokens, settings, records and active sessions.
- Settings does not stop incoming pet events, companion sessions, foreground service or alarms.
- Invalid ports—including blank, nonnumeric, 0 and 65536—show errors and remain editable; valid 1 and 65535 are accepted.
- Conflicting enabled ports are rejected; disabled-port duplication is allowed.
- Discarding connection edits changes neither persisted configuration nor active credentials.
- Valid Save writes the connection group once and requests one reload.
- Bind failures are visibly reported; "enabled" is never presented as proof of listening.
- Tokens remain masked initially, copy accurately, preserve supported legacy formats and never rotate during reset.
- Picker cancellation changes nothing; selected custom sound remains readable after restart.
- Timer/Focus/Breathe countdowns and Water/Routine drafts survive updates, navigation and background/foreground transitions.
- Calendar and microphone denial remain recoverable; leaving Voice stops capture.
- Section/global resets affect only their documented preferences; records, imported pets and pairings remain.
- Search finds nested options and navigates to the correct control.
- TalkBack, 200% text, landscape, keyboard and system bars cause no unreachable/clipped controls.
- All settings/feature UI uses resource-backed, consistent-language copy.
- Repeatedly opening and closing each feature produces no stale callbacks, duplicate announcements or crashes.
- Smoke-test on **API 24 and a current Android device**, including a release build with resource shrinking.
