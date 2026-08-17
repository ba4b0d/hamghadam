"""Auth flow tests: register, login, /users/me, token validation."""

from tests.conftest import auth_headers, register_user


def test_register_returns_token_and_user(client):
    data = register_user(client, email="new@example.com", display_name="Ali")
    assert data["access_token"]
    assert data["token_type"] == "bearer"
    assert data["user"]["email"] == "new@example.com"
    assert data["user"]["display_name"] == "Ali"
    assert data["user"]["premium"] is False


def test_register_duplicate_email_conflict(client):
    register_user(client, email="dup@example.com")
    resp = client.post(
        "/api/v1/auth/register", json={"email": "dup@example.com", "password": "password123"}
    )
    assert resp.status_code == 409


def test_register_short_password_rejected(client):
    resp = client.post("/api/v1/auth/register", json={"email": "short@example.com", "password": "123"})
    assert resp.status_code == 422


def test_register_rejects_unknown_fields(client):
    resp = client.post(
        "/api/v1/auth/register",
        json={"email": "x@example.com", "password": "password123", "hacker": True},
    )
    assert resp.status_code == 422


def test_login_ok(client):
    register_user(client, email="login@example.com")
    resp = client.post(
        "/api/v1/auth/login", json={"email": "login@example.com", "password": "password123"}
    )
    assert resp.status_code == 200
    assert resp.json()["access_token"]


def test_login_wrong_password_unauthorized(client):
    register_user(client, email="wrongpw@example.com")
    resp = client.post(
        "/api/v1/auth/login", json={"email": "wrongpw@example.com", "password": "nope-nope"}
    )
    assert resp.status_code == 401


def test_login_unknown_email_unauthorized(client):
    resp = client.post(
        "/api/v1/auth/login", json={"email": "ghost@example.com", "password": "password123"}
    )
    assert resp.status_code == 401


def test_me_requires_token(client):
    assert client.get("/api/v1/users/me").status_code == 401


def test_me_rejects_garbage_token(client):
    resp = client.get("/api/v1/users/me", headers=auth_headers("not-a-real-token"))
    assert resp.status_code == 401


def test_me_returns_profile(client):
    token = register_user(client, email="me@example.com")["access_token"]
    resp = client.get("/api/v1/users/me", headers=auth_headers(token))
    assert resp.status_code == 200
    assert resp.json()["email"] == "me@example.com"
