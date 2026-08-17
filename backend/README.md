# Fitness App Backend (BE-C1)

FastAPI backend for the all-in-one fitness Android app: users, JWT auth, device
registration, and Health Connect daily ingest. Postgres via Docker (SQLite dev
fallback), SQLAlchemy 2 + Alembic migrations, pytest suite.

Decisions in force: D1 Android-only v1 · D2 FastAPI + Postgres + JWT · D3
steps-only challenges (metric-extensible schema) · D4 free launch
(`users.premium` bool, no billing code) · "day" = user local day (client sends
local date + UTC offset in minutes) · anti-cheat v1 = only Health
Connect-synced data (`source: "health_connect"`), sanity bounds log-only.

## Quick start (Docker, Postgres)

```bash
cp .env.example .env        # then set SECRET_KEY
docker compose up --build
# API on http://localhost:8000, docs on /docs
# (this machine: the BE-C1 stack occupies host 8000, so docker-compose.yml
#  maps host 8001 -> container 8000 — API lands on http://localhost:8001)
```

`docker compose up` runs `alembic upgrade head` before starting uvicorn.

## Local dev (SQLite)

```bash
uv venv .venv --python 3.11
uv pip install -r requirements.txt -r requirements-dev.txt
export DATABASE_URL="sqlite:///./fitness.db"     # default already
env -u PYTHONPATH .venv/Scripts/python.exe -m alembic upgrade head
env -u PYTHONPATH .venv/Scripts/python.exe -m uvicorn app.main:app --reload
```

> Windows/git-bash note: this machine's `PYTHONPATH` leaks the Hermes agent
> venv into every Python run. Always run the project interpreter with
> `env -u PYTHONPATH` (or clear PYTHONPATH in your shell).

## Tests

```bash
env -u PYTHONPATH .venv/Scripts/python.exe -m pytest -v
```

80 tests across the BE-C1/BE-C2/BE-C3 suites: auth flow, ingest validation
(negative rejected, duplicate date = upsert), TZ handling (same UTC day,
different local dates), anti-cheat (manual source rejected), challenge
lifecycle + leaderboards, invites (join codes), FCM push (dry-run mocked),
and alembic migrations up/down on fresh DBs (see per-phase sections below).

## Curl smoke

```bash
bash scripts/smoke.sh
```

## API contract (v1)

Base path `/api/v1`. Auth: `Authorization: Bearer <token>`.

| Method | Path | Body (JSON) | Notes |
|---|---|---|---|
| POST | `/auth/register` | `{email, password (>=8), display_name?, tz_offset?}` | 201 → `{access_token, token_type, expires_in, user}`; 409 dup email |
| POST | `/auth/login` | `{email, password}` | 200 → token; 401 bad creds |
| GET | `/users/me` | — | 200 → user profile |
| PATCH | `/users/me` | `{display_name?, tz_offset?}` | 200 → user profile |
| POST | `/users/me/device` | `{device_token, kind: android\|web\|ios\|unknown, model?}` | 201/200 → device; upserts by (user, token) |
| GET | `/users/me/devices` | — | 200 → list |
| POST | `/daily` | see below | 201 first ingest / 200 upsert update; 422 validation |
| GET | `/daily?date=YYYY-MM-DD` | — | 200 → day; 404 none |
| GET | `/daily/range?from=&to=` | — | 200 → `{items: [...]}` |

### POST /daily payload

```json
{
  "date": "2026-08-15",
  "tz_offset": 210,
  "steps": 12345,
  "sleep_seconds": 28800,
  "avg_hr": 71.5,
  "source_apps": ["com.samsung.health"],
  "source": "health_connect"
}
```

- Identity comes from the JWT — there is **no `user` field** in the body
  (the spike stub included one because it had no auth; the real API derives
  the user from the bearer token).
- `date` is the user's **local calendar date**; `tz_offset` is minutes east
  of UTC at ingest time (range ±14h, hard-rejected outside).
- Upsert key: `(user, metric, date)` per metric row — re-POSTing is
  repeat-safe; latest payload wins.
- `source` must be `health_connect` (device-generated). Any other value is
  rejected 422 — **there is no manual-entry code path in v1** (anti-cheat).
- Sanity bounds (steps > 250k/day, sleep > 24h, avg_hr > 250) are
  **accepted and logged** (log-only per plan).

### Data model

- `users(id, email uq, password_hash, display_name, premium bool, tz_offset, created_at, updated_at)`
- `devices(id, user_id fk, device_token, kind, model, last_seen_at, created_at)` — uq(user_id, device_token)
- `daily_scores(id, user_id fk, metric, date, value, tz_offset, source, source_apps json, created_at, updated_at)` — uq(user_id, metric, date); metric ∈ `steps|sleep_seconds|avg_hr` (extensible for BE-C2 challenges)
- `challenges(id, title, metric, starts_at, ends_at, status, invite_only, creator_id, created_at, updated_at)` — status ∈ `draft|active|ended`
- `challenge_participants(id, challenge_id fk, user_id fk, joined_at)` — uq(challenge_id, user_id); creator is auto-participant

## BE-C2 — Challenges API

### Endpoints

| Method | Path | Body (JSON) | Notes |
|---|---|---|---|
| POST | `/challenges` | `{title, starts_at, ends_at, metric="steps", invite_only=false}` | 201 → challenge; creator auto-joins; 422 window invalid / ends_at not future / metric unknown |
| GET | `/challenges` | — | 200 → challenges I created or joined (newest first) |
| GET | `/challenges/{id}` | — | 200 → challenge + participants + progress totals; 404 |
| POST | `/challenges/{id}/join` | — | 200 → challenge; 409 already joined / ended; 403 invite_only (codes ship in BE-C3); `?code=` reserved |
| POST | `/challenges/{id}/leave` | — | 200 → challenge; 404 not participant; 400 creator; 409 ended |
| PATCH | `/challenges/{id}/status` | `{status: "active"\|"ended"}` | creator-only; forward transitions only (draft→active→ended); 403 non-creator; 409 illegal transition |
| GET | `/challenges/{id}/leaderboard?as_of=YYYY-MM-DD` | — | 200 → ranked daily + total, per-participant local day; 404 |

### Leaderboard semantics (TZ-aware scoring)

- `starts_at` / `ends_at` are **UTC instants** (naive input is treated as UTC).
- A participant's scoring window is the inclusive local-date range
  `[local_date(starts_at, tz), local_date(ends_at, tz)]` where `tz` =
  `users.tz_offset` in minutes east of UTC (0 if unset). Scores come from
  `daily_scores` rows for the challenge metric whose `date` falls in that
  range — each participant's own calendar day, so the same UTC day can be a
  different local date per participant.
- `as_of` is an inclusive cutoff on each participant's local date. Passed
  explicitly it applies to every participant; when omitted the default is
  per participant — their own local calendar date "today" (`local_date(now,
  tz)`), so an east-of-UTC user's local-today ingest is never dropped in the
  daily 00:00-03:30 Tehran skew window (DB-1). The board's `as_of` field then
  reports the latest per-participant cutoff. `daily[]` is zero-filled per
  local day in the window, so charts render deterministically.
- Ranking: `total` desc, then `display_name` asc (case-insensitive), then
  `user_id` asc — deterministic ties. `is_me` flags the viewer.
- Re-ingesting a day is an upsert (BE-C1) and changes the board
  deterministically — repeat-safe, latest value wins.

### Status lifecycle

- `draft` → `active` → `ended`; time-driven propagation is **lazy**: reads,
  joins and leaderboard requests persist the transition when `now` passes
  `starts_at` / `ends_at`. `PATCH /status` gives the creator explicit
  start/end control ("start now", "end now"); no backward transitions.
- Join is allowed in `draft` and `active`; rejected after `ended` (409).
- `invite_only` challenges exist but cannot be joined until BE-C3 ships join
  codes (403 with explicit message). Creator is always a participant.

### Anti-cheat

No score field exists on any challenge payload. Leaderboards only read
`daily_scores`, which BE-C1 restricts to `source=health_connect` — there is
no manual-entry channel anywhere in the API.

### Example leaderboard response

```json
{
  "challenge_id": 1,
  "metric": "steps",
  "status": "active",
  "as_of": "2026-08-17",
  "entries": [
    {
      "rank": 1,
      "user_id": 2,
      "display_name": "Bob",
      "total": 8000.0,
      "daily": [{"date": "2026-08-15", "value": 8000.0}, {"date": "2026-08-16", "value": 0.0}],
      "is_me": false
    },
    {
      "rank": 2,
      "user_id": 1,
      "display_name": "Alice",
      "total": 5000.0,
      "daily": [{"date": "2026-08-16", "value": 5000.0}, {"date": "2026-08-17", "value": 0.0}],
      "is_me": true
    }
  ]
}
```

### Tests

```bash
env -u PYTHONPATH .venv/Scripts/python.exe -m pytest -v
```

51 tests: BE-C1's 28 (auth, ingest, tz, migrations) + 23 BE-C2
(challenge lifecycle/join rules/status transitions, leaderboard TZ math across
two users with different `tz_offsets` on the same UTC day, deterministic tie
breaks, `as_of` cutoff, idempotent re-ingest, lazy time-driven propagation,
migration 0002 up/down).

### Curl smoke

```bash
bash scripts/smoke_challenges.sh
```

## BE-C3 — Invites (join codes) + FCM push

### Endpoints

| Method | Path | Body (JSON) | Notes |
|---|---|---|---|
| POST | `/challenges/{id}/invites` | `{ttl_hours?=168}` | 201 → `{challenge_id, code, expires_at, deep_link}`; creator/participant only (403 otherwise); 409 after ended; ttl 1..720h |
| POST | `/challenges/{id}/join?code=...` | — | invite-only challenges require a valid code; expired/unknown/wrong-challenge code → 403; challenge full (`max_participants`) → 409; ended → 409 |
| POST | `/users/me/fcm-token` | `{token, platform="android"}` | 200 → `{status:"ok", token, platform, registered_at}`; upserts on `devices` (key user_id+token) |

`POST /challenges` also accepts an optional `max_participants` (2..1000, includes
the creator; null = unlimited) — the "not-full" check every join path enforces.

### Invite semantics

- Invite v1 = random 8-char join code from an unambiguous alphabet
  (`A-HJ-NP-Z2-9`, no 0/O/1/I/L) so it can be typed from a screenshot.
- Codes are **multi-use until `expires_at`** — every friend with the link can
  join while it is valid. Expired/unknown codes are rejected at join time 403
  (reusing a dead code never works).
- Response includes the shareable deep link:
  `fitnessapp://challenges/{id}/join?code={code}`.

### FCM push

- **Dry-run is the default** (`FCM_DRY_RUN=true`, `FCM_CREDENTIALS_PATH=`): the
  sender logs `FCM dry-run: token=... type=...` and never touches the network,
  and no delivery rows are written. Tests/CI never hit Firebase.
- Real mode: set `FCM_CREDENTIALS_PATH` to the Firebase service-account JSON
  and `FCM_DRY_RUN=false`; `firebase-admin` is imported lazily (add it to
  requirements when enabling).
- Events:
  - `challenge_started` — to every participant when the creator PATCHes
    status draft→active.
  - `challenge_ended` — to every participant when status →ended (results link).
  - `beat_you` — to a participant who was overtaken during an active challenge,
    detected after the overtaker's `/daily` ingest. **Throttled to 1 per
    rolling 24h per user** (tracked in `fcm_deliveries`).
- Every notification carries `data`: `{type, challenge_id, deep_link}` so the
  Android client can route (see contract below). Push failures are logged and
  never fail the originating API call; users without registered tokens are
  skipped with a log line.

### Deep-link contract (Android / FE-C3)

| Screen | URI |
|---|---|
| Challenge detail | `fitnessapp://challenges/{id}` |
| Join via invite | `fitnessapp://challenges/{id}/join?code={code}` |
| Leaderboard / results | `fitnessapp://challenges/{id}/leaderboard` |

### Example payloads (data map sent to FCM)

```json
{"type": "challenge_started", "challenge_id": "12", "deep_link": "fitnessapp://challenges/12"}
{"type": "challenge_ended",   "challenge_id": "12", "deep_link": "fitnessapp://challenges/12/leaderboard"}
{"type": "beat_you",          "challenge_id": "12", "deep_link": "fitnessapp://challenges/12/leaderboard"}
```

Notification body for beat-you: `"{overtaker} just passed you in \"{title}\"."`

### Data model (migration 0003)

- `challenge_invites(id, challenge_id fk, code uq, created_by fk, expires_at, created_at)`
- `fcm_deliveries(id, user_id fk, type, success, message_id, error, payload json, sent_at)` — audit + throttle key
- `challenges.max_participants` (nullable int; null = unlimited)

### Tests

```bash
env -u PYTHONPATH .venv/Scripts/python.exe -m pytest -v
```

80 tests: BE-C1's 28 + BE-C2's 23 + 29 new BE-C3 (invite create/expire/
reuse-rejected/multi-use/capacity, join-with-code adds participant, FCM token
upsert, payload builders, dry-run sender, start/end on status transitions,
beat-you detection + 24h throttle, migration 0003 up/down).

### Curl smoke

```bash
bash scripts/smoke_invites_fcm.sh [BASE_URL]   # invites + fcm + beat-you
bash scripts/smoke_challenges.sh  [BASE_URL]   # BE-C2 regression
```

## Handoff (for tester / FE-C3)

- Repo: git-initialized in this workspace, branch `be-c3/invites-fcm`;
  parents: `81218b2` (BE-C2, branch be-c2/challenges) → `a6aa230` (BE-C1).
- Endpoints, payloads and the Android deep-link contract are documented
  above. Alembic migrations `0001_initial` + `0002_challenges` +
  `0003_invites_fcm` (up/down verified on SQLite and Postgres).
- Docker Compose: `docker compose up --build` runs `alembic upgrade head`
  then uvicorn — invite + fcm tables come from migration 0003.
- FCM is dry-run by default; enable real pushes with `FCM_CREDENTIALS_PATH`
  + `FCM_DRY_RUN=false` (requires `firebase-admin` in requirements).
- FE-C3 notes: register the Firebase token after login via
  `POST /users/me/fcm-token`; handle deep links
  `fitnessapp://challenges/{id}[/join?code=...|/leaderboard]`; on join-with-code
  call `POST /challenges/{id}/join?code={code}` (403 expired/invalid, 409 full/ended).


