package com.fitnessapp.android.data.model

import com.fitnessapp.android.ui.challenges.ChallengeFormValidator
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChallengeFormattersTest {

    @Test
    fun `window shows both dates`() {
        val s = ChallengeFormatters.formatWindow("2026-08-15T09:30:00Z", "2026-08-17T18:00:00Z")
        assertTrue(s.contains("Aug 15"))
        assertTrue(s.contains("Aug 17"))
    }

    @Test
    fun `window falls back to raw strings for garbage input`() {
        assertEquals("bogus – also-bogus", ChallengeFormatters.formatWindow("bogus", "also-bogus"))
    }

    @Test
    fun `status labels`() {
        assertEquals("Active", ChallengeFormatters.statusLabel("active"))
        assertEquals("Upcoming", ChallengeFormatters.statusLabel("draft"))
        assertEquals("Ended", ChallengeFormatters.statusLabel("ended"))
        assertEquals("weird", ChallengeFormatters.statusLabel("weird"))
    }

    @Test
    fun `invite code validation`() {
        assertTrue(ChallengeFormatters.isValidInviteCode("R7NRX322"))
        // typeable alphabet only: rejects 0/O/1/I/L
        assertFalse(ChallengeFormatters.isValidInviteCode("R7NRX32O"))
        assertFalse(ChallengeFormatters.isValidInviteCode("R7NRX321"))
        assertFalse(ChallengeFormatters.isValidInviteCode("short"))
        assertFalse(ChallengeFormatters.isValidInviteCode("R7NRX3229")) // 9 chars
        // normalization
        assertEquals("R7NRX322", ChallengeFormatters.normalizeInviteCode(" r7nrx322 "))
    }

    @Test
    fun `steps formatting`() {
        assertEquals("12,345", ChallengeFormatters.formatSteps(12345.0))
        assertEquals("8.0K", ChallengeFormatters.formatSteps(8000.0))
        assertEquals("950", ChallengeFormatters.formatSteps(950.0))
    }

    @Test
    fun `total formatting by metric`() {
        assertEquals("12,345", ChallengeFormatters.formatTotal("steps", 12345.0))
        assertEquals("8.0h", ChallengeFormatters.formatTotal("sleep_seconds", 28800.0))
        assertEquals("71 bpm", ChallengeFormatters.formatTotal("avg_hr", 71.0))
    }

    @Test
    fun `expiry formatting`() {
        val out = ChallengeFormatters.formatExpiry("2026-08-23T10:00:00Z")
        assertTrue(out.startsWith("expires"))
    }

    @Test
    fun `parseIso handles Z and offset`() {
        assertEquals(2026, ChallengeFormatters.parseIso("2026-08-15T09:30:00Z")?.year)
        assertEquals(2026, ChallengeFormatters.parseIso("2026-08-15T09:30:00+03:30")?.year)
        assertNull(ChallengeFormatters.parseIso("not-a-date"))
    }

    @Test
    fun `rank label`() {
        assertEquals("#1", ChallengeFormatters.rankLabel(1))
        assertEquals("#4", ChallengeFormatters.rankLabel(4))
    }
}

class ChallengeFormValidatorTest {

    @Test
    fun `rejects blank title`() {
        val now = LocalDateTime.now()
        val err = ChallengeFormValidator.validate("   ", now, now.plusDays(1), null)
        assertEquals("Give the challenge a title", err)
    }

    @Test
    fun `rejects end before start`() {
        val now = LocalDateTime.now()
        val err = ChallengeFormValidator.validate("Walk", now.plusDays(1), now, null)
        assertEquals("End must be after the start", err)
    }

    @Test
    fun `rejects same start and end`() {
        val now = LocalDateTime.now()
        val err = ChallengeFormValidator.validate("Walk", now, now, null)
        assertEquals("End must be after the start", err)
    }

    @Test
    fun `rejects bad max participants`() {
        val now = LocalDateTime.now()
        assertEquals(
            "Max participants must be between 2 and 1000",
            ChallengeFormValidator.validate("Walk", now, now.plusDays(1), 1)
        )
        assertEquals(
            "Max participants must be between 2 and 1000",
            ChallengeFormValidator.validate("Walk", now, now.plusDays(1), 1001)
        )
    }

    @Test
    fun `accepts valid form`() {
        val now = LocalDateTime.now()
        assertNull(ChallengeFormValidator.validate("Walk", now, now.plusDays(1), null))
        assertNull(ChallengeFormValidator.validate("Walk", now, now.plusDays(1), 5))
    }
}

class ChallengeDeepLinkTest {

    @Test
    fun `detail link`() {
        val link = ChallengeDeepLink.parse("fitnessapp://challenges/12")!!
        assertTrue(link is ChallengeDeepLink.Detail)
        assertEquals(12L, (link as ChallengeDeepLink.Detail).challengeId)
        assertEquals("fitnessapp://challenges/12", link.toUri())
        assertEquals("challenge/12", link.toNavRoute())
    }

    @Test
    fun `join link with code`() {
        val link = ChallengeDeepLink.parse("fitnessapp://challenges/2/join?code=R7NRX322")!!
        assertTrue(link is ChallengeDeepLink.Join)
        val join = link as ChallengeDeepLink.Join
        assertEquals(2L, join.challengeId)
        assertEquals("R7NRX322", join.code)
        assertEquals("challenge/2?joinCode=R7NRX322", link.toNavRoute())
    }

    @Test
    fun `join link without code still parses`() {
        val link = ChallengeDeepLink.parse("fitnessapp://challenges/2/join")!!
        assertTrue(link is ChallengeDeepLink.Join)
        assertNull((link as ChallengeDeepLink.Join).code)
        assertEquals("challenge/2?joinCode=", link.toNavRoute())
    }

    @Test
    fun `leaderboard link`() {
        val link = ChallengeDeepLink.parse("fitnessapp://challenges/2/leaderboard")!!
        assertTrue(link is ChallengeDeepLink.Leaderboard)
        assertEquals("challenge/2?showLeaderboard=true", link.toNavRoute())
    }

    @Test
    fun `rejects wrong scheme host and junk`() {
        assertNull(ChallengeDeepLink.parse("https://challenges/2"))
        assertNull(ChallengeDeepLink.parse("fitnessapp://other/2"))
        assertNull(ChallengeDeepLink.parse("fitnessapp://challenges/abc"))
        assertNull(ChallengeDeepLink.parse("fitnessapp://challenges/"))
        assertNull(ChallengeDeepLink.parse("fitnessapp://challenges/1/unknown"))
        assertNull(ChallengeDeepLink.parse("fitnessapp://challengesX/1"))
        assertNull(ChallengeDeepLink.parse(null as String?))
    }

    @Test
    fun `string overload`() {
        val link = ChallengeDeepLink.parse("fitnessapp://challenges/5/leaderboard")
        assertTrue(link is ChallengeDeepLink.Leaderboard)
        assertNull(ChallengeDeepLink.parse("   "))
        assertNull(ChallengeDeepLink.parse("not a uri"))
    }
}