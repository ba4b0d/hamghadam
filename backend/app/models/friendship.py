"""Friendship model for social connections (V1.2 Social)."""

import enum
from datetime import datetime

from sqlalchemy import (
    CheckConstraint,
    DateTime,
    Enum,
    ForeignKey,
    Index,
    Integer,
    UniqueConstraint,
    func,
)
from sqlalchemy.orm import Mapped, mapped_column, relationship

from app.core.database import Base


class FriendshipStatus(str, enum.Enum):
    PENDING = "PENDING"
    ACCEPTED = "ACCEPTED"
    REJECTED = "REJECTED"
    BLOCKED = "BLOCKED"


class Friendship(Base):
    __tablename__ = "friendships"

    id: Mapped[int] = mapped_column(Integer, primary_key=True, autoincrement=True)
    requester_id: Mapped[int] = mapped_column(
        Integer, ForeignKey("users.id", ondelete="CASCADE"), nullable=False, index=True
    )
    addressee_id: Mapped[int] = mapped_column(
        Integer, ForeignKey("users.id", ondelete="CASCADE"), nullable=False, index=True
    )
    status: Mapped[FriendshipStatus] = mapped_column(
        Enum(FriendshipStatus, name="friendship_status_enum", native_enum=False),
        default=FriendshipStatus.PENDING,
        nullable=False,
    )
    created_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True), server_default=func.now(), nullable=False
    )
    updated_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True), server_default=func.now(), onupdate=func.now(), nullable=False
    )

    requester: Mapped["User"] = relationship(  # noqa: F821
        "User", foreign_keys=[requester_id], back_populates="sent_friendships"
    )
    addressee: Mapped["User"] = relationship(  # noqa: F821
        "User", foreign_keys=[addressee_id], back_populates="received_friendships"
    )

    __table_args__ = (
        UniqueConstraint("requester_id", "addressee_id", name="uq_friendship_requester_addressee"),
        CheckConstraint("requester_id != addressee_id", name="ck_friendship_no_self"),
        Index("idx_friendships_requester_status", "requester_id", "status"),
        Index("idx_friendships_addressee_status", "addressee_id", "status"),
    )
