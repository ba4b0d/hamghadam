#!/usr/bin/env bash
# Curl smoke test for BE-C2 challenges: create -> join -> ingest -> leaderboard -> status end.
# Usage: bash scripts/smoke_challenges.sh [BASE_URL]   (default http://localhost:8000)
set -euo pipefail

BASE="${1:-http://localhost:8000}"
API="$BASE/api/v1"

# Local calendar dates for two users on the same UTC day:
# Alice is UTC+3:30 (tz_offset=210), Bob is UTC-4 (tz_offset=-240).
ALICE_DATE=$(python -c "from datetime import datetime,timedelta,timezone; print((datetime.now(timezone.utc)+timedelta(minutes=210)).date())")
BOB_DATE=$(python -c "from datetime import datetime,timedelta,timezone; print((datetime.now(timezone.utc)+timedelta(minutes=-240)).date())")
START=$(python -c "from datetime import datetime,timedelta,timezone; print((datetime.now(timezone.utc)-timedelta(days=1)).strftime('%Y-%m-%dT00:00:00Z'))")
END=$(python -c "from datetime import datetime,timedelta,timezone; print((datetime.now(timezone.utc)+timedelta(days=1)).strftime('%Y-%m-%dT00:00:00Z'))")
AS_OF=$(python -c "from datetime import datetime,timedelta,timezone; print((datetime.now(timezone.utc)+timedelta(days=1)).date())")

TS=$(date +%s)
EMAIL_A="alice-${TS}@example.com"
EMAIL_B="bob-${TS}@example.com"
PASS="password123"

echo "== 1/8 register Alice (creator) + Bob =="
REG_A=$(curl -s -w '\n%{http_code}' -X POST "$API/auth/register" \
  -H 'Content-Type: application/json' \
  -d "{\"email\":\"$EMAIL_A\",\"password\":\"$PASS\",\"display_name\":\"Alice\",\"tz_offset\":210}")
CODE=$(echo "$REG_A" | tail -1); [ "$CODE" = "201" ] || { echo "FAIL register Alice: $REG_A"; exit 1; }
TOKEN_A=$(echo "$REG_A" | head -1 | python -c "import sys,json; print(json.load(sys.stdin)['access_token'])")

REG_B=$(curl -s -w '\n%{http_code}' -X POST "$API/auth/register" \
  -H 'Content-Type: application/json' \
  -d "{\"email\":\"$EMAIL_B\",\"password\":\"$PASS\",\"display_name\":\"Bob\",\"tz_offset\":-240}")
CODE=$(echo "$REG_B" | tail -1); [ "$CODE" = "201" ] || { echo "FAIL register Bob: $REG_B"; exit 1; }
TOKEN_B=$(echo "$REG_B" | head -1 | python -c "import sys,json; print(json.load(sys.stdin)['access_token'])")
echo "ok ($EMAIL_A, $EMAIL_B)"

echo "== 2/8 create challenge (started yesterday, ends tomorrow) =="
CREATE=$(curl -s -w '\n%{http_code}' -X POST "$API/challenges" \
  -H "Authorization: Bearer $TOKEN_A" -H 'Content-Type: application/json' \
  -d "{\"title\":\"Smoke Steps Battle\",\"starts_at\":\"$START\",\"ends_at\":\"$END\",\"metric\":\"steps\",\"invite_only\":false}")
CODE=$(echo "$CREATE" | tail -1); [ "$CODE" = "201" ] || { echo "FAIL create: $CREATE"; exit 1; }
CID=$(echo "$CREATE" | head -1 | python -c "import sys,json; print(json.load(sys.stdin)['id'])")
STATUS=$(echo "$CREATE" | head -1 | python -c "import sys,json; print(json.load(sys.stdin)['status'])")
[ "$STATUS" = "active" ] || { echo "FAIL expected active (started in past), got $STATUS"; exit 1; }
echo "challenge id=$CID status=$STATUS"

echo "== 3/8 GET /challenges (mine) -> creator sees it =="
MINE=$(curl -s -w '\n%{http_code}' "$API/challenges" -H "Authorization: Bearer $TOKEN_A")
CODE=$(echo "$MINE" | tail -1); [ "$CODE" = "200" ] || { echo "FAIL list mine: $MINE"; exit 1; }
COUNT=$(echo "$MINE" | head -1 | python -c "import sys,json; print(len(json.load(sys.stdin)))")
[ "$COUNT" = "1" ] || { echo "FAIL expected 1 challenge, got $COUNT"; exit 1; }
echo "ok ($COUNT challenge)"

echo "== 4/8 Bob joins =="
JOIN=$(curl -s -w '\n%{http_code}' -X POST "$API/challenges/$CID/join" -H "Authorization: Bearer $TOKEN_B")
CODE=$(echo "$JOIN" | tail -1); [ "$CODE" = "200" ] || { echo "FAIL join: $JOIN"; exit 1; }
N=$(echo "$JOIN" | head -1 | python -c "import sys,json; print(len(json.load(sys.stdin)['participants']))")
[ "$N" = "2" ] || { echo "FAIL expected 2 participants, got $N"; exit 1; }
echo "ok ($N participants)"

echo "== 5/8 double-join rejected 409 =="
DUP=$(curl -s -w '\n%{http_code}' -X POST "$API/challenges/$CID/join" -H "Authorization: Bearer $TOKEN_B")
CODE=$(echo "$DUP" | tail -1); [ "$CODE" = "409" ] || { echo "FAIL double join: $DUP"; exit 1; }
echo "ok (409)"

echo "== 6/8 both ingest steps for their own local day =="
DAILY_A=$(curl -s -w '\n%{http_code}' -X POST "$API/daily" \
  -H "Authorization: Bearer $TOKEN_A" -H 'Content-Type: application/json' \
  -d "{\"date\":\"$ALICE_DATE\",\"tz_offset\":210,\"steps\":12345,\"source_apps\":[\"com.samsung.health\"]}")
CODE=$(echo "$DAILY_A" | tail -1); [ "$CODE" = "201" ] || { echo "FAIL daily Alice: $DAILY_A"; exit 1; }
DAILY_B=$(curl -s -w '\n%{http_code}' -X POST "$API/daily" \
  -H "Authorization: Bearer $TOKEN_B" -H 'Content-Type: application/json' \
  -d "{\"date\":\"$BOB_DATE\",\"tz_offset\":-240,\"steps\":9876,\"source_apps\":[\"com.google.android.apps.fitness\"]}")
CODE=$(echo "$DAILY_B" | tail -1); [ "$CODE" = "201" ] || { echo "FAIL daily Bob: $DAILY_B"; exit 1; }
echo "Alice local day=$ALICE_DATE steps=12345 | Bob local day=$BOB_DATE steps=9876"

echo "== 7/8 leaderboard ranked (Alice 12345 > Bob 9876) =="
LB=$(curl -s -w '\n%{http_code}' "$API/challenges/$CID/leaderboard?as_of=$AS_OF" -H "Authorization: Bearer $TOKEN_A")
CODE=$(echo "$LB" | tail -1); [ "$CODE" = "200" ] || { echo "FAIL leaderboard: $LB"; exit 1; }
echo "$LB" | head -1 | python -c "
import sys, json
board = json.load(sys.stdin)
entries = board['entries']
assert len(entries) == 2, entries
assert entries[0]['total'] == 12345, entries
assert entries[1]['total'] == 9876, entries
assert entries[0]['rank'] == 1 and entries[1]['rank'] == 2, entries
assert sum(1 for e in entries if e['is_me']) == 1
print('ok: ranks=%s totals=%s as_of=%s' % ([e['rank'] for e in entries], [e['total'] for e in entries], board['as_of']))
"

echo "== 8/8 creator ends challenge -> join now rejected 409 =="
ENDED=$(curl -s -w '\n%{http_code}' -X PATCH "$API/challenges/$CID/status" \
  -H "Authorization: Bearer $TOKEN_A" -H 'Content-Type: application/json' \
  -d '{"status":"ended"}')
CODE=$(echo "$ENDED" | tail -1); [ "$CODE" = "200" ] || { echo "FAIL status end: $ENDED"; exit 1; }
LATE=$(curl -s -w '\n%{http_code}' -X POST "$API/challenges/$CID/join" -H "Authorization: Bearer $TOKEN_B")
CODE=$(echo "$LATE" | tail -1); [ "$CODE" = "409" ] || { echo "FAIL late join: $LATE"; exit 1; }
echo "ok (409 on join after end)"

echo ""
echo "SMOKE OK: create -> list -> join -> ingest (two local days) -> leaderboard -> end ($EMAIL_A)"
