"""Friendship schemas for social connections and requests."""

from datetime import datetime
from typing import Any

from pydantic import BaseModel, ConfigDict, EmailStr, Field, model_validator

from app.schemas.user import UserPublicOut


class FriendRequestCreate(BaseModel):
    model_config = ConfigDict(extra="ignore")

    target_user_id: int | None = None
    addressee_id: int | None = None

    @model_validator(mode="before")
    @classmethod
    def check_target(cls, data: Any) -> Any:
        if isinstance(data, dict):
            target = data.get("target_user_id")
            if target is None:
                target = data.get("addressee_id")
            if target is None:
                raise ValueError("target_user_id or addressee_id is required")
            try:
                target_int = int(target)
            except (ValueError, TypeError):
                raise ValueError("target_user_id must be an integer or integer string")
            data["target_user_id"] = target_int
            data["addressee_id"] = target_int
        return data


class FriendshipOut(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    id: int
    requester_id: int
    addressee_id: int
    status: str
    created_at: datetime
    updated_at: datetime | None = None


class PendingRequestOut(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    request_id: int
    requester: UserPublicOut
    created_at: datetime


class FriendProfileOut(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    id: int
    email: EmailStr
    display_name: str | None = None
    avatar_url: str | None = None
    bio: str | None = None
    location: str | None = None
    today_steps: int = 0
