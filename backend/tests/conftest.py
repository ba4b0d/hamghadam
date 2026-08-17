"""Test fixtures: isolated in-memory SQLite per test, fresh app TestClient.

Also exposes `engine` / `db_session` so service-level and time-travel tests
can poke the DB directly (e.g. to move a challenge's ends_at into the past
and prove lazy time-driven status propagation).

Clock seam & DB-1 skew simulation
---------------------------------
The app's single clock entry point is `challenge_service.utcnow()` (the
routes re-import it). Clock-sensitive tests that fabricate `now` inputs use
that seam (`utcnow()`), never `datetime.now()` directly.

`SKEW_SIM=1` freezes the seam at the DB-1 skew instant — UTC 2026-08-16
22:30, where the server UTC date (16th) is one BEHIND the Tehran local date
(17th). That is the daily 00:00-03:30 Tehran window where DB-1 dropped
east-of-UTC users' local-today rows; the regression lives only there.
Modules opt in with `pytest.mark.usefixtures("skew_clock")` (test_fcm,
test_leaderboard) so no other module sees a patched clock:

    SKEW_SIM=1 .venv/Scripts/python.exe -m pytest tests/test_fcm.py tests/test_leaderboard.py -q

The datetime/date classes themselves are never replaced (they are immutable;
freezegun's whole-class swap also breaks FastAPI/pydantic field type
identity), so the rest of the suite always runs against the real clock —
which is fine because the DB-1 fix makes every window behave correctly.
"""

import os

# Must be set before any app module import so settings pick up a test DB.
os.environ["DATABASE_URL"] = "sqlite://"
os.environ["SECRET_KEY"] = "test-secret-0123456789abcdef0123456789abcdef"
os.environ["APP_ENV"] = "test"

from datetime import datetime, timezone  # noqa: E402

import pytest  # noqa: E402
from fastapi.testclient import TestClient  # noqa: E402
from sqlalchemy import create_engine  # noqa: E402
from sqlalchemy.orm import sessionmaker  # noqa: E402
from sqlalchemy.pool import StaticPool  # noqa: E402

from app.core.database import Base, get_db  # noqa: E402
from app.main import app  # noqa: E402

SKEW_SIM = os.environ.get("SKEW_SIM") == "1"

# UTC date 2026-08-16, Tehran (UTC+3:30) local date 2026-08-17.
SKEW_NOW = datetime(2026, 8, 16, 22, 30, 0, tzinfo=timezone.utc)


@pytest.fixture()
def skew_clock(monkeypatch):
    """Freeze the app clock seam at the DB-1 skew instant when SKEW_SIM=1.

    Patches `utcnow` at its definition site (challenge_service) and in the
    two route modules that re-import it (challenges, daily). Any test module
    that builds `now` inputs via `utcnow()` then runs deterministically in
    the skew window. No-op unless SKEW_SIM=1.
    """
    if not SKEW_SIM:
        yield
        return
    import app.api.v1.challenges as challenges_mod
    import app.api.v1.daily as daily_mod
    import app.services.challenge_service as service_mod

    frozen = SKEW_NOW
    monkeypatch.setattr(service_mod, "utcnow", lambda: frozen)
    monkeypatch.setattr(challenges_mod, "utcnow", lambda: frozen)
    monkeypatch.setattr(daily_mod, "utcnow", lambda: frozen)
    yield


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
