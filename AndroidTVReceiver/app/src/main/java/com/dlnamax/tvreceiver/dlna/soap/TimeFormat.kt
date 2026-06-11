package com.dlnamax.tvreceiver.dlna.soap

import java.util.Locale

object TimeFormat {
    fun formatMillis(valueMs: Long): String {
        val totalSeconds = (valueMs / MILLIS_PER_SECOND).coerceAtLeast(0L)
        val hours = totalSeconds / SECONDS_PER_HOUR
        val minutes = (totalSeconds % SECONDS_PER_HOUR) / SECONDS_PER_MINUTE
        val seconds = totalSeconds % SECONDS_PER_MINUTE

        return String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
    }

    fun parseMillis(value: String): Long? {
        val parts = value.trim().split(":")
        if (parts.size != TIME_PART_COUNT) return value.toLongOrNull()?.times(MILLIS_PER_SECOND)

        val hours = parts[0].toLongOrNull() ?: return null
        val minutes = parts[1].toLongOrNull() ?: return null
        val seconds = parts[2].substringBefore(".").toLongOrNull() ?: return null
        if (minutes !in 0 until SECONDS_PER_MINUTE || seconds !in 0 until SECONDS_PER_MINUTE) return null

        return ((hours * SECONDS_PER_HOUR) + (minutes * SECONDS_PER_MINUTE) + seconds) * MILLIS_PER_SECOND
    }

    private const val TIME_PART_COUNT = 3
    private const val MILLIS_PER_SECOND = 1_000L
    private const val SECONDS_PER_MINUTE = 60L
    private const val SECONDS_PER_HOUR = 3_600L
}
