package com.olympussurge.game.ui

import android.graphics.BitmapFactory
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import com.olympussurge.core.save.PlayerProfile
import com.olympussurge.game.R
import java.io.File

/** Portraits shipped with the game; picking one needs no permissions. */
enum class AvatarPreset(val key: String, val displayName: String, val drawable: Int) {
    WARRIOR("avatar_warrior", "Chosen Warrior", R.drawable.avatar_warrior),
    ZEUS("avatar_zeus", "Zeus", R.drawable.avatar_zeus),
    ATHENA("avatar_athena", "Athena", R.drawable.avatar_athena),
    ARES("avatar_ares", "Ares", R.drawable.avatar_ares),
    HERMES("avatar_hermes", "Hermes", R.drawable.avatar_hermes),
    ;

    companion object {
        fun byKey(key: String): AvatarPreset =
            entries.firstOrNull { it.key == key } ?: WARRIOR
    }
}

/**
 * Resolves a profile to a painter, preferring a photo the player imported.
 *
 * The photo is decoded from the app's own storage rather than held as a
 * content URI, so it keeps working after a reboot without asking for
 * persistable permissions.
 */
object AvatarPainter {

    @Composable
    fun painterFor(profile: PlayerProfile): Painter {
        val path = profile.avatarPhotoPath
        if (path != null) {
            val painter = remember(path) { decode(path) }
            if (painter != null) return painter
        }
        return painterResource(AvatarPreset.byKey(profile.avatar).drawable)
    }

    private fun decode(path: String): BitmapPainter? {
        val file = File(path)
        if (!file.exists()) return null
        // Photos are downscaled on import, so a straight decode is cheap.
        val bitmap = BitmapFactory.decodeFile(path) ?: return null
        return BitmapPainter(bitmap.asImageBitmap())
    }
}