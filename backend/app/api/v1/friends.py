"""Friends API routes for social graph management."""

from fastapi import APIRouter, Depends, Response, status
from sqlalchemy.orm import Session

from app.api.deps import get_current_user
from app.core.database import get_db
from app.models import User
from app.schemas.friendship import (
    FriendProfileOut,
    FriendRequestCreate,
    FriendshipOut,
    PendingRequestOut,
)
from app.services import friend_service

router = APIRouter(prefix="/friends", tags=["friends"])


@router.post("/request", response_model=FriendshipOut, status_code=status.HTTP_201_CREATED)
def request_friend(
    payload: FriendRequestCreate,
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db),
) -> FriendshipOut:
    target_id = payload.target_user_id or payload.addressee_id
    friendship = friend_service.send_friend_request(db, current_user, target_id)  # type: ignore[arg-type]
    return FriendshipOut.model_validate(friendship)


@router.post("/accept/{request_id}", response_model=FriendshipOut)
def accept_friend(
    request_id: int,
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db),
) -> FriendshipOut:
    friendship = friend_service.accept_friend_request(db, current_user, request_id)
    return FriendshipOut.model_validate(friendship)


@router.post("/reject/{request_id}", response_model=FriendshipOut)
def reject_friend(
    request_id: int,
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db),
) -> FriendshipOut:
    friendship = friend_service.reject_friend_request(db, current_user, request_id)
    return FriendshipOut.model_validate(friendship)


@router.delete("/{friend_id}", status_code=status.HTTP_204_NO_CONTENT)
def delete_friend(
    friend_id: int,
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db),
) -> Response:
    friend_service.remove_friend(db, current_user, friend_id)
    return Response(status_code=status.HTTP_204_NO_CONTENT)


@router.get("", response_model=list[FriendProfileOut])
def list_friends(
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db),
) -> list[FriendProfileOut]:
    return friend_service.get_friends_list(db, current_user)


@router.get("/requests/pending", response_model=list[PendingRequestOut])
def list_pending_requests(
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db),
) -> list[PendingRequestOut]:
    return friend_service.get_pending_requests(db, current_user)
