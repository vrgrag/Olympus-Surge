package com.olympussurge.game.oracle.ui

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.max
import kotlin.math.min

/**
 * Where the painted card sits inside a background artwork, and where it
 * ends up on screen once [androidx.compose.ui.layout.ContentScale.Crop]
 * has had its way with the bitmap.
 *
 * The offline / invite screens are "artwork + buttons": the artwork
 * paints a card, the buttons must land under *that card*. Positioning
 * them at a fixed percentage of screen height only works on a device
 * whose aspect ratio happens to match the source art. On anything else
 * Crop scales to fill the short axis and shaves the long one, the card
 * slides, and the buttons drift away from it — read as skew, which is
 * exactly what pitfalls §12 warns about.
 *
 * So the placement is derived instead of guessed: replay the same
 * scale-and-centre maths Crop uses, and hand back the card edge in
 * layout coordinates.
 *
 * Bounds are fractions of the source bitmap, measured off the artwork
 * itself. Re-measure whenever the art is replaced — see
 * `oracle_pitfalls.md` §12.
 */
internal data class OracleArtCard(
    val artWidth: Int,
    val artHeight: Int,
    val bottom: Float,
) {
    /**
     * @param frameWidth  container width, as reported by BoxWithConstraints
     * @param frameHeight container height
     * @return the card's bottom edge and the artwork's bottom edge, both
     *   clamped into the container. The gap between them is the band the
     *   buttons may occupy.
     */
    fun projectInto(frameWidth: Dp, frameHeight: Dp): OracleArtBand {
        val w = frameWidth.value
        val h = frameHeight.value
        // Crop fills the container, so the scale is driven by whichever
        // axis needs the most magnification; the surplus on the other
        // axis is split evenly around the centre.
        val factor = max(w / artWidth, h / artHeight)
        val painted = artHeight * factor
        val top = (h - painted) / 2f
        return OracleArtBand(
            cardBottom = (top + bottom * painted).coerceIn(0f, h).dp,
            artBottom = min(h, top + painted).dp,
            frameHeight = frameHeight,
        )
    }
}

internal data class OracleArtBand(
    val cardBottom: Dp,
    val artBottom: Dp,
    val frameHeight: Dp,
) {
    /**
     * Top offset that centres a [blockHeight]-tall stack of buttons in
     * the space under the card.
     *
     * [gap] keeps the stack from touching the card's border, and the
     * result is clamped so a heavily cropped artwork can never push the
     * buttons past the bottom of the screen — off-screen buttons are a
     * dead end for the user, a crowded card is merely ugly.
     */
    fun offsetFor(blockHeight: Dp, gap: Dp = 14.dp, safety: Dp = 12.dp): Dp {
        val bandTop = cardBottom + gap
        val bandBottom = min(artBottom.value, frameHeight.value) - safety.value
        val slack = bandBottom - bandTop.value - blockHeight.value
        val offset = bandTop.value + max(0f, slack) / 2f
        val ceiling = frameHeight.value - blockHeight.value - safety.value
        return max(0f, min(offset, ceiling)).dp
    }
}

/**
 * Card bounds measured off the shipped artwork. Only the bottom edge
 * matters for layout — the cards are horizontally centred in all four
 * bitmaps, and Crop keeps a centred bitmap centred, so the buttons can
 * be centred against the container instead of the card.
 */
internal object OracleArtMetrics {
    val InvitePortrait = OracleArtCard(artWidth = 460, artHeight = 1024, bottom = 0.6074f)
    val InviteLandscape = OracleArtCard(artWidth = 1024, artHeight = 460, bottom = 0.6891f)
    val OfflinePortrait = OracleArtCard(artWidth = 460, artHeight = 1024, bottom = 0.6104f)
    val OfflineLandscape = OracleArtCard(artWidth = 1024, artHeight = 460, bottom = 0.6870f)
}
