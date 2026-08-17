# HamGhadam (همقدم) — Android app (FE-C1 + FE-C2 + FE-C3)

Android client for **HamGhadam (همقدم)**, "walking
in step together". Reads daily health aggregates from **Health Connect**
(steps, sleep, heart rate), syncs them to the backend (BE-C1 `/api/v1/daily`),
renders the dashboard with source attribution, and runs **friend step
challenges** (BE-C2/BE-C3): create/join, invite share via deep-linked join
codes, live leaderboards, and FCM push (challenge start/end/beat-you).

- Kotlin + Jetpack Compose (Material 3), min SDK **28**, target/compile SDK **37**
- Health Connect Jetpack SDK **1.2.0-alpha05** (`androidx.health.connect:connect-client`)
  - read-only — **no writes to Health Connect** (v1)
  - per-type `READ_*` permissions via the Health Connect consent UI
  - **Matchmaking API** (`checkIfMatchmakingIsPossible` / `createMatchmakingIntent`)
    on the "Connect your watch" card for onboarding
  - background reads: `READ_HEALTH_DATA_IN_BACKGROUND` declared; the sync worker
    checks the grant and **falls back to foreground-only sync** when missing
- Daily sync: WorkManager periodic (24h + 6h flex) + one-shot "Sync now"
- Dashboard (FE-C2):
  - Today card: steps hero, sleep, avg HR, source-app attribution
    ("Demo Data · Samsung Health"), Refresh / Sync now actions
  - Last-7-days card: totals + best day + pure-Canvas steps bar chart
    (today's bar highlighted orange, empty days dashed)
  - Week data pipeline: local `PrefsDailySummaryCache` first → server
    `GET /api/v1/daily/range` when signed in → direct Health Connect read
    for days still missing (rows cached for next time)
  - Empty/offboarding hero: "Connect your watch" with Matchmaking CTA
  - "2h ago" style last-sync line from AuthStore
- Payload matches BE-C1: `{date, tz_offset, steps, sleep_seconds?, avg_hr?, source_apps[], source:"health_connect"}` — identity comes from the JWT (no `user` field)
- Challenges (FE-C3):
  - **Challenges tab** — active / upcoming / ended lists, creator FAB, join
    via pasted invite link (`fitnessapp://challenges/{id}/join?code=…`), and a
    create form (title, start/end date+time, invite-only switch, optional max
    participants 2..1000). Timestamps are device-local, converted to UTC ISO
    for the API.
  - **Challenge detail** — status/window/creator header, join (open) or
    join-with-code (invite-only), leaderboard ranked list rendered from BE-C2's
    `LeaderboardOut` (medal colors for top 3, "you" highlight via `is_me`,
    per-day breakdown), participants list with totals, creator "Start now /
    End now" controls (which trigger the FCM `challenge_started` /
    `challenge_ended` pushes), invite share sheet (8-char code + deep link via
    Android share intent + copy-to-clipboard).
  - **Deep links** (BE-C3 contract): `fitnessapp://challenges/{id}`,
    `fitnessapp://challenges/{id}/join?code={code}`,
    `fitnessapp://challenges/{id}/leaderboard` — routed by MainActivity
    (VIEW intents) and by push payloads through the same
    `ChallengeDeepLink` parser.
  - **FCM**: `FcmMessagingService` handles data payloads
    `{type, challenge_id, deep_link}`; foreground → navigate in place,
    background → notification whose tap re-opens the deep link.
    `FcmTokenManager` registers the device token via
    `POST /users/me/fcm-token` after login. Without `google-services.json`
    (no Firebase project wired) a stable synthetic `dev:<uuid>` token is
    registered instead — the backend pipeline (dry-run) and routing path stay
    fully testable; drop `google-services.json` into `app/` to switch to real
    Firebase tokens (no code change).
  - **Push simulator (tester, no Firebase needed)** — same routing path as a
    real push:
    `adb shell am broadcast -n com.fitnessapp.android/.data.fcm.DebugFcmSimulatorReceiver -a com.fitnessapp.android.action.SIMULATE_PUSH --es deep_link "fitnessapp://challenges/1/join?code=R7NRX322" --es type challenge_started --es challenge_id 1`
    (the explicit component form is required on Android 8+; an implicit
    broadcast with this custom action is not delivered to a manifest receiver)

## Build

```bash
./gradlew assembleDebug        # APK: app/build/outputs/apk/debug/app-debug.apk
./gradlew test                 # JVM unit tests (aggregation, payload, formatters, codec, cache, challenges)
./gradlew connectedDebugAndroidTest  # instrumented: dashboard + challenges UI states
./gradlew build                # full build (lint + tests + assemble)
```

Requires JDK 17 and an Android SDK (`local.properties` → `sdk.dir`).

## Reproduce on an emulator (tester)

1. Start an API 34+ emulator with the **Health Connect** app installed
   (API 34+ includes the Health Connect controller; on older emulators install
   the Health Connect APK).
2. Install the data generator (writes mock steps/sleep/HR into Health Connect):
   `adb install app-data-gen.apk` — from the spike workspace
   (`boards/fitness-app/workspaces/t_22be008b/app-data-gen`). Grant write access.
3. Install this app: `adb install app/build/outputs/apk/debug/app-debug.apk`.
4. Backend up (BE-C1): `docker compose up` on the backend repo — API on
   `http://localhost:8000` (emulator reaches it at `http://10.0.2.2:8000`).
5. Open the app → Dashboard:
   - "Grant access in Health Connect" → allow Steps (v1 requests only READ_STEPS).
   - "Connect your watch" → Health Connect matchmaking screen (steps source).
   - "Refresh" → today's steps/sleep/HR aggregate appears (sources listed).
6. Account tab → Register/Sign in (any email + ≥8 char password; base URL
   defaults to `http://10.0.2.2:8000/api/v1`).
7. "Sync now" → server logs the daily row (or `GET /api/v1/daily?date=YYYY-MM-DD`
   with the Bearer token).
8. FE-C2 dashboard checks:
   - Today card shows the real aggregate with source attribution
     (data generator writes `com.fitness.explorer.datagenerator` → "Demo Data").
   - "Last 7 days" strip totals + bar chart render; today's bar is orange.
   - Backend rows for prior days (if any) fill the strip after a signed-in refresh;
     with no backend and no HC grants, an empty/offboarding "Connect your watch"
     hero appears instead.
   - "Last sync: <date> (Xh ago)" appears under the cards after a successful sync.

9. FE-C3 challenge checks (backend must be the BE-C3 stack on `:8001`; set the
   base URL on the Account tab to `http://10.0.2.2:8001/api/v1`):
   - Challenges tab → create a challenge (title, start ~now, end in a few days).
   - Detail opens after creation: creator sees "Invite friends" + "Start now".
   - "Invite friends" → invite sheet with an 8-char code + share link → Share
     (Android chooser) or Copy link.
   - Second user (another emulator, or the API via curl) joins with the code:
     `curl -X POST "http://localhost:8001/api/v1/challenges/<id>/join?code=<CODE>" -H "Authorization: Bearer <token2>"`.
   - Sync steps for both users (`Sync now` on each / curl POST /daily), then
     Refresh on the detail → leaderboard shows both users ranked with totals.
   - Deep link: `adb shell am start -a android.intent.action.VIEW -d "fitnessapp://challenges/<id>/join?code=<CODE>"`
     → the detail opens with the join code pre-filled and joins (invite-only).
   - FCM simulation (same routing path as a real push):
       `adb shell am broadcast -n com.fitnessapp.android/.data.fcm.DebugFcmSimulatorReceiver -a com.fitnessapp.android.action.SIMULATE_PUSH --es deep_link "fitnessapp://challenges/<id>" --es type challenge_started --es challenge_id <id>`
       → foreground: navigates to the challenge; background: posts a
       "Challenge started" notification whose tap opens the challenge.
   - FCM token registration: after sign-in the app registers a `dev:*` token
     (or the real Firebase token when `google-services.json` is present) via
     `POST /users/me/fcm-token` — verify with
     `curl http://localhost:8001/api/v1/users/me/devices -H "Authorization: Bearer <token>"`.

## Layout

```
app/src/main/java/com/fitnessapp/android/
  FitnessApp.kt                 Application + manual DI container (challenges/fcm added)
  MainActivity.kt               Compose host + deep-link intent routing
  data/
    HealthConnectRepository.kt  HC status, permissions, daily reads, matchmaking
    model/DailySummary.kt       domain model + aggregation (pure, unit-tested)
    model/DailySummaryCodec.kt  JSON codec for cache + BE-C1 GET responses (pure)
    model/DashboardFormatters.kt source labels, durations, relative time (pure)
    model/ChallengeModels.kt    challenges API models (pure)
    model/ChallengeCodec.kt     challenges/leaderboard/invite JSON codecs (pure)
    model/ChallengeFormatters.kt window/status/invite-code formatting + validation (pure)
    model/ChallengeDeepLink.kt  deep-link parser + route/URI builders (pure)
    cache/DailySummaryCache.kt  SharedPreferences row cache + in-memory test double
    network/AuthStore.kt        JWT/email/userId/base URL/fcm token prefs
    network/ApiClient.kt        OkHttp client (auth, daily, challenges, invites, FCM)
    fcm/FcmTokenManager.kt      Firebase-or-dev token, backend registration
    fcm/NotificationRouter.kt   push data → navigate (foreground) / notification (bg)
    fcm/FcmMessagingService.kt  FirebaseMessagingService + debug push simulator
    sync/DailySyncWorker.kt     WorkManager worker (bg-permission aware)
    sync/SyncScheduler.kt       periodic + one-shot scheduling
  ui/
    MainScreen.kt               bottom nav + NavHost + deep-link handling
    dashboard/…                 DashboardScreen (stateless content), ViewModel,
                                WeekStepsChart (Canvas), DashboardUiState
    challenges/…                list + create form + join dialogs (stateless
                                content), challenge detail + leaderboard + invite
                                share, ViewModels, form validator
    settings/…                  auth + base URL
```

## Notes / decisions

- Timezone: `DailySummary.date` is the device-local calendar date; `tz_offset`
  is minutes east of UTC at read time (BE-C1 validates ±14h).
- Sleep attribution: a session counts toward the local day it **ends** in
  (wake day), full duration clamped to 24h.
- `source_apps` = distinct Health Connect data origins (record metadata) for the day.
- Cleartext HTTP is scoped via `network_security_config.xml` to the dev loopback only
  (`10.0.2.2`, `localhost`, `127.0.0.1`) — everything else stays HTTPS-only.
  Production must use a real HTTPS endpoint.
- HC SDK 1.2.0-alpha05 is used because the Matchmaking API is only exposed there
  (`@OptIn(ExperimentalMatchmakingApi::class)`); the read path is unchanged from
  the validated 1.1.0 spike.
- Consent flow (SPIKE finding, enforced at API 36): the manifest MUST declare
  both the rationale activity (`androidx.health.ACTION_SHOW_PERMISSIONS_RATIONALE`)
  AND the `ViewPermissionUsageActivity` activity-alias
  (`VIEW_PERMISSION_USAGE` + `HEALTH_PERMISSIONS`, exported, protected by
  `START_VIEW_PERMISSION_USAGE`), or the Health Connect consent UI silently
  never opens.
- Background-read gate: the periodic worker skips the read when
  `READ_HEALTH_DATA_IN_BACKGROUND` is not granted (Android 15+). The one-shot
  "Sync now" path carries `trigger=foreground` and reads anyway — the app is on
  screen, so a foreground read is allowed. That is the card's documented fallback.
