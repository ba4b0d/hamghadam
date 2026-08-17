"""Leaderboard tests: TZ-aware local-day math, deterministic ties, as_of cutoff,
idempotent reingest."""

from tests.conftest import auth_headers, register_user

CHALLENGE = {
    "title": "TZ Steps Battle",
    "starts_at": "2026-08-16T00:00:00Z",  # UTC window: Aug 16 00:00Z .. Aug 17 00:00Z
    "ends_at": "2026-08-17T00:00:00Z",
    "metric": "steps",
}

# Same UTC day 2026-08-16, different local calendar dates:
# - Alice (UTC+3:30): local date 2026-08-16
# - Bob (UTC-4): local date 2026-08-15
ALICE = {"email": "lb-alice@example.com", "tz_offset": 210, "display_name": "Alice"}
BOB = {"email": "lb-bob@example.com", "tz_offset": -240, "display_name": "Bob"}


def _setup(client):
    alice = register_user(client, email=ALICE["email"], tz_offset=ALICE["tz_offset"], display_name=ALICE["display_name"])
    bob = register_user(client, email=BOB["email"], tz_offset=BOB["tz_offset"], display_name=BOB["display_name"])
    created = client.post(
        "/api/v1/challenges", json=CHALLENGE, headers=auth_headers(alice["access_token"])
    )
    assert created.status_code == 201, created.text
    cid = created.json()["id"]
    joined = client.post(f"/api/v1/challenges/{cid}/join", headers=auth_headers(bob["access_token"]))
    assert joined.status_code == 200, joined.text
    return alice, bob, cid


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
    alice, bob, cid = _setup(client)

    # Both walked during the same UTC day 2026-08-16; Alice's local day is the
    # 16th, Bob's is the 15th. Each reports their own local calendar date.
    _post_daily(client, alice["access_token"], "2026-08-16", 5000, 210)
    _post_daily(client, bob["access_token"], "2026-08-15", 8000, -240)

    resp = client.get(
        f"/api/v1/challenges/{cid}/leaderboard",
        params={"as_of": "2026-08-17"},
        headers=auth_headers(alice["access_token"]),
    )
    assert resp.status_code == 200, resp.text
    board = resp.json()
    assert board["metric"] == "steps"
    assert board["as_of"] == "2026-08-17"

    entries = {e["user_id"]: e for e in board["entries"]}
    # Bob's window is local [2026-08-15, 2026-08-16]; his 15th counts.
    assert entries[bob["user"]["id"]]["total"] == 8000
    assert [(d["date"], d["value"]) for d in entries[bob["user"]["id"]]["daily"]] == [
        ("2026-08-15", 8000),
        ("2026-08-16", 0),
    ]
    # Alice's window is local [2026-08-16, 2026-08-17]; her 16th counts.
    assert entries[alice["user"]["id"]]["total"] == 5000
    assert [(d["date"], d["value"]) for d in entries[alice["user"]["id"]]["daily"]] == [
        ("2026-08-16", 5000),
        ("2026-08-17", 0),
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
    alice, bob, cid = _setup(client)
    _post_daily(client, alice["access_token"], "2026-08-16", 9000, 210)
    _post_daily(client, bob["access_token"], "2026-08-15", 9000, -240)

    resp = client.get(
        f"/api/v1/challenges/{cid}/leaderboard",
        params={"as_of": "2026-08-16"},
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
    created = client.post(
        "/api/v1/challenges",
        json={**CHALLENGE, "starts_at": "2026-08-16T00:00:00Z", "ends_at": "2026-08-17T00:00:00Z"},
        headers=auth_headers(u1["access_token"]),
    ).json()
    cid = created["id"]
    assert client.post(f"/api/v1/challenges/{cid}/join", headers=auth_headers(u2["access_token"])).status_code == 200

    for u in (u1, u2):
        _post_daily(client, u["access_token"], "2026-08-16", 7777, 0)

    board = client.get(
        f"/api/v1/challenges/{cid}/leaderboard",
        params={"as_of": "2026-08-16"},
        headers=auth_headers(u1["access_token"]),
    ).json()
    assert [e["user_id"] for e in board["entries"]] == [u1["user"]["id"], u2["user"]["id"]]


def test_leaderboard_as_of_cutoff(client):
    alice, bob, cid = _setup(client)
    _post_daily(client, alice["access_token"], "2026-08-16", 5000, 210)
    _post_daily(client, bob["access_token"], "2026-08-15", 8000, -240)

    # as_of before Alice's window starts -> she has no days yet.
    resp = client.get(
        f"/api/v1/challenges/{cid}/leaderboard",
        params={"as_of": "2026-08-15"},
        headers=auth_headers(alice["access_token"]),
    )
    board = resp.json()
    entries = {e["user_id"]: e for e in board["entries"]}
    assert entries[alice["user"]["id"]]["total"] == 0
    assert entries[alice["user"]["id"]]["daily"] == []
    assert entries[bob["user"]["id"]]["total"] == 8000
    assert [(d["date"], d["value"]) for d in entries[bob["user"]["id"]]["daily"]] == [
        ("2026-08-15", 8000)
    ]


def test_leaderboard_reingest_is_deterministic(client):
    alice, bob, cid = _setup(client)
    _post_daily(client, alice["access_token"], "2026-08-16", 5000, 210)
    _post_daily(client, bob["access_token"], "2026-08-15", 8000, -240)

    def totals():
        board = client.get(
            f"/api/v1/challenges/{cid}/leaderboard",
            params={"as_of": "2026-08-16"},
            headers=auth_headers(alice["access_token"]),
        ).json()
        return {e["user_id"]: e["total"] for e in board["entries"]}

    first = totals()
    assert first == {alice["user"]["id"]: 5000, bob["user"]["id"]: 8000}

    # Idempotent re-POST of the exact same payload changes nothing.
    client.post(
        "/api/v1/daily",
        json={"date": "2026-08-15", "tz_offset": -240, "steps": 8000, "source_apps": ["com.samsung.health"]},
        headers=auth_headers(bob["access_token"]),
    )
    assert totals() == first

    # Bob's next local day (2026-08-16) also falls in his window.
    _post_daily(client, bob["access_token"], "2026-08-16", 2000, -240)
    second = totals()
    assert second[bob["user"]["id"]] == 10000

    # Updating a previous day's value deterministically updates the board.
    client.post(
        "/api/v1/daily",
        json={"date": "2026-08-15", "tz_offset": -240, "steps": 8500, "source_apps": ["com.samsung.health"]},
        headers=auth_headers(bob["access_token"]),
    )
    third = totals()
    assert third[bob["user"]["id"]] == 10500

    # Alice unchanged throughout; ranking is deterministic.
    assert second[alice["user"]["id"]] == 5000 == third[alice["user"]["id"]]
    assert [e["rank"] for e in client.get(
        f"/api/v1/challenges/{cid}/leaderboard",
        params={"as_of": "2026-08-16"},
        headers=auth_headers(alice["access_token"]),
    ).json()["entries"]] == [1, 2]


def test_leaderboard_default_as_of_and_others_daily_rows_ignored(client):
    alice, bob, cid = _setup(client)
    _post_daily(client, alice["access_token"], "2026-08-16", 5000, 210)
    _post_daily(client, bob["access_token"], "2026-08-15", 8000, -240)
    # Outside the challenge window: Alice's local 15th (before her window) and
    # Bob's local 17th (after his window) must NOT count.
    _post_daily(client, alice["access_token"], "2026-08-15", 999999, 210)
    _post_daily(client, bob["access_token"], "2026-08-17", 999999, -240)

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
