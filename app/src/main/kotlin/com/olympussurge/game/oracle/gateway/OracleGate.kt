package com.olympussurge.game.oracle.gateway

import android.util.Log
import com.olympussurge.game.BuildConfig
import com.olympussurge.game.oracle.config.OracleFacade
import com.olympussurge.game.oracle.model.OracleReply
import com.olympussurge.game.oracle.util.OracleUrlGuard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

/**
 * The single POST call that asks the backend "should this install see
 * the WebView or the native game?".
 *
 * See `.cursor/rules/oracle_gray_guide.md` §"Config Request Contract"
 * — this class must mirror that spec exactly.
 *
 * Every exit path is explicit about whether the endpoint *answered*,
 * because the router persists a native verdict permanently and must
 * never do so on the strength of a timeout. A URL that comes back but
 * is not web content counts as an answer we then rejected ourselves,
 * so the question stays open for the next launch.
 */
class OracleGate {

    /**
     * @param body flat JSON built by [OracleAttribution.collectBody]
     *   plus the device-side fields.
     * @return [OracleReply] — never throws, always resolves.
     */
    suspend fun query(body: JSONObject): OracleReply = withContext(Dispatchers.IO) {
        val endpoint = OracleFacade.configEndpoint
        if (endpoint.isEmpty()) {
            return@withContext OracleReply.silence("no endpoint packed")
        }
        val serialized = body.toString()
        // Logged as one line with the field count in front, so a QA run
        // can tell "the backend said no" apart from "we asked it the
        // wrong question" without attaching a proxy.
        debug { "request (${body.length()} fields): $serialized" }

        val request = Request.Builder()
            .url(endpoint)
            .header("Accept", "application/json")
            .header("Content-Type", "application/json")
            .post(serialized.toRequestBody(JSON))
            .build()

        val outcome = withTimeoutOrNull(OracleFacade.GATE_TIMEOUT_MS) {
            runCatching {
                OracleUaForge.http.newCall(request).execute().use { resp ->
                    val payload = resp.body.string()
                    debug { "response ${resp.code}: $payload" }
                    interpret(resp.code, payload).also {
                        debug { "verdict: allowed=${it.allowed} note=${it.note}" }
                    }
                }
            }.getOrElse { OracleReply.silence("io: ${it.message}") }
        }
        outcome ?: OracleReply.silence("timeout")
    }

    /**
     * 404 is the backend's ordinary way of saying "not one of ours" —
     * a real verdict, not an error. Any other non-2xx is the server
     * speaking too, so it also settles the question.
     */
    private fun interpret(code: Int, payload: String): OracleReply {
        if (code !in 200..299) return OracleReply.verdict("http $code")
        if (payload.isBlank()) return OracleReply.verdict("empty body")

        val parsed = OracleReply.parse(payload)
        if (!parsed.allowed || !parsed.hasLink) return OracleReply.verdict(parsed.note ?: "ok=false")

        if (!OracleUrlGuard.accepts(parsed.link)) {
            debug { "endpoint returned a non-web destination — ignoring it" }
            return OracleReply.verdict("destination rejected by url guard")
        }
        return parsed
    }

    private inline fun debug(message: () -> String) {
        if (BuildConfig.DEBUG) Log.d(TAG, message())
    }

    private companion object {
        val JSON = "application/json; charset=utf-8".toMediaType()
        const val TAG = "OracleGate"
    }
}
