"""Test fixtures: isolated in-memory SQLite per test, fresh app TestClient.

Also exposes `engine` / `db_session` so service-level and time-travel tests
can poke the DB directly (e.g. to move a challenge's ends_at into the past
and prove lazy time-driven status propagation).
"""

import os

# Must be set before any app module import so settings pick up a test DB.
os.environ["DATABASE_URL"] = "sqlite://"
os.environ["SECRET_KEY"] = "test-secret-0123456789abcdef0123456789abcdef"
os.environ["APP_ENV"] = "test"

import pytest  # noqa: E402
from fastapi.testclient import TestClient  # noqa: E402
from sqlalchemy import create_engine  # noqa: E402
from sqlalchemy.orm import sessionmaker  # noqa: E402
from sqlalchemy.pool import StaticPool  # noqa: E402

from app.core.database import Base, get_db  # noqa: E402
from app.main import app  # noqa: E402


@pytest.fixture()
def engine():
    eng = create_engine(
        "sqlite://",
        connect_args={"check_same_thread": False},
        poolclass=StaticPool,
    )
    Base.metadata.create_all(bind=eng)
    yield eng
    Base.metadata.drop_all(bind=eng)
    eng.dispose()


@pytest.fixture()
def db_session(engine):
    TestingSessionLocal = sessionmaker(autocommit=False, autoflush=False, bind=engine)
    session = TestingSessionLocal()
    yield session
    session.close()


@pytest.fixture()
def client(engine):
    TestingSessionLocal = sessionmaker(autocommit=False, autoflush=False, bind=engine)

    def override_get_db():
        db = TestingSessionLocal()
        try:
            yield db
        finally:
            db.close()

    app.dependency_overrides[get_db] = override_get_db
    with TestClient(app) as c:
        yield c
    app.dependency_overrides.clear()


def register_user(client: TestClient, email: str = "a@example.com", password: str = "password123", **extra) -> dict:
    body = {"email": email, "password": password}
    body.update(extra)
    resp = client.post("/api/v1/auth/register", json=body)
    assert resp.status_code == 201, resp.text
    return resp.json()


def auth_headers(token: str) -> dict:
    return {"Authorization": f"Bearer {token}"}
