"""Challenge lifecycle tests: create, list, detail, join/leave rules, status."""

from datetime import datetime, timedelta, timezone

from tests.conftest import auth_headers, register_user

NOW = datetime(2026, 8, 16, 12, 0, 0, tzinfo=timezone.utc)


def _challenge_body(**overrides):
    """Future-start challenge (draft) by default; overrides replace fields."""
    now = datetime.now(timezone.utc)
    body = {
        "title": "Weekend Steps",
        "starts_at": (now + timedelta(days=1)).isoformat(),
        "ends_at": (now + timedelta(days=2)).isoformat(),
        "metric": "steps",
        "invite_only": False,
    }
    body.update(overrides)
    return body


def _register(client, email, **extra):
    return register_user(client, email=email, **extra)


def test_create_challenge_future_starts_as_draft(client):
    token = _register(client, "creator@example.com")["access_token"]
    resp = client.post("/api/v1/challenges", json=_challenge_body(), headers=auth_headers(token))
    assert resp.status_code == 201, resp.text
    body = resp.json()
    assert body["title"] == "Weekend Steps"
    assert body["metric"] == "steps"
    assert body["status"] == "draft"
    assert body["invite_only"] is False
    assert body["creator"]["id"] is not None
    # creator is automatically a participant
    assert [p["user_id"] for p in body["participants"]] == [body["creator"]["id"]]


def test_create_challenge_started_in_past_is_active(client):
    token = _register(client, "creator2@example.com")["access_token"]
    resp = client.post(
        "/api/v1/challenges",
        json=_challenge_body(starts_at="2026-08-15T00:00:00Z"),
        headers=auth_headers(token),
    )
    assert resp.status_code == 201, resp.text
    assert resp.json()["status"] == "active"


def test_create_challenge_requires_auth(client):
    resp = client.post("/api/v1/challenges", json=_challenge_body())
    assert resp.status_code == 401


def test_create_challenge_validation(client):
    token = _register(client, "creator3@example.com")["access_token"]
    headers = auth_headers(token)

    # ends_at before starts_at
    resp = client.post(
        "/api/v1/challenges",
        json=_challenge_body(
            starts_at=(datetime.now(timezone.utc) + timedelta(days=3)).isoformat(),
            ends_at=(datetime.now(timezone.utc) + timedelta(days=2)).isoformat(),
        ),
        headers=headers,
    )
    assert resp.status_code == 422

    # entire window in the past -> ends_at must be in the future
    resp = client.post(
        "/api/v1/challenges",
        json=_challenge_body(
            starts_at=(datetime.now(timezone.utc) - timedelta(days=2)).isoformat(),
            ends_at=(datetime.now(timezone.utc) - timedelta(days=1)).isoformat(),
        ),
        headers=headers,
    )
    assert resp.status_code == 422
    assert "future" in resp.json()["detail"].lower()

    # unknown metric
    resp = client.post(
        "/api/v1/challenges",
        json=_challenge_body(metric="calories"),
        headers=headers,
    )
    assert resp.status_code == 422

    # empty title
    resp = client.post(
        "/api/v1/challenges",
        json=_challenge_body(title=""),
        headers=headers,
    )
    assert resp.status_code == 422


def test_create_sleep_and_hr_challenges(client):
    token = _register(client, "creator_metrics@example.com")["access_token"]
    headers = auth_headers(token)

    # Sleep challenge
    resp_sleep = client.post(
        "/api/v1/challenges",
        json=_challenge_body(title="8 Hours Sleep Club", metric="sleep_seconds"),
        headers=headers,
    )
    assert resp_sleep.status_code == 201, resp_sleep.text
    assert resp_sleep.json()["metric"] == "sleep_seconds"
    assert resp_sleep.json()["title"] == "8 Hours Sleep Club"

    # HR challenge
    resp_hr = client.post(
        "/api/v1/challenges",
        json=_challenge_body(title="Resting HR Challenge", metric="avg_hr"),
        headers=headers,
    )
    assert resp_hr.status_code == 201, resp_hr.text
    assert resp_hr.json()["metric"] == "avg_hr"
    assert resp_hr.json()["title"] == "Resting HR Challenge"


def test_list_mine_and_visibility(client):
    alice = _register(client, "alice@example.com")
    bob = _register(client, "bob@example.com")
    created = client.post(
        "/api/v1/challenges", json=_challenge_body(), headers=auth_headers(alice["access_token"])
    )
    assert created.status_code == 201
    challenge_id = created.json()["id"]

    mine_alice = client.get("/api/v1/challenges", headers=auth_headers(alice["access_token"]))
    assert mine_alice.status_code == 200
    assert [c["id"] for c in mine_alice.json()] == [challenge_id]

    mine_bob = client.get("/api/v1/challenges", headers=auth_headers(bob["access_token"]))
    assert mine_bob.status_code == 200
    assert mine_bob.json() == []


def test_detail_shows_participants_and_progress(client):
    alice = _register(client, "alice2@example.com", display_name="Alice")
    bob = _register(client, "bob2@example.com", display_name="Bob")
    created = client.post(
        "/api/v1/challenges", json=_challenge_body(), headers=auth_headers(alice["access_token"])
    ).json()
    client.post(
        f"/api/v1/challenges/{created['id']}/join", headers=auth_headers(bob["access_token"])
    )

    detail = client.get(
        f"/api/v1/challenges/{created['id']}", headers=auth_headers(alice["access_token"])
    )
    assert detail.status_code == 200
    body = detail.json()
    assert len(body["participants"]) == 2
    by_user = {p["user_id"]: p for p in body["participants"]}
    assert by_user[alice["user"]["id"]]["is_creator"] is True
    assert by_user[bob["user"]["id"]]["is_creator"] is False
    assert all("total" in p for p in body["participants"])


def test_detail_404_for_missing(client):
    token = _register(client, "ghost@example.com")["access_token"]
    resp = client.get("/api/v1/challenges/9999", headers=auth_headers(token))
    assert resp.status_code == 404


def test_join_and_leave_flow(client):
    alice = _register(client, "alice3@example.com")
    bob = _register(client, "bob3@example.com")
    created = client.post(
        "/api/v1/challenges", json=_challenge_body(), headers=auth_headers(alice["access_token"])
    ).json()
    cid = created["id"]

    join = client.post(f"/api/v1/challenges/{cid}/join", headers=auth_headers(bob["access_token"]))
    assert join.status_code == 200, join.text
    assert len(join.json()["participants"]) == 2

    # join twice -> 409
    dup = client.post(f"/api/v1/challenges/{cid}/join", headers=auth_headers(bob["access_token"]))
    assert dup.status_code == 409
    assert "already" in dup.json()["detail"].lower()

    # leave
    left = client.post(f"/api/v1/challenges/{cid}/leave", headers=auth_headers(bob["access_token"]))
    assert left.status_code == 200, left.text
    assert [p["user_id"] for p in left.json()["participants"]] == [alice["user"]["id"]]

    # leave again -> 404
    again = client.post(f"/api/v1/challenges/{cid}/leave", headers=auth_headers(bob["access_token"]))
    assert again.status_code == 404

    # re-join works after leave
    rejoined = client.post(f"/api/v1/challenges/{cid}/join", headers=auth_headers(bob["access_token"]))
    assert rejoined.status_code == 200


def test_creator_cannot_leave(client):
    alice = _register(client, "alice4@example.com")
    created = client.post(
        "/api/v1/challenges", json=_challenge_body(), headers=auth_headers(alice["access_token"])
    ).json()
    resp = client.post(
        f"/api/v1/challenges/{created['id']}/leave", headers=auth_headers(alice["access_token"])
    )
    assert resp.status_code == 400


def test_join_after_ended_rejected(client):
    alice = _register(client, "alice5@example.com")
    bob = _register(client, "bob5@example.com")
    created = client.post(
        "/api/v1/challenges", json=_challenge_body(), headers=auth_headers(alice["access_token"])
    ).json()
    cid = created["id"]

    ended = client.patch(
        f"/api/v1/challenges/{cid}/status",
        json={"status": "ended"},
        headers=auth_headers(alice["access_token"]),
    )
    assert ended.status_code == 200
    assert ended.json()["status"] == "ended"

    join = client.post(f"/api/v1/challenges/{cid}/join", headers=auth_headers(bob["access_token"]))
    assert join.status_code == 409
    assert "ended" in join.json()["detail"].lower()

    leave = client.post(f"/api/v1/challenges/{cid}/leave", headers=auth_headers(bob["access_token"]))
    assert leave.status_code == 409


def test_invite_only_join_rejected_until_codes_ship(client):
    alice = _register(client, "alice6@example.com")
    bob = _register(client, "bob6@example.com")
    created = client.post(
        "/api/v1/challenges",
        json=_challenge_body(invite_only=True),
        headers=auth_headers(alice["access_token"]),
    ).json()
    cid = created["id"]
    resp = client.post(f"/api/v1/challenges/{cid}/join", headers=auth_headers(bob["access_token"]))
    assert resp.status_code == 403
    assert "invite" in resp.json()["detail"].lower()


def test_status_transitions(client):
    alice = _register(client, "alice7@example.com")
    bob = _register(client, "bob7@example.com")
    created = client.post(
        "/api/v1/challenges", json=_challenge_body(), headers=auth_headers(alice["access_token"])
    ).json()
    cid = created["id"]
    assert created["status"] == "draft"

    # draft -> active (start now)
    active = client.patch(
        f"/api/v1/challenges/{cid}/status",
        json={"status": "active"},
        headers=auth_headers(alice["access_token"]),
    )
    assert active.status_code == 200
    assert active.json()["status"] == "active"

    # active -> draft is forbidden (no backward transitions)
    back = client.patch(
        f"/api/v1/challenges/{cid}/status",
        json={"status": "draft"},
        headers=auth_headers(alice["access_token"]),
    )
    assert back.status_code == 409

    # active -> ended
    ended = client.patch(
        f"/api/v1/challenges/{cid}/status",
        json={"status": "ended"},
        headers=auth_headers(alice["access_token"]),
    )
    assert ended.status_code == 200

    # ended -> anything is forbidden
    revive = client.patch(
        f"/api/v1/challenges/{cid}/status",
        json={"status": "active"},
        headers=auth_headers(alice["access_token"]),
    )
    assert revive.status_code == 409


def test_status_change_requires_creator(client):
    alice = _register(client, "alice8@example.com")
    bob = _register(client, "bob8@example.com")
    created = client.post(
        "/api/v1/challenges", json=_challenge_body(), headers=auth_headers(alice["access_token"])
    ).json()
    resp = client.patch(
        f"/api/v1/challenges/{created['id']}/status",
        json={"status": "active"},
        headers=auth_headers(bob["access_token"]),
    )
    assert resp.status_code == 403


def test_invalid_status_value_rejected(client):
    alice = _register(client, "alice9@example.com")
    created = client.post(
        "/api/v1/challenges", json=_challenge_body(), headers=auth_headers(alice["access_token"])
    ).json()
    resp = client.patch(
        f"/api/v1/challenges/{created['id']}/status",
        json={"status": "paused"},
        headers=auth_headers(alice["access_token"]),
    )
    assert resp.status_code == 422


def test_time_driven_status_propagation_lazy(client, db_session):
    """Moving clock (via direct DB update) propagates draft->active->ended on read."""
    from app.models import Challenge as ChallengeModel

    alice = _register(client, "alice10@example.com")
    created = client.post(
        "/api/v1/challenges",
        json=_challenge_body(starts_at="2026-08-20T00:00:00Z", ends_at="2026-08-25T00:00:00Z"),
        headers=auth_headers(alice["access_token"]),
    ).json()
    cid = created["id"]
    assert created["status"] == "draft"

    row = db_session.get(ChallengeModel, cid)
    row.starts_at = NOW - timedelta(days=1)
    row.ends_at = NOW - timedelta(hours=1)
    db_session.add(row)
    db_session.commit()

    # first read lazily propagates draft -> active -> ended
    resp = client.get(f"/api/v1/challenges/{cid}", headers=auth_headers(alice["access_token"]))
    assert resp.status_code == 200
    assert resp.json()["status"] == "ended"

    # join after lazy end is rejected
    bob = _register(client, "bob10@example.com")
    join = client.post(f"/api/v1/challenges/{cid}/join", headers=auth_headers(bob["access_token"]))
    assert join.status_code == 409


def test_naive_datetime_treated_as_utc(client):
    alice = _register(client, "alice11@example.com")
    past = (datetime.now(timezone.utc) - timedelta(hours=2)).replace(tzinfo=None)  # naive, no Z suffix
    future = (datetime.now(timezone.utc) + timedelta(hours=2)).replace(tzinfo=None)
    resp = client.post(
        "/api/v1/challenges",
        json={
            "title": "Naive times",
            "starts_at": past.isoformat(),
            "ends_at": future.isoformat(),
        },
        headers=auth_headers(alice["access_token"]),
    )
    assert resp.status_code == 201, resp.text
    assert resp.json()["status"] == "active"  # naive inputs interpreted as UTC
