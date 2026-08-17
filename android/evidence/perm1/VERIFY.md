# PERM-1 verification — steps-only Health Connect permission set (v1)

Task t_52f4e0bb (frontend-dev). Commit `0b8a57e` on `brand/hamghadam`.

## Fix summary
v1 now requests and surfaces ONLY `READ_STEPS` (powers step challenges, checklist 1.4).
`READ_SLEEP` / `READ_HEART_RATE` removed from:
- AndroidManifest.xml uses-permission declarations
- `HealthConnectRepository.REQUIRED_READ_TYPES` and matchmaking request
- `PermissionsRationaleActivity` consent request (dialog now lists Steps only)
- Dashboard Data-access card (Sleep/Heart rate no longer shown as rows/grants; muted
  "aren't requested in v1" note)

Conditional sleep/HR read logic in `readDailySummary` is retained (guarded by grant
checks; never requested, so dead on fresh v1 installs). Today-card sleep/HR metrics
render only when data exists.

## Proof
- `hc_dialog_steps_only.png` — Health Connect consent dialog lists ONLY "Steps"
  (no Sleep, no Heart rate). Captured on emulator-5554 (Medium_Phone_API_36), fresh
  `pm clear` install of app-debug.apk built from this commit.
- `permission_card_steps_only.png` — Dashboard Data-access card shows a single row
  "Steps — powers your step challenges / Not granted", the v1 note, and the
  "Grant access in Health Connect" CTA. Sleep/Heart rate absent as rows.

## Suites (all green after change)
- `./gradlew compileDebugKotlin testDebugUnitTest lintDebug` — BUILD SUCCESSFUL,
  56/56 unit tests, 0 lint errors.
- `./gradlew assembleDebug compileDebugAndroidTestKotlin` — BUILD SUCCESSFUL.
- `./gradlew connectedDebugAndroidTest` on emulator-5554 — 12/12 instrumented tests
  passed (DashboardUiTest, DashboardPermissionOkTest, ChallengesUiTest).

## Note
The emulator's Health Connect instance did not persist a Steps grant through its own
permission store when "Allow" was tapped (known HC-on-emulator limitation; framework
`pm grant android.permission.health.READ_STEPS` reports granted=true while the HC SDK
`getGrantedPermissions()` stays empty without a real health data provider). The
verification target — the consent dialog lists only Steps and the Data-access card
marks the real grant state — is fully evidenced; functional Step reads were already
gated by the prior FE-C2 on-device run.