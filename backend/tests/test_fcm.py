"""FCM push tests: payload builders, dry-run sender, token upsert, event firing.

Covers BE-C3 acceptance: device-token upsert, FCM payload builder unit tests
with a mocked sender (dry-run — no real Firebase in CI), challenge
start/end notifications on status transitions, beat-you detection after an
ingest overtakes someone, and the 1/day beat-you throttle.
"""

from datetime import date, datetime, timedelta, timezone
from types import SimpleNamespace

import pytest
from sqlalchemy import select

from app.models import Challenge, Device, FcmDelivery, User
from app.schemas.daily import DailyIngestRequest
from app.services import challenge_service
from app.services.challenge_service import (
    capture_standings,
    notify_overtaken,
    update_challenge_status,
)
from app.services.daily_ingest import upsert_daily
from app.services.fcm import (
    FcmSender,
    build_beat_you,
    build_challenge_ended,
    build_challenge_started,
    invite_deep_link,
    notify_challenge_ended,
    notify_challenge_started,
    recent_successful_delivery,
)
from tests.conftest import auth_headers, register_user

# DB-1 skew window simulation: with SKEW_SIM=1 the app clock seam is frozen at
# the skew instant (see tests/conftest.py). `now` inputs go through the
# *module* attribute (challenge_service.utcnow) so the skew_clock fixture's
# monkeypatch is seen at call time; tests in THIS module then run
# deterministically in both the aligned and the daily 00:00-03:30 Tehran
# date-skew windows.
pytestmark = pytest.mark.usefixtures("skew_clock")

# The skew instant: server UTC date 2026-08-16, local (UTC+3:30) date 2026-08-17.
SKEW_INSTANT = datetime(2026, 8, 16, 22, 30, 0, tzinfo=timezone.utc)


def _register(client, email, **extra):
    return register_user(client, email=email, **extra)


def _headers(token):
    return auth_headers(token)


def _draft_body(**overrides):
    now = challenge_service.utcnow()
    body = {
        "title": "Push Challenge",
        "starts_at": (now + timedelta(days=1)).isoformat(),
        "ends_at": (now + timedelta(days=2)).isoformat(),
        "metric": "steps",
        "invite_only": False,
    }
    body.update(overrides)
    return body


def _active_body(**overrides):
    """Challenge already in progress so today's local date is in the window."""
    now = challenge_service.utcnow()
    body = {
        "title": "Beat Battle",
        "starts_at": (now - timedelta(days=2)).isoformat(),
        "ends_at": (now + timedelta(days=2)).isoformat(),
        "metric": "steps",
        "invite_only": False,
    }
    body.update(overrides)
    return body


def _add_device(db, user_id: int, token: str):
    db.add(Device(user_id=user_id, device_token=token, kind="android"))
    db.commit()


def _ingest(steps: int, day: str | None = None) -> DailyIngestRequest:
    """Daily ingest payload dated the device's LOCAL today (date.today()).

    tz_offset=210 mirrors the UTC+3:30 app environment (the DB-1 repro):
    during the 00:00-03:30 Tehran skew window the reported local date is one
    AHEAD of the server UTC date — the exact condition DB-1 got wrong.
    """
    return DailyIngestRequest(
        date=date.fromisoformat(day or (challenge_service.utcnow() + timedelta(minutes=210)).date().isoformat()),
        tz_offset=210,
        steps=steps,
        source_apps=["com.test"],
        source="health_connect",
    )


def _user(db, user_id: int) -> User:
    return db.get(User, user_id)


# ---------------------------------------------------------------- payloads

def test_build_challenge_started_payload():
    ch = SimpleNamespace(id=7, title="Weekend Steps")
    p = build_challenge_started(ch)
    assert p["title"] == "Challenge started!"
    assert ch.title in p["body"]
    assert p["data"]["type"] == "challenge_started"
    assert p["data"]["challenge_id"] == "7"
    assert p["data"]["deep_link"] == "fitnessapp://challenges/7"


def test_build_challenge_ended_payload():
    ch = SimpleNamespace(id=9, title="Weekend Steps")
    p = build_challenge_ended(ch)
    assert p["data"]["type"] == "challenge_ended"
    assert p["data"]["deep_link"] == "fitnessapp://challenges/9/leaderboard"


def test_build_beat_you_payload():
    ch = SimpleNamespace(id=5, title="Weekend Steps")
    p = build_beat_you(ch, "Bob")
    assert p["title"] == "You've been overtaken!"
    assert "Bob" in p["body"]
    assert p["data"]["type"] == "beat_you"
    assert p["data"]["deep_link"] == "fitnessapp://challenges/5/leaderboard"

    anon = build_beat_you(ch, None)
    assert "Someone" in anon["body"]


def test_invite_deep_link_contract():
    assert invite_deep_link(12, "ABC23456") == "fitnessapp://challenges/12/join?code=ABC23456"


# ------------------------------------------------------------------ sender

def test_sender_dry_run_never_touches_network():
    sender = FcmSender(dry_run=True)
    result = sender.send("tok-1", build_challenge_started(SimpleNamespace(id=1, title="X")))
    assert result.success is True
    assert result.message_id == "dry-run"
    assert len(sender.sent_messages) == 1
    assert sender.sent_messages[0][0] == "tok-1"


def test_sender_real_mode_without_credentials_errors():
    sender = FcmSender(dry_run=False, credentials_path="")
    result = sender.send("tok-1", build_challenge_started(SimpleNamespace(id=1, title="X")))
    assert result.success is False
    assert "not configured" in (result.error or "")


# ------------------------------------------------------- token upsert API

def test_fcm_token_upsert_endpoint(client):
    alice = _register(client, "fcm-alice@example.com")
    headers = _headers(alice["access_token"])

    resp = client.post("/api/v1/users/me/fcm-token", json={"token": "fcm-tok-1", "platform": "android"}, headers=headers)
    assert resp.status_code == 200, resp.text
    body = resp.json()
    assert body["status"] == "ok"
    assert body["token"] == "fcm-tok-1"
    assert body["platform"] == "android"

    # Upsert same token -> still one device, kind updated.
    resp = client.post("/api/v1/users/me/fcm-token", json={"token": "fcm-tok-1", "platform": "web"}, headers=headers)
    assert resp.status_code == 200, resp.text
    assert resp.json()["platform"] == "web"

    devices = client.get("/api/v1/users/me/devices", headers=headers).json()
    assert len(devices) == 1
    assert devices[0]["device_token"] == "fcm-tok-1"
    assert devices[0]["kind"] == "web"

    # Same token on a different user -> separate row (per-user upsert key).
    bob = _register(client, "fcm-bob@example.com")
    resp = client.post("/api/v1/users/me/fcm-token", json={"token": "fcm-tok-1"}, headers=_headers(bob["access_token"]))
    assert resp.status_code == 200, resp.text
    assert len(client.get("/api/v1/users/me/devices", headers=_headers(bob["access_token"])).json()) == 1
    assert len(client.get("/api/v1/users/me/devices", headers=headers).json()) == 1


def test_fcm_token_validation(client):
    alice = _register(client, "fcm-val@example.com")
    resp = client.post(
        "/api/v1/users/me/fcm-token",
        json={"token": ""},
        headers=_headers(alice["access_token"]),
    )
    assert resp.status_code == 422


# ------------------------------------------- start/end on status transition

def test_status_transition_fires_started_and_ended(client, db_session):
    alice = _register(client, "push-a@example.com", display_name="Alice")
    bob = _register(client, "push-b@example.com", display_name="Bob")
    created = client.post(
        "/api/v1/challenges", json=_draft_body(), headers=_headers(alice["access_token"])
    )
    assert created.status_code == 201
    cid = created.json()["id"]

    join = client.post(
        f"/api/v1/challenges/{cid}/join", headers=_headers(bob["access_token"])
    )
    assert join.status_code == 200

    alice_id = created.json()["creator"]["id"]
    bob_id = join.json()["participants"][1]["user_id"]
    _add_device(db_session, alice_id, "tok-alice")
    _add_device(db_session, bob_id, "tok-bob")

    sender = FcmSender(dry_run=True)
    creator = _user(db_session, alice_id)

    update_challenge_status(db_session, creator, cid, "active", challenge_service.utcnow(), sender=sender)
    started = [m for m in sender.sent_messages if m[1]["data"]["type"] == "challenge_started"]
    assert sorted(t for t, _ in started) == ["tok-alice", "tok-bob"]

    update_challenge_status(db_session, creator, cid, "ended", challenge_service.utcnow(), sender=sender)
    ended = [m for m in sender.sent_messages if m[1]["data"]["type"] == "challenge_ended"]
    assert sorted(t for t, _ in ended) == ["tok-alice", "tok-bob"]


def test_notify_challenge_started_skips_users_without_tokens(client, db_session):
    alice = _register(client, "push-c@example.com")
    created = client.post(
        "/api/v1/challenges", json=_draft_body(), headers=_headers(alice["access_token"])
    )
    cid = created.json()["id"]
    challenge = db_session.get(Challenge, cid)
    sender = FcmSender(dry_run=True)

    notify_challenge_started(db_session, sender, challenge, challenge_service.utcnow())
    assert sender.sent_messages == []  # no devices registered -> nothing sent


def test_patch_status_with_registered_tokens_does_not_crash(client):
    """End-to-end through the API: default (dry-run) sender, tokens registered."""
    alice = _register(client, "push-d@example.com")
    headers = _headers(alice["access_token"])
    client.post("/api/v1/users/me/fcm-token", json={"token": "tok-e2e"}, headers=headers)
    created = client.post("/api/v1/challenges", json=_draft_body(), headers=headers)
    cid = created.json()["id"]

    resp = client.patch(f"/api/v1/challenges/{cid}/status", json={"status": "active"}, headers=headers)
    assert resp.status_code == 200, resp.text
    resp = client.patch(f"/api/v1/challenges/{cid}/status", json={"status": "ended"}, headers=headers)
    assert resp.status_code == 200, resp.text


# ------------------------------------------------------------- beat-you

def _make_beat_battle(client, db_session):
    """Active challenge with Alice(rank 1) and Bob(rank 2), both with tokens.

    Both users are UTC+3:30 (like the app's primary market and the DB-1
    repro), so the local calendar date of their ingest rows (date.today())
    matches how the board derives their per-participant "today" cutoff —
    also during the daily 00:00-03:30 date-skew window.
    """
    alice = _register(client, "beat-a@example.com", display_name="Alice", tz_offset=210)
    bob = _register(client, "beat-b@example.com", display_name="Bob", tz_offset=210)
    created = client.post(
        "/api/v1/challenges", json=_active_body(), headers=_headers(alice["access_token"])
    )
    assert created.status_code == 201
    cid = created.json()["id"]
    join = client.post(f"/api/v1/challenges/{cid}/join", headers=_headers(bob["access_token"]))
    assert join.status_code == 200

    alice_id = created.json()["creator"]["id"]
    bob_id = join.json()["participants"][1]["user_id"]

    # Seed scores: Alice 1000, Bob 500 (same local day, both within window).
    upsert_daily(db_session, alice_id, _ingest(1000))
    upsert_daily(db_session, bob_id, _ingest(500))

    _add_device(db_session, alice_id, "tok-alice")
    _add_device(db_session, bob_id, "tok-bob")
    return cid, alice_id, bob_id, alice, bob


def test_beat_you_fires_when_ingest_overtakes(client, db_session):
    cid, alice_id, bob_id, _, _ = _make_beat_battle(client, db_session)
    now = challenge_service.utcnow()

    before = capture_standings(db_session, bob_id, now)
    assert before[cid][alice_id] == 1 and before[cid][bob_id] == 2

    sender = FcmSender(dry_run=True, log_deliveries=True)
    upsert_daily(db_session, bob_id, _ingest(2000))
    notified = notify_overtaken(db_session, before, bob_id, now, sender=sender)

    assert notified == [alice_id]
    beat_msgs = [m for m in sender.sent_messages if m[1]["data"]["type"] == "beat_you"]
    assert [t for t, _ in beat_msgs] == ["tok-alice"]
    assert beat_msgs[0][1]["data"]["deep_link"] == f"fitnessapp://challenges/{cid}/leaderboard"
    # The push goes to the overtaken user and names the overtaker (Bob).
    assert "Bob" in beat_msgs[0][1]["body"]


def test_beat_you_not_fired_when_no_overtake(client, db_session):
    cid, alice_id, bob_id, _, _ = _make_beat_battle(client, db_session)
    now = challenge_service.utcnow()

    # Alice ingests more — she was already ahead; nobody overtaken.
    before = capture_standings(db_session, alice_id, now)
    sender = FcmSender(dry_run=True)
    upsert_daily(db_session, alice_id, _ingest(3000))
    notified = notify_overtaken(db_session, before, alice_id, now, sender=sender)

    assert notified == []
    assert sender.sent_messages == []


def test_beat_you_throttled_within_24h(client, db_session):
    cid, alice_id, bob_id, _, _ = _make_beat_battle(client, db_session)
    now = challenge_service.utcnow()

    sender = FcmSender(dry_run=True, log_deliveries=True)

    # First overtake -> delivered (row recorded).
    before1 = capture_standings(db_session, bob_id, now)
    upsert_daily(db_session, bob_id, _ingest(2000))
    notified = notify_overtaken(db_session, before1, bob_id, now, sender=sender)
    assert notified == [alice_id]
    assert len(sender.sent_messages) == 1

    # Alice fights back (3000) and retakes the lead; Bob overtakes again
    # within the same 24h -> detected but throttled (no second push).
    upsert_daily(db_session, alice_id, _ingest(3000))
    before2 = capture_standings(db_session, bob_id, now)
    assert before2[cid][alice_id] == 1 and before2[cid][bob_id] == 2
    upsert_daily(db_session, bob_id, _ingest(4000))
    notified = notify_overtaken(db_session, before2, bob_id, now, sender=sender)
    assert notified == [alice_id]  # detection still reports the overtake
    beat_msgs = [m for m in sender.sent_messages if m[1]["data"]["type"] == "beat_you"]
    assert len(beat_msgs) == 1  # but no second push


def test_beat_you_not_throttled_after_24h(client, db_session):
    cid, alice_id, bob_id, _, _ = _make_beat_battle(client, db_session)
    now = challenge_service.utcnow()

    sender = FcmSender(dry_run=True, log_deliveries=True)

    before1 = capture_standings(db_session, bob_id, now)
    upsert_daily(db_session, bob_id, _ingest(2000))
    notify_overtaken(db_session, before1, bob_id, now, sender=sender)
    assert len(sender.sent_messages) == 1

    # Age the successful delivery past the 24h throttle window.
    row = db_session.scalar(select(FcmDelivery).where(FcmDelivery.user_id == alice_id, FcmDelivery.type == "beat_you"))
    row.sent_at = now - timedelta(hours=25)
    db_session.commit()
    assert recent_successful_delivery(db_session, alice_id, "beat_you", now) is False

    # A new overtake after the window -> delivered again.
    upsert_daily(db_session, alice_id, _ingest(3000))
    before2 = capture_standings(db_session, bob_id, now)
    assert before2[cid][alice_id] == 1 and before2[cid][bob_id] == 2
    upsert_daily(db_session, bob_id, _ingest(4000))
    notified = notify_overtaken(db_session, before2, bob_id, now, sender=sender)
    assert notified == [alice_id]
    beat_msgs = [m for m in sender.sent_messages if m[1]["data"]["type"] == "beat_you"]
    assert len(beat_msgs) == 2


# --------------------------------------------------- DB-1 skew regression

# UTC 2026-08-16 22:30 -> server UTC date 2026-08-16, Tehran (UTC+3:30)
# local date 2026-08-17. This is the daily 00:00-03:30 skew window where the
# old server-UTC-date default cutoff dropped the users' local-today ingest
# rows (assert [] == [1] / 0 == 1 failures: totals were 0, no overtake seen).
SKEW_INSTANT = datetime(2026, 8, 16, 22, 30, 0, tzinfo=timezone.utc)


def test_beat_you_fires_in_skew_window(client, db_session):
    """DB-1 regression: beat-you detection in the date-skew window.

    Participants are UTC+3:30 and ingest their LOCAL today (2026-08-17 in
    this instant). The default cutoff must be each participant's local today
    (2026-08-17), not the server UTC date (2026-08-16), or Bob's overtake is
    never detected. Same fixture and assertions as
    test_beat_you_fires_when_ingest_overtakes, with `now` pinned to the skew.
    """
    cid, alice_id, bob_id, _, _ = _make_beat_battle(client, db_session)
    now = SKEW_INSTANT

    before = capture_standings(db_session, bob_id, now)
    assert before[cid][alice_id] == 1 and before[cid][bob_id] == 2

    sender = FcmSender(dry_run=True, log_deliveries=True)
    upsert_daily(db_session, bob_id, _ingest(2000))  # Bob's local today -> 2026-08-17
    notified = notify_overtaken(db_session, before, bob_id, now, sender=sender)

    assert notified == [alice_id]
    beat_msgs = [m for m in sender.sent_messages if m[1]["data"]["type"] == "beat_you"]
    assert [t for t, _ in beat_msgs] == ["tok-alice"]
