"""Challenge use cases: lifecycle, join/leave rules, TZ-aware leaderboard math.

All scoring reads `daily_scores` rows only — there is deliberately no
score-write path on a challenge (anti-cheat: the only ingest channel is the
Health Connect-only /daily endpoint from BE-C1).

Time conventions:
- Challenge `starts_at`/`ends_at` are UTC instants (naive input is treated as
  UTC; `_utc()` normalizes so SQLite's naive round-trip is handled too).
- A participant's window is the inclusive local-date range
  `[local_date(starts_at, tz), local_date(ends_at, tz)]` where `tz` is the
  participant's `users.tz_offset` in minutes east of UTC (0 if unset).
- `as_of` is an inclusive cutoff on each participant's local date. When the
  caller passes one it applies to every participant; when omitted the default
  is *per participant* — that participant's local calendar date "today"
  (`local_date(now, tz)`), so an east-of-UTC user's local-today ingest is
  never dropped just because the server's UTC date is one behind (the daily
  00:00-03:30 Tehran skew). The leaderboard's `as_of` field then reports the
  latest per-participant cutoff. Daily series are zero-filled for the window
  so the client can render charts deterministically.

Status lifecycle (draft -> active -> ended):
- Time-driven: reads/joins lazily persist transitions when now passes
  starts_at/ends_at (`sync_challenge_status`).
- Creator-driven: PATCH /status allows draft->active and {draft,active}->ended
  only. No backward transitions.
"""

from __future__ import annotations

import logging
from datetime import date, datetime, timezone
from datetime import timedelta

from sqlalchemy import func, select
from sqlalchemy.orm import Session

from app.models import Challenge, ChallengeInvite, ChallengeParticipant, DailyScore, User
from app.models.challenge import (
    CHALLENGE_STATUS_ACTIVE,
    CHALLENGE_STATUS_DRAFT,
    CHALLENGE_STATUS_ENDED,
)
from app.models.daily_score import METRIC_STEPS, SUPPORTED_METRICS
from app.schemas.challenge import ChallengeCreateRequest
from app.services.fcm import FcmSender, get_sender, notify_beat_you, notify_challenge_ended, notify_challenge_started

logger = logging.getLogger("app.challenges")

# Soft cap: beyond a year, daily series are not zero-filled (avoids huge
# payloads for misconfigured long challenges); only real rows are listed.
MAX_ZERO_FILL_DAYS = 366


class ChallengeError(Exception):
    """Domain error mapped to an HTTP response by the API layer."""

    def __init__(self, status_code: int, detail: str):
        super().__init__(detail)
        self.status_code = status_code
        self.detail = detail


def _utc(dt: datetime) -> datetime:
    """Normalize a possibly-naive datetime to aware UTC."""
    if dt.tzinfo is None:
        return dt.replace(tzinfo=timezone.utc)
    return dt.astimezone(timezone.utc)


def utcnow() -> datetime:
    return datetime.now(timezone.utc)


def _local_date(instant: datetime, tz_offset_minutes: int) -> date:
    return (_utc(instant) + timedelta(minutes=tz_offset_minutes)).date()


def sync_challenge_status(db: Session, challenge: Challenge, now: datetime) -> str:
    """Lazily propagate time-driven draft->active->ended transitions (persisted)."""
    now_utc = _utc(now)
    changed = False
    if challenge.status == CHALLENGE_STATUS_DRAFT and now_utc >= _utc(challenge.starts_at):
        challenge.status = CHALLENGE_STATUS_ACTIVE
        changed = True
    if challenge.status in (CHALLENGE_STATUS_DRAFT, CHALLENGE_STATUS_ACTIVE) and now_utc >= _utc(challenge.ends_at):
        challenge.status = CHALLENGE_STATUS_ENDED
        changed = True
    if changed:
        db.add(challenge)
        db.commit()
    return challenge.status


def create_challenge(
    db: Session, creator: User, payload: ChallengeCreateRequest, now: datetime
) -> Challenge:
    if payload.metric not in SUPPORTED_METRICS:
        raise ChallengeError(422, f"metric must be one of {', '.join(SUPPORTED_METRICS)}")
    starts_at = _utc(payload.starts_at)
    ends_at = _utc(payload.ends_at)
    if starts_at >= ends_at:
        raise ChallengeError(422, "starts_at must be before ends_at")
    if ends_at <= _utc(now):
        raise ChallengeError(422, "ends_at must be in the future")

    status = CHALLENGE_STATUS_ACTIVE if starts_at <= _utc(now) else CHALLENGE_STATUS_DRAFT
    challenge = Challenge(
        title=payload.title,
        metric=payload.metric,
        starts_at=starts_at,
        ends_at=ends_at,
        status=status,
        invite_only=payload.invite_only,
        max_participants=payload.max_participants,
        creator_id=creator.id,
    )
    db.add(challenge)
    db.flush()
    # Creator is automatically the first participant.
    db.add(ChallengeParticipant(challenge_id=challenge.id, user_id=creator.id))
    db.commit()
    db.refresh(challenge)
    return challenge


def get_challenge(db: Session, challenge_id: int, now: datetime) -> Challenge:
    challenge = db.get(Challenge, challenge_id)
    if challenge is None:
        raise ChallengeError(404, "Challenge not found")
    sync_challenge_status(db, challenge, now)
    return challenge


def _participant_count(db: Session, challenge_id: int) -> int:
    return (
        db.scalar(
            select(func.count())
            .select_from(ChallengeParticipant)
            .where(ChallengeParticipant.challenge_id == challenge_id)
        )
        or 0
    )


def _check_capacity(db: Session, challenge: Challenge) -> None:
    """Reject joins when the challenge is at its participant cap (\"not-full\")."""
    if challenge.max_participants is None:
        return
    if _participant_count(db, challenge.id) >= challenge.max_participants:
        raise ChallengeError(409, "Challenge is full (max participants reached)")


def _resolve_invite_code(db: Session, challenge: Challenge, code: str, now: datetime) -> None:
    """Validate a join code against this challenge; 403 when invalid/expired.

    Codes are multi-use until `expires_at`: any number of friends may join
    with the same code while it is valid. An expired or unknown code (or a
    code belonging to another challenge) is rejected — reusing an expired
    code never works.
    """
    invite = db.scalar(select(ChallengeInvite).where(ChallengeInvite.code == code))
    if invite is None or invite.challenge_id != challenge.id or _utc(now) >= _utc(invite.expires_at):
        raise ChallengeError(403, "Invalid or expired invite code")


def join_challenge(
    db: Session, user: User, challenge_id: int, now: datetime, invite_code: str | None = None
) -> Challenge:
    challenge = get_challenge(db, challenge_id, now)
    if challenge.status == CHALLENGE_STATUS_ENDED:
        raise ChallengeError(409, "Challenge has ended; no new participants")
    existing = db.scalar(
        select(ChallengeParticipant).where(
            ChallengeParticipant.challenge_id == challenge.id,
            ChallengeParticipant.user_id == user.id,
        )
    )
    if existing is not None:
        raise ChallengeError(409, "Already joined this challenge")
    if invite_code is not None:
        _resolve_invite_code(db, challenge, invite_code, now)
    elif challenge.invite_only:
        raise ChallengeError(
            403,
            "This challenge requires an invite code to join",
        )
    _check_capacity(db, challenge)
    participant = ChallengeParticipant(challenge_id=challenge.id, user_id=user.id)
    db.add(participant)
    db.commit()
    db.refresh(participant)
    return challenge


def leave_challenge(db: Session, user: User, challenge_id: int, now: datetime) -> Challenge:
    challenge = get_challenge(db, challenge_id, now)
    if challenge.status == CHALLENGE_STATUS_ENDED:
        raise ChallengeError(409, "Challenge has ended; results are frozen")
    if challenge.creator_id == user.id:
        raise ChallengeError(400, "Creator cannot leave the challenge")
    participant = db.scalar(
        select(ChallengeParticipant).where(
            ChallengeParticipant.challenge_id == challenge.id,
            ChallengeParticipant.user_id == user.id,
        )
    )
    if participant is None:
        raise ChallengeError(404, "Not a participant of this challenge")
    db.delete(participant)
    db.commit()
    return challenge


def update_challenge_status(
    db: Session,
    user: User,
    challenge_id: int,
    new_status: str,
    now: datetime,
    sender: FcmSender | None = None,
) -> Challenge:
    challenge = get_challenge(db, challenge_id, now)
    if challenge.creator_id != user.id:
        raise ChallengeError(403, "Only the creator can change challenge status")
    allowed: dict[str, set[str]] = {
        CHALLENGE_STATUS_DRAFT: {CHALLENGE_STATUS_ACTIVE, CHALLENGE_STATUS_ENDED},
        CHALLENGE_STATUS_ACTIVE: {CHALLENGE_STATUS_ENDED},
        CHALLENGE_STATUS_ENDED: set(),
    }
    if new_status not in allowed[challenge.status]:
        raise ChallengeError(
            409,
            f"Cannot move challenge from '{challenge.status}' to '{new_status}'",
        )
    old_status = challenge.status
    challenge.status = new_status
    db.add(challenge)
    db.commit()
    db.refresh(challenge)

    # FCM push on explicit transitions (fire-and-forget; never fail the call).
    if old_status != new_status:
        try:
            sender = sender or get_sender()
            if new_status == CHALLENGE_STATUS_ACTIVE:
                notify_challenge_started(db, sender, challenge, _utc(now))
            elif new_status == CHALLENGE_STATUS_ENDED:
                notify_challenge_ended(db, sender, challenge, _utc(now))
        except Exception:  # noqa: BLE001 - push must not break the API
            logger.exception("FCM notification failed after status transition")
    return challenge


# ---------------------------------------------------------------- scoring ---

def _participant_window(challenge: Challenge, tz_offset_minutes: int) -> tuple[date, date]:
    return (
        _local_date(challenge.starts_at, tz_offset_minutes),
        _local_date(challenge.ends_at, tz_offset_minutes),
    )


def _metric_display_value(value: float, metric: str) -> float:
    return int(round(value)) if metric == METRIC_STEPS else value


def _score_rows_by_user(
    db: Session, challenge: Challenge, user_ids: list[int]
) -> dict[int, dict[date, float]]:
    """Map user_id -> {local date -> metric value} for the challenge metric."""
    if not user_ids:
        return {}
    rows = db.scalars(
        select(DailyScore).where(
            DailyScore.metric == challenge.metric,
            DailyScore.user_id.in_(user_ids),
        )
    ).all()
    by_user: dict[int, dict[date, float]] = {uid: {} for uid in user_ids}
    for row in rows:
        by_user.setdefault(row.user_id, {})[row.date] = row.value
    return by_user


def _series(
    window_start: date,
    window_end: date,
    cutoff: date,
    scores: dict[date, float],
    zero_fill: bool,
) -> list[tuple[date, float]]:
    """Local-date series from window_start..min(window_end, cutoff), zero-filled."""
    series_end = min(cutoff, window_end)
    if series_end < window_start:
        return []
    days = (series_end - window_start).days + 1
    if days > MAX_ZERO_FILL_DAYS and zero_fill:
        zero_fill = False
    out: list[tuple[date, float]] = []
    for offset in range(days):
        day = window_start + timedelta(days=offset)
        out.append((day, scores.get(day, 0.0)))
    if not zero_fill:
        out = [(d, v) for d, v in out if v != 0.0]
    return out


def _default_cutoff(now: datetime, tz_offset_minutes: int) -> date:
    """Per-participant default cutoff: that participant's local calendar today.

    `now` is an aware UTC instant; the participant's local date is derived by
    applying their `tz_offset`, so the cutoff never lags an east-of-UTC user's
    own "today" (DB-1: leaderboard/beat-you dropped local-today rows in the
    daily 00:00-03:30 Tehran skew window when cutoff was the server UTC date).
    """
    return _local_date(_utc(now), tz_offset_minutes)


def _compute_participant_scores(
    db: Session,
    challenge: Challenge,
    participant: ChallengeParticipant,
    scores_by_user: dict[int, dict[date, float]],
    as_of: date | None,
    now: datetime,
    zero_fill: bool,
) -> tuple[list[tuple[date, float]], float]:
    tz = participant.user.tz_offset or 0
    window_start, window_end = _participant_window(challenge, tz)
    cutoff = as_of if as_of is not None else _default_cutoff(now, tz)
    series = _series(window_start, window_end, cutoff, scores_by_user.get(participant.user_id, {}), zero_fill)
    total = sum(v for _, v in series)
    return series, total


def participant_totals(
    db: Session,
    challenge: Challenge,
    participants: list[ChallengeParticipant],
    now: datetime,
) -> list[dict]:
    """Per-participant progress totals (as of each participant's local today), sorted desc."""
    scores_by_user = _score_rows_by_user(db, challenge, [p.user_id for p in participants])
    rows = []
    for p in participants:
        series, total = _compute_participant_scores(db, challenge, p, scores_by_user, None, now, zero_fill=False)
        rows.append(
            {
                "user_id": p.user_id,
                "display_name": p.user.display_name,
                "is_creator": challenge.creator_id == p.user_id,
                "joined_at": p.joined_at,
                "total": _metric_display_value(total, challenge.metric),
            }
        )
    rows.sort(key=lambda r: (-r["total"], (r["display_name"] or "").lower(), r["user_id"]))
    return rows


def compute_leaderboard(
    db: Session,
    challenge: Challenge,
    now: datetime,
    as_of: date | None,
    viewer_id: int,
) -> dict:
    """Ranked leaderboard: total + per-participant local-day series, ties broken
    deterministically (total desc, display_name asc case-insensitive, user_id asc).

    `as_of` (optional) is an inclusive cutoff applied to every participant's
    local date. When omitted, each participant is cut at their own local
    "today" (see `_default_cutoff`) — the returned `as_of` is then the latest
    per-participant cutoff so clients still get a single board date.
    """
    sync_challenge_status(db, challenge, now)

    participants = list(
        db.scalars(
            select(ChallengeParticipant)
            .where(ChallengeParticipant.challenge_id == challenge.id)
            .order_by(ChallengeParticipant.joined_at)
        )
    )
    scores_by_user = _score_rows_by_user(db, challenge, [p.user_id for p in participants])

    entries = []
    effective_cutoffs: list[date] = []
    for p in participants:
        series, total = _compute_participant_scores(
            db, challenge, p, scores_by_user, as_of, now, zero_fill=True
        )
        entries.append(
            {
                "user_id": p.user_id,
                "display_name": p.user.display_name,
                "total": _metric_display_value(total, challenge.metric),
                "daily": [{"date": d, "value": _metric_display_value(v, challenge.metric)} for d, v in series],
                "is_me": p.user_id == viewer_id,
            }
        )
        if as_of is None:
            effective_cutoffs.append(_default_cutoff(now, p.user.tz_offset or 0))

    if as_of is not None:
        board_as_of = as_of
    elif effective_cutoffs:
        board_as_of = max(effective_cutoffs)
    else:
        board_as_of = _utc(now).date()

    entries.sort(key=lambda e: (-e["total"], (e["display_name"] or "").lower(), e["user_id"]))
    for rank, entry in enumerate(entries, start=1):
        entry["rank"] = rank

    return {
        "challenge_id": challenge.id,
        "metric": challenge.metric,
        "status": challenge.status,
        "as_of": board_as_of,
        "entries": entries,
    }


# ----------------------------------------------------------- beat-you ---

def _standings_map(
    db: Session, challenge: Challenge, now: datetime
) -> dict[int, int]:
    """user_id -> rank for one challenge (as-of now), from the leaderboard."""
    board = compute_leaderboard(db, challenge, now, as_of=None, viewer_id=0)
    return {e["user_id"]: e["rank"] for e in board["entries"]}


def capture_standings(
    db: Session, user_id: int, now: datetime
) -> dict[int, dict[int, int]]:
    """Pre-ingest snapshot: challenge_id -> {user_id: rank} for every *active*
    challenge `user_id` participates in.

    Call BEFORE the daily ingest commits; the returned map is the \"before\"
    board used to detect overtakes.
    """
    challenges = db.scalars(
        select(Challenge)
        .join(ChallengeParticipant, ChallengeParticipant.challenge_id == Challenge.id)
        .where(
            ChallengeParticipant.user_id == user_id,
            Challenge.status == CHALLENGE_STATUS_ACTIVE,
        )
    ).all()
    return {c.id: _standings_map(db, c, now) for c in challenges}


def notify_overtaken(
    db: Session,
    before_map: dict[int, dict[int, int]],
    ingesting_user_id: int,
    now: datetime,
    sender: FcmSender | None = None,
) -> list[int]:
    """After a daily ingest, notify anyone the ingesting user just overtook.

    `before_map` is the pre-ingest standings snapshot from
    `capture_standings`. For each active challenge, participants who were
    strictly above the ingesting user before and are strictly below now were
    overtaken; the notification goes to the overtaken user (throttled
    1/day/user).

    Returns the list of notified user ids (informational; failures are
    swallowed so push never breaks the ingest API).
    """
    if not before_map:
        return []

    sender = sender or get_sender()
    notified: list[int] = []

    for challenge_id, before in before_map.items():
        challenge = db.get(Challenge, challenge_id)
        if challenge is None:
            continue
        try:
            after = _standings_map(db, challenge, now)
        except Exception:  # noqa: BLE001 - one challenge must not break the rest
            logger.exception("beat-you detection failed for challenge %s", challenge_id)
            continue

        me_before = before.get(ingesting_user_id)
        me_after = after.get(ingesting_user_id)
        if me_before is None or me_after is None:
            continue

        ingester_name = None
        for e in compute_leaderboard(db, challenge, now, as_of=None, viewer_id=0)["entries"]:
            if e["user_id"] == ingesting_user_id:
                ingester_name = e["display_name"]
                break

        for uid, rank_before in before.items():
            rank_after = after.get(uid)
            if uid == ingesting_user_id or rank_after is None:
                continue
            # Overtaken: was above the ingester before, is below now.
            if rank_before < me_before and rank_after > me_after:
                notified.append(uid)
                try:
                    notify_beat_you(db, sender, challenge, uid, ingester_name, _utc(now))
                except Exception:  # noqa: BLE001
                    logger.exception("beat-you push failed for user %s", uid)

    return notified
