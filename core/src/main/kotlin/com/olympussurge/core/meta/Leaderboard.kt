package com.olympussurge.core.meta

import kotlinx.serialization.Serializable

/**
 * One finished run as it appears in the hall of fame.
 *
 * The board is local and offline: it ranks the player against their own past
 * attempts, which is the only honest comparison an offline game can offer.
 */
@Serializable
data class RunRecord(
    val timestamp: Long,
    val victory: Boolean,
    val waves: Int,
    val kills: Int,
    val gems: Int,
    val essence: Int,
    val durationSeconds: Int = 0,
    val levelId: String = "",
) {
    /**
     * Ranking score. Waves dominate because surviving deeper is the real
     * measure; kills and gems break ties between equally deep runs.
     */
    val score: Int
        get() = waves * 1000 + kills * 10 + gems * 5 + if (victory) 5000 else 0
}

/** Immutable top-N board; adding a run returns a new, re-sorted board. */
@Serializable
data class Leaderboard(val records: List<RunRecord> = emptyList()) {

    fun withRun(record: RunRecord): Leaderboard = Leaderboard(
        (records + record).sortedByDescending { it.score }.take(MAX_ENTRIES)
    )

    /** Position of a run in the board, 1-based, or null if it did not place. */
    fun rankOf(record: RunRecord): Int? {
        val index = records.indexOfFirst { it.timestamp == record.timestamp }
        return if (index < 0) null else index + 1
    }

    val best: RunRecord? get() = records.firstOrNull()

    companion object {
        const val MAX_ENTRIES = 20
    }
}
