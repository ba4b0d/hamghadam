#!/usr/bin/env bash
# Curl smoke test for BE-C3: invites (join codes) + FCM push wiring.
# Usage: bash scripts/smoke_invites_fcm.sh [BASE_URL]   (default http://localhost:8000)
# FCM runs in dry-run mode by default (fcm_dry_run=true) so no Firebase is contacted;
# the smoke verifies the endpoints and the beat-you overtake math, which is what
# triggers the (logged) push.
set -euo pipefail

BASE="${1:-http://localhost:8000}"
API="$BASE/api/v1"

TODAY=$(python -c "from datetime import date; print(date.today().isoformat())")
START=$(python -c "from datetime import datetime,timedelta,timezone; print((datetime.now(timezone.utc)-timedelta(days=1)).strftime('%Y-%m-%dT00:00:00Z'))")
END=$(python -c "from datetime import datetime,timedelta,timezone; print((datetime.now(timezone.utc)+timedelta(days=1)).strftime('%Y-%m-%dT00:00:00Z'))")

TS=$(date +%s)
PASS="password123"
EMAIL_A="alice-${TS}@example.com"
EMAIL_B="bob-${TS}@example.com"
EMAIL_C="carol-${TS}@example.com"

register() { # $1=email
  curl -s -w '\n%{http_code}' -X POST "$API/auth/register" \
    -H 'Content-Type: application/json' \
    -d "{\"email\":\"$1\",\"password\":\"$PASS\",\"display_name\":\"$(echo "$1" | cut -d@ -f1)\"}"
}
token_of() { echo "$1" | head -1 | python -c "import sys,json; print(json.load(sys.stdin)['access_token'])"; }
code_of() { echo "$1" | tail -1; }

echo "== 1/9 register Alice (creator), Bob, Carol =="
REG_A=$(register "$EMAIL_A"); [ "$(code_of "$REG_A")" = "201" ] || { echo "FAIL register A"; exit 1; }
REG_B=$(register "$EMAIL_B"); [ "$(code_of "$REG_B")" = "201" ] || { echo "FAIL register B"; exit 1; }
REG_C=$(register "$EMAIL_C"); [ "$(code_of "$REG_C")" = "201" ] || { echo "FAIL register C"; exit 1; }
TOKEN_A=$(token_of "$REG_A"); TOKEN_B=$(token_of "$REG_B"); TOKEN_C=$(token_of "$REG_C")
echo "ok ($EMAIL_A / $EMAIL_B / $EMAIL_C)"

echo "== 2/9 create invite-only challenge, max_participants=2 (active: started yesterday) =="
CREATE=$(curl -s -w '\n%{http_code}' -X POST "$API/challenges" \
  -H "Authorization: Bearer $TOKEN_A" -H 'Content-Type: application/json' \
  -d "{\"title\":\"Smoke Invite Battle\",\"starts_at\":\"$START\",\"ends_at\":\"$END\",\"metric\":\"steps\",\"invite_only\":true,\"max_participants\":2}")
[ "$(code_of "$CREATE")" = "201" ] || { echo "FAIL create: $CREATE"; exit 1; }
CID=$(echo "$CREATE" | head -1 | python -c "import sys,json; print(json.load(sys.stdin)['id'])")
MAXP=$(echo "$CREATE" | head -1 | python -c "import sys,json; print(json.load(sys.stdin)['max_participants'])")
[ "$MAXP" = "2" ] || { echo "FAIL max_participants=$MAXP"; exit 1; }
echo "challenge id=$CID invite_only=true max_participants=$MAXP"

echo "== 3/9 creator creates invite -> code + deep_link =="
INV=$(curl -s -w '\n%{http_code}' -X POST "$API/challenges/$CID/invites" \
  -H "Authorization: Bearer $TOKEN_A" -H 'Content-Type: application/json' -d '{}')
[ "$(code_of "$INV")" = "201" ] || { echo "FAIL invite: $INV"; exit 1; }
CODE=$(echo "$INV" | head -1 | python -c "import sys,json; print(json.load(sys.stdin)['code'])")
LINK=$(echo "$INV" | head -1 | python -c "import sys,json; print(json.load(sys.stdin)['deep_link'])")
[ "$LINK" = "fitnessapp://challenges/$CID/join?code=$CODE" ] || { echo "FAIL deep link: $LINK"; exit 1; }
echo "code=$CODE deep_link=$LINK"

echo "== 4/9 non-participant (Bob) join without code -> 403 =="
NOCODE=$(curl -s -w '\n%{http_code}' -X POST "$API/challenges/$CID/join" -H "Authorization: Bearer $TOKEN_B")
[ "$(code_of "$NOCODE")" = "403" ] || { echo "FAIL no-code join: $NOCODE"; exit 1; }
echo "ok (403)"

echo "== 5/9 Bob joins with the code -> participant added =="
JOIN=$(curl -s -w '\n%{http_code}' -X POST "$API/challenges/$CID/join?code=$CODE" -H "Authorization: Bearer $TOKEN_B")
[ "$(code_of "$JOIN")" = "200" ] || { echo "FAIL join with code: $JOIN"; exit 1; }
N=$(echo "$JOIN" | head -1 | python -c "import sys,json; print(len(json.load(sys.stdin)['participants']))")
[ "$N" = "2" ] || { echo "FAIL expected 2 participants, got $N"; exit 1; }
echo "ok ($N participants)"

echo "== 6/9 Carol join with code -> 409 (challenge full at max_participants=2) =="
FULL=$(curl -s -w '\n%{http_code}' -X POST "$API/challenges/$CID/join?code=$CODE" -H "Authorization: Bearer $TOKEN_C")
[ "$(code_of "$FULL")" = "409" ] || { echo "FAIL full join: $FULL"; exit 1; }
echo "ok (409 full)"

echo "== 7/9 register FCM tokens; beat-you overtake math (Alice 1000 -> Bob 2000) =="
curl -s -X POST "$API/users/me/fcm-token" -H "Authorization: Bearer $TOKEN_A" -H 'Content-Type: application/json' -d "{\"token\":\"smoke-token-$TS-a\"}" >/dev/null
curl -s -X POST "$API/users/me/fcm-token" -H "Authorization: Bearer $TOKEN_B" -H 'Content-Type: application/json' -d "{\"token\":\"smoke-token-$TS-b\"}" >/dev/null
DAILY_A=$(curl -s -w '\n%{http_code}' -X POST "$API/daily" -H "Authorization: Bearer $TOKEN_A" -H 'Content-Type: application/json' \
  -d "{\"date\":\"$TODAY\",\"tz_offset\":0,\"steps\":1000,\"source_apps\":[\"com.samsung.health\"]}")
[ "$(code_of "$DAILY_A")" = "201" ] || { echo "FAIL daily Alice: $DAILY_A"; exit 1; }
DAILY_B=$(curl -s -w '\n%{http_code}' -X POST "$API/daily" -H "Authorization: Bearer $TOKEN_B" -H 'Content-Type: application/json' \
  -d "{\"date\":\"$TODAY\",\"tz_offset\":0,\"steps\":500,\"source_apps\":[\"com.samsung.health\"]}")
[ "$(code_of "$DAILY_B")" = "201" ] || { echo "FAIL daily Bob: $DAILY_B"; exit 1; }
LB1=$(curl -s "$API/challenges/$CID/leaderboard" -H "Authorization: Bearer $TOKEN_A")
echo "$LB1" | python -c "
import sys, json
e = json.load(sys.stdin)['entries']
assert e[0]['total'] == 1000 and e[1]['total'] == 500, e
print('ok: before overtake totals=%s' % [x['total'] for x in e])
"
# Bob overtakes Alice -> dry-run beat-you push logged server-side.
DAILY_B2=$(curl -s -w '\n%{http_code}' -X POST "$API/daily" -H "Authorization: Bearer $TOKEN_B" -H 'Content-Type: application/json' \
  -d "{\"date\":\"$TODAY\",\"tz_offset\":0,\"steps\":2000,\"source_apps\":[\"com.samsung.health\"]}")
[ "$(code_of "$DAILY_B2")" = "200" ] || { echo "FAIL daily Bob re-ingest: $DAILY_B2"; exit 1; }
LB2=$(curl -s "$API/challenges/$CID/leaderboard" -H "Authorization: Bearer $TOKEN_A")
echo "$LB2" | python -c "
import sys, json
e = json.load(sys.stdin)['entries']
assert e[0]['total'] == 2000 and e[1]['total'] == 1000, e
print('ok: after overtake totals=%s (beat-you push logged dry-run)' % [x['total'] for x in e])
"

echo "== 8/9 status transition fires start/end pushes (dry-run) =="
DRAFT_START=$(python -c "from datetime import datetime,timedelta,timezone; print((datetime.now(timezone.utc)+timedelta(days=1)).strftime('%Y-%m-%dT%H:%M:%SZ'))")
DRAFT_END=$(python -c "from datetime import datetime,timedelta,timezone; print((datetime.now(timezone.utc)+timedelta(days=2)).strftime('%Y-%m-%dT%H:%M:%SZ'))")
DRAFT=$(curl -s -w '\n%{http_code}' -X POST "$API/challenges" \
  -H "Authorization: Bearer $TOKEN_A" -H 'Content-Type: application/json' \
  -d "{\"title\":\"Smoke Push Draft\",\"starts_at\":\"$DRAFT_START\",\"ends_at\":\"$DRAFT_END\",\"metric\":\"steps\"}")
[ "$(code_of "$DRAFT")" = "201" ] || { echo "FAIL create draft: $DRAFT"; exit 1; }
DID=$(echo "$DRAFT" | head -1 | python -c "import sys,json; print(json.load(sys.stdin)['id'])")
STARTED=$(curl -s -w '\n%{http_code}' -X PATCH "$API/challenges/$DID/status" -H "Authorization: Bearer $TOKEN_A" -H 'Content-Type: application/json' -d '{"status":"active"}')
[ "$(code_of "$STARTED")" = "200" ] || { echo "FAIL status active: $STARTED"; exit 1; }
ENDED=$(curl -s -w '\n%{http_code}' -X PATCH "$API/challenges/$DID/status" -H "Authorization: Bearer $TOKEN_A" -H 'Content-Type: application/json' -d '{"status":"ended"}')
[ "$(code_of "$ENDED")" = "200" ] || { echo "FAIL status ended: $ENDED"; exit 1; }
echo "ok (draft -> active -> ended; challenge_started + challenge_ended pushes logged dry-run)"

echo "== 9/9 invites after challenge ended -> 409 =="
LATE=$(curl -s -w '\n%{http_code}' -X PATCH "$API/challenges/$CID/status" -H "Authorization: Bearer $TOKEN_A" -H 'Content-Type: application/json' -d '{"status":"ended"}')
[ "$(code_of "$LATE")" = "200" ] || { echo "FAIL end challenge: $LATE"; exit 1; }
INV_LATE=$(curl -s -w '\n%{http_code}' -X POST "$API/challenges/$CID/invites" -H "Authorization: Bearer $TOKEN_A" -H 'Content-Type: application/json' -d '{}')
[ "$(code_of "$INV_LATE")" = "409" ] || { echo "FAIL invite after end: $INV_LATE"; exit 1; }
echo "ok (409 invite after end)"

echo ""
echo "SMOKE OK: invites (create/403/join/full) + fcm tokens + beat-you overtake + status pushes ($EMAIL_A)"
