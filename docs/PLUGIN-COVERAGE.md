# OpenPets plugin coverage on Android

How the upstream OpenPets plugin lineup maps onto this Android companion, and
how to implement the rest natively.

> Caveat: upstream `web/public/plugins/catalog.v2.json` currently returns 404, so
> the lineup below comes from `docs/official-plugins.md` (13 official + 5
> community) plus each community repo's README. Field-level catalog metadata
> could not be verified.

## Legend

- **Status:** ✅ native equivalent built · 🟡 partial · ⬜ not built.
- **Dependency:** `device-local` · `cloud` · `host-data` (needs PC data) · `desktop-runtime`.

## Official plugins (13)

| Plugin | Status | Dependency | Android approach |
| --- | --- | --- | --- |
| Quick Reminders | ✅ `a5d1e94` `84b58a0` | device-local | native store + `AlarmManager` + notifications + snooze/done |
| Simple Timer | ✅ `0b4d0b5` `198045e` | device-local | native countdown + exact-alarm expiry |
| Focus Buddy | ✅ `add66f4` `a8301b2` | device-local | native focus/break state machine + HUD |
| Virtual Pet | ✅ `1e8808a` | device-local | native stats/decay + actions + HUD |
| Launch Buddy | 🟡 (greeting ✅) | device-local | greeting shipped via **Fun**; a *checklist* flow + `launch.greet` assistant action are TODO |
| Daily Fortune Cookie | ✅ `a224402` | device-local | **Fun → Fortune** |
| Magic 8-Ball | ✅ `a224402` | device-local | **Fun → 8-ball** |
| Mood Check-in | ✅ `02670a4` | device-local | native log + nudge |
| Water Reminder | ✅ `7d93329` | device-local | native interval scheduler within a window |
| Morning & Evening Routine | ✅ `583b43d` | device-local | native daily slots |
| Anxiety Aid Tools | 🟡 (breathing ✅) | cloud + device-local | breathing shipped; **audio** = download + cache (needs media URLs/bundled assets) |
| Calendar Airmail | 🟡 (local ✅) | cloud | `CalendarContract` read shipped; **Google Calendar** needs native OAuth |
| System Resources | 🟡 (device ✅; host ✅ partial) | host-data (or device) | device metrics native; **host** metrics now arrive over the experimental WS (`status.host`) |

Separate repo (SDK v3 example, not in the 13):

| Plugin | Status | Dependency | Android approach |
| --- | --- | --- | --- |
| Deadline Buddy (manual) | ⬜ | device-local | reuse `ReminderEngine`: pinned "nearest deadline" HUD + 1 day / 1 h / 15 min reminders |

## Community plugins (5)

| Plugin | Status | Dependency | Android approach |
| --- | --- | --- | --- |
| Vocabulary Drag & Drop | ⬜ | cloud (+ local Anki service) | accept shared/selected text -> define/translate (cloud) -> optional AnkiConnect (reachable local service) |
| Higgsfield Watch | ⬜ | cloud | user token -> poll `fnf.higgsfield.ai` -> notify start/done/fail |
| Spotify Buddy | ⬜ | desktop-runtime | possible only as a redesign: native Spotify OAuth + playback state (the documented version needs a desktop bridge + tunnel) |
| Walkabout | 🟡 (idle wander ✅) | desktop-runtime | native reinterpretation: roam/patrol modes on the phone (idle wandering exists; add patrol/follow) |
| Usage Buddy | ⬜ | host-data | needs a PC usage monitor; now feasible as a **WS host feed** (same pattern as `status.host`) |

## Implementation patterns

1. **Device-local** — add a `companion/` controller following the established
   pattern (`Engine` pure + JVM tests, `Store`, `Scheduler`/receiver,
   `Controller`, feature HUD via `PetBus.hud`). Reuse reminders/scheduling.
2. **Cloud** — native network + user-provided credentials (OAuth where needed);
   no host required.
3. **Host-data** — the PC pushes data over the experimental WS channel; we
   already have `status.host`; generalize it into a `host.data` feed
   (`integrations/host-status/` becomes one sender among several).
4. **Desktop-runtime** — reinterpret natively where it makes sense (screen
   roaming); otherwise skip (Spotify playback control).

## Remaining work (prioritized)

1. **Deadline Buddy** (native) — highest fit; reuses the reminder engine + a
   pinned HUD.
2. **Launch Buddy checklist** (native, small) + `launch.greet` assistant action.
3. **Anxiety Aid Tools audio** (download + cache; needs media source).
4. **Vocabulary Drag & Drop** (shared-text intake + cloud lookups).
5. **Higgsfield Watch** (token + polling + notifications).
6. **Walkabout** roam/patrol mode (native).
7. **Generalized host feed** for host-data plugins (Usage Buddy; richer host
   System Resources: GPU/battery/network).
8. **Spotify Buddy** — redesign, optional/large.

## Explicitly excluded

- **Usage Buddy / host System Resources as-is** need a PC-side source; viable
  only through the WS host feed (item 7).
- **Desktop-runtime specifics** (pet movement/cursor, desktop sandbox) are not
  portable; only native reinterpretations are in scope.
