"""Live HTTP verification of the DB-1 fix inside the skew instant.

Starts the REAL FastAPI app on 127.0.0.1:8001 (uvicorn, real sockets) with the
app clock pinned to the DB-1 skew instant (UTC 2026-08-16 22:30 -> server UTC
date 2026-08-16, Tehran UTC+3:30 local date 2026-08-17), then runs the same
scenario as the TEST-E2E `repro_cross_tz.py` against it:

  - register a tz=+210 user
  - create an active challenge
  - ingest 12345 steps dated LOCAL today (2026-08-17 in the skew instant)
  - GET leaderboard with DEFAULT as_of and with explicit as_of
  - assert default total == explicit total and board as_of == local today

This is exactly the live curl repro from the DB-1 card, run in the daily
00:00-03:30 Tehran window where the old server-UTC cutoff dropped the row.

Run (foreground):
    DATABASE_URL=sqlite:///./live_skew.db env -u PYTHONPATH \
        .venv/Scripts/python.exe scripts/live_skew_repro.py
Exit 0 only if the DB-1 assertion holds (i.e. the fix works in the skew).
"""
import datetime as dt
import json
import os
import sys
import threading
import time
import urllib.error
import urllib.request
from datetime import datetime, timezone

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

import uvicorn  # noqa: E402

SKEW_NOW = datetime(2026, 8, 16, 22, 30, 0, tzinfo=timezone.utc)

BASE = "http://127.0.0.1:8001"
API = BASE + "/api/v1"


def call(method, path, token=None, body=None):
    data = json.dumps(body).encode() if body is not None else None
    req = urllib.request.Request(API + path, data=data, method=method)
    req.add_header("Content-Type", "application/json")
    if token:
        req.add_header("Authorization", "Bearer " + token)
    try:
        with urllib.request.urlopen(req, timeout=10) as r:
            return r.status, json.loads(r.read().decode())
    except urllib.error.HTTPError as e:  # noqa: PERF203
        return e.code, json.loads(e.read().decode())


def main() -> int:
    # Patch the clock seam BEFORE serving so every route sees the skew instant.
    import app.api.v1.challenges as challenges_mod
    import app.api.v1.daily as daily_mod
    import app.services.challenge_service as service_mod
    from app.main import app

    service_mod.utcnow = lambda: SKEW_NOW
    challenges_mod.utcnow = service_mod.utcnow
    daily_mod.utcnow = service_mod.utcnow

    config = uvicorn.Config(app, host="127.0.0.1", port=8001, log_level="warning", access_log=False)
    server = uvicorn.Server(config)
    thread = threading.Thread(target=server.run, daemon=True)
    thread.start()

    # Wait for the socket.
    for _ in range(50):
        try:
            with urllib.request.urlopen(BASE + "/healthz", timeout=2) as r:
                if r.status == 200:
                    break
        except Exception:  # noqa: BLE001
            time.sleep(0.2)
    else:
        print("FAIL: server never became ready")
        return 2

    now_utc = datetime.now(timezone.utc)
    ts = int(time.time())

    code, reg = call("POST", "/auth/register", body={
        "email": f"live-skew-{ts}@example.com", "password": "password123",
        "display_name": "Iman", "tz_offset": 210})
    assert code == 200 or code == 201, reg
    tok = reg["access_token"]
    print("registered tz=+210 user id", reg["user"]["id"])

    start = (now_utc - dt.timedelta(days=2)).strftime("%Y-%m-%dT00:00:00Z")
    end = (now_utc + dt.timedelta(days=2)).strftime("%Y-%m-%dT00:00:00Z")
    code, ch = call("POST", "/challenges", tok, {
        "title": f"Live Skew {ts}", "starts_at": start, "ends_at": end,
        "metric": "steps", "invite_only": False})
    assert code == 201, ch
    cid = ch["id"]

    # Ingest dated the participant's LOCAL today in the skew instant (2026-08-17).
    local_today = (SKEW_NOW + dt.timedelta(minutes=210)).date()
    code, d = call("POST", "/daily", tok, {
        "date": local_today.isoformat(), "tz_offset": 210, "steps": 12345,
        "source_apps": ["com.samsung.health"], "source": "health_connect"})
    print(f"ingested {local_today.isoformat()} 12345 steps -> http {code}")

    _, lb_def = call("GET", f"/challenges/{cid}/leaderboard", tok)
    _, lb_exp = call("GET", f"/challenges/{cid}/leaderboard?as_of={local_today.isoformat()}", tok)

    total_def = next(e["total"] for e in lb_def["entries"] if e["user_id"] == reg["user"]["id"])
    total_exp = next(e["total"] for e in lb_exp["entries"] if e["user_id"] == reg["user"]["id"])

    print("server clock pinned to:", SKEW_NOW.isoformat(), "(UTC date", SKEW_NOW.date(), ")")
    print("participant local today (=ingest date):", local_today)
    print("DEFAULT as_of board:", lb_def["as_of"], "| my total =", total_def)
    print("EXPLICIT as_of board:", lb_exp["as_of"], "| my total =", total_exp)

    ok = (
        lb_def["as_of"] == local_today.isoformat()
        and total_def == 12345.0
        and total_def == total_exp
    )
    print("\n=== DB-1 SKEW VERDICT ===")
    print("PASS: default board includes local-today ingest in the skew window" if ok
          else "FAIL: default board still drops local-today ingest in the skew window")
    return 0 if ok else 1


if __name__ == "__main__":
    import datetime as _dt

    sys.exit(main())