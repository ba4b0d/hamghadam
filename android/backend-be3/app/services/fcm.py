"""FCM push service (BE-C3): payload builders + sender with dry-run mock.

Design
------
- `build_*` payload builders are pure functions — unit-testable without any
  Firebase dependency. Every payload carries `data`:
  `{"type", "challenge_id", "deep_link"}` so Android can route to the right
  screen (FE-C3 deep-link contract).
- `FcmSender` abstracts Firebase. **Dry-run is the default** (and the CI/test
  setting): sends are logged, no network call happens, and no delivery rows
  are written. Production sets `fcm_credentials_path` (service-account JSON)
  and `fcm_dry_run=false`; `firebase-admin` is imported lazily.
- Notifications: `challenge_started` / `challenge_ended` go to every
  participant with a registered device; `beat_you` goes to the overtaken user
  and is throttled to 1 per rolling 24h per user (via `fcm_deliveries`).
- Event triggers live in the use-case layer (`challenge_service`): explicit
  status transitions (PATCH /status) and post-ingest beat-you detection.
  Push failures never fail the originating API call.

Deep-link contract (Android, FE-C3)
-----------------------------------
    fitnessapp://challenges/{id}                      challenge detail
    fitnessapp://challenges/{id}/join?code={code}     join via invite
    fitnessapp://challenges/{id}/leaderboard          results / board
"""

from __future__ import annotations

import logging
from dataclasses import dataclass
from datetime import datetime, timedelta
from typing import Any

from sqlalchemy import select
from sqlalchemy.orm import Session

from app.core.config import settings
from app.models import Device, FcmDelivery
from app.models.fcm import (
    PUSH_TYPE_BEAT_YOU,
    PUSH_TYPE_CHALLENGE_ENDED,
    PUSH_TYPE_CHALLENGE_STARTED,
)

logger = logging.getLogger("app.fcm")

DEEP_LINK_SCHEME = "fitnessapp"
DEEP_LINK_HOST = "challenges"
BEAT_YOU_THROTTLE_HOURS = 24


# ---------------------------------------------------------------- links ---

def deep_link_for(challenge_id: int, screen: str = "") -> str:
    """Deep link into the challenge screen (no screen -> detail)."""
    base = f"{DEEP_LINK_SCHEME}://{DEEP_LINK_HOST}/{challenge_id}"
    return f"{base}/{screen}" if screen else base


def invite_deep_link(challenge_id: int, code: str) -> str:
    """Deep link that carries the join code: fitnessapp://challenges/{id}/join?code=..."""
    return f"{DEEP_LINK_SCHEME}://{DEEP_LINK_HOST}/{challenge_id}/join?code={code}"


# ------------------------------------------------------------- builders ---

def build_challenge_started(challenge: Any) -> dict[str, Any]:
    return {
        "title": "Challenge started!",
        "body": f'"{challenge.title}" is live — go go go!',
        "data": {
            "type": PUSH_TYPE_CHALLENGE_STARTED,
            "challenge_id": str(challenge.id),
            "deep_link": deep_link_for(challenge.id),
        },
    }


def build_challenge_ended(challenge: Any) -> dict[str, Any]:
    return {
        "title": "Challenge ended",
        "body": f'"{challenge.title}" is over — see the final results!',
        "data": {
            "type": PUSH_TYPE_CHALLENGE_ENDED,
            "challenge_id": str(challenge.id),
            "deep_link": deep_link_for(challenge.id, "leaderboard"),
        },
    }


def build_beat_you(challenge: Any, opponent_display_name: str | None) -> dict[str, Any]:
    name = opponent_display_name or "Someone"
    return {
        "title": "You've been overtaken!",
        "body": f"{name} just passed you in \"{challenge.title}\".",
        "data": {
            "type": PUSH_TYPE_BEAT_YOU,
            "challenge_id": str(challenge.id),
            "deep_link": deep_link_for(challenge.id, "leaderboard"),
        },
    }


# -------------------------------------------------------------- sender ---

@dataclass
class SendResult:
    success: bool
    message_id: str | None = None
    error: str | None = None


class FcmSender:
    """Firebase Cloud Messaging sender.

    - `dry_run=True` (default): log-and-return, never touches the network.
    - `dry_run=False` requires `credentials_path`; firebase-admin is imported
      lazily only then.
    - `log_deliveries`: write `fcm_deliveries` rows after each send. Defaults
      to `not dry_run`; tests can enable it on a dry-run sender to exercise
      throttle/audit paths end-to-end.
    - `sent_messages` captures every (token, payload) for assertions.
    """

    def __init__(
        self,
        dry_run: bool = True,
        credentials_path: str = "",
        log_deliveries: bool | None = None,
    ) -> None:
        self.dry_run = dry_run
        self.credentials_path = credentials_path
        self.log_deliveries = log_deliveries if log_deliveries is not None else (not dry_run)
        self.sent_messages: list[tuple[str, dict[str, Any]]] = []
        self._messaging: Any = None

    def _messaging_module(self) -> Any:
        """firebase_admin.messaging module, or None when unusable."""
        if self._messaging is not None:
            return self._messaging
        if self.dry_run or not self.credentials_path:
            self._messaging = None
            return None
        try:
            import firebase_admin
            from firebase_admin import credentials, messaging  # type: ignore
        except ImportError as exc:  # pragma: no cover - env-dependent
            raise RuntimeError(
                "FCM real mode requires firebase-admin; add it to requirements.txt"
            ) from exc
        if not firebase_admin._apps:
            firebase_admin.initialize_app(credentials.Certificate(self.credentials_path))
        self._messaging = messaging
        return messaging

    def send(self, token: str, payload: dict[str, Any]) -> SendResult:
        """Send one notification. Never raises; failures come back as results."""
        self.sent_messages.append((token, payload))
        data = payload.get("data", {})
        if self.dry_run:
            logger.info(
                "FCM dry-run: token=%s type=%s challenge_id=%s title=%r",
                token, data.get("type"), data.get("challenge_id"), payload.get("title"),
            )
            return SendResult(success=True, message_id="dry-run")

        messaging = self._messaging_module()
        if messaging is None:
            return SendResult(success=False, error="FCM not configured (missing credentials)")
        try:
            message = messaging.Message(
                token=token,
                notification=messaging.Notification(
                    title=payload["title"], body=payload["body"]
                ),
                data={k: str(v) for k, v in data.items()},
                android=messaging.AndroidConfig(priority="high"),
            )
            message_id = messaging.send(message)
            return SendResult(success=True, message_id=message_id)
        except Exception as exc:  # noqa: BLE001 - per-token failure surface
            logger.warning("FCM send failed: %s", exc)
            return SendResult(success=False, error=str(exc)[:500])


_sender: FcmSender | None = None


def get_sender() -> FcmSender:
    """Process-wide default sender built from settings (dry-run by default)."""
    global _sender
    if _sender is None:
        _sender = FcmSender(
            dry_run=settings.fcm_dry_run,
            credentials_path=settings.fcm_credentials_path,
        )
    return _sender


def reset_sender() -> None:
    global _sender
    _sender = None


# ------------------------------------------------------------ delivery ---

def _user_tokens(db: Session, user_id: int) -> list[str]:
    return list(
        db.scalars(select(Device.device_token).where(Device.user_id == user_id))
    )


def recent_successful_delivery(
    db: Session,
    user_id: int,
    push_type: str,
    now: datetime,
    hours: int = BEAT_YOU_THROTTLE_HOURS,
) -> bool:
    """True when a successful delivery of `push_type` exists in the window."""
    cutoff = now - timedelta(hours=hours)
    row = db.scalar(
        select(FcmDelivery.id)
        .where(
            FcmDelivery.user_id == user_id,
            FcmDelivery.type == push_type,
            FcmDelivery.success.is_(True),
            FcmDelivery.sent_at >= cutoff,
        )
        .limit(1)
    )
    return row is not None


def _record_delivery(
    db: Session,
    user_id: int,
    push_type: str,
    result: SendResult,
    payload: dict[str, Any],
    now: datetime,
) -> None:
    db.add(
        FcmDelivery(
            user_id=user_id,
            type=push_type,
            success=result.success,
            message_id=result.message_id,
            error=result.error,
            payload=payload,
            sent_at=now,
        )
    )
    db.commit()


def send_to_user(
    db: Session,
    sender: FcmSender,
    user_id: int,
    push_type: str,
    payload: dict[str, Any],
    now: datetime,
) -> SendResult | None:
    """Send `payload` to every device registered to `user_id`.

    Returns the last SendResult, or None when throttled / no tokens.
    Throttle (beat_you): one successful delivery per rolling 24h per user.
    """
    if push_type == PUSH_TYPE_BEAT_YOU and recent_successful_delivery(
        db, user_id, push_type, now
    ):
        logger.info(
            "FCM throttle: skip %s for user %s (1 per %dh)",
            push_type, user_id, BEAT_YOU_THROTTLE_HOURS,
        )
        return None

    tokens = _user_tokens(db, user_id)
    if not tokens:
        logger.info("FCM: user %s has no device tokens; skip %s", user_id, push_type)
        return None

    last: SendResult | None = None
    for token in tokens:
        last = sender.send(token, payload)
        if sender.log_deliveries:
            _record_delivery(db, user_id, push_type, last, payload, now)
    return last


# ----------------------------------------------------------- notify fns ---

def _notify_participants(
    db: Session,
    sender: FcmSender,
    challenge: Any,
    push_type: str,
    payload: dict[str, Any],
    now: datetime,
) -> None:
    from app.models import ChallengeParticipant

    participant_ids = list(
        db.scalars(
            select(ChallengeParticipant.user_id).where(
                ChallengeParticipant.challenge_id == challenge.id
            )
        )
    )
    for uid in participant_ids:
        send_to_user(db, sender, uid, push_type, payload, now)


def notify_challenge_started(
    db: Session, sender: FcmSender, challenge: Any, now: datetime
) -> None:
    _notify_participants(
        db, sender, challenge, PUSH_TYPE_CHALLENGE_STARTED,
        build_challenge_started(challenge), now,
    )


def notify_challenge_ended(
    db: Session, sender: FcmSender, challenge: Any, now: datetime
) -> None:
    _notify_participants(
        db, sender, challenge, PUSH_TYPE_CHALLENGE_ENDED,
        build_challenge_ended(challenge), now,
    )


def notify_beat_you(
    db: Session,
    sender: FcmSender,
    challenge: Any,
    overtaken_user_id: int,
    opponent_display_name: str | None,
    now: datetime,
) -> SendResult | None:
    return send_to_user(
        db, sender, overtaken_user_id, PUSH_TYPE_BEAT_YOU,
        build_beat_you(challenge, opponent_display_name), now,
    )
