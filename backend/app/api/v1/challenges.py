"""Challenge endpoints: CRUD, join/leave, status, leaderboard (BE-C2)."""

from datetime import date

from fastapi import APIRouter, Depends, HTTPException, Query, Response, status
from sqlalchemy import select
from sqlalchemy.orm import Session

from app.api.deps import get_current_user
from app.core.database import get_db
from app.models import Challenge, ChallengeParticipant, User
from app.schemas.challenge import (
    ChallengeCreateRequest,
    ChallengeOut,
    ChallengeStatusUpdateRequest,
    LeaderboardEntryOut,
    LeaderboardOut,
    ParticipantProgressOut,
    UserBriefOut,
)
from app.schemas.invite import InviteCreateRequest, InviteOut
from app.services.challenge_service import (
    ChallengeError,
    cancel_challenge,
    compute_leaderboard,
    create_challenge,
    get_challenge,
    join_challenge,
    leave_challenge,
    participant_totals,
    update_challenge_status,
    utcnow,
)
from app.services.invite_service import create_invite
from app.services.fcm import invite_deep_link

router = APIRouter(prefix="/challenges", tags=["challenges"])


def _raise_challenge_error(exc: ChallengeError) -> None:
    raise HTTPException(status_code=exc.status_code, detail=exc.detail) from exc


def _challenge_out(
    db: Session, challenge: Challenge, now, viewer: User | None = None
) -> ChallengeOut:
    participants = list(
        db.scalars(
            select(ChallengeParticipant)
            .where(ChallengeParticipant.challenge_id == challenge.id)
            .order_by(ChallengeParticipant.joined_at)
        )
    )
    totals = participant_totals(db, challenge, participants, now)
    totals_by_user = {t["user_id"]: t for t in totals}
    participants_out = [
        ParticipantProgressOut(
            user_id=p.user_id,
            display_name=p.user.display_name,
            is_creator=challenge.creator_id == p.user_id,
            joined_at=p.joined_at,
            total=totals_by_user.get(p.user_id, {}).get("total", 0.0),
        )
        for p in participants
    ]
    return ChallengeOut(
        id=challenge.id,
        title=challenge.title,
        metric=challenge.metric,
        starts_at=challenge.starts_at,
        ends_at=challenge.ends_at,
        status=challenge.status,
        invite_only=challenge.invite_only,
        max_participants=challenge.max_participants,
        creator=UserBriefOut(id=challenge.creator_id, display_name=challenge.creator.display_name),
        created_at=challenge.created_at,
        updated_at=challenge.updated_at,
        participants=participants_out,
    )


@router.post("", response_model=ChallengeOut, status_code=status.HTTP_201_CREATED)
def create(
    payload: ChallengeCreateRequest,
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db),
) -> ChallengeOut:
    """Create a challenge; the creator is automatically the first participant."""
    try:
        challenge = create_challenge(db, current_user, payload, utcnow())
    except ChallengeError as exc:
        _raise_challenge_error(exc)
    return _challenge_out(db, challenge, utcnow(), current_user)


@router.get("", response_model=list[ChallengeOut])
def list_mine(
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db),
) -> list[ChallengeOut]:
    """Challenges I participate in (created or joined), newest first."""
    now = utcnow()
    challenges = db.scalars(
        select(Challenge)
        .join(ChallengeParticipant, ChallengeParticipant.challenge_id == Challenge.id)
        .where(ChallengeParticipant.user_id == current_user.id)
        .order_by(Challenge.created_at.desc())
    ).all()
    return [_challenge_out(db, c, now, current_user) for c in challenges]


@router.get("/{challenge_id}", response_model=ChallengeOut)
def detail(
    challenge_id: int,
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db),
) -> ChallengeOut:
    try:
        challenge = get_challenge(db, challenge_id, utcnow())
    except ChallengeError as exc:
        _raise_challenge_error(exc)
    return _challenge_out(db, challenge, utcnow(), current_user)


@router.post("/{challenge_id}/join", response_model=ChallengeOut, status_code=status.HTTP_200_OK)
def join(
    challenge_id: int,
    code: str | None = Query(default=None, description="Invite code (BE-C3); required for invite-only challenges"),
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db),
) -> ChallengeOut:
    """Join a challenge. Not allowed after it has ended; duplicate joins are 409.

    `invite_only` challenges require a valid invite code (`?code=...` from
    `POST /challenges/{id}/invites`); expired/unknown codes are rejected 403.
    A challenge at its `max_participants` cap rejects new joins with 409.
    The creator is already a participant.
    """
    try:
        challenge = join_challenge(db, current_user, challenge_id, utcnow(), invite_code=code)
    except ChallengeError as exc:
        _raise_challenge_error(exc)
    return _challenge_out(db, challenge, utcnow(), current_user)


@router.post("/{challenge_id}/invites", response_model=InviteOut, status_code=status.HTTP_201_CREATED)
def create_invite_code(
    challenge_id: int,
    payload: InviteCreateRequest,
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db),
) -> InviteOut:
    """Create a join code for a challenge (creator or any participant).

    The code is multi-use until `expires_at` — share the deep link with
    friends; anyone can join while it is valid. 403 for non-participants,
    409 after the challenge has ended.
    """
    try:
        invite = create_invite(db, current_user, challenge_id, payload.ttl_hours, utcnow())
    except ChallengeError as exc:
        _raise_challenge_error(exc)
    return InviteOut(
        challenge_id=invite.challenge_id,
        code=invite.code,
        expires_at=invite.expires_at,
        deep_link=invite_deep_link(invite.challenge_id, invite.code),
        created_at=invite.created_at,
    )


@router.post("/{challenge_id}/leave", response_model=ChallengeOut)
def leave(
    challenge_id: int,
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db),
) -> ChallengeOut:
    try:
        challenge = leave_challenge(db, current_user, challenge_id, utcnow())
    except ChallengeError as exc:
        _raise_challenge_error(exc)
    return _challenge_out(db, challenge, utcnow(), current_user)


@router.delete("/{challenge_id}", status_code=status.HTTP_204_NO_CONTENT)
def cancel_challenge_endpoint(
    challenge_id: int,
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db),
):
    """Creator-only endpoint to cancel/delete a challenge before it has ended."""
    try:
        cancel_challenge(db, current_user, challenge_id, utcnow())
    except ChallengeError as exc:
        _raise_challenge_error(exc)
    return Response(status_code=status.HTTP_204_NO_CONTENT)


@router.patch("/{challenge_id}/status", response_model=ChallengeOut)
def set_status(
    challenge_id: int,
    payload: ChallengeStatusUpdateRequest,
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db),
) -> ChallengeOut:
    """Creator-only manual status propagation (draft->active, {draft,active}->ended).

    Time-driven propagation still happens automatically on any read; this
    endpoint is for explicit start/end control (e.g. "start now").
    """
    try:
        challenge = update_challenge_status(db, current_user, challenge_id, payload.status, utcnow())
    except ChallengeError as exc:
        _raise_challenge_error(exc)
    return _challenge_out(db, challenge, utcnow(), current_user)


@router.get("/{challenge_id}/leaderboard", response_model=LeaderboardOut)
def leaderboard(
    challenge_id: int,
    as_of: date | None = Query(
        default=None,
        description="Inclusive cutoff on each participant's local date (default: per-participant local date today)",
    ),
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db),
) -> LeaderboardOut:
    """Ranked daily + total standings, per-participant local day.

    Ties are broken deterministically: total desc, then display_name asc
    (case-insensitive), then user_id asc. Re-ingesting a daily score changes
    the board deterministically (latest value wins, per BE-C1 upsert).
    """
    try:
        challenge = get_challenge(db, challenge_id, utcnow())
    except ChallengeError as exc:
        _raise_challenge_error(exc)
    result = compute_leaderboard(db, challenge, utcnow(), as_of, current_user.id)
    result["entries"] = [LeaderboardEntryOut(**e) for e in result["entries"]]
    return LeaderboardOut(**result)
