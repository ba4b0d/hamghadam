"""User endpoints: profile + device registration."""

from datetime import datetime, timezone

from fastapi import APIRouter, Depends, status
from sqlalchemy import select
from sqlalchemy.orm import Session

from app.api.deps import get_current_user
from app.core.database import get_db
from app.models import Device, User
from app.schemas.auth import UserOut, UserUpdateRequest
from app.schemas.device import DeviceOut, DeviceRegisterRequest
from app.schemas.fcm import FcmTokenOut, FcmTokenRegisterRequest

router = APIRouter(prefix="/users", tags=["users"])


@router.get("/me", response_model=UserOut)
def get_me(current_user: User = Depends(get_current_user)) -> User:
    return current_user


@router.patch("/me", response_model=UserOut)
def update_me(
    payload: UserUpdateRequest,
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db),
) -> User:
    if payload.display_name is not None:
        current_user.display_name = payload.display_name
    if payload.tz_offset is not None:
        current_user.tz_offset = payload.tz_offset
    db.add(current_user)
    db.commit()
    db.refresh(current_user)
    return current_user


@router.post("/me/device", response_model=DeviceOut, status_code=status.HTTP_201_CREATED)
def register_device(
    payload: DeviceRegisterRequest,
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db),
) -> Device:
    device = db.scalar(
        select(Device).where(
            Device.user_id == current_user.id,
            Device.device_token == payload.device_token,
        )
    )
    if device is None:
        device = Device(
            user_id=current_user.id,
            device_token=payload.device_token,
            kind=payload.kind,
            model=payload.model,
        )
        db.add(device)
    else:
        device.kind = payload.kind
        if payload.model is not None:
            device.model = payload.model
    device.last_seen_at = datetime.now(timezone.utc)
    db.commit()
    db.refresh(device)
    return device


@router.post("/me/fcm-token", response_model=FcmTokenOut)
def register_fcm_token(
    payload: FcmTokenRegisterRequest,
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db),
) -> FcmTokenOut:
    """Upsert the Firebase Cloud Messaging registration token (FE-C3).

    The token is stored on the existing `devices` table (key: user_id +
    token, kind = platform) so push targets reuse BE-C1's device registry.
    Re-registering the same token refreshes `last_seen_at`; a token moving
    to a new user simply creates a row for that user.
    """
    now = datetime.now(timezone.utc)
    device = db.scalar(
        select(Device).where(
            Device.user_id == current_user.id,
            Device.device_token == payload.token,
        )
    )
    if device is None:
        device = Device(
            user_id=current_user.id,
            device_token=payload.token,
            kind=payload.platform,
        )
        db.add(device)
    else:
        device.kind = payload.platform
    device.last_seen_at = now
    db.commit()
    return FcmTokenOut(
        status="ok",
        token=payload.token,
        platform=payload.platform,
        registered_at=device.last_seen_at or now,
    )


@router.get("/me/devices", response_model=list[DeviceOut])
def list_devices(
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db),
) -> list[Device]:
    return list(db.scalars(select(Device).where(Device.user_id == current_user.id).order_by(Device.created_at)))
