from datetime import datetime

from pydantic import BaseModel, ConfigDict, Field


class DeviceRegisterRequest(BaseModel):
    model_config = ConfigDict(extra="forbid")

    device_token: str = Field(min_length=1, max_length=512)
    kind: str = Field(default="android", pattern=r"^(android|web|ios|unknown)$")
    model: str | None = Field(default=None, max_length=100)


class DeviceOut(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    id: int
    kind: str
    device_token: str
    model: str | None
    last_seen_at: datetime | None
    created_at: datetime
