# HamGhadam API Deployment Guide (RP5 + Nginx Proxy Manager)

Deploys the HamGhadam FastAPI backend to Raspberry Pi 5 (`192.168.100.51`) on host port **8008**, then routes a public domain to it via Nginx Proxy Manager (NPM).

> Port note: RP5 port 3000 is occupied (ntopng) — the API publishes on **8008** (container 8000). NPM proxies the domain to `192.168.100.51:8008`.

---

## 3-Command Pi Deployment

### Step 1: Copy Code and Secrets to RP5
From Windows / Git Bash on host:
```bash
scp -r "C:/Users/barba/HamGhadam" pi@192.168.100.51:~/hamghadam
```

### Step 2: Configure Production Environment
SSH into Pi and set up environment:
```bash
ssh pi@192.168.100.51
cd ~/hamghadam/deploy
cp .env.prod.example .env
# Edit .env to set SECRET_KEY (e.g. openssl rand -hex 32) and POSTGRES_PASSWORD
nano .env
```

### Step 3: Launch Docker Compose Stack
```bash
cd ~/hamghadam/deploy
docker compose -f docker-compose.prod.yml up -d --build
```

---

## Verification Commands

### 1. Healthcheck
```bash
curl http://localhost:8008/healthz
# Expected output: {"status":"ok",...}
```

### 2. Verify FCM Real Push from Pi
```bash
docker compose -f docker-compose.prod.yml exec api python scripts/send_test_push.py <ANDROID_DEVICE_TOKEN>
# Expected output: SendResult(success=True, message_id="projects/hamghadam-6b25d/messages/...")
```

---

## Nginx Proxy Manager (NPM) — Public HTTPS Routing

The operator is adding a new domain in NPM. Configure the proxy host:

| NPM field | Value |
|-----------|-------|
| Domain | **api.hamghadam.ba4b0d.ir** |
| Scheme | http |
| Forward Host / IP | 192.168.100.51 |
| Forward Port | **8008** |
| SSL | ON — request a new Let's Encrypt certificate, force HTTPS |

- NPM must be on a host that can reach the RP5 (same LAN/subnet). It already routes other Pi services today.
- No router port-forward is needed for NPM → Pi if NPM is on the same LAN; the public DNS record (A/AAAA to the NPM host IP, or CNAME to a hostname) is what exposes NPM to the internet.
- Verify: `curl https://api.hamghadam.ba4b0d.ir/healthz` returns `{"status":"ok",...}`.

## Release Android BuildConfig

Bake `https://api.hamghadam.ba4b0d.ir/api/v1` into the Android release BuildConfig (replaces the `BACKEND_URL` placeholder in UI-POLISH t_0b521102).
