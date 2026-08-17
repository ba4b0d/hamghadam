#!/usr/bin/env python3
"""Send a test FCM push notification using real Firebase Admin SDK.

Usage:
    python scripts/send_test_push.py <device_token> [credentials_path]
"""

import os
import sys
from pathlib import Path

# Add project root to sys.path
sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

from app.core.config import settings
from app.services.fcm import FcmSender, build_beat_you


class DummyChallenge:
    id = 1
    title = "Test Challenge"


def main() -> None:
    if len(sys.argv) < 2:
        print("Usage: python scripts/send_test_push.py <device_token> [credentials_path]")
        sys.exit(1)

    token = sys.argv[1]
    creds_path = (
        sys.argv[2]
        if len(sys.argv) > 2
        else settings.fcm_credentials_path or "secrets/firebase-service-account.json"
    )
    if not os.path.isabs(creds_path):
        creds_path = str(Path(__file__).resolve().parent.parent / creds_path)

    if not os.path.exists(creds_path):
        print(f"Error: Credentials file not found at {creds_path}")
        sys.exit(1)

    print(f"Sending real FCM test push to token: {token[:10]}... using creds: {creds_path}")
    sender = FcmSender(dry_run=False, credentials_path=creds_path)
    payload = build_beat_you(DummyChallenge(), "Test Opponent")
    res = sender.send(token, payload)

    print(f"Result: success={res.success}, message_id={res.message_id}, error={res.error}")
    if not res.success:
        sys.exit(1)


if __name__ == "__main__":
    main()
