package com.olympussurge.game.atrium.network

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Assembles the JSON body sent to the remote content descriptor endpoint.
 *
 * Merge order (spec-mandated — the raw attribution keys must survive
 * verbatim, only the client-side identifiers are appended last so they
 * cannot be shadowed by an attribution key with the same name):
 *
 *   1. Deep-link payload  (only fills gaps)
 *   2. Attribution data   (wins on conflict with the deep link)
 *   3. Client identifiers (always win, appended last)
 */
object OracleQuery {

    fun compose(
        attribution: Map<String, Any?>?,
        deepLink: Map<String, Any?>?,
        client: ChariotClientFields,
    ): JsonElement {
        val bag = linkedMapOf<String, JsonElement>()

        deepLink?.forEach { (k, v) -> bag[k] = pack(v) }
        attribution?.forEach { (k, v) -> bag[k] = pack(v) }

        bag["af_id"] = JsonPrimitive(client.afId)
        bag["bundle_id"] = JsonPrimitive(client.bundleId)
        bag["os"] = JsonPrimitive(client.os)
        bag["store_id"] = JsonPrimitive(client.storeId)
        bag["locale"] = JsonPrimitive(client.locale)
        client.pushToken?.let { bag["push_token"] = JsonPrimitive(it) }
        client.firebaseProjectId?.let { bag["firebase_project_id"] = JsonPrimitive(it) }

        return JsonObject(bag)
    }

    private fun pack(value: Any?): JsonElement = when (value) {
        null -> JsonNull
        is JsonElement -> value
        is Boolean -> JsonPrimitive(value)
        is Number -> JsonPrimitive(value)
        is String -> JsonPrimitive(value)
        is Map<*, *> -> JsonObject(
            value.entries.associate { (k, v) -> k.toString() to pack(v) },
        )
        is Collection<*> -> JsonObject(
            value.withIndex().associate { (i, v) -> i.toString() to pack(v) },
        )
        else -> JsonPrimitive(value.toString())
    }
}

/** Client-side identifiers merged into the descriptor request body. */
data class ChariotClientFields(
    val afId: String,
    val bundleId: String,
    val os: String,
    val storeId: String,
    val locale: String,
    val pushToken: String?,
    val firebaseProjectId: String?,
)
