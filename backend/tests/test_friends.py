"""Friends service and API endpoints tests."""

from datetime import datetime, timedelta, timezone
from tests.conftest import auth_headers, register_user


def test_user_search(client):
    user_a = register_user(client, email="alice@example.com", display_name="Alice Wonderland")
    user_b = register_user(client, email="bob@example.com", display_name="Bob Builder")
    user_c = register_user(client, email="charlie@example.com", display_name="Charlie Chaplin")

    token_a = user_a["access_token"]

    # Search for "Bob"
    resp = client.get("/api/v1/users/search?q=Bob", headers=auth_headers(token_a))
    assert resp.status_code == 200
    results = resp.json()
    assert len(results) == 1
    assert results[0]["id"] == user_b["user"]["id"]
    assert results[0]["display_name"] == "Bob Builder"
    assert results[0]["friendship_status"] == "NONE"

    # Search shouldn't return self
    resp_self = client.get("/api/v1/users/search?q=Alice", headers=auth_headers(token_a))
    assert resp_self.status_code == 200
    assert len(resp_self.json()) == 0


def test_friend_request_lifecycle(client):
    user_a = register_user(client, email="user_a@example.com", display_name="User A")
    user_b = register_user(client, email="user_b@example.com", display_name="User B")
    user_c = register_user(client, email="user_c@example.com", display_name="User C")

    token_a = user_a["access_token"]
    token_b = user_b["access_token"]
    token_c = user_c["access_token"]
    b_id = user_b["user"]["id"]

    # 1. User A sends friend request to User B
    req_resp = client.post(
        "/api/v1/friends/request",
        json={"target_user_id": b_id},
        headers=auth_headers(token_a),
    )
    assert req_resp.status_code == 201, req_resp.text
    req_data = req_resp.json()
    assert req_data["status"] == "PENDING"
    assert req_data["requester_id"] == user_a["user"]["id"]
    assert req_data["addressee_id"] == b_id
    request_id = req_data["id"]

    # 2. Check search status for both users
    search_a = client.get(f"/api/v1/users/search?q=user_b", headers=auth_headers(token_a)).json()
    assert search_a[0]["friendship_status"] == "PENDING_SENT"

    search_b = client.get(f"/api/v1/users/search?q=user_a", headers=auth_headers(token_b)).json()
    assert search_b[0]["friendship_status"] == "PENDING_RECEIVED"

    # 3. User B checks pending requests
    pending_resp = client.get("/api/v1/friends/requests/pending", headers=auth_headers(token_b))
    assert pending_resp.status_code == 200
    pending_list = pending_resp.json()
    assert len(pending_list) == 1
    assert pending_list[0]["request_id"] == request_id
    assert pending_list[0]["requester"]["id"] == user_a["user"]["id"]

    # 4. User C tries to accept (unauthorized) -> 403
    unauth_resp = client.post(
        f"/api/v1/friends/accept/{request_id}",
        headers=auth_headers(token_c),
    )
    assert unauth_resp.status_code == 403

    # 5. User B accepts the request
    accept_resp = client.post(
        f"/api/v1/friends/accept/{request_id}",
        headers=auth_headers(token_b),
    )
    assert accept_resp.status_code == 200
    assert accept_resp.json()["status"] == "ACCEPTED"

    # 6. Both users see each other in /api/v1/friends
    friends_a = client.get("/api/v1/friends", headers=auth_headers(token_a)).json()
    assert len(friends_a) == 1
    assert friends_a[0]["id"] == b_id
    assert friends_a[0]["display_name"] == "User B"

    friends_b = client.get("/api/v1/friends", headers=auth_headers(token_b)).json()
    assert len(friends_b) == 1
    assert friends_b[0]["id"] == user_a["user"]["id"]
    assert friends_b[0]["display_name"] == "User A"


def test_friend_request_validation(client):
    user_a = register_user(client, email="val_a@example.com")
    user_b = register_user(client, email="val_b@example.com")
    token_a = user_a["access_token"]
    token_b = user_b["access_token"]
    a_id = user_a["user"]["id"]
    b_id = user_b["user"]["id"]

    # Self request rejected
    resp = client.post(
        "/api/v1/friends/request",
        json={"target_user_id": a_id},
        headers=auth_headers(token_a),
    )
    assert resp.status_code == 400
    assert "yourself" in resp.json()["detail"]

    # Nonexistent user rejected
    resp = client.post(
        "/api/v1/friends/request",
        json={"target_user_id": 999999},
        headers=auth_headers(token_a),
    )
    assert resp.status_code == 404

    # Send valid request
    client.post(
        "/api/v1/friends/request",
        json={"target_user_id": b_id},
        headers=auth_headers(token_a),
    )

    # Duplicate request rejected
    resp = client.post(
        "/api/v1/friends/request",
        json={"target_user_id": b_id},
        headers=auth_headers(token_a),
    )
    assert resp.status_code == 400
    assert "already pending" in resp.json()["detail"]

    # Reverse duplicate request rejected
    resp = client.post(
        "/api/v1/friends/request",
        json={"target_user_id": a_id},
        headers=auth_headers(token_b),
    )
    assert resp.status_code == 400
    assert "already pending" in resp.json()["detail"]


def test_friend_request_reject_and_reopen(client):
    user_a = register_user(client, email="rej_a@example.com")
    user_b = register_user(client, email="rej_b@example.com")
    token_a = user_a["access_token"]
    token_b = user_b["access_token"]
    b_id = user_b["user"]["id"]

    # A sends request to B
    req = client.post(
        "/api/v1/friends/request",
        json={"target_user_id": b_id},
        headers=auth_headers(token_a),
    ).json()

    # B rejects request
    rej = client.post(
        f"/api/v1/friends/reject/{req['id']}",
        headers=auth_headers(token_b),
    )
    assert rej.status_code == 200
    assert rej.json()["status"] == "REJECTED"

    # A sends request again -> reopened to PENDING
    reopen = client.post(
        "/api/v1/friends/request",
        json={"target_user_id": b_id},
        headers=auth_headers(token_a),
    )
    assert reopen.status_code == 201 or reopen.status_code == 200
    assert reopen.json()["status"] == "PENDING"


def test_delete_friendship(client):
    user_a = register_user(client, email="del_a@example.com")
    user_b = register_user(client, email="del_b@example.com")
    token_a = user_a["access_token"]
    token_b = user_b["access_token"]
    b_id = user_b["user"]["id"]

    # A requests B, B accepts
    req = client.post(
        "/api/v1/friends/request",
        json={"target_user_id": b_id},
        headers=auth_headers(token_a),
    ).json()
    client.post(f"/api/v1/friends/accept/{req['id']}", headers=auth_headers(token_b))

    # Verify friends
    assert len(client.get("/api/v1/friends", headers=auth_headers(token_a)).json()) == 1

    # A deletes friend B
    del_resp = client.delete(f"/api/v1/friends/{b_id}", headers=auth_headers(token_a))
    assert del_resp.status_code == 204

    # Verify friends list is now empty for both
    assert len(client.get("/api/v1/friends", headers=auth_headers(token_a)).json()) == 0
    assert len(client.get("/api/v1/friends", headers=auth_headers(token_b)).json()) == 0


def test_friends_list_today_steps(client):
    user_a = register_user(client, email="steps_a@example.com", tz_offset=210)
    user_b = register_user(client, email="steps_b@example.com", tz_offset=210)
    token_a = user_a["access_token"]
    token_b = user_b["access_token"]
    b_id = user_b["user"]["id"]

    # Connect A and B
    req = client.post(
        "/api/v1/friends/request",
        json={"target_user_id": b_id},
        headers=auth_headers(token_a),
    ).json()
    client.post(f"/api/v1/friends/accept/{req['id']}", headers=auth_headers(token_b))

    # User B ingests steps for user B's local today
    local_today = (datetime.now(timezone.utc) + timedelta(minutes=210)).date()
    today_str = local_today.strftime("%Y-%m-%d")
    ingest_resp = client.post(
        "/api/v1/daily/ingest",
        json={"date": today_str, "tz_offset": 210, "steps": 8500},
        headers=auth_headers(token_b),
    )
    assert ingest_resp.status_code == 201

    # User A views friends list -> shows User B with 8500 steps
    friends = client.get("/api/v1/friends", headers=auth_headers(token_a)).json()
    assert len(friends) == 1
    assert friends[0]["id"] == b_id
    assert friends[0]["today_steps"] == 8500


def test_friend_request_target_user_id_string_and_addressee_id(client):
    user_a = register_user(client, email="str_a@example.com")
    user_b = register_user(client, email="str_b@example.com")
    user_c = register_user(client, email="str_c@example.com")
    token_a = user_a["access_token"]
    b_id = user_b["user"]["id"]
    c_id = user_c["user"]["id"]

    # Request with string target_user_id
    resp1 = client.post(
        "/api/v1/friends/request",
        json={"target_user_id": str(b_id)},
        headers=auth_headers(token_a),
    )
    assert resp1.status_code == 201
    assert resp1.json()["addressee_id"] == b_id

    # Request with addressee_id
    resp2 = client.post(
        "/api/v1/friends/request",
        json={"addressee_id": c_id},
        headers=auth_headers(token_a),
    )
    assert resp2.status_code == 201
    assert resp2.json()["addressee_id"] == c_id
