"""Daily score rows — one row per (user, metric, local date).

Metric-extensible (`metric` column): v1 plays `steps` only; `sleep_seconds` and
`avg_hr` are stored for future challenges. "Day" = user local day: `date` is the
local calendar date as reported by the client, `tz_offset` is the client's offset
from UTC in minutes at ingest time.

Anti-cheat v1: only Health Connect-synced data is accepted (`source =
'health_connect'`); the API rejects any other source, so there is no manual-entry
channel in v1.
"""

from datetime import date, datetime

from sqlalchemy import (
    JSON,
    Date,
    DateTime,
    Float,
    ForeignKey,
    Index,
    Integer,
    String,
    UniqueConstraint,
    func,
)
from sqlalchemy.orm import Mapped, mapped_column, relationship

from app.core.database import Base

# Valid metric names (v1 playable: steps; sleep/HR stored for future challenge types).
METRIC_STEPS = "steps"
METRIC_SLEEP_SECONDS = "sleep_seconds"
METRIC_AVG_HR = "avg_hr"
SUPPORTED_METRICS = (METRIC_STEPS, METRIC_SLEEP_SECONDS, METRIC_AVG_HR)

# Only Health Connect device-generated data accepted in v1.
SOURCE_HEALTH_CONNECT = "health_connect"
ALLOWED_SOURCES = (SOURCE_HEALTH_CONNECT,)


class DailyScore(Base):
    __tablename__ = "daily_scores"
    __table_args__ = (
        UniqueConstraint("user_id", "metric", "date", name="uq_daily_user_metric_date"),
        Index("ix_daily_user_date", "user_id", "date"),
    )

    id: Mapped[int] = mapped_column(Integer, primary_key=True, autoincrement=True)
    user_id: Mapped[int] = mapped_column(
        ForeignKey("users.id", ondelete="CASCADE"), index=True, nullable=False
    )
    metric: Mapped[str] = mapped_column(String(32), nullable=False)
    date: Mapped[date] = mapped_column(Date, nullable=False)
    value: Mapped[float] = mapped_column(Float, nullable=False)
    tz_offset: Mapped[int] = mapped_column(Integer, nullable=False)  # minutes east of UTC
    source: Mapped[str] = mapped_column(String(32), default=SOURCE_HEALTH_CONNECT, nullable=False)
    source_apps: Mapped[list] = mapped_column(JSON, default=list, nullable=False)

    created_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True), server_default=func.now(), nullable=False
    )
    updated_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True), server_default=func.now(), onupdate=func.now(), nullable=False
    )

    user: Mapped["User"] = relationship(back_populates="daily_scores")  # noqa: F821
