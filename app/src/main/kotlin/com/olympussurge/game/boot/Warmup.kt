package com.olympussurge.game.boot

import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.badlogic.gdx.utils.GdxNativesLoader
import com.olympussurge.core.save.PlayerProfile
import com.olympussurge.core.save.PlayerStore
import com.olympussurge.game.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * Work the app must finish before the menu can be shown, split into steps the
 * splash screen can report one by one.
 *
 * Every step does real work: nothing here exists to pad a progress bar. The
 * heavy items are the native libGDX libraries and the full-screen menu art,
 * both of which would otherwise stall the first frame of the menu.
 */
object Warmup {

    /** Decoded once here, then handed to the menu so it never decodes on-screen. */
    private val art = HashMap<Int, ImageBitmap>()

    /** Profile read during warm-up, so the menu opens with real numbers. */
    @Volatile
    var profile: PlayerProfile? = null
        private set

    fun artOrNull(resId: Int): ImageBitmap? = art[resId]

    /** Named so the splash can say what it is actually waiting on. */
    data class Step(val label: String, val run: suspend (Context) -> Unit)

    val steps: List<Step> = listOf(
        Step("Loading save data") { context ->
            profile = PlayerStore(context).profile.first()
        },
        Step("Starting game engine") {
            // Pulls the gdx .so files into the process. Doing it here means the
            // battle activity starts on an engine that is already resident.
            GdxNativesLoader.load()
        },
        Step("Preparing sprites") { context ->
            // Touching the manifest warms the asset system and fails loudly here
            // rather than half-way into a run.
            context.assets.open("sprites.json").use { it.readBytes() }
        },
        Step("Decoding artwork") { context ->
            decode(context, R.drawable.bg_clouds)
            decode(context, R.drawable.logo_olympus_surge)
        },
    )

    private suspend fun decode(context: Context, resId: Int) = withContext(Dispatchers.IO) {
        if (art.containsKey(resId)) return@withContext
        val bitmap = BitmapFactory.decodeResource(context.resources, resId) ?: return@withContext
        art[resId] = bitmap.asImageBitmap()
    }
}
