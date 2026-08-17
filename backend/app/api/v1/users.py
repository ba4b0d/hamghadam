"""User endpoints: profile, avatar upload, user search, and device registration."""

import io
import os
import uuid
from datetime import datetime, timezone

from fastapi import APIRouter, Depends, File, HTTPException, Query, Response, UploadFile, status
from PIL import Image
from sqlalchemy import or_, select
from sqlalchemy.orm import Session

from app.api.deps import get_current_user
from app.core.config import settings
from app.core.database import get_db
from app.models import Device, Friendship, FriendshipStatus, User
from app.schemas.auth import UserOut, UserUpdateRequest
from app.schemas.device import DeviceOut, DeviceRegisterRequest
from app.schemas.fcm import FcmTokenOut, FcmTokenRegisterRequest
from app.schemas.user import AvatarUploadResponse, BioUpdateRequest, UserPublicOut

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
    if payload.bio is not None:
        current_user.bio = payload.bio
    if payload.location is not None:
        current_user.location = payload.location
    if payload.tz_offset is not None:
        current_user.tz_offset = payload.tz_offset
    db.add(current_user)
    db.commit()
    db.refresh(current_user)
    return current_user


@router.patch("/me/bio", response_model=UserOut)
def update_bio(
    payload: BioUpdateRequest,
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db),
) -> User:
    current_user.bio = payload.bio
    db.add(current_user)
    db.commit()
    db.refresh(current_user)
    return current_user


@router.post("/me/avatar", response_model=AvatarUploadResponse)
async def upload_avatar(
    file: UploadFile = File(...),
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db),
) -> AvatarUploadResponse:
    # 1. Content-Type pre-check
    allowed_content_types = {"image/jpeg", "image/jpg", "image/png", "image/webp"}
    if file.content_type and file.content_type.lower() not in allowed_content_types:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="Invalid image format. Allowed formats: JPEG, PNG, WebP.",
        )

    # 2. Read and enforce size limit (5MB)
    contents = await file.read()
    if len(contents) > settings.max_avatar_size_bytes:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="Image size exceeds 5MB limit.",
        )
    if len(contents) == 0:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="Uploaded file is empty.",
        )

    # 3. Binary header inspection & image validation using PIL
    try:
        image = Image.open(io.BytesIO(contents))
        img_format = (image.format or "").upper()
        if img_format not in ("JPEG", "PNG", "WEBP"):
            raise ValueError("Unsupported format")
    except Exception:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="Invalid or corrupted image file.",
        )

    # 4. Generate unique filename
    ext_map = {"JPEG": ".jpg", "PNG": ".png", "WEBP": ".webp"}
    ext = ext_map.get(img_format, ".jpg")
    filename = f"{uuid.uuid4()}{ext}"

    # 5. Save to disk
    os.makedirs(settings.avatar_dir, exist_ok=True)
    file_path = os.path.join(settings.avatar_dir, filename)
    with open(file_path, "wb") as f:
        f.write(contents)

    # 6. Generate public URL & persist on user
    public_url = f"{settings.avatar_base_url.rstrip('/')}/{filename}"
    current_user.avatar_url = public_url
    db.add(current_user)
    db.commit()
    db.refresh(current_user)

    return AvatarUploadResponse(avatar_url=public_url)


@router.delete("/me", status_code=status.HTTP_204_NO_CONTENT)
def delete_me(
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db),
) -> Response:
    """Permanently delete the current user account and cascade associated records."""
    db.delete(current_user)
    db.commit()
    return Response(status_code=status.HTTP_204_NO_CONTENT)


@router.get("/search", response_model=list[UserPublicOut])
def search_users(
    q: str = Query(..., min_length=1, description="Search query matching display name or email"),
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db),
) -> list[UserPublicOut]:
    query_term = f"%{q.strip().lower()}%"
    users = list(
        db.scalars(
            select(User)
            .where(
                User.id != current_user.id,
                or_(
                    User.email.ilike(query_term),
                    User.display_name.ilike(query_term),
                ),
            )
            .limit(50)
        )
    )

    if not users:
        return []

    # Batch query friendships involving current_user and these users
    user_ids = [u.id for u in users]
    friendships = list(
        db.scalars(
            select(Friendship).where(
                or_(
                    (Friendship.requester_id == current_user.id)
                    & (Friendship.addressee_id.in_(user_ids)),
                    (Friendship.addressee_id == current_user.id)
                    & (Friendship.requester_id.in_(user_ids)),
                )
            )
        )
    )

    # Map (other_user_id) -> Friendship
    f_map: dict[int, Friendship] = {}
    for f in friendships:
        other_id = f.addressee_id if f.requester_id == current_user.id else f.requester_id
        f_map[other_id] = f

    results: list[UserPublicOut] = []
    for u in users:
        f = f_map.get(u.id)
        if f is None:
            f_status = "NONE"
        elif f.status == FriendshipStatus.ACCEPTED:
            f_status = "ACCEPTED"
        elif f.status == FriendshipStatus.PENDING:
            f_status = "PENDING_SENT" if f.requester_id == current_user.id else "PENDING_RECEIVED"
        elif f.status == FriendshipStatus.BLOCKED:
            f_status = "BLOCKED"
        elif f.status == FriendshipStatus.REJECTED:
            f_status = "REJECTED"
        else:
            f_status = "NONE"

        results.append(
            UserPublicOut(
                id=u.id,
                display_name=u.display_name,
                avatar_url=u.avatar_url,
                bio=u.bio,
                location=u.location,
                friendship_status=f_status,
            )
        )

    return results


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
    return list(
        db.scalars(
            select(Device).where(Device.user_id == current_user.id).order_by(Device.created_at)
        )
    )
