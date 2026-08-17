"""Challenge invite (join code) model (BE-C3).

Invite v1 = a random join code shared via deep link
(`fitnessapp://challenges/{id}/join?code=...`); no phonebook/social graph.
Codes are multi-use until `expires_at` — any number of friends can join with
the same code while it is valid (this is what makes a share link work). An
unknown or expired code is rejected at join time (403). The code alphabet
excludes ambiguous characters (0/O/1/I/L) so it can be typed from a
screenshot or read aloud.
"""

from datetime import datetime

from sqlalchemy import DateTime, ForeignKey, Integer, String, func
from sqlalchemy.orm import Mapped, mapped_column, relationship

from app.core.database import Base


class ChallengeInvite(Base):
    __tablename__ = "challenge_invites"

    id: Mapped[int] = mapped_column(Integer, primary_key=True, autoincrement=True)
    challenge_id: Mapped[int] = mapped_column(
        ForeignKey("challenges.id", ondelete="CASCADE"), index=True, nullable=False
    )
    code: Mapped[str] = mapped_column(String(16), unique=True, index=True, nullable=False)
    created_by: Mapped[int] = mapped_column(
        ForeignKey("users.id", ondelete="CASCADE"), index=True, nullable=False
    )
    expires_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), nullable=False)

    created_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True), server_default=func.now(), nullable=False
    )

    challenge: Mapped["Challenge"] = relationship()  # noqa: F821
    creator: Mapped["User"] = relationship()  # noqa: F821
