"""Invite (join code) API contract (BE-C3).

Invite v1 is a random join code shared via a deep link
(`fitnessapp://challenges/{id}/join?code=...`). Codes are multi-use until
`expires_at` — everyone with the link can join while it is valid. Expired or
unknown codes are rejected at join time with 403.
"""

from datetime import datetime

from pydantic import BaseModel, ConfigDict, Field

INVITE_DEFAULT_TTL_HOURS = 24 * 7  # 7 days
INVITE_MIN_TTL_HOURS = 1
INVITE_MAX_TTL_HOURS = 24 * 30  # 30 days


class InviteCreateRequest(BaseModel):
    model_config = ConfigDict(extra="forbid")

    ttl_hours: int = Field(
        default=INVITE_DEFAULT_TTL_HOURS,
        ge=INVITE_MIN_TTL_HOURS,
        le=INVITE_MAX_TTL_HOURS,
        description="Hours the code stays valid (1..720; default 168 = 7 days)",
    )


class InviteOut(BaseModel):
    challenge_id: int
    code: str
    expires_at: datetime
    deep_link: str
    created_at: datetime
