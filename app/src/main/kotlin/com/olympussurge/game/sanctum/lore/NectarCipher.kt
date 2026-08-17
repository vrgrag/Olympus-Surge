package com.olympussurge.game.sanctum.lore

/**
 * Reversible byte-mangler for the handful of sensitive strings that
 * would otherwise sit in the compiled DEX as plaintext (descriptor
 * endpoint, attribution SDK key, Firebase project id).
 *
 * The transform:
 *   1. FNV-1a 32-bit over [SIGIL] seeds an xorshift32 state.
 *   2. The low byte of each successive state fills a [PAD_LEN]-byte pad.
 *   3. out[i] = enc[i]  ^  pad[i % PAD_LEN]  ^  ((i * SPICE + BIAS) & 0xFF)
 *
 * XOR is symmetric so the same routine encodes and decodes.
 *
 * NOTE — this is deliberately NOT cryptographic strength. Any determined
 * reverse-engineer flips it back in seconds. The goal is only to keep
 * static string-grep clustering from pulling this app into a bucket
 * with any sibling that ships the same plaintext URL.
 *
 * [FINGERPRINT WARNING] The SIGIL / PAD_LEN / SPICE / BIAS tuple below
 * is Olympus-only. Never reuse it in another shipped app — the whole
 * point of the encoder is that each install has its own decoded
 * payload AND its own encoder parameters.
 */
internal object NectarCipher {

    private const val SIGIL = "sanctum_oly_verse_2f9E"
    private const val PAD_LEN = 31
    private const val SPICE = 47
    private const val BIAS = 11

    private val pad: IntArray by lazy { primePad() }

    /** Turns an encoded byte-run back into a plain ASCII string. */
    fun unfold(enc: IntArray): String {
        if (enc.isEmpty()) return ""
        val out = ByteArray(enc.size)
        for (i in enc.indices) {
            val salt = ((i * SPICE) + BIAS) and 0xFF
            out[i] = ((enc[i] xor pad[i % PAD_LEN] xor salt) and 0xFF).toByte()
        }
        return String(out, Charsets.US_ASCII)
    }

    /** Inverse of [unfold]. Exposed for the encoder helper below and
     *  for on-device sanity checks in debug builds. */
    fun fold(plain: String): IntArray {
        val bytes = plain.toByteArray(Charsets.US_ASCII)
        val out = IntArray(bytes.size)
        for (i in bytes.indices) {
            val salt = ((i * SPICE) + BIAS) and 0xFF
            out[i] = ((bytes[i].toInt() and 0xFF) xor pad[i % PAD_LEN] xor salt) and 0xFF
        }
        return out
    }

    private fun primePad(): IntArray {
        var h = 0x811C9DC5.toInt()
        for (c in SIGIL.toByteArray(Charsets.US_ASCII)) {
            h = h xor (c.toInt() and 0xFF)
            h *= 0x01000193
        }
        var s = if (h == 0) 0x9E3779B9.toInt() else h
        val out = IntArray(PAD_LEN)
        for (i in 0 until PAD_LEN) {
            s = s xor (s shl 13)
            s = s xor (s ushr 17)
            s = s xor (s shl 5)
            out[i] = s and 0xFF
        }
        return out
    }
}
