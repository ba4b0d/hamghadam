#!/usr/bin/env bash
# FE-C3 end-to-end challenge flow against a live BE-C3 stack.
# Usage: bash scripts/e2e_challenges.sh [BASE_URL]   (default http://localhost:8001/api/v1)
#
# Covers: register 2 users -> create challenge -> invite code -> join via code
# -> ingest steps for both -> leaderboard shows both -> FCM token registration.
set -euo pipefail

BASE="${1:-http://localhost:8001/api/v1}"
NOW="$(date +%s)"
EMAIL_A="e2e_a_${NOW}@example.com"
EMAIL_B="e2e_b_${NOW}@example.com"
PASS="passw0rd-${NOW}"
STAMP="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
# Starts in the future so the challenge is created as draft and the
# draft->active PATCH transition (which also fires FCM challenge_started)
# is exercised. Window still covers today's daily ingest.
START_ISO="$(date -u -d '+1 hour' +%Y-%m-%dT%H:%M:%SZ 2>/dev/null || date -u -v+1H +%Y-%m-%dT%H:%M:%SZ)"
END_ISO="$(date -u -d '+3 days 23:59' +%Y-%m-%dT%H:%M:%SZ 2>/dev/null || date -u -v+3d +%Y-%m-%dT23:59:59Z)"
TODAY="$(date -u +%Y-%m-%d)"

log() { echo "[e2e] $*"; }
jqget() { python -c "import sys,json;print(json.load(sys.stdin)$1)"; }

log "base: $BASE"

log "registering user A ($EMAIL_A)"
RESP_A=$(curl -sS -X POST "$BASE/auth/register" -H 'Content-Type: application/json' \
  -d "{\"email\":\"$EMAIL_A\",\"password\":\"$PASS\",\"display_name\":\"Alice E2E\",\"tz_offset\":210}")
TOKEN_A=$(echo "$RESP_A" | jqget "['access_token']")
ID_A=$(echo "$RESP_A" | jqget "['user']['id']")
log "user A id=$ID_A"

log "registering user B ($EMAIL_B)"
RESP_B=$(curl -sS -X POST "$BASE/auth/register" -H 'Content-Type: application/json' \
  -d "{\"email\":\"$EMAIL_B\",\"password\":\"$PASS\",\"display_name\":\"Bob E2E\",\"tz_offset\":210}")
TOKEN_B=$(echo "$RESP_B" | jqget "['access_token']")
ID_B=$(echo "$RESP_B" | jqget "['user']['id']")
log "user B id=$ID_B"

log "creating challenge (active window)"
CH=$(curl -sS -X POST "$BASE/challenges" -H "Authorization: Bearer $TOKEN_A" -H 'Content-Type: application/json' \
  -d "{\"title\":\"E2E Steps ${NOW}\",\"starts_at\":\"$START_ISO\",\"ends_at\":\"$END_ISO\",\"metric\":\"steps\"}")
CH_ID=$(echo "$CH" | jqget "['id']")
CH_STATUS=$(echo "$CH" | jqget "['status']")
log "challenge id=$CH_ID status=$CH_STATUS"

log "creator starts the challenge (draft -> active)"
curl -sS -X PATCH "$BASE/challenges/$CH_ID/status" -H "Authorization: Bearer $TOKEN_A" -H 'Content-Type: application/json' \
  -d '{"status":"active"}' | jqget "['status']"

log "creating invite"
INV=$(curl -sS -X POST "$BASE/challenges/$CH_ID/invites" -H "Authorization: Bearer $TOKEN_A" -H 'Content-Type: application/json' -d '{}')
CODE=$(echo "$INV" | jqget "['code']")
DEEP_LINK=$(echo "$INV" | jqget "['deep_link']")
log "invite code=$CODE link=$DEEP_LINK"

log "user B joins with the code"
JOIN=$(curl -sS -X POST "$BASE/challenges/$CH_ID/join?code=$CODE" -H "Authorization: Bearer $TOKEN_B" -H 'Content-Type: application/json' -d '{}')
N=$(echo "$JOIN" | python -c "import sys,json;print(len(json.load(sys.stdin)['participants']))")
log "participants after join: $N"

log "ingesting steps for both users on $TODAY"
curl -sS -X POST "$BASE/daily" -H "Authorization: Bearer $TOKEN_A" -H 'Content-Type: application/json' \
  -d "{\"date\":\"$TODAY\",\"tz_offset\":210,\"steps\":7530,\"source_apps\":[\"com.example.e2e\"],\"source\":\"health_connect\"}" >/dev/null
curl -sS -X POST "$BASE/daily" -H "Authorization: Bearer $TOKEN_B" -H 'Content-Type: application/json' \
  -d "{\"date\":\"$TODAY\",\"tz_offset\":210,\"steps\":9400,\"source_apps\":[\"com.example.e2e\"],\"source\":\"health_connect\"}" >/dev/null

log "leaderboard (expect Bob 9400 > Alice 7530)"
BOARD=$(curl -sS "$BASE/challenges/$CH_ID/leaderboard" -H "Authorization: Bearer $TOKEN_A")
echo "$BOARD" | python -c "
import sys, json
b = json.load(sys.stdin)
for e in b['entries']:
    print(f\"  rank {e['rank']} {e['display_name']} total={e['total']} is_me={e['is_me']}\")
assert len(b['entries']) == 2, 'expected 2 entries'
names = [e['display_name'] for e in b['entries']]
assert 'Alice E2E' in names and 'Bob E2E' in names, names
assert b['entries'][0]['display_name'] == 'Bob E2E', 'Bob should lead'
print('LEADERBOARD OK')
"

log "FCM token registration (dev token) + device lookup"
curl -sS -X POST "$BASE/users/me/fcm-token" -H "Authorization: Bearer $TOKEN_A" -H 'Content-Type: application/json' \
  -d "{\"token\":\"dev:e2e-$NOW\",\"platform\":\"android\"}" | jqget "['status']"
curl -sS "$BASE/users/me/devices" -H "Authorization: Bearer $TOKEN_A" | python -c "
import sys, json
devs = json.load(sys.stdin)
tokens = [d['device_token'] for d in devs]
assert any('dev:e2e-' in t for t in tokens), tokens
print('FCM TOKEN REGISTERED OK')
"

log "ALL E2E CHECKS PASSED — challenge $CH_ID code $CODE"
echo "CH_ID=$CH_ID"
echo "CODE=$CODE"