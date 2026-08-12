package com.olympussurge.game.oracle.gateway

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.olympussurge.game.BuildConfig
import com.olympussurge.game.R
import com.olympussurge.game.oracle.OraclePortalActivity
import com.olympussurge.game.oracle.model.OracleMode
import com.olympussurge.game.oracle.util.OracleUrlGuard
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Push notification plumbing.
 *
 * The `CHANNEL_ID` here MUST match the manifest meta-data
 * `com.google.firebase.messaging.default_notification_channel_id`.
 * See `.cursor/rules/oracle_pitfalls.md` §13.
 */
object OracleBeacon {

    // [FINGERPRINT] Change per project (and update AndroidManifest.xml).
    const val CHANNEL_ID = "oracle_surge_alerts"
    private const val CHANNEL_NAME = "Olympus alerts"
    private const val CHANNEL_DESC = "Bonuses, promos, and important updates"
    private const val TAG = "OracleBeacon"

    /** Our own extras, set when *we* build the tap intent. */
    const val EXTRA_URL = "portal_url"
    const val EXTRA_FROM_PUSH = "portal_from_push"

    /** Payload keys the Firebase SDK forwards verbatim as string extras. */
    private val RAW_URL_KEYS = listOf("url", "link", "target_url")

    fun ensureChannel(ctx: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val mgr = ctx.getSystemService(NotificationManager::class.java) ?: return
        if (mgr.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = CHANNEL_DESC
            enableLights(true)
            enableVibration(true)
        }
        mgr.createNotificationChannel(channel)
    }

    /**
     * Current FCM token, or `null` on any failure — a missing
     * google-services.json, Play Services absent, a device with no
     * network. Per the config contract the caller must then omit
     * `push_token` and `firebase_project_id` entirely rather than send
     * empty strings.
     */
    suspend fun fetchToken(): String? = suspendCancellableCoroutine { cont ->
        runCatching {
            FirebaseMessaging.getInstance().token
                .addOnSuccessListener { if (cont.isActive) cont.resume(it) }
                .addOnFailureListener { if (cont.isActive) cont.resume(null) }
        }.onFailure {
            if (cont.isActive) cont.resume(null)
        }
    }

    /**
     * The URL a notification tap carried, in either shape it can arrive.
     *
     * A data-only message reaches [OracleTokenService], which builds the
     * tap intent with [EXTRA_URL]. A message carrying a `notification`
     * block is drawn by the Firebase SDK itself whenever the app is not
     * in the foreground — that path never runs our service at all, and
     * the tap opens the launcher with the raw `data` payload as plain
     * string extras instead. Reading only our own extra is how a pushed
     * link gets silently dropped and the shell reopens on the previously
     * saved page (pitfalls §32).
     */
    fun extractUrl(intent: Intent?): String? {
        if (intent == null) return null
        val own = intent.getStringExtra(EXTRA_URL)
        val raw = RAW_URL_KEYS.firstNotNullOfOrNull { intent.getStringExtra(it) }
        return OracleUrlGuard.sanitize(own ?: raw)
    }

    /** True when this intent came from a notification tap at all. */
    fun isPushTap(intent: Intent?): Boolean {
        if (intent == null) return false
        if (intent.getBooleanExtra(EXTRA_FROM_PUSH, false)) return true
        return RAW_URL_KEYS.any { intent.hasExtra(it) }
    }

    internal fun buildTapIntent(ctx: Context, url: String?): Intent =
        Intent(ctx, OraclePortalActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            putExtra(EXTRA_FROM_PUSH, true)
            if (!url.isNullOrEmpty()) putExtra(EXTRA_URL, url)
        }

    internal fun render(ctx: Context, title: String, body: String, url: String?, imageUrl: String?) {
        ensureChannel(ctx)

        val pending = PendingIntent.getActivity(
            ctx,
            System.currentTimeMillis().toInt(),
            buildTapIntent(ctx, url),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val builder = NotificationCompat.Builder(ctx, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_oracle_beacon)
            .setColor(ContextCompat.getColor(ctx, R.color.oracle_beacon_flame))
            .setContentTitle(title)
            .setContentText(body)
            .setContentIntent(pending)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)

        val bitmap = imageUrl?.takeIf { it.isNotEmpty() }?.let(::loadBitmap)
        if (bitmap != null) {
            builder
                .setLargeIcon(bitmap)
                .setStyle(
                    NotificationCompat.BigPictureStyle()
                        .bigPicture(bitmap)
                        .bigLargeIcon(null as Bitmap?),
                )
        } else {
            builder.setStyle(NotificationCompat.BigTextStyle().bigText(body))
        }

        val id = (System.currentTimeMillis() and 0x7FFFFFFF).toInt()
        runCatching { NotificationManagerCompat.from(ctx).notify(id, builder.build()) }
    }

    private fun loadBitmap(url: String): Bitmap? = runCatching {
        OracleUaForge.http.newCall(okhttp3.Request.Builder().url(url).get().build())
            .execute().use { resp ->
                if (!resp.isSuccessful) null
                else resp.body.byteStream().use { BitmapFactory.decodeStream(it) }
            }
    }.getOrElse {
        if (BuildConfig.DEBUG) Log.w(TAG, "big-picture load failed: ${it.message}")
        null
    }
}

/**
 * Firebase messaging service. Registered from AndroidManifest.xml —
 * the class name is what stays stable, so ProGuard must keep it.
 *
 * URL routing rules, in order:
 *
 *  - A URL that fails [OracleUrlGuard] is dropped and the notification
 *    is shown as text. A tap must never open a page the shell cannot
 *    load.
 *  - A user on [OracleMode.Native] keeps their game. The notification
 *    still appears, but the URL is neither handed over nor saved:
 *    flipping someone into a WebView after the fact is a store-review
 *    problem, not a feature.
 *  - A live shell takes the URL directly through [OracleRelay] and
 *    nothing is persisted — a warm URL is a fact about this moment.
 *  - Otherwise it is stashed for exactly one cold start.
 */
class OracleTokenService : FirebaseMessagingService() {

    private val bg = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onDestroy() {
        bg.cancel()
        super.onDestroy()
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        // Cached so the config POST does not have to wait on Firebase
        // on every launch, and so a rotation is not lost if the next
        // launch happens offline.
        OracleVault(applicationContext).writePushToken(token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val data = message.data
        val notification = message.notification

        val title = data["title"] ?: notification?.title.orEmpty()
        val body = data["body"] ?: notification?.body.orEmpty()
        if (title.isEmpty() && body.isEmpty()) return

        val url = OracleUrlGuard.sanitize(data["url"] ?: data["link"] ?: data["target_url"])
        val image = data["image"] ?: notification?.imageUrl?.toString()

        val vault = OracleVault(applicationContext)
        val mode = vault.readMode()

        if (url != null && mode != OracleMode.Native) {
            if (OracleRelay.offer(url)) {
                // The shell took it; still show the notification so the
                // user knows something arrived, but do not stash a URL
                // that has already been delivered.
                bg.launch { OracleBeacon.render(applicationContext, title, body, null, image) }
                return
            }
            vault.stashPushLink(url)
        }

        val tapUrl = if (mode == OracleMode.Native) null else url
        bg.launch { OracleBeacon.render(applicationContext, title, body, tapUrl, image) }
    }
}
