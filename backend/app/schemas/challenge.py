"""Challenge API contract (BE-C2).

Time semantics:
- `starts_at` / `ends_at` are UTC instants.
- A participant's "local day" = their own calendar date, derived from their
  `users.tz_offset` (minutes east of UTC, ±14h). The leaderboard window for a
  participant is the inclusive local-date range
  `[local_date(starts_at), local_date(ends_at)]`.
- `as_of` on the leaderboard is an inclusive cutoff on each participant's
  local date. Passed explicitly it applies to every participant; when omitted
  the default is per participant — each user's own local calendar date
  "today" (DB-1 fix: the old server-UTC-date default dropped east-of-UTC
  local-today rows in the daily Tehran 00:00-03:30 skew). The board's `as_of`
  field then reports the latest per-participant cutoff.

Anti-cheat: no score field exists on any challenge payload. Scores are read
from `daily_scores`, which BE-C1 already restricts to Health Connect source.
"""

from datetime import date, datetime

from pydantic import BaseModel, ConfigDict, Field, field_validator

from app.models.challenge import CHALLENGE_STATUSES
from app.models.daily_score import SUPPORTED_METRICS

TZ_OFFSET_MIN = -14 * 60
TZ_OFFSET_MAX = 14 * 60


class ChallengeCreateRequest(BaseModel):
    model_config = ConfigDict(extra="forbid")

    title: str = Field(min_length=1, max_length=120)
    starts_at: datetime
    ends_at: datetime
    metric: str = Field(default="steps", description="steps|sleep_seconds|avg_hr (v1 playable: steps)")
    invite_only: bool = Field(default=False, description="v1: join code support ships in BE-C3")
    max_participants: int | None = Field(
        default=None,
        ge=2,
        le=1000,
        description="Optional participant cap including the creator; null = unlimited (BE-C3)",
    )

    @field_validator("metric")
    @classmethod
    def _metric_supported(cls, v: str) -> str:
        if v not in SUPPORTED_METRICS:
            raise ValueError(f"metric must be one of {', '.join(SUPPORTED_METRICS)}")
        return v


class ChallengeStatusUpdateRequest(BaseModel):
    model_config = ConfigDict(extra="forbid")

    status: str

    @field_validator("status")
    @classmethod
    def _status_known(cls, v: str) -> str:
        if v not in CHALLENGE_STATUSES:
            raise ValueError(f"status must be one of {', '.join(CHALLENGE_STATUSES)}")
        return v


class UserBriefOut(BaseModel):
    id: int
    display_name: str | None


class ParticipantProgressOut(BaseModel):
    user_id: int
    display_name: str | None
    is_creator: bool
    joined_at: datetime
    total: float  # sum of challenge metric over the window (as of the participant's local today)


class ChallengeOut(BaseModel):
    id: int
    title: str
    metric: str
    starts_at: datetime
    ends_at: datetime
    status: str
    invite_only: bool
    max_participants: int | None
    creator: UserBriefOut
    created_at: datetime
    updated_at: datetime
    participants: list[ParticipantProgressOut]


class DailyEntryOut(BaseModel):
    date: date
    value: float


class LeaderboardEntryOut(BaseModel):
    rank: int
    user_id: int
    display_name: str | None
    total: float
    daily: list[DailyEntryOut]  # one entry per participant-local day in window (0 when missing)
    is_me: bool


class LeaderboardOut(BaseModel):
    challenge_id: int
    metric: str
    status: str
    as_of: date
    entries: list[LeaderboardEntryOut]
