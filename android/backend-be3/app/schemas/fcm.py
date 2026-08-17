"""FCM token registration contract (BE-C3).

The Android client registers its Firebase Cloud Messaging registration token
here after login (FE-C3). The token is upserted on the existing `devices`
table (key: user_id + token) so push targets reuse the same device registry
as BE-C1's `/users/me/device`.
"""

from datetime import datetime

from pydantic import BaseModel, ConfigDict, Field


class FcmTokenRegisterRequest(BaseModel):
    model_config = ConfigDict(extra="forbid")

    token: str = Field(
        min_length=1,
        max_length=512,
        description="Firebase Cloud Messaging registration token (from FirebaseMessaging.getToken())",
    )
    platform: str = Field(default="android", pattern=r"^(android|web|ios)$")


class FcmTokenOut(BaseModel):
    status: str = "ok"
    token: str
    platform: str
    registered_at: datetime
