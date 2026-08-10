package com.olympussurge.game.audio

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.audio.Sound
import com.badlogic.gdx.utils.Disposable

/**
 * Combat sound effects, played through libGDX's Sound (SoundPool on Android).
 *
 * Clips are short and loaded once at startup, so triggering one during a fight
 * never touches the disk. Volume is a single knob the settings screen owns.
 */
class GameSounds : Disposable {

    enum class Cue(val file: String) {
        BOLT("sfx/Zeus_Lightning_Strike_asset.mp3"),
        GEM_PICKUP("sfx/Gemstone_Collect_asset.mp3"),
        SYNERGY("sfx/Energy_Chain_Reaction_asset.mp3"),
        BLESSING("sfx/God_Blessing_Select_asset.mp3"),
        WAVE_CLEARED("sfx/Olympus_Altar_Activate_asset.mp3"),
        ELITE("sfx/Olympus_Thunder_asset.mp3"),
        VICTORY("sfx/Level_Victory_asset.mp3"),
        DEFEAT("sfx/Level_Defeat_asset.mp3"),
    }

    private val sounds = HashMap<Cue, Sound>()

    var volume = 1f

    /** Loads every cue; called once the render thread has an audio device. */
    fun load() {
        for (cue in Cue.entries) {
            val handle = Gdx.files.internal(cue.file)
            if (handle.exists()) sounds[cue] = Gdx.audio.newSound(handle)
        }
    }

    fun play(cue: Cue, pitchVariation: Float = 0f) {
        if (volume <= 0.01f) return
        val sound = sounds[cue] ?: return
        val pitch = if (pitchVariation <= 0f) {
            1f
        } else {
            1f + (Math.random().toFloat() * 2f - 1f) * pitchVariation
        }
        sound.play(volume, pitch, 0f)
    }

    override fun dispose() {
        sounds.values.forEach(Sound::dispose)
        sounds.clear()
    }
}
