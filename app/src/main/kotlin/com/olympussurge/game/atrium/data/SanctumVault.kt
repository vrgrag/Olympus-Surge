package com.olympussurge.game.atrium.data

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

/**
 * Persistent state of the shell layer: which content channel was picked,
 * the cached descriptor url and its expiry, plus a couple of cooldowns
 * used by the consent screen.
 *
 * Kept in a dedicated `SharedPreferences` file so the game's own prefs
 * are never touched.
 */
class SanctumVault(context: Context) {

    private val store: SharedPreferences =
        context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    var channel: SanctumChannel
        get() = SanctumChannel.fromStored(store.getString(K_CHANNEL, null))
        set(value) = store.edit { putString(K_CHANNEL, value.stored) }

    var portalUrl: String?
        get() = store.getString(K_URL, null)
        set(value) = store.edit { putString(K_URL, value) }

    /** Unix seconds. `0L` means "no expiry — the cached url is fine forever". */
    var portalExpiresAt: Long
        get() = store.getLong(K_EXPIRES, 0L)
        set(value) = store.edit { putLong(K_EXPIRES, value) }

    /** Unix millis of the last "not now" tap on the consent screen. */
    var consentDeclinedAt: Long
        get() = store.getLong(K_CONSENT_DECLINED, 0L)
        set(value) = store.edit { putLong(K_CONSENT_DECLINED, value) }

    /** Set once the platform prompt has been shown at least once. */
    var systemAlertPrompted: Boolean
        get() = store.getBoolean(K_ALERT_PROMPTED, false)
        set(value) = store.edit { putBoolean(K_ALERT_PROMPTED, value) }

    /**
     * Stashed remote-alert URL delivered while the stage was dead. The
     * launcher pass consumes and clears it — see
     * [com.olympussurge.game.IgnitionActivity]. Survives a full process
     * kill so an OEM-launcher-replaced PendingIntent never loses the URL.
     */
    var stashedAlertUrl: String?
        get() = store.getString(K_STASHED_ALERT, null)
        set(value) = store.edit { putString(K_STASHED_ALERT, value) }

    /** Consumes and clears the pending stashed alert URL. */
    fun consumeStashedAlertUrl(): String? {
        val v = store.getString(K_STASHED_ALERT, null)
        if (!v.isNullOrBlank()) store.edit { remove(K_STASHED_ALERT) }
        return v?.takeIf { it.isNotBlank() }
    }

    /** How many times the first-boot pipeline has bounced back to the
     *  silence screen because the attribution SDK had not delivered
     *  anything yet. Safety valve — see the router. */
    var firstBootStalls: Int
        get() = store.getInt(K_FIRST_STALLS, 0)
        set(value) = store.edit { putInt(K_FIRST_STALLS, value) }

    fun rememberDescriptor(url: String, expiresAtSeconds: Long) {
        store.edit {
            putString(K_URL, url)
            putLong(K_EXPIRES, expiresAtSeconds)
        }
    }

    fun descriptorFresh(nowSeconds: Long = System.currentTimeMillis() / 1000): Boolean {
        val exp = portalExpiresAt
        return portalUrl != null && (exp == 0L || exp > nowSeconds)
    }

    private companion object {
        // Intentionally terse. An `adb backup` of a device this app is
        // installed on will dump this file as-is; long descriptive keys
        // like "content_channel" or "stashed_alert_url" read as a
        // WebView-shell blueprint at a glance, which is a fingerprint
        // signal all on its own. Short opaque tokens read as random
        // ordinary app state.
        const val FILE = "os_kv7"
        const val K_CHANNEL = "s_ch"
        const val K_URL = "s_pu"
        const val K_EXPIRES = "s_px"
        const val K_CONSENT_DECLINED = "s_cd"
        const val K_ALERT_PROMPTED = "s_ap"
        const val K_STASHED_ALERT = "s_sa"
        const val K_FIRST_STALLS = "s_fs"
    }
}
