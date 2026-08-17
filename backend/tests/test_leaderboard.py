"""Leaderboard tests: TZ-aware local-day math, deterministic ties, as_of cutoff,
idempotent reingest.

The challenge window and all row dates are built relative to the (frozen,
see conftest) clock so the suite is deterministic in both the aligned and the
DB-1 skew windows. Let D0 = the UTC date of `now`:
  - Alice (UTC+3:30): local window [D0, D0+1]
  - Bob   (UTC-4):    local window [D0-1, D0]
The scenario is identical to the original fixed 2026-08-16/17 window, just
translated to "today".
"""

from datetime import date, datetime, timedelta, timezone

import pytest

from app.services import challenge_service
from tests.conftest import auth_headers, register_user

# DB-1: with SKEW_SIM=1 the app clock seam (challenge_service.utcnow) is
# frozen at the skew instant (tests/conftest.py); `_now` calls through the
# module attribute so the monkeypatch is seen at call time.
pytestmark = pytest.mark.usefixtures("skew_clock")

ALICE = {"email": "lb-alice@example.com", "tz_offset": 210, "display_name": "Alice"}
BOB = {"email": "lb-bob@example.com", "tz_offset": -240, "display_name": "Bob"}


def _now() -> datetime:
    return challenge_service.utcnow()


def _window(**overrides):
    """One-UTC-day challenge window ending tomorrow (relative to frozen now).

    Returns (body, D0) where D0 = UTC date of now; ends_at = (D0+1) 00:00Z is
    always in the future so creation succeeds, starts_at = D0 00:00Z is past
    so the challenge is active.
    """
    d0 = _now().date()
    body = {
        "title": "TZ Steps Battle",
        "starts_at": f"{d0.isoformat()}T00:00:00Z",
        "ends_at": f"{(d0 + timedelta(days=1)).isoformat()}T00:00:00Z",
        "metric": "steps",
    }
    body.update(overrides)
    return body, d0


def _setup(client):
    alice = register_user(client, email=ALICE["email"], tz_offset=ALICE["tz_offset"], display_name=ALICE["display_name"])
    bob = register_user(client, email=BOB["email"], tz_offset=BOB["tz_offset"], display_name=BOB["display_name"])
    body, d0 = _window()
    created = client.post(
        "/api/v1/challenges", json=body, headers=auth_headers(alice["access_token"])
    )
    assert created.status_code == 201, created.text
    cid = created.json()["id"]
    joined = client.post(f"/api/v1/challenges/{cid}/join", headers=auth_headers(bob["access_token"]))
    assert joined.status_code == 200, joined.text
    return alice, bob, cid, d0


def _post_daily(client, token, date, steps, tz_offset):
    resp = client.post(
        "/api/v1/daily",
        json={
            "date": date,
            "tz_offset": tz_offset,
            "steps": steps,
            "source_apps": ["com.samsung.health"],
        },
        headers=auth_headers(token),
    )
    assert resp.status_code in (200, 201), resp.text


def test_leaderboard_same_utc_day_different_local_dates(client):
    alice, bob, cid, d0 = _setup(client)
    as_of = (d0 + timedelta(days=1)).isoformat()

    # Both walked during the same UTC day D0; Alice's local day is D0, Bob's
    # local day is D0-1. Each reports their own local calendar date.
    _post_daily(client, alice["access_token"], d0.isoformat(), 5000, 210)
    _post_daily(client, bob["access_token"], (d0 - timedelta(days=1)).isoformat(), 8000, -240)

    resp = client.get(
        f"/api/v1/challenges/{cid}/leaderboard",
        params={"as_of": as_of},
        headers=auth_headers(alice["access_token"]),
    )
    assert resp.status_code == 200, resp.text
    board = resp.json()
    assert board["metric"] == "steps"
    assert board["as_of"] == as_of

    entries = {e["user_id"]: e for e in board["entries"]}
    # Bob's window is local [D0-1, D0]; his (D0-1)th counts.
    assert entries[bob["user"]["id"]]["total"] == 8000
    assert [(d["date"], d["value"]) for d in entries[bob["user"]["id"]]["daily"]] == [
        ((d0 - timedelta(days=1)).isoformat(), 8000),
        (d0.isoformat(), 0),
    ]
    # Alice's window is local [D0, D0+1]; her D0th counts.
    assert entries[alice["user"]["id"]]["total"] == 5000
    assert [(d["date"], d["value"]) for d in entries[alice["user"]["id"]]["daily"]] == [
        (d0.isoformat(), 5000),
        ((d0 + timedelta(days=1)).isoformat(), 0),
    ]

    # Ranked by total desc.
    ranks = [(e["rank"], e["user_id"]) for e in board["entries"]]
    assert ranks == [
        (1, bob["user"]["id"]),
        (2, alice["user"]["id"]),
    ]
    # is_me flag reflects the viewer.
    by_rank = {e["rank"]: e for e in board["entries"]}
    assert by_rank[2]["is_me"] is True
    assert by_rank[1]["is_me"] is False


def test_leaderboard_ties_broken_deterministically(client):
    alice, bob, cid, d0 = _setup(client)
    _post_daily(client, alice["access_token"], d0.isoformat(), 9000, 210)
    _post_daily(client, bob["access_token"], (d0 - timedelta(days=1)).isoformat(), 9000, -240)

    resp = client.get(
        f"/api/v1/challenges/{cid}/leaderboard",
        params={"as_of": d0.isoformat()},
        headers=auth_headers(alice["access_token"]),
    )
    board = resp.json()
    # Equal totals -> display_name asc (Alice before Bob), then user_id asc.
    assert [e["user_id"] for e in board["entries"]] == [alice["user"]["id"], bob["user"]["id"]]
    assert [e["rank"] for e in board["entries"]] == [1, 2]


def test_leaderboard_user_id_breaks_display_name_tie(client):
    # Two users with no display_name and identical totals -> lower user_id ranks first.
    u1 = register_user(client, email="tie1@example.com", tz_offset=0)
    u2 = register_user(client, email="tie2@example.com", tz_offset=0)
    body, d0 = _window()
    created = client.post(
        "/api/v1/challenges", json=body, headers=auth_headers(u1["access_token"])
    ).json()
    cid = created["id"]
    assert client.post(f"/api/v1/challenges/{cid}/join", headers=auth_headers(u2["access_token"])).status_code == 200

    date_str = d0.isoformat()
    for u in (u1, u2):
        _post_daily(client, u["access_token"], date_str, 7777, 0)

    board = client.get(
        f"/api/v1/challenges/{cid}/leaderboard",
        params={"as_of": date_str},
        headers=auth_headers(u1["access_token"]),
    ).json()
    assert [e["user_id"] for e in board["entries"]] == [u1["user"]["id"], u2["user"]["id"]]


def test_leaderboard_as_of_cutoff(client):
    alice, bob, cid, d0 = _setup(client)
    _post_daily(client, alice["access_token"], d0.isoformat(), 5000, 210)
    _post_daily(client, bob["access_token"], (d0 - timedelta(days=1)).isoformat(), 8000, -240)

    # as_of before Alice's window starts -> she has no days yet.
    resp = client.get(
        f"/api/v1/challenges/{cid}/leaderboard",
        params={"as_of": (d0 - timedelta(days=1)).isoformat()},
        headers=auth_headers(alice["access_token"]),
    )
    board = resp.json()
    entries = {e["user_id"]: e for e in board["entries"]}
    assert entries[alice["user"]["id"]]["total"] == 0
    assert entries[alice["user"]["id"]]["daily"] == []
    assert entries[bob["user"]["id"]]["total"] == 8000
    assert [(d["date"], d["value"]) for d in entries[bob["user"]["id"]]["daily"]] == [
        ((d0 - timedelta(days=1)).isoformat(), 8000)
    ]


def test_leaderboard_reingest_is_deterministic(client):
    alice, bob, cid, d0 = _setup(client)
    _post_daily(client, alice["access_token"], d0.isoformat(), 5000, 210)
    _post_daily(client, bob["access_token"], (d0 - timedelta(days=1)).isoformat(), 8000, -240)

    def totals():
        board = client.get(
            f"/api/v1/challenges/{cid}/leaderboard",
            params={"as_of": d0.isoformat()},
            headers=auth_headers(alice["access_token"]),
        ).json()
        return {e["user_id"]: e["total"] for e in board["entries"]}

    first = totals()
    assert first == {alice["user"]["id"]: 5000, bob["user"]["id"]: 8000}

    # Idempotent re-POST of the exact same payload changes nothing.
    client.post(
        "/api/v1/daily",
        json={
            "date": (d0 - timedelta(days=1)).isoformat(),
            "tz_offset": -240,
            "steps": 8000,
            "source_apps": ["com.samsung.health"],
        },
        headers=auth_headers(bob["access_token"]),
    )
    assert totals() == first

    # Bob's next local day (D0) also falls in his window.
    _post_daily(client, bob["access_token"], d0.isoformat(), 2000, -240)
    second = totals()
    assert second[bob["user"]["id"]] == 10000

    # Updating a previous day's value deterministically updates the board.
    client.post(
        "/api/v1/daily",
        json={
            "date": (d0 - timedelta(days=1)).isoformat(),
            "tz_offset": -240,
            "steps": 8500,
            "source_apps": ["com.samsung.health"],
        },
        headers=auth_headers(bob["access_token"]),
    )
    third = totals()
    assert third[bob["user"]["id"]] == 10500

    # Alice unchanged throughout; ranking is deterministic.
    assert second[alice["user"]["id"]] == 5000 == third[alice["user"]["id"]]
    assert [e["rank"] for e in client.get(
        f"/api/v1/challenges/{cid}/leaderboard",
        params={"as_of": d0.isoformat()},
        headers=auth_headers(alice["access_token"]),
    ).json()["entries"]] == [1, 2]


def test_leaderboard_default_as_of_and_others_daily_rows_ignored(client):
    alice, bob, cid, d0 = _setup(client)
    _post_daily(client, alice["access_token"], d0.isoformat(), 5000, 210)
    _post_daily(client, bob["access_token"], (d0 - timedelta(days=1)).isoformat(), 8000, -240)
    # Outside the challenge window: Alice's local (D0-1)th (before her window)
    # and Bob's local (D0+1)th (after his window) must NOT count.
    _post_daily(client, alice["access_token"], (d0 - timedelta(days=1)).isoformat(), 999999, 210)
    _post_daily(client, bob["access_token"], (d0 + timedelta(days=1)).isoformat(), 999999, -240)

    resp = client.get(
        f"/api/v1/challenges/{cid}/leaderboard", headers=auth_headers(alice["access_token"])
    )
    assert resp.status_code == 200
    entries = {e["user_id"]: e for e in resp.json()["entries"]}
    assert entries[alice["user"]["id"]]["total"] == 5000
    assert entries[bob["user"]["id"]]["total"] == 8000


def test_leaderboard_requires_auth_and_404(client):
    resp = client.get("/api/v1/challenges/1/leaderboard")
    assert resp.status_code == 401

    token = register_user(client, email="lb-ghost@example.com")["access_token"]
    resp = client.get("/api/v1/challenges/9999/leaderboard", headers=auth_headers(token))
    assert resp.status_code == 404


def test_leaderboard_default_as_of_matches_explicit_local_today(client):
    """DB-1 regression (HTTP path, repro_cross_tz.py semantics).

    The DEFAULT board must include a participant's local-today ingest. This
    is the live repro check verbatim: default-total must equal the explicit
    local-today total. Under the skew-window run (SKEW_SIM=1) the server UTC
    date is one BEHIND the Tehran local date, and the old server-UTC default
    cutoff dropped the local-today row (default total 0 vs explicit 12345).
    """
    alice, bob, cid, d0 = _setup(client)
    local_today = date.today().isoformat()  # frozen; 2026-08-17 in both windows
    _post_daily(client, alice["access_token"], local_today, 12345, 210)
    _post_daily(client, bob["access_token"], (d0 - timedelta(days=1)).isoformat(), 8000, -240)

    default_board = client.get(
        f"/api/v1/challenges/{cid}/leaderboard", headers=auth_headers(alice["access_token"])
    ).json()
    explicit = client.get(
        f"/api/v1/challenges/{cid}/leaderboard",
        params={"as_of": local_today},
        headers=auth_headers(alice["access_token"]),
    ).json()

    alice_default = next(e for e in default_board["entries"] if e["user_id"] == alice["user"]["id"])
    alice_explicit = next(e for e in explicit["entries"] if e["user_id"] == alice["user"]["id"])
    # Latest per-participant cutoff is Alice's (UTC+3:30) local today, and it
    # equals what she explicitly asked for.
    assert default_board["as_of"] == local_today
    assert alice_default["total"] == 12345
    assert alice_default["total"] == alice_explicit["total"]
