"""Google OAuth token verification and account linking tests."""

from unittest.mock import patch
from tests.conftest import auth_headers, register_user


def test_google_auth_new_user_success(client):
    mock_id_info = {
        "sub": "google-user-12345",
        "email": "google_new@example.com",
        "email_verified": True,
        "name": "Google New User",
        "picture": "https://lh3.googleusercontent.com/a/avatar1.jpg",
    }

    with patch("app.api.v1.auth.id_token.verify_oauth2_token", return_value=mock_id_info):
        resp = client.post("/api/v1/auth/google", json={"id_token": "valid.mock.token"})

    assert resp.status_code == 200, resp.text
    data = resp.json()
    assert "access_token" in data
    assert data["token_type"] == "bearer"
    assert data["user"]["email"] == "google_new@example.com"
    assert data["user"]["display_name"] == "Google New User"
    assert data["user"]["avatar_url"] == "https://lh3.googleusercontent.com/a/avatar1.jpg"
    assert data["user"]["auth_provider"] == "google"


def test_google_auth_account_linking(client):
    # 1. Register initial email/password user
    reg_data = register_user(client, email="link_me@example.com", display_name="Old Email User")
    original_user_id = reg_data["user"]["id"]

    # 2. Login with Google token having the same email
    mock_id_info = {
        "sub": "google-linked-id-999",
        "email": "link_me@example.com",
        "email_verified": True,
        "name": "Google Linker",
        "picture": "https://lh3.googleusercontent.com/a/link_avatar.jpg",
    }

    with patch("app.api.v1.auth.id_token.verify_oauth2_token", return_value=mock_id_info):
        resp = client.post("/api/v1/auth/google", json={"id_token": "valid.linking.token"})

    assert resp.status_code == 200, resp.text
    data = resp.json()
    assert data["user"]["id"] == original_user_id
    assert data["user"]["email"] == "link_me@example.com"
    assert data["user"]["display_name"] == "Old Email User"  # preserved existing display name
    assert data["user"]["avatar_url"] == "https://lh3.googleusercontent.com/a/link_avatar.jpg"
    assert data["user"]["auth_provider"] == "google"


def test_google_auth_existing_google_user(client):
    mock_id_info = {
        "sub": "google-repeat-user-777",
        "email": "repeat@example.com",
        "email_verified": True,
        "name": "Repeat User",
        "picture": "https://lh3.googleusercontent.com/a/repeat.jpg",
    }

    with patch("app.api.v1.auth.id_token.verify_oauth2_token", return_value=mock_id_info):
        resp1 = client.post("/api/v1/auth/google", json={"id_token": "token1"})
        assert resp1.status_code == 200
        user_id_1 = resp1.json()["user"]["id"]

        resp2 = client.post("/api/v1/auth/google", json={"id_token": "token2"})
        assert resp2.status_code == 200
        user_id_2 = resp2.json()["user"]["id"]

    assert user_id_1 == user_id_2


def test_google_auth_invalid_token(client):
    with patch("app.api.v1.auth.id_token.verify_oauth2_token", side_effect=ValueError("Token expired")):
        resp = client.post("/api/v1/auth/google", json={"id_token": "expired.token"})

    assert resp.status_code == 401
    assert "Invalid Google ID token" in resp.json()["detail"]


def test_google_auth_unverified_email(client):
    mock_id_info = {
        "sub": "unverified-user",
        "email": "unverified@example.com",
        "email_verified": False,
        "name": "Unverified",
    }

    with patch("app.api.v1.auth.id_token.verify_oauth2_token", return_value=mock_id_info):
        resp = client.post("/api/v1/auth/google", json={"id_token": "unverified.token"})

    assert resp.status_code == 400
    assert "Unverified Google email" in resp.json()["detail"]


def test_google_auth_missing_claims(client):
    mock_id_info = {
        "email": "noclaims@example.com",
        # missing sub
    }

    with patch("app.api.v1.auth.id_token.verify_oauth2_token", return_value=mock_id_info):
        resp = client.post("/api/v1/auth/google", json={"id_token": "badclaims.token"})

    assert resp.status_code == 400
