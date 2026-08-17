package com.fitnessapp.android.data.model

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChallengeCodecTest {

    private fun sampleChallengeJson(overrides: Map<String, Any?> = emptyMap()): JSONObject {
        val json = JSONObject(
            """
            {
              "id": 7,
              "title": "Weekend 10k",
              "metric": "steps",
              "starts_at": "2026-08-15T00:00:00Z",
              "ends_at": "2026-08-17T23:59:59Z",
              "status": "active",
              "invite_only": false,
              "max_participants": null,
              "creator": {"id": 1, "display_name": "Alice"},
              "created_at": "2026-08-14T10:00:00Z",
              "updated_at": "2026-08-14T10:00:00Z",
              "participants": [
                {"user_id": 1, "display_name": "Alice", "is_creator": true, "joined_at": "2026-08-14T10:00:00Z", "total": 12000.0},
                {"user_id": 2, "display_name": "Bob", "is_creator": false, "joined_at": "2026-08-15T08:00:00Z", "total": 14500.0}
              ]
            }
            """.trimIndent()
        )
        overrides.forEach { (k, v) ->
            if (v == null) json.put(k, JSONObject.NULL) else json.put(k, v)
        }
        return json
    }

    @Test
    fun `parse full challenge`() {
        val c = ChallengeCodec.challengeFromJson(sampleChallengeJson())
        assertEquals(7, c.id)
        assertEquals("Weekend 10k", c.title)
        assertEquals("steps", c.metric)
        assertEquals("active", c.status)
        assertTrue(c.isActive)
        assertFalse(c.isEnded)
        assertFalse(c.inviteOnly)
        assertNull(c.maxParticipants)
        assertEquals(1, c.creator.id)
        assertEquals("Alice", c.creator.displayName)
        assertEquals(2, c.participants.size)
        assertEquals(14500.0, c.participants[1].total, 0.001)
        assertTrue(c.participants[0].isCreator)
    }

    @Test
    fun `parse challenge tolerates missing optionals`() {
        val json = sampleChallengeJson(
            mapOf(
                "max_participants" to null,
                "creator" to null,
                "participants" to null,
            )
        )
        val c = ChallengeCodec.challengeFromJson(json)
        assertNull(c.maxParticipants)
        assertEquals(-1, c.creator.id) // silent fallback
        assertTrue(c.participants.isEmpty())
    }

    @Test
    fun `parse challenge with max participants`() {
        val c = ChallengeCodec.challengeFromJson(sampleChallengeJson(mapOf("max_participants" to 4)))
        assertEquals(4, c.maxParticipants)
        assertTrue(c.inviteOnly.not())
    }

    @Test
    fun `parse bare array list from backend`() {
        val text = "[${sampleChallengeJson()}, ${sampleChallengeJson(mapOf("id" to 8, "status" to "draft", "invite_only" to true))}]"
        val list = ChallengeCodec.challengeListFromText(text)
        assertEquals(2, list.size)
        assertEquals(7, list[0].id)
        assertEquals(8, list[1].id)
        assertTrue(list[1].isDraft)
        assertTrue(list[1].inviteOnly)
    }

    @Test
    fun `parse wrapped list shape`() {
        val text = """{"items": [${sampleChallengeJson()}]}"""
        val list = ChallengeCodec.challengeListFromText(text)
        assertEquals(1, list.size)
        assertEquals(7, list[0].id)
    }

    @Test
    fun `blank list text yields empty`() {
        assertTrue(ChallengeCodec.challengeListFromText(null).isEmpty())
        assertTrue(ChallengeCodec.challengeListFromText("").isEmpty())
        assertTrue(ChallengeCodec.challengeListFromText("garbage").isEmpty())
    }

    @Test
    fun `parse leaderboard with is_me flags`() {
        val text = """
            {
              "challenge_id": 1,
              "metric": "steps",
              "status": "active",
              "as_of": "2026-08-17",
              "entries": [
                {"rank": 1, "user_id": 2, "display_name": "Bob", "total": 8000.0, "daily": [{"date": "2026-08-15", "value": 8000.0}, {"date": "2026-08-16", "value": 0.0}], "is_me": false},
                {"rank": 2, "user_id": 1, "display_name": "Alice", "total": 5000.0, "daily": [{"date": "2026-08-15", "value": 5000.0}], "is_me": true}
              ]
            }
        """.trimIndent()
        val board = ChallengeCodec.leaderboardFromJson(JSONObject(text))!!
        assertEquals(1, board.challengeId)
        assertEquals("2026-08-17", board.asOf)
        assertEquals(2, board.entries.size)
        assertEquals(1, board.entries[0].rank)
        assertTrue(board.me?.isMe == true)
        assertEquals(5000.0, board.myTotal!!, 0.001)
        assertEquals(2, board.myRank)
        assertEquals(2, board.entries[0].daily.size)
        assertEquals(0.0, board.entries[0].daily[1].value, 0.001)
    }

    @Test
    fun `leaderboard without entries still parses`() {
        val board = ChallengeCodec.leaderboardFromJson(JSONObject("""{"challenge_id":1,"metric":"steps","status":"active","as_of":"2026-08-17","entries":[]}"""))
        assertNotNull(board)
        assertTrue(board!!.entries.isEmpty())
        assertNull(board.me)
    }

    @Test
    fun `parse invite`() {
        val invite = ChallengeCodec.inviteFromJson(
            JSONObject(
                """
                {"challenge_id": 2, "code": "R7NRX322", "expires_at": "2026-08-23T10:00:00Z",
                 "deep_link": "fitnessapp://challenges/2/join?code=R7NRX322", "created_at": "2026-08-16T10:00:00Z"}
                """.trimIndent()
            )
        )!!
        assertEquals("R7NRX322", invite.code)
        assertEquals("fitnessapp://challenges/2/join?code=R7NRX322", invite.deepLink)
        assertEquals(2, invite.challengeId)
    }

    @Test
    fun `parse fcm registration`() {
        val reg = ChallengeCodec.fcmRegistrationFromJson(
            JSONObject("""{"status": "ok", "token": "abc", "platform": "android", "registered_at": "2026-08-16T10:00:00Z"}""")
        )!!
        assertEquals("abc", reg.token)
        assertEquals("android", reg.platform)
    }
}