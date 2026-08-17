"""User model. D4: free launch — premium is a plain bool, no billing code in v1."""

from datetime import datetime

from sqlalchemy import Boolean, DateTime, Integer, String, Text, func
from sqlalchemy.orm import Mapped, mapped_column, relationship

from app.core.database import Base


class User(Base):
    __tablename__ = "users"

    id: Mapped[int] = mapped_column(Integer, primary_key=True, autoincrement=True)
    email: Mapped[str] = mapped_column(String(255), unique=True, index=True, nullable=False)
    password_hash: Mapped[str] = mapped_column(String(255), nullable=False)
    display_name: Mapped[str | None] = mapped_column(String(100), nullable=True)
    bio: Mapped[str | None] = mapped_column(String(255), nullable=True)
    avatar_url: Mapped[str | None] = mapped_column(String(512), nullable=True)
    location: Mapped[str | None] = mapped_column(String(100), nullable=True)
    google_id: Mapped[str | None] = mapped_column(String(255), unique=True, index=True, nullable=True)
    auth_provider: Mapped[str] = mapped_column(String(32), default="email", nullable=False)
    premium: Mapped[bool] = mapped_column(Boolean, default=False, nullable=False)
    # Default TZ hint (minutes east of UTC); each daily_scores row carries its own offset.
    tz_offset: Mapped[int | None] = mapped_column(Integer, nullable=True)

    created_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True), server_default=func.now(), nullable=False
    )
    updated_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True), server_default=func.now(), onupdate=func.now(), nullable=False
    )

    devices: Mapped[list["Device"]] = relationship(back_populates="user", cascade="all, delete-orphan")  # noqa: F821
    daily_scores: Mapped[list["DailyScore"]] = relationship(back_populates="user", cascade="all, delete-orphan")  # noqa: F821
    sent_friendships: Mapped[list["Friendship"]] = relationship(  # noqa: F821
        "Friendship", foreign_keys="Friendship.requester_id", back_populates="requester", cascade="all, delete-orphan"
    )
    received_friendships: Mapped[list["Friendship"]] = relationship(  # noqa: F821
        "Friendship", foreign_keys="Friendship.addressee_id", back_populates="addressee", cascade="all, delete-orphan"
    )
