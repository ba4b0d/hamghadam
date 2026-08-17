"""Challenge + participant models (BE-C2).

A challenge is a scored window over one metric (`metric`, default `steps` —
same vocabulary as daily_scores so the leaderboard is metric-generic).

Status lifecycle is draft -> active -> ended:
  - `draft`   created, starts_at in the future
  - `active`  starts_at reached (or creator force-starts via PATCH status)
  - `ended`   ends_at reached (or creator force-ends via PATCH status)
Time-driven propagation happens lazily on reads/joins (see services).

Anti-cheat: there is no score-write path on a challenge. Leaderboards read
only existing daily_scores rows, which are Health Connect-only by BE-C1
design (no manual-entry channel exists anywhere in the API).
"""

from datetime import datetime

from sqlalchemy import (
    Boolean,
    DateTime,
    ForeignKey,
    Integer,
    String,
    UniqueConstraint,
    func,
)
from sqlalchemy.orm import Mapped, mapped_column, relationship

from app.core.database import Base

CHALLENGE_STATUS_DRAFT = "draft"
CHALLENGE_STATUS_ACTIVE = "active"
CHALLENGE_STATUS_ENDED = "ended"
CHALLENGE_STATUSES = (CHALLENGE_STATUS_DRAFT, CHALLENGE_STATUS_ACTIVE, CHALLENGE_STATUS_ENDED)


class Challenge(Base):
    __tablename__ = "challenges"

    id: Mapped[int] = mapped_column(Integer, primary_key=True, autoincrement=True)
    title: Mapped[str] = mapped_column(String(120), nullable=False)
    metric: Mapped[str] = mapped_column(String(32), default="steps", nullable=False)
    starts_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), nullable=False)
    ends_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), nullable=False)
    status: Mapped[str] = mapped_column(String(16), default=CHALLENGE_STATUS_DRAFT, nullable=False)
    invite_only: Mapped[bool] = mapped_column(Boolean, default=False, nullable=False)
    # Optional participant cap (includes the creator, who auto-joins). NULL = unlimited.
    # Enforced at join time (BE-C3) so invites validate "not-full" too.
    max_participants: Mapped[int | None] = mapped_column(Integer, nullable=True)
    creator_id: Mapped[int] = mapped_column(
        ForeignKey("users.id", ondelete="CASCADE"), index=True, nullable=False
    )

    created_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True), server_default=func.now(), nullable=False
    )
    updated_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True), server_default=func.now(), onupdate=func.now(), nullable=False
    )

    creator: Mapped["User"] = relationship()  # noqa: F821
    participants: Mapped[list["ChallengeParticipant"]] = relationship(
        back_populates="challenge", cascade="all, delete-orphan"
    )


class ChallengeParticipant(Base):
    __tablename__ = "challenge_participants"
    __table_args__ = (
        UniqueConstraint("challenge_id", "user_id", name="uq_participant_challenge_user"),
    )

    id: Mapped[int] = mapped_column(Integer, primary_key=True, autoincrement=True)
    challenge_id: Mapped[int] = mapped_column(
        ForeignKey("challenges.id", ondelete="CASCADE"), index=True, nullable=False
    )
    user_id: Mapped[int] = mapped_column(
        ForeignKey("users.id", ondelete="CASCADE"), index=True, nullable=False
    )
    joined_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True), server_default=func.now(), nullable=False
    )

    challenge: Mapped["Challenge"] = relationship(back_populates="participants")  # noqa: F821
    user: Mapped["User"] = relationship()  # noqa: F821
