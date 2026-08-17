"""User profile and avatar upload tests."""

import io
from PIL import Image
from tests.conftest import auth_headers, register_user


def _make_dummy_image(format="JPEG", size=(100, 100), color="blue") -> bytes:
    buf = io.BytesIO()
    img = Image.new("RGB", size, color=color)
    img.save(buf, format=format)
    return buf.getvalue()


def test_get_profile_me(client):
    reg = register_user(client, email="profile@example.com", display_name="Profile User")
    token = reg["access_token"]

    resp = client.get("/api/v1/users/me", headers=auth_headers(token))
    assert resp.status_code == 200
    data = resp.json()
    assert data["email"] == "profile@example.com"
    assert data["display_name"] == "Profile User"
    assert data["bio"] is None
    assert data["avatar_url"] is None
    assert data["auth_provider"] == "email"


def test_patch_profile_me(client):
    token = register_user(client, email="patch_me@example.com")["access_token"]

    resp = client.patch(
        "/api/v1/users/me",
        json={"display_name": "New Name", "bio": "Runner and hiker", "location": "Tehran", "tz_offset": 210},
        headers=auth_headers(token),
    )
    assert resp.status_code == 200
    data = resp.json()
    assert data["display_name"] == "New Name"
    assert data["bio"] == "Runner and hiker"
    assert data["location"] == "Tehran"
    assert data["tz_offset"] == 210

    # Verify persistence with subsequent GET
    get_resp = client.get("/api/v1/users/me", headers=auth_headers(token))
    assert get_resp.status_code == 200
    assert get_resp.json()["bio"] == "Runner and hiker"


def test_patch_profile_me_bio(client):
    token = register_user(client, email="bio_me@example.com")["access_token"]

    resp = client.patch(
        "/api/v1/users/me/bio",
        json={"bio": "Marathon enthusiast"},
        headers=auth_headers(token),
    )
    assert resp.status_code == 200
    assert resp.json()["bio"] == "Marathon enthusiast"


def test_upload_avatar_jpeg(client):
    token = register_user(client, email="avatar_jpg@example.com")["access_token"]
    jpeg_bytes = _make_dummy_image(format="JPEG")

    files = {"file": ("avatar.jpg", jpeg_bytes, "image/jpeg")}
    resp = client.post("/api/v1/users/me/avatar", files=files, headers=auth_headers(token))
    assert resp.status_code == 200, resp.text
    data = resp.json()
    assert "avatar_url" in data
    assert data["avatar_url"].endswith(".jpg")
    assert "/avatars/" in data["avatar_url"]

    # Verify profile now reflects avatar_url
    me_resp = client.get("/api/v1/users/me", headers=auth_headers(token))
    assert me_resp.json()["avatar_url"] == data["avatar_url"]

    # Verify static file serving
    filename = data["avatar_url"].split("/")[-1]
    static_resp = client.get(f"/static/avatars/{filename}")
    assert static_resp.status_code == 200
    assert static_resp.content == jpeg_bytes

    avatars_resp = client.get(f"/avatars/{filename}")
    assert avatars_resp.status_code == 200
    assert avatars_resp.content == jpeg_bytes


def test_upload_avatar_png(client):
    token = register_user(client, email="avatar_png@example.com")["access_token"]
    png_bytes = _make_dummy_image(format="PNG")

    files = {"file": ("avatar.png", png_bytes, "image/png")}
    resp = client.post("/api/v1/users/me/avatar", files=files, headers=auth_headers(token))
    assert resp.status_code == 200
    assert resp.json()["avatar_url"].endswith(".png")


def test_upload_avatar_webp(client):
    token = register_user(client, email="avatar_webp@example.com")["access_token"]
    webp_bytes = _make_dummy_image(format="WEBP")

    files = {"file": ("avatar.webp", webp_bytes, "image/webp")}
    resp = client.post("/api/v1/users/me/avatar", files=files, headers=auth_headers(token))
    assert resp.status_code == 200
    assert resp.json()["avatar_url"].endswith(".webp")


def test_upload_avatar_invalid_mime(client):
    token = register_user(client, email="invalid_mime@example.com")["access_token"]
    files = {"file": ("doc.txt", b"plain text content", "text/plain")}
    resp = client.post("/api/v1/users/me/avatar", files=files, headers=auth_headers(token))
    assert resp.status_code == 400
    assert "Allowed formats" in resp.json()["detail"]


def test_upload_avatar_corrupt_image(client):
    token = register_user(client, email="corrupt@example.com")["access_token"]
    files = {"file": ("bad.jpg", b"fake binary data here not real jpeg", "image/jpeg")}
    resp = client.post("/api/v1/users/me/avatar", files=files, headers=auth_headers(token))
    assert resp.status_code == 400
    assert "Invalid or corrupted image" in resp.json()["detail"]


def test_upload_avatar_oversized(client, monkeypatch):
    token = register_user(client, email="huge@example.com")["access_token"]
    # Temporarily set max_avatar_size_bytes low to test enforcement
    from app.core.config import settings
    monkeypatch.setattr(settings, "max_avatar_size_bytes", 100)

    jpeg_bytes = _make_dummy_image(format="JPEG")
    files = {"file": ("huge.jpg", jpeg_bytes, "image/jpeg")}
    resp = client.post("/api/v1/users/me/avatar", files=files, headers=auth_headers(token))
    assert resp.status_code == 400
    assert "exceeds" in resp.json()["detail"]


def test_delete_account_me(client):
    token = register_user(client, email="delete_me@example.com")["access_token"]

    resp = client.delete("/api/v1/users/me", headers=auth_headers(token))
    assert resp.status_code == 204

    # Subsequent request using the token should fail with 401
    resp2 = client.get("/api/v1/users/me", headers=auth_headers(token))
    assert resp2.status_code == 401
