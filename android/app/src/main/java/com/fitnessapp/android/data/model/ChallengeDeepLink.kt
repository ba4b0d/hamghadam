package com.fitnessapp.android.data.model

import android.net.Uri

/**
 * Parser for the app's deep-link contract (BE-C3):
 *
 *   fitnessapp://challenges/{id}                    → challenge detail
 *   fitnessapp://challenges/{id}/join?code={code}   → join via invite code
 *   fitnessapp://challenges/{id}/leaderboard        → results / leaderboard
 *
 * The core parser is pure Kotlin (unit-testable without the Android
 * framework); the `Uri` overload delegates so activity VIEW intents, FCM
 * `data.deep_link` payloads and the debug broadcast all share one path.
 */
sealed class ChallengeDeepLink {
    data class Detail(val challengeId: Long) : ChallengeDeepLink()
    data class Join(val challengeId: Long, val code: String?) : ChallengeDeepLink()
    data class Leaderboard(val challengeId: Long) : ChallengeDeepLink()

    companion object {
        private const val SCHEME_HOST = "fitnessapp://challenges"

        /** Uri variant — used by MainActivity VIEW intents and unit tests. */
        fun parse(uri: Uri?): ChallengeDeepLink? = parse(uri?.toString())

        /** String variant — used by FCM payloads and the debug simulator. */
        fun parse(text: String?): ChallengeDeepLink? {
            if (text.isNullOrBlank()) return null
            val s = text.trim()
            if (!s.startsWith(SCHEME_HOST)) return null
            val rest = s.removePrefix(SCHEME_HOST)
            // Host boundary: "fitnessapp://challengesX/1" must not match.
            if (rest.isNotEmpty() && !rest.startsWith("/")) return null

            val pathAndQuery = rest.removePrefix("/")
            val query = pathAndQuery.substringAfter('?', missingDelimiterValue = "").ifEmpty { null }
            val path = pathAndQuery.substringBefore('?')
            val segments = path.split('/').filter { it.isNotEmpty() }
            if (segments.isEmpty()) return null

            val id = segments[0].toLongOrNull() ?: return null
            val code = query?.split('&')
                ?.firstOrNull { it.startsWith("code=") }
                ?.removePrefix("code=")
                ?.ifEmpty { null }

            return when {
                segments.size == 1 -> Detail(id)
                segments.size == 2 && segments[1] == "join" -> Join(id, code)
                segments.size == 2 && segments[1] == "leaderboard" -> Leaderboard(id)
                else -> null
            }
        }
    }
}

/** Deep-link URI (matches the BE-C3 contract; used for notifications/adb). */
fun ChallengeDeepLink.toUri(): String = when (this) {
    is ChallengeDeepLink.Detail -> "fitnessapp://challenges/$challengeId"
    is ChallengeDeepLink.Join -> "fitnessapp://challenges/$challengeId/join?code=${code ?: ""}"
    is ChallengeDeepLink.Leaderboard -> "fitnessapp://challenges/$challengeId/leaderboard"
}

/** Navigation-compose route string for the app's NavHost. */
fun ChallengeDeepLink.toNavRoute(): String = when (this) {
    is ChallengeDeepLink.Detail -> "challenge/$challengeId"
    is ChallengeDeepLink.Join -> "challenge/$challengeId?joinCode=${code ?: ""}"
    is ChallengeDeepLink.Leaderboard -> "challenge/$challengeId?showLeaderboard=true"
}