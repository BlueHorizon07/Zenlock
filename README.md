# Zenlock

An Android focus app: per-app daily limits, a sleep window that closes everything except an
allowlist, a night warmth/dimming filter, and — the part that actually matters — a **Zen pause**
that puts a breath and today's numbers between you and the app you reached for without thinking.

Kotlin, Jetpack Compose, Material 3. No backend, no account, no analytics. Nothing leaves the phone.

---

## Running it

```bash
./gradlew :app:assembleDebug     # debug build
./gradlew :app:assembleRelease   # minified, ~1.5 MB
./gradlew test                   # 32 unit tests: rule engine, lock guard, time maths
```

Or open the folder in Android Studio and hit Run. Requires JDK 17+ (Android Studio's bundled JBR
works) and Android SDK 35.

On first launch the app asks for three permissions, each on its own Settings screen:

| Permission | Why |
|---|---|
| Usage access | Read per-app foreground time, so limits can be measured |
| Accessibility | Notice the moment an app comes to the foreground |
| Draw over other apps | The night filter, and reliable background activity starts for the pause screen |

---

## What is in it

**Enforcement** — per-app daily limits, a sleep window that closes everything outside an
allowlist, a manual "off for today" switch, and the Zen pause.

**Insight** — the day drawn hour by hour, the last 7 or 30 days against the daily average, and
the apps that took the most of it.

**The lock** — opt in per app, and once that app's limit is spent for the day, the rule refuses
to be loosened: the limit cannot be raised or switched off, the rule cannot be deleted, and
Zenlock's own master switch cannot be turned off while any lock is held. It opens again at
midnight. The refusal lives in the data layer, not in a disabled button, so there is no screen
that can forget to enforce it.

**Screen** — scheduled amber warmth and dimming, drawn as an overlay so it goes below the
panel's minimum brightness.

**Look** — five palettes including a monochrome one, each in light, dark or system. Nunito
shipped as a variable font and instanced across five weights.

**Reach** — a Quick Settings tile for the master switch, because a blocker you have to open an
app to disable is a blocker people uninstall.

---

## How it works

```
ZenAccessibilityService     window changed -> which package?
        |
        v
UsageTracker                how long has that package been open today?
        |
        v
BlockEngine.decide()        pure function: Allow | Block(reason) | Pause(seconds)
        |
        v
BlockActivity               full-screen: hard stop, or breathe-then-choose
```

And, on the side, the record that outlives the system's own:

```
UsageTracker.dailyBreakdown(7)   the week Android still remembers
        |
        v
HistoryRepository.sync()         rolled up, upserted
        |
        v
Room: day_app_usage              one row per app per day, kept for 400
        |
        v
charts                           7-day and 30-day views
```

**`BlockEngine`** ([app/src/main/java/com/zenlock/data/BlockEngine.kt](app/src/main/java/com/zenlock/data/BlockEngine.kt))
is deliberately pure — no Android types — so the rule precedence is readable in one screen and
testable without an emulator. Order: master switch, sleep allowlist, manual block, daily limit,
active pass, Zen pause.

**Usage numbers come from `UsageStatsManager`**, not from our own bookkeeping. If the service is
killed and restarted, the totals are still right, and they match what Digital Wellbeing shows.

**Dimming is an overlay, not `SCREEN_BRIGHTNESS`.** An overlay can go darker than the panel's own
minimum, which is the entire point at 1am, and it needs no `WRITE_SETTINGS`.

**Passes are in-memory only** (`ZenPasses`). If the process dies, the pass is gone and the app asks
again — failing toward blocking rather than away from it.

**State is one JSON blob in DataStore.** It is tens of rules, not a database's worth. `ZenStore`
normalises the day on every read, so a stale `blockedToday` can never survive into tomorrow.

**History is Room, and it has to be.** Android retains raw usage events for roughly a week and
then drops them, so anything longer than that exists only because the app rolled it up and kept
it. `HistoryRepository.sync()` runs on every foreground, pulls the system's whole retained
window, and upserts it — today's rows are rewritten each time while the day is still moving, and
older days freeze once they pass. The practical effect is that the first week of history is
inherited from Android for free and the rest accumulates from install.

---

## Where the data goes

Nowhere. There is no account, no server, no analytics SDK and no network permission in the
manifest — the app cannot phone home because it has no way to. Usage history is a local Room
database, settings are a local DataStore file, and both are removed when the app is uninstalled.

This is a deliberate product position, not an oversight. The data this app handles — which apps
you open, how often, and at what hour — is about as personal as a phone gets.

---

## What is honest to say about this

**Blocking on Android is soft.** A determined user force-stops Zenlock, revokes the accessibility
permission, or uninstalls it in about fifteen seconds. Device Admin uninstall-protection is not
available to consumer apps under Play policy. This app is a speed bump with good manners, not a
cage — and the research on friction says the speed bump is what actually changes behaviour anyway.

**Play Store review will have opinions.** `AccessibilityService` used for anything that is not
assistive technology requires a declaration form and is a common rejection reason. Same for
`FOREGROUND_SERVICE_SPECIAL_USE`. Both are declared narrowly here — the service config sets
`canRetrieveWindowContent="false"` and the app never calls `getRootInActiveWindow()` — which is
the strongest position to argue from. Package visibility uses a `<queries>` declaration rather
than `QUERY_ALL_PACKAGES`, so that fight is avoided entirely.

**Digital Wellbeing ships free on every Android** and already does app timers, bedtime mode,
grayscale and Night Light. The limits and filters here are table stakes, not a product. The Zen
pause is the only part a person would pay for.

---

## Not built yet

- Accountability: notify a partner on override, or require their code to unblock
- Commitment sessions that genuinely cannot be cancelled once started
- Streaks, and month-over-month comparison
- Export (CSV or JSON) of the history database
- Home screen widget
- Per-app schedules (limits are daily totals only)
- Instrumented UI tests

---

## Credits

Typeface: [Nunito](https://fonts.google.com/specimen/Nunito) by Vernon Adams, Cyreal and Jacques
Le Bailly, used under the SIL Open Font License 1.1. Full licence text in `OFL-Nunito.txt`.
