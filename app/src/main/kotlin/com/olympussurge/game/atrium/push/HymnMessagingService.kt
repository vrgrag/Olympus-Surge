package com.olympussurge.game.atrium.push

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.core.app.NotificationCompat
import androidx.core.content.getSystemService
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.olympussurge.game.R
import com.olympussurge.game.SanctumApplication
import com.olympussurge.game.IgnitionActivity
import com.olympussurge.game.sanctum.lore.MnemonicLog
import com.olympussurge.game.atrium.SanctumStageActivity
import com.olympussurge.game.atrium.data.SanctumChannel
import java.net.URL
import java.util.concurrent.atomic.AtomicInteger

/**
 * Handles incoming FCM messages. Three delivery modes, in priority:
 *
 *   1. Live in foreground: the stage is up AND has a callback listening
 *      on [HymnRelay]. Push the alert straight to the running stage —
 *      no system notification, no user-visible chrome change.
 *
 *   2. Live in background: stage is alive but the callback is cleared
 *      (activity paused). Post a notification whose PendingIntent
 *      targets the stage directly with SINGLE_TOP | CLEAR_TOP so a tap
 *      wakes it via `onNewIntent`.
 *
 *   3. Cold (stage dead, or the app is on the homefront channel, or
 *      never launched): PendingIntent goes through [IgnitionActivity] so
 *      the router runs its usual pass and picks up the alert URL along
 *      the way.
 *
 * Homefront channel is a special case: no alert is ever handed to the
 * stage, since organic users don't have one. We still render the
 * notification for them so the message is visible; the tap just opens
 * the app.
 */
class HymnMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        // Next descriptor request will pick up the fresh token via
        // `FirebaseMessaging.getInstance().token.await()`.
        MnemonicLog.chant(TAG, "onNewToken (len=${token.length})")
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val data = message.data
        val title = message.notification?.title
            ?: data["title"]
            ?: getString(R.string.notification_channel_default)
        val body = message.notification?.body ?: data["body"].orEmpty()
        val url = pickUrl(data)
        val imageUrl = message.notification?.imageUrl?.toString() ?: data[KEY_IMAGE]

        MnemonicLog.chant(
            TAG,
            "onMessageReceived url=${url ?: "<none>"} title='$title' stageAlive=${HymnRelay.stageAlive}",
        )

        val app = applicationContext as? SanctumApplication
        val homefront = app?.vault?.channel == SanctumChannel.Homefront

        if (!homefront && url != null && HymnRelay.handOff(url)) {
            MnemonicLog.chant(TAG, "live alert delivered to running stage (no notification)")
            return
        }

        // Stash for the next launch even before posting the notification.
        // Two reasons this is critical:
        //   a) Firebase's notification-payload pushes bypass
        //      onMessageReceived when the app is in the background,
        //      so the tap opens the launcher via the default intent —
        //      WITHOUT our EXTRA_ALERT_URL. The router picks the URL
        //      up from the vault on next boot (see IgnitionActivity).
        //   b) On some ROMs (MIUI, EMUI) our custom PendingIntent is
        //      replaced by the OEM launcher shortcut, again dropping
        //      the extras. The vault is the only survivable path.
        if (!homefront && url != null && !HymnRelay.stageAlive) {
            app?.vault?.stashedAlertUrl = url
            MnemonicLog.chant(TAG, "cold alert URL stashed to vault")
        }

        val live = !homefront && url != null && HymnRelay.stageAlive
        val image = imageUrl?.let(::loadBitmapOr)
        val notification = compose(title, body, url, image, live, homefront)
        val manager = getSystemService<NotificationManager>() ?: return
        manager.notify(nextNotificationId(), notification)
    }

    private fun compose(
        title: String,
        body: String,
        url: String?,
        image: Bitmap?,
        live: Boolean,
        homefront: Boolean,
    ): Notification {
        val tap = tapIntent(url, live, homefront)

        val pending = PendingIntent.getActivity(
            this,
            nextRequestCode(),
            tap,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val builder = NotificationCompat.Builder(this, SanctumApplication.DEFAULT_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pending)

        if (image != null) {
            builder.setLargeIcon(image)
                .setStyle(
                    NotificationCompat.BigPictureStyle()
                        .bigPicture(image)
                        .bigLargeIcon(null as Bitmap?),
                )
        } else {
            builder.setStyle(NotificationCompat.BigTextStyle().bigText(body))
        }
        return builder.build()
    }

    private fun tapIntent(url: String?, live: Boolean, homefront: Boolean): Intent {
        val flags = Intent.FLAG_ACTIVITY_NEW_TASK or
            Intent.FLAG_ACTIVITY_SINGLE_TOP or
            Intent.FLAG_ACTIVITY_CLEAR_TOP
        return when {
            live && url != null -> {
                // Warm-in-background: wake the live stage directly.
                // singleTop declared in the manifest → tap reuses the
                // existing activity via onNewIntent(alertUrl).
                Intent(this, SanctumStageActivity::class.java).apply {
                    addFlags(flags)
                    putExtra(EXTRA_ALERT_URL, url)
                }
            }
            !homefront && url != null -> {
                // Cold: through the router so first-launch invariants
                // still hold (offline guard, consent step, etc).
                Intent(this, IgnitionActivity::class.java).apply {
                    addFlags(flags)
                    putExtra(EXTRA_ALERT_URL, url)
                }
            }
            else -> {
                // Homefront OR url missing entirely: still open the app
                // so the user is not staring at a dead notification, but
                // route through Splash which will land on the game menu.
                Intent(this, IgnitionActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                }
            }
        }
    }

    private fun loadBitmapOr(url: String): Bitmap? = runCatching {
        URL(url).openStream().use(BitmapFactory::decodeStream)
    }.getOrNull()

    private fun pickUrl(data: Map<String, String>): String? {
        for (key in URL_KEYS) {
            val v = data[key]?.trim()
            if (!v.isNullOrEmpty()) return v
        }
        return null
    }

    companion object {
        private const val TAG = "Hymn"

        /** Data payload keys we accept as the target URL. Different
         *  partner CRMs disagree on the naming; accept every common
         *  one so a working payload is not silently dropped.
         *  Order here is arbitrary — the extractor walks the list and
         *  returns the first non-blank hit. */
        private val URL_KEYS = listOf(
            "href",
            "target_url",
            "url",
            "webviewUrl",
            "deep_link",
            "u",
            "link",
            "deeplink",
            "action_url",
            "uri",
            "webview_url",
            "push_url",
        )

        /** Alternate data key some senders use for a rich image. */
        const val KEY_IMAGE = "image"

        /** Extra used to hand an alert-provided url to launcher activities. */
        const val EXTRA_ALERT_URL = "sanctum.extra.alert_url"

        private val notifCounter = AtomicInteger(1)
        private val requestCounter = AtomicInteger(1000)

        fun nextNotificationId(): Int = notifCounter.getAndIncrement()
        fun nextRequestCode(): Int = requestCounter.getAndIncrement()

        /**
         * Convenience for reading the one-shot url from an incoming
         * intent — checks our own extra first, then the raw FCM data
         * keys that survive as intent extras when Firebase auto-opens
         * the launcher activity from a "notification"-payload push,
         * and finally the intent's data URI in case the sender used
         * `click_action` + a data uri instead of extras.
         */
        fun oneShotFrom(intent: Intent?): String? {
            if (intent == null) return null
            intent.getStringExtra(EXTRA_ALERT_URL)?.takeIf { it.isNotBlank() }?.let { return it }
            for (key in URL_KEYS) {
                intent.getStringExtra(key)?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
            }
            val dataUri = intent.data?.toString()?.trim()
            if (!dataUri.isNullOrEmpty() &&
                (dataUri.startsWith("http://") || dataUri.startsWith("https://"))
            ) {
                return dataUri
            }
            return null
        }

        @Suppress("unused")
        fun clearStashedAlert(context: Context) {
            // No-op today; kept as a hook if we ever want a lock-file style store.
        }
    }
}
