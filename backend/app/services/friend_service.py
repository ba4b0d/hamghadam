"""Friendship domain service: requests, accepts, rejects, deletions, and friend listings."""

from datetime import datetime, timedelta, timezone

from fastapi import HTTPException, status
from sqlalchemy import or_, select
from sqlalchemy.orm import Session

from app.models import DailyScore, Friendship, FriendshipStatus, User
from app.schemas.friendship import FriendProfileOut, FriendshipOut, PendingRequestOut
from app.schemas.user import UserPublicOut


def _utcnow() -> datetime:
    return datetime.now(timezone.utc)


def send_friend_request(db: Session, current_user: User, target_user_id: int) -> Friendship:
    if current_user.id == target_user_id:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="Cannot send a friend request to yourself",
        )

    target_user = db.get(User, target_user_id)
    if target_user is None:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail="User not found",
        )

    existing = db.scalar(
        select(Friendship).where(
            or_(
                (Friendship.requester_id == current_user.id)
                & (Friendship.addressee_id == target_user_id),
                (Friendship.requester_id == target_user_id)
                & (Friendship.addressee_id == current_user.id),
            )
        )
    )

    if existing is not None:
        if existing.status == FriendshipStatus.ACCEPTED:
            raise HTTPException(
                status_code=status.HTTP_400_BAD_REQUEST,
                detail="Already friends",
            )
        if existing.status == FriendshipStatus.PENDING:
            raise HTTPException(
                status_code=status.HTTP_400_BAD_REQUEST,
                detail="Friend request already pending",
            )
        if existing.status == FriendshipStatus.BLOCKED:
            raise HTTPException(
                status_code=status.HTTP_403_FORBIDDEN,
                detail="Cannot send friend request",
            )
        if existing.status == FriendshipStatus.REJECTED:
            # Re-open rejected request
            existing.requester_id = current_user.id
            existing.addressee_id = target_user_id
            existing.status = FriendshipStatus.PENDING
            existing.updated_at = _utcnow()
            db.add(existing)
            db.commit()
            db.refresh(existing)
            return existing

    friendship = Friendship(
        requester_id=current_user.id,
        addressee_id=target_user_id,
        status=FriendshipStatus.PENDING,
    )
    db.add(friendship)
    db.commit()
    db.refresh(friendship)
    return friendship


def accept_friend_request(db: Session, current_user: User, request_id: int) -> Friendship:
    friendship = db.get(Friendship, request_id)
    if friendship is None:
        # Fallback: check if request_id was passed as requester user ID
        friendship = db.scalar(
            select(Friendship).where(
                Friendship.requester_id == request_id,
                Friendship.addressee_id == current_user.id,
                Friendship.status == FriendshipStatus.PENDING,
            )
        )

    if friendship is None:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail="Friend request not found",
        )

    if friendship.addressee_id != current_user.id:
        raise HTTPException(
            status_code=status.HTTP_403_FORBIDDEN,
            detail="Not authorized to accept this friend request",
        )

    if friendship.status == FriendshipStatus.ACCEPTED:
        return friendship

    if friendship.status != FriendshipStatus.PENDING:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail=f"Cannot accept friend request with status {friendship.status.value}",
        )

    friendship.status = FriendshipStatus.ACCEPTED
    friendship.updated_at = _utcnow()
    db.add(friendship)
    db.commit()
    db.refresh(friendship)
    return friendship


def reject_friend_request(db: Session, current_user: User, request_id: int) -> Friendship:
    friendship = db.get(Friendship, request_id)
    if friendship is None:
        # Fallback: check if request_id was passed as requester user ID
        friendship = db.scalar(
            select(Friendship).where(
                Friendship.requester_id == request_id,
                Friendship.addressee_id == current_user.id,
                Friendship.status == FriendshipStatus.PENDING,
            )
        )

    if friendship is None:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail="Friend request not found",
        )

    if friendship.addressee_id != current_user.id:
        raise HTTPException(
            status_code=status.HTTP_403_FORBIDDEN,
            detail="Not authorized to reject this friend request",
        )

    friendship.status = FriendshipStatus.REJECTED
    friendship.updated_at = _utcnow()
    db.add(friendship)
    db.commit()
    db.refresh(friendship)
    return friendship


def remove_friend(db: Session, current_user: User, friend_id: int) -> None:
    # 1. Search by friend's User ID
    friendship = db.scalar(
        select(Friendship).where(
            or_(
                (Friendship.requester_id == current_user.id)
                & (Friendship.addressee_id == friend_id),
                (Friendship.requester_id == friend_id)
                & (Friendship.addressee_id == current_user.id),
            )
        )
    )

    # 2. Fallback: check if friend_id is the friendship primary key ID
    if friendship is None:
        f_by_pk = db.get(Friendship, friend_id)
        if f_by_pk and (f_by_pk.requester_id == current_user.id or f_by_pk.addressee_id == current_user.id):
            friendship = f_by_pk

    if friendship is None:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail="Friendship not found",
        )

    db.delete(friendship)
    db.commit()


def get_friends_list(db: Session, current_user: User) -> list[FriendProfileOut]:
    friendships = list(
        db.scalars(
            select(Friendship).where(
                (
                    (Friendship.requester_id == current_user.id)
                    | (Friendship.addressee_id == current_user.id)
                )
                & (Friendship.status == FriendshipStatus.ACCEPTED)
            )
        )
    )

    now_utc = _utcnow()
    friends_out: list[FriendProfileOut] = []

    for f in friendships:
        friend = f.addressee if f.requester_id == current_user.id else f.requester
        if friend is None:
            continue

        tz_offset = friend.tz_offset or 0
        local_today = (now_utc + timedelta(minutes=tz_offset)).date()

        score_val = db.scalar(
            select(DailyScore.value).where(
                DailyScore.user_id == friend.id,
                DailyScore.metric == "steps",
                DailyScore.date == local_today,
            )
        )
        today_steps = int(score_val) if score_val is not None else 0

        friends_out.append(
            FriendProfileOut(
                id=friend.id,
                email=friend.email,
                display_name=friend.display_name,
                avatar_url=friend.avatar_url,
                bio=friend.bio,
                location=friend.location,
                today_steps=today_steps,
            )
        )

    # Sort friends alphabetically by display_name or email
    friends_out.sort(key=lambda u: (u.display_name or u.email).lower())
    return friends_out


def get_pending_requests(db: Session, current_user: User) -> list[PendingRequestOut]:
    friendships = list(
        db.scalars(
            select(Friendship)
            .where(
                Friendship.addressee_id == current_user.id,
                Friendship.status == FriendshipStatus.PENDING,
            )
            .order_by(Friendship.created_at.desc())
        )
    )

    results: list[PendingRequestOut] = []
    for f in friendships:
        requester = f.requester
        if requester is None:
            continue
        req_out = UserPublicOut(
            id=requester.id,
            display_name=requester.display_name,
            avatar_url=requester.avatar_url,
            bio=requester.bio,
            location=requester.location,
            friendship_status="PENDING_RECEIVED",
        )
        results.append(
            PendingRequestOut(
                request_id=f.id,
                requester=req_out,
                created_at=f.created_at,
            )
        )
    return results
