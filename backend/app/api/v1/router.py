from fastapi import APIRouter

from app.api.v1 import auth, challenges, daily, friends, users

api_router = APIRouter(prefix="/api/v1")
api_router.include_router(auth.router)
api_router.include_router(users.router)
api_router.include_router(daily.router)
api_router.include_router(challenges.router)
api_router.include_router(friends.router)
