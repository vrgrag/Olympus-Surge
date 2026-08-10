package com.olympussurge.game

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.FrameLayout
import com.badlogic.gdx.backends.android.AndroidApplication
import com.badlogic.gdx.backends.android.AndroidApplicationConfiguration
import com.olympussurge.core.save.PlayerProfile
import com.olympussurge.game.world.RunModifiers

/**
 * Hosts the libGDX render surface for a single run.
 *
 * Battle lives in its own activity so the GL surface has a clean lifecycle of
 * its own; menus stay in Compose in [MainActivity]. Temple bonuses travel in
 * with the intent and the outcome travels back as an activity result, which
 * keeps the game module free of Android storage APIs.
 */
class BattleActivity : AndroidApplication() {

    private var victory = false
    private var waves = 0
    private var kills = 0
    private var gems = 0
    private var duration = 0
    private var stars = 0
    private var finished = false
    private var game: OlympusGame? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val config = AndroidApplicationConfiguration().apply {
            useAccelerometer = false
            useCompass = false
            useGyroscope = false
            useImmersiveMode = true
            depth = 0
            stencil = 0
            numSamples = 0
        }

        val callbacks = OlympusGame.Callbacks(
            onRunFinished = { won, wavesReached, killCount, gemCount, seconds, starCount ->
                victory = won
                waves = wavesReached
                kills = killCount
                gems = gemCount
                duration = seconds
                stars = starCount
                finished = true
            },
            onExitRequested = { runOnUiThread { finishWithResult() } },
        )

        val game = OlympusGame(
            levelId = intent.getStringExtra(EXTRA_LEVEL_ID).orEmpty(),
            callbacks = callbacks,
            modifiers = modifiersFromIntent(),
            sfxVolume = intent.getFloatExtra(EXTRA_SFX_VOLUME, 1f),
            hapticsEnabled = intent.getBooleanExtra(EXTRA_HAPTICS, true),
            showDiagnostics = intent.getBooleanExtra(EXTRA_DIAGNOSTICS, false),
        )
        this.game = game

        setContentView(FrameLayout(this).apply { addView(initializeForView(game, config)) })
    }

    private fun modifiersFromIntent(): RunModifiers = RunModifiers(
        bonusMaxHealth = intent.getFloatExtra(EXTRA_BONUS_HEALTH, 0f),
        damageMultiplier = intent.getFloatExtra(EXTRA_DAMAGE, 1f),
        moveSpeedMultiplier = intent.getFloatExtra(EXTRA_MOVE_SPEED, 1f),
        attackSpeedMultiplier = intent.getFloatExtra(EXTRA_ATTACK_SPEED, 1f),
        gemChanceMultiplier = intent.getFloatExtra(EXTRA_GEM_CHANCE, 1f),
        pickupMultiplier = intent.getFloatExtra(EXTRA_PICKUP, 1f),
        damageReduction = intent.getFloatExtra(EXTRA_REDUCTION, 0f),
        startingGems = intent.getIntExtra(EXTRA_STARTING_GEMS, 0),
    )

    /**
     * Back pauses a live run instead of abandoning it; the pause menu is where
     * leaving is an explicit choice. Handing the decision to the game means it
     * happens on the render thread that owns the clock.
     */
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        val game = this.game ?: return finishWithResult()
        postRunnable { game.requestBack() }
    }

    private fun finishWithResult() {
        val data = Intent().apply {
            putExtra(EXTRA_FINISHED, finished)
            putExtra(EXTRA_VICTORY, victory)
            putExtra(EXTRA_WAVES, waves)
            putExtra(EXTRA_KILLS, kills)
            putExtra(EXTRA_GEMS, gems)
            putExtra(EXTRA_DURATION, duration)
            putExtra(EXTRA_STARS, stars)
            putExtra(EXTRA_LEVEL_ID, intent.getStringExtra(EXTRA_LEVEL_ID).orEmpty())
        }
        setResult(Activity.RESULT_OK, data)
        finish()
    }

    companion object {
        const val EXTRA_FINISHED = "finished"
        const val EXTRA_VICTORY = "victory"
        const val EXTRA_WAVES = "waves"
        const val EXTRA_KILLS = "kills"
        const val EXTRA_GEMS = "gems"
        const val EXTRA_DURATION = "duration"
        const val EXTRA_STARS = "stars"
        const val EXTRA_LEVEL_ID = "level_id"

        private const val EXTRA_BONUS_HEALTH = "bonus_health"
        private const val EXTRA_DAMAGE = "damage"
        private const val EXTRA_MOVE_SPEED = "move_speed"
        private const val EXTRA_ATTACK_SPEED = "attack_speed"
        private const val EXTRA_GEM_CHANCE = "gem_chance"
        private const val EXTRA_PICKUP = "pickup"
        private const val EXTRA_REDUCTION = "reduction"
        private const val EXTRA_STARTING_GEMS = "starting_gems"
        private const val EXTRA_SFX_VOLUME = "sfx_volume"
        private const val EXTRA_HAPTICS = "haptics"
        private const val EXTRA_DIAGNOSTICS = "diagnostics"

        /** Builds the launch intent carrying the player's permanent bonuses. */
        fun intent(context: Context, profile: PlayerProfile, levelId: String): Intent {
            val bonuses = profile.bonuses
            return Intent(context, BattleActivity::class.java).apply {
                putExtra(EXTRA_LEVEL_ID, levelId)
                putExtra(EXTRA_BONUS_HEALTH, bonuses.bonusMaxHealth)
                putExtra(EXTRA_DAMAGE, bonuses.damageMultiplier)
                putExtra(EXTRA_MOVE_SPEED, bonuses.moveSpeedMultiplier)
                putExtra(EXTRA_ATTACK_SPEED, bonuses.attackSpeedMultiplier)
                putExtra(EXTRA_GEM_CHANCE, bonuses.gemChanceMultiplier)
                putExtra(EXTRA_PICKUP, bonuses.pickupMultiplier)
                putExtra(EXTRA_REDUCTION, bonuses.damageReduction)
                putExtra(EXTRA_STARTING_GEMS, bonuses.startingGems)
                putExtra(EXTRA_SFX_VOLUME, profile.sfxVolume)
                putExtra(EXTRA_HAPTICS, profile.hapticsEnabled)
                putExtra(EXTRA_DIAGNOSTICS, profile.showDiagnostics)
            }
        }
    }
}
