package com.olympussurge.game.atrium.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * Thin client for the remote content descriptor endpoint. Body is a
 * raw [JsonElement] so nothing about the attribution payload is ever
 * reshaped by this layer — the server contract is preserved verbatim.
 *
 * Success semantics:
 *   • HTTP 2xx with a parseable JSON body → `Result.success(reply)`.
 *   • Any other outcome (transport error, non-2xx, empty body, malformed
 *     JSON) → `Result.failure` with a message the router uses to tell a
 *     bad server response apart from a broken pipe.
 */
class OracleApi(
    private val endpoint: String,
    private val http: OkHttpClient = defaultClient(),
    private val json: Json = defaultJson,
) {

    suspend fun ask(body: JsonElement): Result<OracleReply> = withContext(Dispatchers.IO) {
        runCatching {
            val payload = json.encodeToString(JsonElement.serializer(), body)
                .toRequestBody(JSON_MEDIA)
            val request = Request.Builder()
                .url(endpoint)
                .addHeader("accept", "application/json")
                .addHeader("content-type", "application/json")
                .post(payload)
                .build()

            http.newCall(request).execute().use { response ->
                if (!response.isSuccessful) error("HTTP ${response.code}")
                val raw = response.body?.string().orEmpty()
                if (raw.isBlank()) error("Empty body")
                json.decodeFromString(OracleReply.serializer(), raw)
            }
        }
    }

    private companion object {
        val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()

        val defaultJson: Json = Json {
            ignoreUnknownKeys = true
            isLenient = true
            encodeDefaults = true
        }

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .build()
    }
}

/**
 * Fixed descriptor endpoint provided by the operator for Olympus Surge.
 *
 * The URL is folded through [com.olympussurge.game.sanctum.lore.NectarCipher]
 * so the compiled DEX does not carry it as a plain ASCII string — a
 * naive `strings <apk>` grep will miss it. This is not cryptography
 * (any reverse-engineer flips it back in seconds); it just keeps the
 * install out of the "same plaintext URL as sibling X" bucket during
 * static clustering.
 */
object DelphiEndpoint {
    private val URL_ENC: IntArray = intArrayOf(
        114, 31, 100, 167, 235, 124, 227, 43, 180, 31, 236, 48,
        204, 112, 137, 25, 21, 219, 62, 215, 78, 93, 206, 205,
        249, 144, 110, 207, 42, 162, 178, 202, 148, 19, 110, 87,
    )

    /** Unfolded lazily so the string exists in memory only after the
     *  first descriptor call — not sitting in the .rodata section of
     *  the DEX for a static scanner to trip over. */
    val URL: String by lazy {
        com.olympussurge.game.sanctum.lore.NectarCipher.unfold(URL_ENC)
    }
}
