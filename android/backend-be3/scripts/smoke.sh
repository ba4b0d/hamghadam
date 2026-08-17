#!/usr/bin/env bash
# Curl smoke test for BE-C1: register -> login -> POST /daily -> GET back same daily.
# Usage: bash scripts/smoke.sh [BASE_URL]   (default http://localhost:8000)
set -euo pipefail

BASE="${1:-http://localhost:8000}"
API="$BASE/api/v1"

EMAIL="smoke-$(date +%s)@example.com"
PASS="password123"

echo "== 1/5 register =="
REG=$(curl -s -w '\n%{http_code}' -X POST "$API/auth/register" \
  -H 'Content-Type: application/json' \
  -d "{\"email\":\"$EMAIL\",\"password\":\"$PASS\",\"display_name\":\"Smoke\"}")
REG_CODE=$(echo "$REG" | tail -1)
echo "http $REG_CODE"
[ "$REG_CODE" = "201" ] || { echo "FAIL register: $REG"; exit 1; }

TOKEN=$(echo "$REG" | head -1 | python -c "import sys,json; print(json.load(sys.stdin)['access_token'])")
echo "token len: ${#TOKEN}"

echo "== 2/5 login =="
LOGIN=$(curl -s -w '\n%{http_code}' -X POST "$API/auth/login" \
  -H 'Content-Type: application/json' \
  -d "{\"email\":\"$EMAIL\",\"password\":\"$PASS\"}")
LOGIN_CODE=$(echo "$LOGIN" | tail -1)
echo "http $LOGIN_CODE"
[ "$LOGIN_CODE" = "200" ] || { echo "FAIL login: $LOGIN"; exit 1; }

echo "== 3/5 register device =="
DEV=$(curl -s -w '\n%{http_code}' -X POST "$API/users/me/device" \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"device_token":"smoke-fcm-token-1","kind":"android","model":"Pixel 8"}')
DEV_CODE=$(echo "$DEV" | tail -1)
echo "http $DEV_CODE"
[ "$DEV_CODE" = "201" ] || { echo "FAIL device: $DEV"; exit 1; }

echo "== 4/5 POST /daily (ingest) =="
TODAY=$(date +%F)
DAILY=$(curl -s -w '\n%{http_code}' -X POST "$API/daily" \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d "{\"date\":\"$TODAY\",\"tz_offset\":210,\"steps\":9876,\"sleep_seconds\":28800,\"avg_hr\":68.5,\"source_apps\":[\"com.samsung.health\"],\"source\":\"health_connect\"}")
DAILY_CODE=$(echo "$DAILY" | tail -1)
echo "http $DAILY_CODE"
[ "$DAILY_CODE" = "201" ] || { echo "FAIL daily: $DAILY"; exit 1; }

echo "== 5/5 GET /daily?date= -> same day back =="
GOT=$(curl -s -w '\n%{http_code}' "$API/daily?date=$TODAY" \
  -H "Authorization: Bearer $TOKEN")
GOT_CODE=$(echo "$GOT" | tail -1)
echo "http $GOT_CODE"
[ "$GOT_CODE" = "200" ] || { echo "FAIL get daily: $GOT"; exit 1; }

STEPS=$(echo "$GOT" | head -1 | python -c "import sys,json; print(json.load(sys.stdin)['steps'])")
echo "steps read back: $STEPS"
[ "$STEPS" = "9876" ] || { echo "FAIL steps mismatch"; exit 1; }

echo ""
echo "SMOKE OK: register -> login -> device -> ingest -> read-back all passed ($EMAIL)"
