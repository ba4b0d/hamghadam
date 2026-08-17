"""Daily ingest contract.

Identity: the authenticated user (from the JWT) owns the data — there is no
`user` field in the request body. This mirrors the spike payload
{user, date, tz_offset, steps, sleep_seconds?, avg_hr?, source_apps[]} minus
`user`, which the real API derives from the bearer token.

Anti-cheat v1: `source` must be "health_connect" (device-generated). Manual
entry is rejected at the API boundary — there is no manual-ingest code path.
"""

from datetime import date, datetime

from pydantic import BaseModel, ConfigDict, Field

TZ_OFFSET_MIN = -14 * 60
TZ_OFFSET_MAX = 14 * 60

# Sanity bounds — log-only per plan (anti-cheat), not hard rejections.
STEPS_SANITY_MAX = 250_000
SLEEP_SECONDS_SANITY_MAX = 24 * 3600  # 24h
AVG_HR_SANITY_MAX = 250.0


class DailyIngestRequest(BaseModel):
    model_config = ConfigDict(extra="forbid")

    date: date
    tz_offset: int = Field(ge=TZ_OFFSET_MIN, le=TZ_OFFSET_MAX, description="Minutes east of UTC")
    steps: int = Field(ge=0, le=1_000_000)
    sleep_seconds: float | None = Field(default=None, ge=0, le=200_000)
    avg_hr: float | None = Field(default=None, ge=0, le=500)
    source_apps: list[str] = Field(default_factory=list, max_length=50)
    source: str = Field(default="health_connect", pattern=r"^[a-z0-9_]+$")


class DailyOut(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    date: date
    tz_offset: int
    steps: int
    sleep_seconds: float | None
    avg_hr: float | None
    source_apps: list[str]
    source: str
    updated_at: datetime


class DailyListOut(BaseModel):
    items: list[DailyOut]
