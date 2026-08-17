# HamGhadam API Deployment Guide (RP5 + Cloudflare Tunnel)

This guide covers deploying the HamGhadam FastAPI backend to Raspberry Pi 5 (`192.168.100.51`) and exposing it via Cloudflare Tunnel.

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
curl http://localhost:8000/healthz
# Expected output: {"status":"ok","app_env":"production"}
```

### 2. Verify FCM Real Push from Pi
```bash
docker compose -f docker-compose.prod.yml exec api python scripts/send_test_push.py <ANDROID_DEVICE_TOKEN>
# Expected output: SendResult(success=True, message_id="projects/hamghadam-6b25d/messages/...")
```

---

## Cloudflare Tunnel Setup (Public HTTPS Quickstart)

To expose `http://localhost:8000` to the internet via HTTPS without opening router ports:

### 1. Install `cloudflared` on Pi 5 (ARM64)
```bash
curl -L --output cloudflared.deb https://github.com/cloudflare/cloudflared/releases/latest/download/cloudflared-linux-arm64.deb
sudo dpkg -i cloudflared.deb
```

### 2. Authenticate and Create Tunnel
```bash
# Login to Cloudflare account (requires browser / auth link)
cloudflared tunnel login

# Create tunnel for HamGhadam
cloudflared tunnel create hamghadam-api
```

### 3. Create Configuration File (`~/.cloudflared/config.yml`)
```yaml
tunnel: <TUNNEL_UUID>
credentials-file: /home/pi/.cloudflared/<TUNNEL_UUID>.json

ingress:
  - hostname: api.yourdomain.com # Set to chosen public subdomain
    service: http://localhost:8000
  - service: http_status:404
```

### 4. Route DNS & Enable System Service
```bash
cloudflared tunnel route dns hamghadam-api api.yourdomain.com
sudo cloudflared service install
sudo systemctl start cloudflared
```

Once running, test public endpoint:
```bash
curl https://api.yourdomain.com/healthz
```
Bake this public HTTPS URL into the Android Release BuildConfig.
