# HamGhadam (همقدم) — Walk Together

All-in-one Android fitness app: aggregates steps from **all watch ecosystems** (Samsung Health, Google Fit/Pixel, Nothing/CMF, Fitbit) via **Health Connect**, with **friend challenges**, leaderboards, and push notifications.

> همقدم — "walking in step together"

## Repo layout

| Path | What |
|------|------|
| `android/` | Android app (Kotlin / Jetpack Compose) — Health Connect read + daily sync, dashboard, challenges UI, FCM push, deep links |
| `backend/` | FastAPI + Postgres backend (Docker) — auth (JWT), daily ingest API, challenges CRUD + leaderboards, invites, FCM push service |
| `deploy/` | Production deployment bundle + guide (RP5 + Nginx Proxy Manager) |
| `branding/` | HamGhadam logo (SVG), brand palette + usage rules |
| `secrets/` | **Never committed.** Firebase service account + google-services.json live here locally (gitignored) |

## Stack decisions (v1)

- **Android-only** — HealthKit/Apple later; Health Connect is the single aggregation hub (Google Fit APIs sunset end of 2026; Fitbit Web API → Google Health API)
- **FastAPI + Postgres**, dockerized; JWT auth; SQLAlchemy 2 + Alembic migrations
- **Steps-only challenges** in v1 — metric-extensible schema (`sleep_seconds`, `avg_hr` planned)
- **Free launch** — `users.premium` flag, no billing code yet
- "Day" = each user's local day (client sends local date + UTC offset); anti-cheat v1 = Health Connect-synced data only

## Public endpoint (live)

```
https://api.hamghadam.ba4b0d.ir/api/v1
```

Backend runs on a Raspberry Pi 5 (Docker Compose, postgres:16-alpine + python:3.11-slim), behind Nginx Proxy Manager + Let's Encrypt. See `deploy/DEPLOY.md` for the full runbook and NPM config.

## Repo hygiene

- `secrets/` is gitignored — Firebase private keys must never be committed
- Anything untracked-but-present on disk (e.g. `google-services.json`, `.env`) is generated or operator-supplied at build/deploy time