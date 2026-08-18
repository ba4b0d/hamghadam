package com.fitnessapp.android.data.model

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SocialCodecTest {

    @Test
    fun testUserProfileFromJson() {
        val json = JSONObject("""
            {
                "id": 42,
                "email": "test@example.com",
                "display_name": "Test User",
                "bio": "Avid runner",
                "avatar_url": "https://api.hamghadam.ba4b0d.ir/static/avatars/42.jpg",
                "location": "Tehran",
                "premium": true,
                "tz_offset": 210,
                "auth_provider": "google",
                "created_at": "2026-08-18T10:00:00Z"
            }
        """.trimIndent())

        val profile = SocialCodec.userProfileFromJson(json)
        assertNotNull(profile)
        assertEquals(42, profile?.id)
        assertEquals("test@example.com", profile?.email)
        assertEquals("Test User", profile?.displayName)
        assertEquals("Avid runner", profile?.bio)
        assertEquals("https://api.hamghadam.ba4b0d.ir/static/avatars/42.jpg", profile?.avatarUrl)
        assertEquals("Tehran", profile?.location)
        assertTrue(profile?.premium == true)
        assertEquals(210, profile?.tzOffset)
        assertEquals("google", profile?.authProvider)
        assertEquals("2026-08-18T10:00:00Z", profile?.createdAt)
    }

    @Test
    fun testUserPublicProfileFromJson() {
        val json = JSONObject("""
            {
                "id": 10,
                "display_name": "Public User",
                "avatar_url": "https://api.hamghadam.ba4b0d.ir/static/avatars/10.jpg",
                "bio": "Public Bio",
                "location": "Shiraz",
                "friendship_status": "PENDING_SENT"
            }
        """.trimIndent())

        val pub = SocialCodec.userPublicProfileFromJson(json)
        assertNotNull(pub)
        assertEquals(10, pub?.id)
        assertEquals("Public User", pub?.displayName)
        assertEquals("PENDING_SENT", pub?.friendshipStatus)
    }

    @Test
    fun testUserPublicListFromText() {
        val jsonArrayText = """
            [
                {"id": 1, "display_name": "User 1", "friendship_status": "NONE"},
                {"id": 2, "display_name": "User 2", "friendship_status": "ACCEPTED"}
            ]
        """.trimIndent()

        val list = SocialCodec.userPublicListFromText(jsonArrayText)
        assertEquals(2, list.size)
        assertEquals("User 1", list[0].displayName)
        assertEquals("User 2", list[1].displayName)
    }

    @Test
    fun testFriendProfileFromJsonAndList() {
        val jsonText = """
            [
                {
                    "id": 5,
                    "email": "friend@example.com",
                    "display_name": "Friend Five",
                    "avatar_url": "https://api.hamghadam.ba4b0d.ir/static/avatars/5.jpg",
                    "bio": "Friend Bio",
                    "location": "Isfahan",
                    "today_steps": 12500
                }
            ]
        """.trimIndent()

        val friends = SocialCodec.friendListFromText(jsonText)
        assertEquals(1, friends.size)
        val friend = friends[0]
        assertEquals(5, friend.id)
        assertEquals("friend@example.com", friend.email)
        assertEquals("Friend Five", friend.displayName)
        assertEquals(12500, friend.todaySteps)
    }

    @Test
    fun testPendingRequestFromJsonAndList() {
        val jsonText = """
            [
                {
                    "request_id": 99,
                    "requester": {
                        "id": 7,
                        "display_name": "Requester Seven",
                        "avatar_url": null,
                        "bio": null,
                        "friendship_status": "PENDING_RECEIVED"
                    },
                    "created_at": "2026-08-18T12:00:00Z"
                }
            ]
        """.trimIndent()

        val requests = SocialCodec.pendingRequestListFromText(jsonText)
        assertEquals(1, requests.size)
        assertEquals(99, requests[0].requestId)
        assertEquals(7, requests[0].requester.id)
        assertEquals("Requester Seven", requests[0].requester.displayName)
    }

    @Test
    fun testFriendshipFromJson() {
        val json = JSONObject("""
            {
                "id": 101,
                "requester_id": 1,
                "addressee_id": 2,
                "status": "ACCEPTED",
                "created_at": "2026-08-18T10:00:00Z",
                "updated_at": "2026-08-18T10:05:00Z"
            }
        """.trimIndent())

        val fs = SocialCodec.friendshipFromJson(json)
        assertNotNull(fs)
        assertEquals(101, fs?.id)
        assertEquals(1, fs?.requesterId)
        assertEquals(2, fs?.addresseeId)
        assertEquals("ACCEPTED", fs?.status)
    }

    @Test
    fun testAvatarUploadResponseFromJson() {
        val json = JSONObject("""{"avatar_url": "https://api.hamghadam.ba4b0d.ir/static/avatars/abc.jpg"}""")
        val url = SocialCodec.avatarUploadResponseFromJson(json)
        assertEquals("https://api.hamghadam.ba4b0d.ir/static/avatars/abc.jpg", url)
    }
}
