"""Daily ingest tests: validation, upsert idempotency, anti-cheat, read-back."""

import pytest

from tests.conftest import auth_headers, register_user

PAYLOAD = {
    "date": "2026-08-15",
    "tz_offset": 210,
    "steps": 12345,
    "sleep_seconds": 28800,
    "avg_hr": 71.5,
    "source_apps": ["com.samsung.health", "com.google.android.apps.fitness"],
}


def _token(client, email="daily@example.com"):
    return register_user(client, email=email)["access_token"]


def test_ingest_happy_path_and_read_back(client):
    token = _token(client)
    headers = auth_headers(token)

    resp = client.post("/api/v1/daily", json=PAYLOAD, headers=headers)
    assert resp.status_code == 201, resp.text
    body = resp.json()
    assert body["steps"] == 12345
    assert body["sleep_seconds"] == 28800
    assert body["avg_hr"] == 71.5
    assert body["date"] == "2026-08-15"
    assert body["tz_offset"] == 210
    assert body["source_apps"] == ["com.samsung.health", "com.google.android.apps.fitness"]
    assert body["source"] == "health_connect"

    # curl smoke contract: GET back same daily by date
    got = client.get("/api/v1/daily", params={"date": "2026-08-15"}, headers=headers)
    assert got.status_code == 200
    assert got.json()["steps"] == 12345
    assert got.json()["avg_hr"] == 71.5


def test_ingest_requires_auth(client):
    resp = client.post("/api/v1/daily", json=PAYLOAD)
    assert resp.status_code == 401


def test_ingest_negative_steps_rejected(client):
    token = _token(client)
    bad = {**PAYLOAD, "steps": -1}
    resp = client.post("/api/v1/daily", json=bad, headers=auth_headers(token))
    assert resp.status_code == 422


def test_ingest_negative_sleep_rejected(client):
    token = _token(client)
    bad = {**PAYLOAD, "sleep_seconds": -5}
    resp = client.post("/api/v1/daily", json=bad, headers=auth_headers(token))
    assert resp.status_code == 422


def test_ingest_tz_offset_out_of_range_rejected(client):
    token = _token(client)
    bad = {**PAYLOAD, "tz_offset": 15 * 60 + 1}
    resp = client.post("/api/v1/daily", json=bad, headers=auth_headers(token))
    assert resp.status_code == 422


def test_ingest_invalid_date_rejected(client):
    token = _token(client)
    resp = client.post("/api/v1/daily", json={**PAYLOAD, "date": "not-a-date"}, headers=auth_headers(token))
    assert resp.status_code == 422


def test_ingest_rejects_unknown_fields(client):
    token = _token(client)
    resp = client.post(
        "/api/v1/daily", json={**PAYLOAD, "manual_note": "cheat"}, headers=auth_headers(token)
    )
    assert resp.status_code == 422


def test_duplicate_date_upserts_not_duplicates(client):
    token = _token(client)
    headers = auth_headers(token)

    first = client.post("/api/v1/daily", json=PAYLOAD, headers=headers)
    assert first.status_code == 201

    updated = {**PAYLOAD, "steps": 15000, "avg_hr": 68.0}
    second = client.post("/api/v1/daily", json=updated, headers=headers)
    assert second.status_code == 200  # update, not create
    assert second.json()["steps"] == 15000
    assert second.json()["avg_hr"] == 68.0

    # read back: GET still returns a single day object
    got = client.get("/api/v1/daily", params={"date": "2026-08-15"}, headers=headers)
    assert got.status_code == 200
    assert got.json()["steps"] == 15000


def test_repeat_same_payload_is_idempotent(client):
    token = _token(client)
    headers = auth_headers(token)
    assert client.post("/api/v1/daily", json=PAYLOAD, headers=headers).status_code == 201
    again = client.post("/api/v1/daily", json=PAYLOAD, headers=headers)
    assert again.status_code == 200
    assert again.json()["steps"] == PAYLOAD["steps"]


def test_manual_entry_source_rejected(client):
    token = _token(client)
    payload = {**PAYLOAD, "source": "manual"}
    resp = client.post("/api/v1/daily", json=payload, headers=auth_headers(token))
    assert resp.status_code == 422
    assert "manual" in resp.json()["detail"].lower()


def test_sanity_bound_steps_log_only_accepted(client):
    token = _token(client)
    payload = {**PAYLOAD, "steps": 300_000}  # > 250k sanity bound → accepted, log-only
    resp = client.post("/api/v1/daily", json=payload, headers=auth_headers(token))
    assert resp.status_code == 201
    assert resp.json()["steps"] == 300_000


def test_get_daily_404_for_missing_date(client):
    token = _token(client)
    resp = client.get("/api/v1/daily", params={"date": "2026-08-14"}, headers=auth_headers(token))
    assert resp.status_code == 404


def test_get_daily_requires_date_param(client):
    token = _token(client)
    resp = client.get("/api/v1/daily", headers=auth_headers(token))
    assert resp.status_code == 422


def test_daily_isolation_between_users(client):
    token_a = _token(client, email="alice@example.com")
    token_b = _token(client, email="bob@example.com")

    assert client.post("/api/v1/daily", json=PAYLOAD, headers=auth_headers(token_a)).status_code == 201
    # Bob sees nothing for that date
    resp = client.get("/api/v1/daily", params={"date": "2026-08-15"}, headers=auth_headers(token_b))
    assert resp.status_code == 404
    # Alice still sees it
    resp = client.get("/api/v1/daily", params={"date": "2026-08-15"}, headers=auth_headers(token_a))
    assert resp.status_code == 200


def test_range_query(client):
    token = _token(client)
    headers = auth_headers(token)
    client.post("/api/v1/daily", json={**PAYLOAD, "date": "2026-08-14"}, headers=headers)
    client.post("/api/v1/daily", json={**PAYLOAD, "date": "2026-08-15"}, headers=headers)

    resp = client.get(
        "/api/v1/daily/range", params={"from": "2026-08-14", "to": "2026-08-16"}, headers=headers
    )
    assert resp.status_code == 200
    dates = [item["date"] for item in resp.json()["items"]]
    assert dates == ["2026-08-14", "2026-08-15"]

    resp = client.get(
        "/api/v1/daily/range", params={"from": "2026-08-16", "to": "2026-08-14"}, headers=headers
    )
    assert resp.status_code == 422
