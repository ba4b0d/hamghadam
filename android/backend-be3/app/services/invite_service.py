"""Invite (join code) use cases (BE-C3).

Invite v1 = random join code shared via deep link
(`fitnessapp://challenges/{id}/join?code=...`); no phonebook matching.
Codes are multi-use until `expires_at`. Validation of a code at join time
lives in `challenge_service.join_challenge` (it needs the challenge context);
this module only creates codes.

Code alphabet excludes ambiguous characters (0/O/1/I/L) so the code can be
typed from a screenshot; 8 chars from a 32-char alphabet = ~1.1e12 space.
"""

from __future__ import annotations

import secrets
from datetime import datetime, timedelta
from datetime import timezone

from sqlalchemy import select
from sqlalchemy.orm import Session

from app.models import ChallengeInvite, ChallengeParticipant
from app.models.challenge import CHALLENGE_STATUS_ENDED
from app.services.challenge_service import ChallengeError, get_challenge

CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
CODE_LENGTH = 8


def generate_code() -> str:
    return "".join(secrets.choice(CODE_ALPHABET) for _ in range(CODE_LENGTH))


def _utc(dt: datetime) -> datetime:
    if dt.tzinfo is None:
        return dt.replace(tzinfo=timezone.utc)
    return dt.astimezone(timezone.utc)


def create_invite(
    db: Session,
    user,
    challenge_id: int,
    ttl_hours: int,
    now: datetime,
) -> ChallengeInvite:
    """Create a join code for a challenge (creator or any participant).

    403 for non-participants, 409 after the challenge has ended.
    """
    challenge = get_challenge(db, challenge_id, now)
    if challenge.status == CHALLENGE_STATUS_ENDED:
        raise ChallengeError(409, "Challenge has ended; invites are closed")
    if challenge.creator_id != user.id:
        participant = db.scalar(
            select(ChallengeParticipant).where(
                ChallengeParticipant.challenge_id == challenge.id,
                ChallengeParticipant.user_id == user.id,
            )
        )
        if participant is None:
            raise ChallengeError(
                403,
                "Only the creator or a participant can create invites for a challenge",
            )

    invite = ChallengeInvite(
        challenge_id=challenge.id,
        code=generate_code(),
        created_by=user.id,
        expires_at=_utc(now) + timedelta(hours=ttl_hours),
    )
    db.add(invite)
    db.commit()
    db.refresh(invite)
    return invite
