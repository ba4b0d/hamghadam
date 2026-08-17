"""User schemas for profile management and public user representations."""

from datetime import datetime

from pydantic import BaseModel, ConfigDict, EmailStr, Field


class UserOut(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    id: int
    email: EmailStr
    display_name: str | None = None
    bio: str | None = None
    avatar_url: str | None = None
    location: str | None = None
    premium: bool = False
    tz_offset: int | None = None
    auth_provider: str = "email"
    created_at: datetime


class UserUpdateRequest(BaseModel):
    model_config = ConfigDict(extra="forbid")

    display_name: str | None = Field(default=None, max_length=100)
    bio: str | None = Field(default=None, max_length=255)
    location: str | None = Field(default=None, max_length=100)
    tz_offset: int | None = Field(default=None, ge=-14 * 60, le=14 * 60)


class BioUpdateRequest(BaseModel):
    model_config = ConfigDict(extra="forbid")

    bio: str | None = Field(default=None, max_length=255)


class AvatarUploadResponse(BaseModel):
    avatar_url: str


class UserPublicOut(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    id: int
    display_name: str | None = None
    avatar_url: str | None = None
    bio: str | None = None
    location: str | None = None
    friendship_status: str = "NONE"
