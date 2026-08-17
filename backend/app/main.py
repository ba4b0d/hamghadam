"""FastAPI application factory."""

import logging
import os

from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from fastapi.staticfiles import StaticFiles

from app.api.v1.router import api_router
from app.core.config import settings


def create_app() -> FastAPI:
    logging.basicConfig(
        level=settings.log_level.upper(),
        format="%(asctime)s %(levelname)s [%(name)s] %(message)s",
    )
    app = FastAPI(
        title="Fitness App API",
        version="0.1.0",
        description="Backend for the all-in-one fitness Android app: users, auth, "
        "device registration, Health Connect daily ingest, and social features.",
    )

    app.add_middleware(
        CORSMiddleware,
        allow_origins=settings.cors_origins,
        allow_credentials=True,
        allow_methods=["*"],
        allow_headers=["*"],
    )

    # Ensure avatar directory exists & mount static directories for avatar serving
    os.makedirs(settings.avatar_dir, exist_ok=True)
    static_root = os.path.dirname(settings.avatar_dir) or "static"
    os.makedirs(static_root, exist_ok=True)
    app.mount("/static", StaticFiles(directory=static_root), name="static")
    app.mount("/avatars", StaticFiles(directory=settings.avatar_dir), name="avatars")

    app.include_router(api_router)

    @app.get("/healthz", tags=["meta"])
    def healthz() -> dict:
        return {"status": "ok"}

    return app


app = create_app()
