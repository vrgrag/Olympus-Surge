package com.olympussurge.game.atrium.data

/**
 * Which content channel the app has settled on.
 *
 * The choice is picked exactly once (on the very first cold boot) from
 * a combination of attribution signals and a remote content descriptor.
 * From that moment onwards it stays frozen for the rest of the install.
 *
 * The literal storage values are intentionally short and neutral so a
 * casual dump of `SharedPreferences` does not reveal our routing logic.
 */
enum class SanctumChannel(val stored: String) {
    /** Remote-hosted panel is live for this install. */
    Portal("p2"),

    /** No remote panel — the native arena is the only surface. */
    Homefront("h1"),

    /** Not decided yet — the router will run the first-boot pipeline. */
    Uncharted("u0");

    companion object {
        fun fromStored(value: String?): SanctumChannel = when (value) {
            Portal.stored -> Portal
            Homefront.stored -> Homefront
            else -> Uncharted
        }
    }
}
