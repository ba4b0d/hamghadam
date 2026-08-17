"""TZ handling: two users reporting the same UTC day but different local dates.

Scenario: at UTC 2026-08-16 02:00,
  - user A (tz_offset=+180, UTC+3) local time is 2026-08-16 05:00 → local date 2026-08-16
  - user B (tz_offset=-480, UTC-8) local time is 2026-08-15 18:00 → local date 2026-08-15
Each client reports its own local date + offset; the server must store and
read back per-user local days independently.
"""

from tests.conftest import auth_headers, register_user

PAYLOAD_A = {
    "date": "2026-08-16",
    "tz_offset": 180,  # UTC+3
    "steps": 9000,
    "source_apps": ["com.samsung.health"],
}
PAYLOAD_B = {
    "date": "2026-08-15",
    "tz_offset": -480,  # UTC-8
    "steps": 6000,
    "source_apps": ["com.google.android.apps.fitness"],
}


def test_same_utc_day_different_local_dates(client):
    token_a = register_user(client, email="tz-a@example.com")["access_token"]
    token_b = register_user(client, email="tz-b@example.com")["access_token"]
    headers_a = auth_headers(token_a)
    headers_b = auth_headers(token_b)

    assert client.post("/api/v1/daily", json=PAYLOAD_A, headers=headers_a).status_code == 201
    assert client.post("/api/v1/daily", json=PAYLOAD_B, headers=headers_b).status_code == 201

    # A's local date: 2026-08-16
    got_a = client.get("/api/v1/daily", params={"date": "2026-08-16"}, headers=headers_a)
    assert got_a.status_code == 200
    assert got_a.json()["steps"] == 9000
    assert got_a.json()["tz_offset"] == 180

    # B's local date: 2026-08-15
    got_b = client.get("/api/v1/daily", params={"date": "2026-08-15"}, headers=headers_b)
    assert got_b.status_code == 200
    assert got_b.json()["steps"] == 6000
    assert got_b.json()["tz_offset"] == -480

    # Neither sees the other's local date
    assert client.get("/api/v1/daily", params={"date": "2026-08-15"}, headers=headers_a).status_code == 404
    assert client.get("/api/v1/daily", params={"date": "2026-08-16"}, headers=headers_b).status_code == 404


def test_user_tz_offset_hint_stored_at_register(client):
    token = register_user(client, email="tz-hint@example.com", tz_offset=-300)["access_token"]
    resp = client.get("/api/v1/users/me", headers=auth_headers(token))
    assert resp.status_code == 200
    assert resp.json()["tz_offset"] == -300
