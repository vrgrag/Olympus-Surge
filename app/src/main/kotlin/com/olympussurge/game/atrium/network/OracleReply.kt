package com.olympussurge.game.atrium.network

import kotlinx.serialization.Serializable

/**
 * Response envelope for a remote content descriptor.
 *
 * Only `ok` is guaranteed to be present on the failure branch; on the
 * success branch the server also fills `url` (mandatory) and `expires`
 * (optional cache lifetime, unix seconds).
 */
@Serializable
data class OracleReply(
    val ok: Boolean = false,
    val url: String? = null,
    val expires: Long? = null,
    val message: String? = null,
)
