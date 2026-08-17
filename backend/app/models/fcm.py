"""FCM push delivery log (BE-C3).

One row per notification attempt (success or failure). `type` is the
notification kind (`challenge_started|challenge_ended|beat_you`) and doubles
as the throttle key: beat-you notifications are capped at 1 per user per
rolling 24h (see app/services/fcm.py).

In dry-run mode (the default and the CI/test setting) no rows are written —
the sender is mocked so tests never touch Firebase and never leave rows.
"""

from datetime import datetime

from sqlalchemy import JSON, Boolean, DateTime, ForeignKey, Index, Integer, String, func
from sqlalchemy.orm import Mapped, mapped_column, relationship

from app.core.database import Base

# Notification kinds (also used as throttle keys).
PUSH_TYPE_CHALLENGE_STARTED = "challenge_started"
PUSH_TYPE_CHALLENGE_ENDED = "challenge_ended"
PUSH_TYPE_BEAT_YOU = "beat_you"


class FcmDelivery(Base):
    __tablename__ = "fcm_deliveries"
    __table_args__ = (Index("ix_fcm_user_type_sent", "user_id", "type", "sent_at"),)

    id: Mapped[int] = mapped_column(Integer, primary_key=True, autoincrement=True)
    user_id: Mapped[int] = mapped_column(
        ForeignKey("users.id", ondelete="CASCADE"), index=True, nullable=False
    )
    type: Mapped[str] = mapped_column(String(32), nullable=False)
    success: Mapped[bool] = mapped_column(Boolean, default=False, nullable=False)
    message_id: Mapped[str | None] = mapped_column(String(255), nullable=True)
    error: Mapped[str | None] = mapped_column(String(512), nullable=True)
    payload: Mapped[dict] = mapped_column(JSON, default=dict, nullable=False)

    sent_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True), server_default=func.now(), nullable=False
    )

    user: Mapped["User"] = relationship()  # noqa: F821
