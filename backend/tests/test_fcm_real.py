"""Tests for real FCM mode with Firebase Admin SDK."""

import os
from pathlib import Path
import pytest
from app.services.fcm import FcmSender, build_beat_you


class DummyChallenge:
    id = 1
    title = "Real FCM Test Challenge"


CREDENTIALS_PATH = os.getenv(
    "FCM_CREDENTIALS_PATH",
    "C:/Users/barba/HamGhadam/secrets/firebase-service-account.json",
)


@pytest.mark.skipif(
    not os.path.exists(CREDENTIALS_PATH),
    reason="Firebase service account JSON not present",
)
def test_fcm_real_sender_initialization():
    """Verify FcmSender initializes firebase-admin app in real mode."""
    sender = FcmSender(dry_run=False, credentials_path=CREDENTIALS_PATH)
    messaging = sender._messaging_module()
    assert messaging is not None


@pytest.mark.skipif(
    not os.path.exists(CREDENTIALS_PATH),
    reason="Firebase service account JSON not present",
)
def test_fcm_real_send_invalid_token():
    """Verify real FCM send returns structured SendResult failure for invalid token without raising exception."""
    sender = FcmSender(dry_run=False, credentials_path=CREDENTIALS_PATH)
    payload = build_beat_you(DummyChallenge(), "Tester")
    res = sender.send("invalid_dummy_token_12345", payload)
    assert res.success is False
    assert res.error is not None
