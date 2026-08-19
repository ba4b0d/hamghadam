# HamPa (هم پا)

> **Walk together. One app for every watch.**

HamPa is a modern, cross-ecosystem Android fitness app & backend. It unifies health metrics from Samsung Health, Google Fit, Pixel Watch, Nothing/CMF, and Fitbit through Android's **Health Connect**, then turns your daily steps, distance, and calories into friendly challenges.

---

## 🌟 Key Features

- 👟 **Unified Health Connect Sync:** Reads steps, distance (km), active calories (kcal), sleep, and heart rate across any watch brand.
- 🏃 **Metric & Distance Challenges:** Create and compete in **Steps**, **Distance (km)**, or **Calories (kcal)** challenges with live leaderboards.
- ⚡ **1-Tap Quick Presets:** Launch 5km Daily Runner, 10k Steps, 600 kcal Burner, or Early Bird Morning Walk in 1 tap.
- 📲 **Instagram & Telegram Story Cards:** Generate and share 9:16 branded progress story cards with 1 tap.
- 🔔 **FCM Friend Push Alerts:** Real-time push notifications for friend requests, acceptances, and leaderboard overtakes.
- 🌙 **System & Manual Dark Mode:** Rich Material3 dark charcoal design system.
- 🛡️ **Privacy & Account Compliance:** Built-in account & data deletion (`DELETE /users/me`).

---

## 🏗️ Architecture

```
[ Samsung Watch / Pixel Watch / Fitbit ]
                 │
         (Health Connect API)
                 │
         ┌───────▼────────┐
         │  HamPa Android │ (Jetpack Compose, Kotlin)
         └───────┬────────┘
                 │ (REST API / JWT)
         ┌───────▼────────┐
         │ FastAPI Server │ (Python 3.11, Docker, Postgres)
         └────────────────┘
```

---

## 🚀 Quick Start

### Android App
```bash
cd android
./gradlew assembleRelease
# APK output: android/app/build/outputs/apk/release/HamPa-v0.7.0.apk
```

### Backend (FastAPI + Postgres)
```bash
cd deploy
docker compose -f docker-compose.prod.yml up -d --build
# Endpoint: https://api.hamghadam.ba4b0d.ir/api/v1
```

---

## 📦 Releases

Latest builds and release notes are available on **[GitHub Releases](https://github.com/ba4b0d/hamghadam/releases)**.
