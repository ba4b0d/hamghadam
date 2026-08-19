"""Auth endpoints: register + login + google auth → JWT access token."""

import secrets
from google.auth.transport import requests as google_requests
from google.oauth2 import id_token
from fastapi import APIRouter, Depends, HTTPException, status
from sqlalchemy import select
from sqlalchemy.orm import Session

from app.api.deps import get_current_user
from app.core.config import settings
from app.core.database import get_db
from app.core.security import create_access_token, hash_password, verify_password
from app.models import User
from app.schemas.auth import (
    GoogleAuthRequest,
    LoginRequest,
    RegisterRequest,
    TokenResponse,
    UserOut,
)

router = APIRouter(prefix="/auth", tags=["auth"])


def _token_response(user: User) -> TokenResponse:
    return TokenResponse(
        access_token=create_access_token(subject=str(user.id)),
        token_type="bearer",
        expires_in=settings.access_token_expire_minutes * 60,
        user=UserOut.model_validate(user),
    )


@router.post("/register", response_model=TokenResponse, status_code=status.HTTP_201_CREATED)
def register(payload: RegisterRequest, db: Session = Depends(get_db)) -> TokenResponse:
    email = payload.email.lower()
    existing = db.scalar(select(User).where(User.email == email))
    if existing is not None:
        raise HTTPException(status_code=status.HTTP_409_CONFLICT, detail="Email already registered")

    user = User(
        email=email,
        password_hash=hash_password(payload.password),
        display_name=payload.display_name,
        tz_offset=payload.tz_offset,
        auth_provider="email",
    )
    db.add(user)
    db.commit()
    db.refresh(user)
    return _token_response(user)


@router.post("/login", response_model=TokenResponse)
def login(payload: LoginRequest, db: Session = Depends(get_db)) -> TokenResponse:
    user = db.scalar(select(User).where(User.email == payload.email.lower()))
    if user is None or not verify_password(payload.password, user.password_hash):
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Invalid email or password",
            headers={"WWW-Authenticate": "Bearer"},
        )
    return _token_response(user)


@router.post("/google", response_model=TokenResponse)
def google_auth(payload: GoogleAuthRequest, db: Session = Depends(get_db)) -> TokenResponse:
    try:
        req = google_requests.Request()
        id_info = id_token.verify_oauth2_token(payload.id_token, req, audience=None)
        token_aud = id_info.get("aud")
        allowed = [
            settings.google_web_client_id,
            "590964300109-p10jff24glu9mite50u27ho56jl79hml.apps.googleusercontent.com",
            "590964300109-d296llapcb5on97kk0ope7pi2r3u6vu3.apps.googleusercontent.com",
        ]
        if token_aud and not (token_aud in allowed or token_aud.startswith("590964300109-")):
            raise ValueError(f"Unrecognized audience: {token_aud}")
    except Exception:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Invalid Google ID token",
            headers={"WWW-Authenticate": "Bearer"},
        )

    google_id = id_info.get("sub")
    email = id_info.get("email")
    if not google_id or not email:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="Invalid token claims: missing sub or email",
        )

    email = email.lower()
    email_verified = id_info.get("email_verified", True)
    if isinstance(email_verified, str):
        email_verified = email_verified.lower() in ("true", "1")
    if not email_verified:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="Unverified Google email",
        )

    name = id_info.get("name")
    picture = id_info.get("picture")

    # 1. Lookup by google_id
    user = db.scalar(select(User).where(User.google_id == google_id))
    if user is None:
        # 2. Lookup by email (account linking)
        user = db.scalar(select(User).where(User.email == email))
        if user is not None:
            user.google_id = google_id
            if not user.avatar_url and picture:
                user.avatar_url = picture
            if not user.display_name and name:
                user.display_name = name
            user.auth_provider = "google" if user.auth_provider == "email" else user.auth_provider
            db.commit()
            db.refresh(user)
        else:
            # 3. Create new user
            random_pw = secrets.token_urlsafe(32)
            user = User(
                email=email,
                password_hash=hash_password(random_pw),
                display_name=name or email.split("@")[0],
                avatar_url=picture,
                google_id=google_id,
                auth_provider="google",
            )
            db.add(user)
            db.commit()
            db.refresh(user)
    else:
        # Update avatar or name if provided and user doesn't have custom ones set
        changed = False
        if picture and not user.avatar_url:
            user.avatar_url = picture
            changed = True
        if name and not user.display_name:
            user.display_name = name
            changed = True
        if changed:
            db.commit()
            db.refresh(user)

    return _token_response(user)


@router.get("/me", response_model=UserOut)
def me(current_user: User = Depends(get_current_user)) -> User:
    return current_user
