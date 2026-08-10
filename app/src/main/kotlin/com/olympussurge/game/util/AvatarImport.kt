package com.olympussurge.game.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

private const val AVATAR_SIZE = 384
private const val AVATAR_FILE = "avatar.jpg"

/**
 * Copies a picked photo into app storage, square-cropped and downscaled.
 *
 * Storing our own copy means the avatar keeps working after a reboot or after
 * the source image is deleted, without holding a persistable URI permission.
 * Returns the absolute path, or null if the image could not be read.
 */
suspend fun importAvatarPhoto(context: Context, uri: Uri): String? = withContext(Dispatchers.IO) {
    val source = context.contentResolver.openInputStream(uri)?.use { stream ->
        BitmapFactory.decodeStream(stream)
    } ?: return@withContext null

    val cropped = centerSquare(source)
    val scaled = Bitmap.createScaledBitmap(cropped, AVATAR_SIZE, AVATAR_SIZE, true)

    val file = File(context.filesDir, AVATAR_FILE)
    file.outputStream().use { output ->
        scaled.compress(Bitmap.CompressFormat.JPEG, 90, output)
    }

    if (cropped !== source) cropped.recycle()
    if (scaled !== cropped) scaled.recycle()
    source.recycle()

    file.absolutePath
}

private fun centerSquare(bitmap: Bitmap): Bitmap {
    val side = minOf(bitmap.width, bitmap.height)
    if (side == bitmap.width && side == bitmap.height) return bitmap
    val x = (bitmap.width - side) / 2
    val y = (bitmap.height - side) / 2
    return Bitmap.createBitmap(bitmap, x, y, side, side)
}
