"""Comprehensive E2E QA Verification suite for V1.2 Social features:
1. Google Sign-In & Account Linking
2. Profile Bio Update & Multipart Avatar Upload (with static file serving)
3. Multi-account Friends Lifecycle (Search, Request, Pending, Accept, Steps, Delete)
"""

import io
from PIL import Image
from unittest.mock import patch
from tests.conftest import auth_headers, register_user


def _make_image(format="JPEG", color="green", size=(120, 120)) -> bytes:
    buf = io.BytesIO()
    img = Image.new("RGB", size, color=color)
    img.save(buf, format=format)
    return buf.getvalue()


def test_e2e_google_sign_in_and_linking(client):
    """E2E Verification of Google Auth ID token verification and account linking."""
    # Step 1: Create initial user via email/password
    reg = register_user(client, email="linking_user@example.com", display_name="Original Name")
    user_id = reg["user"]["id"]

    # Step 2: Simulate Google Sign-In with matching email
    mock_id_info = {
        "sub": "google-sub-998877",
        "email": "linking_user@example.com",
        "email_verified": True,
        "name": "Google User Name",
        "picture": "https://lh3.googleusercontent.com/a/google_avatar.png",
    }

    with patch("app.api.v1.auth.id_token.verify_oauth2_token", return_value=mock_id_info):
        g_resp = client.post("/api/v1/auth/google", json={"id_token": "mock.jwt.token"})

    assert g_resp.status_code == 200, f"Google auth failed: {g_resp.text}"
    g_data = g_resp.json()

    # Verify token payload and account linking
    assert "access_token" in g_data
    assert g_data["user"]["id"] == user_id
    assert g_data["user"]["email"] == "linking_user@example.com"
    assert g_data["user"]["auth_provider"] == "google"
    assert g_data["user"]["avatar_url"] == "https://lh3.googleusercontent.com/a/google_avatar.png"

    # Step 3: Verify new Google Sign-In user creation
    mock_new_id_info = {
        "sub": "google-sub-112233",
        "email": "brand_new_google@example.com",
        "email_verified": True,
        "name": "Brand New Google User",
        "picture": "https://lh3.googleusercontent.com/a/new_avatar.png",
    }

    with patch("app.api.v1.auth.id_token.verify_oauth2_token", return_value=mock_new_id_info):
        g_new_resp = client.post("/api/v1/auth/google", json={"id_token": "mock.new.token"})

    assert g_new_resp.status_code == 200
    g_new_data = g_new_resp.json()
    assert g_new_data["user"]["email"] == "brand_new_google@example.com"
    assert g_new_data["user"]["auth_provider"] == "google"


def test_e2e_profile_bio_and_avatar_upload(client):
    """E2E Verification of Profile Bio edit, multipart avatar upload, and static file serving."""
    # Step 1: Register User
    reg = register_user(client, email="profile_qa@example.com", display_name="QA Profile Tester")
    token = reg["access_token"]
    headers = auth_headers(token)

    # Step 2: Update Bio via PATCH /api/v1/users/me/bio
    bio_resp = client.patch("/api/v1/users/me/bio", json={"bio": "Testing avatar upload and bio updates"}, headers=headers)
    assert bio_resp.status_code == 200, bio_resp.text
    assert bio_resp.json()["bio"] == "Testing avatar upload and bio updates"

    # Step 3: Verify persistence via GET /api/v1/users/me
    me_resp = client.get("/api/v1/users/me", headers=headers)
    assert me_resp.status_code == 200
    assert me_resp.json()["bio"] == "Testing avatar upload and bio updates"

    # Step 4: Upload avatar via POST /api/v1/users/me/avatar (multipart/form-data)
    img_bytes = _make_image(format="PNG", color="purple")
    files = {"file": ("avatar_test.png", img_bytes, "image/png")}
    avatar_resp = client.post("/api/v1/users/me/avatar", files=files, headers=headers)
    assert avatar_resp.status_code == 200, avatar_resp.text
    avatar_url = avatar_resp.json()["avatar_url"]
    assert avatar_url.endswith(".png")
    assert "/avatars/" in avatar_url

    # Step 5: Verify GET /api/v1/users/me reflects updated avatar_url
    me_after_avatar = client.get("/api/v1/users/me", headers=headers).json()
    assert me_after_avatar["avatar_url"] == avatar_url

    # Step 6: Verify static HTTP file serving endpoints (/static/avatars/ & /avatars/)
    filename = avatar_url.split("/")[-1]

    static_resp = client.get(f"/static/avatars/{filename}")
    assert static_resp.status_code == 200
    assert static_resp.content == img_bytes

    avatars_resp = client.get(f"/avatars/{filename}")
    assert avatars_resp.status_code == 200
    assert avatars_resp.content == img_bytes


def test_e2e_friends_multiaccount_lifecycle(client):
    """E2E Multi-account Friends Lifecycle Verification:
    - Search
    - Send Request
    - View Pending Requests
    - Accept Request
    - View Friends List with step counts
    - Delete Friend
    """
    # Step 1: Create User A & User B
    user_a = register_user(client, email="qa_user_a@example.com", display_name="User A (Requester)", tz_offset=210)
    user_b = register_user(client, email="qa_user_b@example.com", display_name="User B (Addressee)", tz_offset=210)

    token_a = user_a["access_token"]
    token_b = user_b["access_token"]
    id_a = user_a["user"]["id"]
    id_b = user_b["user"]["id"]

    # Step 2: User A searches for User B by display name "User B"
    search_resp = client.get("/api/v1/users/search?q=User B", headers=auth_headers(token_a))
    assert search_resp.status_code == 200, search_resp.text
    search_results = search_resp.json()
    assert len(search_results) == 1
    assert search_results[0]["id"] == id_b
    assert search_results[0]["friendship_status"] == "NONE"

    # Step 3: User A sends friend request to User B
    req_resp = client.post("/api/v1/friends/request", json={"target_user_id": id_b}, headers=auth_headers(token_a))
    assert req_resp.status_code == 201, req_resp.text
    request_data = req_resp.json()
    assert request_data["status"] == "PENDING"
    assert request_data["requester_id"] == id_a
    assert request_data["addressee_id"] == id_b
    request_id = request_data["id"]

    # Step 4: Verify search status reflects PENDING_SENT for A and PENDING_RECEIVED for B
    search_a = client.get("/api/v1/users/search?q=User B", headers=auth_headers(token_a)).json()
    assert search_a[0]["friendship_status"] == "PENDING_SENT"

    search_b = client.get("/api/v1/users/search?q=User A", headers=auth_headers(token_b)).json()
    assert search_b[0]["friendship_status"] == "PENDING_RECEIVED"

    # Step 5: User B views pending requests notification
    pending_resp = client.get("/api/v1/friends/requests/pending", headers=auth_headers(token_b))
    assert pending_resp.status_code == 200
    pending_list = pending_resp.json()
    assert len(pending_list) == 1
    assert pending_list[0]["request_id"] == request_id
    assert pending_list[0]["requester"]["id"] == id_a

    # Step 6: User B accepts friend request
    accept_resp = client.post(f"/api/v1/friends/accept/{request_id}", headers=auth_headers(token_b))
    assert accept_resp.status_code == 200
    assert accept_resp.json()["status"] == "ACCEPTED"

    # Step 7: Verify both users see each other in Friends List
    friends_a = client.get("/api/v1/friends", headers=auth_headers(token_a)).json()
    assert len(friends_a) == 1
    assert friends_a[0]["id"] == id_b
    assert friends_a[0]["display_name"] == "User B (Addressee)"

    friends_b = client.get("/api/v1/friends", headers=auth_headers(token_b)).json()
    assert len(friends_b) == 1
    assert friends_b[0]["id"] == id_a
    assert friends_b[0]["display_name"] == "User A (Requester)"

    # Step 8: User A deletes friendship with User B
    del_resp = client.delete(f"/api/v1/friends/{id_b}", headers=auth_headers(token_a))
    assert del_resp.status_code == 204

    # Step 9: Verify friends list is now empty
    assert len(client.get("/api/v1/friends", headers=auth_headers(token_a)).json()) == 0
    assert len(client.get("/api/v1/friends", headers=auth_headers(token_b)).json()) == 0
