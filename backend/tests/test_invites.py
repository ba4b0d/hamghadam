"""Invite (join code) tests: create/expire/reuse-rejected, join via code, capacity.

Covers BE-C3 acceptance: invite code create -> join via code adds a
participant; expired codes rejected; reusing a (dead) code rejected; codes
are multi-use while valid; invite-only challenges are gated on the code.
"""

from datetime import datetime, timedelta, timezone
import re

from sqlalchemy import select

from app.models import ChallengeInvite
from tests.conftest import auth_headers, register_user


def _challenge_body(**overrides):
    now = datetime.now(timezone.utc)
    body = {
        "title": "Steps Battle",
        "starts_at": (now + timedelta(days=1)).isoformat(),
        "ends_at": (now + timedelta(days=2)).isoformat(),
        "metric": "steps",
        "invite_only": True,
    }
    body.update(overrides)
    return body


def _register(client, email, **extra):
    return register_user(client, email=email, **extra)


def _create_challenge(client, token, **overrides) -> dict:
    resp = client.post("/api/v1/challenges", json=_challenge_body(**overrides), headers=auth_headers(token))
    assert resp.status_code == 201, resp.text
    return resp.json()


def _create_invite(client, token, challenge_id, **overrides):
    body = {"ttl_hours": 168}
    body.update(overrides)
    return client.post(f"/api/v1/challenges/{challenge_id}/invites", json=body, headers=auth_headers(token))


def test_creator_creates_invite_and_gets_deep_link(client):
    alice = _register(client, "alice@example.com")
    challenge = _create_challenge(client, alice["access_token"])

    resp = _create_invite(client, alice["access_token"], challenge["id"])
    assert resp.status_code == 201, resp.text
    body = resp.json()
    assert re.fullmatch(r"[A-HJ-NP-Z2-9]{8}", body["code"])  # no 0/O/1/I/L
    assert body["challenge_id"] == challenge["id"]
    expires = datetime.fromisoformat(body["expires_at"].replace("Z", "+00:00"))
    if expires.tzinfo is None:  # SQLite round-trip is naive -> assume UTC
        expires = expires.replace(tzinfo=timezone.utc)
    assert expires > datetime.now(timezone.utc)
    assert body["deep_link"] == f"fitnessapp://challenges/{challenge['id']}/join?code={body['code']}"


def test_non_participant_cannot_create_invite(client):
    alice = _register(client, "alice2@example.com")
    bob = _register(client, "bob2@example.com")
    challenge = _create_challenge(client, alice["access_token"])

    resp = _create_invite(client, bob["access_token"], challenge["id"])
    assert resp.status_code == 403
    assert "participant" in resp.json()["detail"]


def test_joined_participant_can_create_invite(client):
    alice = _register(client, "alice3@example.com")
    bob = _register(client, "bob3@example.com")
    challenge = _create_challenge(client, alice["access_token"])

    invite = _create_invite(client, alice["access_token"], challenge["id"]).json()
    join = client.post(
        f"/api/v1/challenges/{challenge['id']}/join?code={invite['code']}",
        headers=auth_headers(bob["access_token"]),
    )
    assert join.status_code == 200, join.text

    resp = _create_invite(client, bob["access_token"], challenge["id"])
    assert resp.status_code == 201, resp.text


def test_join_invite_only_requires_code(client):
    alice = _register(client, "alice4@example.com")
    bob = _register(client, "bob4@example.com")
    challenge = _create_challenge(client, alice["access_token"])

    # No code -> 403
    resp = client.post(
        f"/api/v1/challenges/{challenge['id']}/join", headers=auth_headers(bob["access_token"])
    )
    assert resp.status_code == 403
    assert "invite code" in resp.json()["detail"].lower()

    # With code -> joined
    invite = _create_invite(client, alice["access_token"], challenge["id"]).json()
    resp = client.post(
        f"/api/v1/challenges/{challenge['id']}/join?code={invite['code']}",
        headers=auth_headers(bob["access_token"]),
    )
    assert resp.status_code == 200, resp.text
    assert bob["user"]["id"] in [p["user_id"] for p in resp.json()["participants"]]

    # Duplicate join with the same code -> 409
    resp = client.post(
        f"/api/v1/challenges/{challenge['id']}/join?code={invite['code']}",
        headers=auth_headers(bob["access_token"]),
    )
    assert resp.status_code == 409


def test_invite_code_multi_use_by_different_users(client):
    alice = _register(client, "alice5@example.com")
    bob = _register(client, "bob5@example.com")
    carol = _register(client, "carol5@example.com")
    challenge = _create_challenge(client, alice["access_token"])

    invite = _create_invite(client, alice["access_token"], challenge["id"]).json()
    for token in (bob["access_token"], carol["access_token"]):
        resp = client.post(
            f"/api/v1/challenges/{challenge['id']}/join?code={invite['code']}",
            headers=auth_headers(token),
        )
        assert resp.status_code == 200, resp.text
    assert [p["user_id"] for p in resp.json()["participants"]] == [
        alice["user"]["id"],
        bob["user"]["id"],
        carol["user"]["id"],
    ]


def test_expired_invite_code_rejected(client, db_session):
    alice = _register(client, "alice6@example.com")
    bob = _register(client, "bob6@example.com")
    challenge = _create_challenge(client, alice["access_token"])
    invite = _create_invite(client, alice["access_token"], challenge["id"]).json()

    # Expire the code server-side (past expires_at).
    row = db_session.scalar(select(ChallengeInvite).where(ChallengeInvite.code == invite["code"]))
    row.expires_at = datetime.now(timezone.utc) - timedelta(minutes=1)
    db_session.commit()

    resp = client.post(
        f"/api/v1/challenges/{challenge['id']}/join?code={invite['code']}",
        headers=auth_headers(bob["access_token"]),
    )
    assert resp.status_code == 403
    assert "Invalid or expired invite code" in resp.json()["detail"]


def test_unknown_invite_code_rejected(client):
    alice = _register(client, "alice7@example.com")
    bob = _register(client, "bob7@example.com")
    challenge = _create_challenge(client, alice["access_token"])

    resp = client.post(
        f"/api/v1/challenges/{challenge['id']}/join?code=ABCDEFGH",
        headers=auth_headers(bob["access_token"]),
    )
    assert resp.status_code == 403


def test_invite_code_from_other_challenge_rejected(client):
    alice = _register(client, "alice8@example.com")
    bob = _register(client, "bob8@example.com")
    c1 = _create_challenge(client, alice["access_token"], title="Battle One")
    c2 = _create_challenge(client, alice["access_token"], title="Battle Two")
    invite = _create_invite(client, alice["access_token"], c1["id"]).json()

    resp = client.post(
        f"/api/v1/challenges/{c2['id']}/join?code={invite['code']}",
        headers=auth_headers(bob["access_token"]),
    )
    assert resp.status_code == 403


def test_join_via_code_after_challenge_ended_rejected(client):
    alice = _register(client, "alice9@example.com")
    bob = _register(client, "bob9@example.com")
    challenge = _create_challenge(client, alice["access_token"])
    invite = _create_invite(client, alice["access_token"], challenge["id"]).json()

    end = client.patch(
        f"/api/v1/challenges/{challenge['id']}/status",
        json={"status": "ended"},
        headers=auth_headers(alice["access_token"]),
    )
    assert end.status_code == 200, end.text

    resp = client.post(
        f"/api/v1/challenges/{challenge['id']}/join?code={invite['code']}",
        headers=auth_headers(bob["access_token"]),
    )
    assert resp.status_code == 409


def test_invite_ttl_validation(client):
    alice = _register(client, "alice10@example.com")
    challenge = _create_challenge(client, alice["access_token"])

    assert _create_invite(client, alice["access_token"], challenge["id"], ttl_hours=0).status_code == 422
    assert _create_invite(client, alice["access_token"], challenge["id"], ttl_hours=31 * 24).status_code == 422
    assert _create_invite(client, alice["access_token"], challenge["id"], ttl_hours=1).status_code == 201


def test_invite_after_challenge_ended_rejected(client):
    alice = _register(client, "alice11@example.com")
    challenge = _create_challenge(client, alice["access_token"])
    end = client.patch(
        f"/api/v1/challenges/{challenge['id']}/status",
        json={"status": "ended"},
        headers=auth_headers(alice["access_token"]),
    )
    assert end.status_code == 200

    resp = _create_invite(client, alice["access_token"], challenge["id"])
    assert resp.status_code == 409


def test_join_public_challenge_ignores_code_gate(client):
    alice = _register(client, "alice12@example.com")
    bob = _register(client, "bob12@example.com")
    challenge = _create_challenge(client, alice["access_token"], invite_only=False)

    # Public challenges join without a code (BE-C2 behavior preserved).
    resp = client.post(
        f"/api/v1/challenges/{challenge['id']}/join", headers=auth_headers(bob["access_token"])
    )
    assert resp.status_code == 200, resp.text


def test_max_participants_capacity_enforced(client):
    alice = _register(client, "alice13@example.com")
    bob = _register(client, "bob13@example.com")
    carol = _register(client, "carol13@example.com")
    challenge = _create_challenge(client, alice["access_token"], invite_only=False, max_participants=2)

    assert challenge["max_participants"] == 2
    resp = client.post(
        f"/api/v1/challenges/{challenge['id']}/join", headers=auth_headers(bob["access_token"])
    )
    assert resp.status_code == 200, resp.text

    resp = client.post(
        f"/api/v1/challenges/{challenge['id']}/join", headers=auth_headers(carol["access_token"])
    )
    assert resp.status_code == 409
    assert "full" in resp.json()["detail"].lower()


def test_max_participants_capacity_with_invite_code(client):
    alice = _register(client, "alice14@example.com")
    bob = _register(client, "bob14@example.com")
    carol = _register(client, "carol14@example.com")
    challenge = _create_challenge(client, alice["access_token"], invite_only=True, max_participants=2)
    invite = _create_invite(client, alice["access_token"], challenge["id"]).json()

    assert client.post(
        f"/api/v1/challenges/{challenge['id']}/join?code={invite['code']}",
        headers=auth_headers(bob["access_token"]),
    ).status_code == 200

    resp = client.post(
        f"/api/v1/challenges/{challenge['id']}/join?code={invite['code']}",
        headers=auth_headers(carol["access_token"]),
    )
    assert resp.status_code == 409
    assert "full" in resp.json()["detail"].lower()
