package com.fitnessapp.android.data.model

import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Display formatting for challenges (pure; unit-tested). All parsing is
 * defensive — unparseable timestamps fall back to the raw string.
 */
object ChallengeFormatters {

    private val isoParser = DateTimeFormatter.ISO_OFFSET_DATE_TIME
    private val displayDate = DateTimeFormatter.ofPattern("MMM d", Locale.getDefault())
    private val displayDateTime = DateTimeFormatter.ofPattern("MMM d, HH:mm", Locale.getDefault())

    /** Parse a server UTC instant (ISO-8601) into a local-zone OffsetDateTime. */
    fun parseIso(value: String): OffsetDateTime? = try {
        if (value.endsWith("Z")) {
            Instant.parse(value).atZone(ZoneId.systemDefault()).toOffsetDateTime()
        } else {
            OffsetDateTime.parse(value, isoParser)
        }
    } catch (_: Exception) {
        null
    }

    /** "Aug 16 – Aug 22" (or "Aug 16, 09:00 – Aug 22, 18:00" when the days differ). */
    fun formatWindow(startsAt: String, endsAt: String): String {
        val s = parseIso(startsAt)
        val e = parseIso(endsAt)
        if (s == null || e == null) return "$startsAt – $endsAt"
        val sameDay = s.toLocalDate() == e.toLocalDate()
        return if (sameDay) "${s.format(displayDateTime)} – ${e.format(displayDateTime)}"
        else "${s.format(displayDateTime)} – ${e.format(displayDateTime)}"
    }

    /** Short window label used on list cards. */
    fun formatWindowShort(startsAt: String, endsAt: String): String {
        val s = parseIso(startsAt)
        val e = parseIso(endsAt)
        if (s == null || e == null) return "$startsAt → $endsAt"
        return "${s.format(displayDate)} → ${e.format(displayDate)}"
    }

    fun formatExpiry(expiresAt: String): String {
        val t = parseIso(expiresAt) ?: return "expires $expiresAt"
        return "expires ${t.format(displayDateTime)}"
    }

    fun statusLabel(status: String): String = when (status) {
        "active" -> "Active"
        "draft" -> "Upcoming"
        "ended" -> "Ended"
        else -> status
    }

    fun statusDescription(status: String): String = when (status) {
        "active" -> "Live now — steps count toward the total."
        "draft" -> "Not started yet. Joins are open."
        "ended" -> "Finished. Final results below."
        else -> status
    }

    /** Invite codes use an unambiguous alphabet: A–H J–N P–Z 2–9 (8 chars). */
    private val INVITE_CODE_REGEX = Regex("^[A-HJ-NP-Z2-9]{8}$")

    fun isValidInviteCode(code: String): Boolean = INVITE_CODE_REGEX.matches(code.trim().uppercase(Locale.ROOT))

    /** Normalize a typed invite code (trim, uppercase). */
    fun normalizeInviteCode(code: String): String = code.trim().uppercase(Locale.ROOT)

    fun formatSteps(value: Double): String {
        val v = value.toLong()
        return if (v >= 1000) {
            val thousands = v / 1000.0
            if (thousands >= 10) "%,d".format(Locale.getDefault(), v)
            else String.format(Locale.getDefault(), "%.1fK", thousands)
        } else {
            v.toString()
        }
    }

    fun formatTotal(metric: String, value: Double): String = when (metric) {
        "steps" -> formatSteps(value)
        "sleep_seconds" -> "%.1fh".format(Locale.getDefault(), value / 3600.0)
        "avg_hr" -> "%.0f bpm".format(Locale.getDefault(), value)
        else -> "%.0f".format(Locale.getDefault(), value)
    }

    /** Ordinal-ish rank badge: "1", "2", "3", "4+". */
    fun rankLabel(rank: Int): String = if (rank > 3) "#$rank" else "#$rank"
}
