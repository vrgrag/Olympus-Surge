package com.olympussurge.game.sanctum.lore

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Uniform timestamp formatter used by [MnemonicLog] for boot-phase
 * traces and by the splash tick for its debug overlay.
 */
internal object EchoTimestamp {

    private val ISO = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS", Locale.ROOT).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    fun nowIso(): String = synchronized(ISO) { ISO.format(Date()) }

    fun sinceBootMs(bootAtMs: Long): Long {
        val now = System.currentTimeMillis()
        return if (bootAtMs > 0L && now > bootAtMs) now - bootAtMs else 0L
    }
}
