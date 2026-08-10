package com.olympussurge.game

import com.badlogic.gdx.ApplicationAdapter
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.OrthographicCamera
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.utils.viewport.FitViewport
import com.olympussurge.engine.assets.SpriteLibrary
import com.olympussurge.engine.input.VirtualJoystick
import com.olympussurge.engine.loop.GameClock
import com.olympussurge.engine.render.Primitives
import com.olympussurge.game.audio.GameSounds
import com.olympussurge.game.config.Levels
import com.olympussurge.game.input.BattleInput
import com.olympussurge.game.render.BattleHud
import com.olympussurge.game.render.BattleRenderer
import com.olympussurge.game.render.LoadingScreen
import com.olympussurge.game.render.PauseMenu
import com.olympussurge.game.render.UpgradePanel
import com.olympussurge.game.world.BattleWorld
import com.olympussurge.game.world.RunModifiers

/**
 * Root of the battle scene: owns the simulation clock, input and rendering.
 * Menus live in Compose; this class only ever runs a run.
 */
class OlympusGame(
    private val levelId: String,
    private val callbacks: Callbacks = Callbacks(),
    private val modifiers: RunModifiers = RunModifiers(),
    private val sfxVolume: Float = 1f,
    private val hapticsEnabled: Boolean = true,
    showDiagnostics: Boolean = false,
) : ApplicationAdapter() {

    /**
     * Hooks back into the Android layer. Kept as plain lambdas so the game
     * module stays free of Android dependencies.
     */
    class Callbacks(
        val onLoadProgress: (Float) -> Unit = {},
        val onRunFinished: (
            victory: Boolean,
            waves: Int,
            kills: Int,
            gems: Int,
            seconds: Int,
            stars: Int,
        ) -> Unit = { _, _, _, _, _, _ -> },
        val onExitRequested: () -> Unit = {},
    )

    /** Design resolution; the viewport letterboxes to any landscape device. */
    private val worldWidth = 1920f
    private val worldHeight = 1080f

    private lateinit var batch: SpriteBatch
    private lateinit var camera: OrthographicCamera
    private lateinit var viewport: FitViewport
    private lateinit var sprites: SpriteLibrary
    private lateinit var font: BitmapFont
    private lateinit var primitives: Primitives
    private lateinit var renderer: BattleRenderer
    private lateinit var hud: BattleHud
    private lateinit var upgradePanel: UpgradePanel
    private lateinit var pauseMenu: PauseMenu
    private lateinit var loadingScreen: LoadingScreen
    private lateinit var world: BattleWorld
    private lateinit var input: BattleInput
    private val sounds = GameSounds()

    private val clock = GameClock()
    private val joystick = VirtualJoystick()
    private val touchPoint = Vector3()

    private var ready = false
    private var resultReported = false

    /** Seconds the finished bar has been on screen; negative until loading ends. */
    private var fullBarHold = -1f

    var showDiagnostics = showDiagnostics

    override fun create() {
        batch = SpriteBatch()
        camera = OrthographicCamera()
        viewport = FitViewport(worldWidth, worldHeight, camera)
        font = BitmapFont().apply { data.setScale(1.9f) }
        primitives = Primitives()
        loadingScreen = LoadingScreen(primitives, font, worldWidth, worldHeight)

        sprites = SpriteLibrary()
        sprites.enqueueAll { category -> category in COMBAT_CATEGORIES }
        sounds.load()
        sounds.volume = sfxVolume

        val toWorld: (Int, Int, FloatArray) -> Unit = { screenX, screenY, out ->
            touchPoint.set(screenX.toFloat(), screenY.toFloat(), 0f)
            viewport.unproject(touchPoint)
            out[0] = touchPoint.x
            out[1] = touchPoint.y
        }
        joystick.unproject = toWorld
        input = BattleInput(joystick, toWorld)
    }

    override fun resize(width: Int, height: Int) {
        viewport.update(width, height, true)
    }

    override fun render() {
        if (!ready) {
            advanceLoading()
            return
        }

        input.overlayVisible = world.phase != BattleWorld.Phase.FIGHTING || clock.paused

        clock.advance(Gdx.graphics.deltaTime) { step ->
            world.update(step, joystick.dirX, joystick.dirY)
        }

        reportResultIfFinished()
        draw()
    }

    override fun pause() {
        clock.paused = true
        joystick.release()
    }

    override fun resume() {
        clock.paused = false
        clock.reset()
    }

    private fun advanceLoading() {
        val progress = sprites.progress()
        // Drive the asset manager; `done` is true only on the frame all GPU
        // uploads are finished so LoadingScreen can snap the bar to 100 %.
        val done = sprites.update()

        Gdx.gl.glClearColor(0.03f, 0.05f, 0.12f, 1f)
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT)
        viewport.apply()
        batch.projectionMatrix = camera.combined
        batch.begin()
        loadingScreen.draw(batch, progress, done)
        batch.end()

        if (!done) {
            callbacks.onLoadProgress(progress)
            return
        }

        if (fullBarHold < 0f) {
            sprites.finish()
            callbacks.onLoadProgress(1f)
            fullBarHold = 0f
        }
        // A full bar the player never sees is a bar that lied about the wait,
        // so the arena waits a blink once everything is genuinely resident.
        fullBarHold += Gdx.graphics.deltaTime
        if (fullBarHold >= FULL_BAR_SECONDS) startRun()
    }

    private fun startRun() {
        loadingScreen.releaseArt()
        world = BattleWorld(Levels.byId(levelId), modifiers)
        renderer = BattleRenderer(sprites, primitives, font, worldWidth, worldHeight)
        hud = BattleHud(sprites, primitives, font, worldWidth, worldHeight)
        upgradePanel = UpgradePanel(primitives, font, worldWidth, worldHeight)
        pauseMenu = PauseMenu(primitives, font, worldWidth, worldHeight)

        input.isPauseTarget = { x, y ->
            val dx = x - hud.pauseX
            val dy = y - hud.pauseY
            dx * dx + dy * dy <= hud.pauseRadius * hud.pauseRadius
        }
        input.onPauseTapped = { clock.paused = !clock.paused }
        input.onOverlayTapped = { x, y -> handleOverlayTap(x, y) }
        world.onEvent = ::playCue

        Gdx.input.inputProcessor = input
        clock.reset()
        resultReported = false
        ready = true
    }

    private fun playCue(event: BattleWorld.Event) {
        when (event) {
            // Bolts fire constantly, so their pitch is jittered to avoid a
            // machine-gun sameness, and they play quieter than milestones.
            BattleWorld.Event.BOLT_FIRED -> sounds.play(GameSounds.Cue.BOLT, pitchVariation = 0.12f)
            BattleWorld.Event.GEM_PICKED -> sounds.play(GameSounds.Cue.GEM_PICKUP, 0.08f)
            BattleWorld.Event.SYNERGY -> sounds.play(GameSounds.Cue.SYNERGY)
            BattleWorld.Event.WAVE_DONE -> sounds.play(GameSounds.Cue.WAVE_CLEARED)
            BattleWorld.Event.ELITE_SPAWNED, BattleWorld.Event.BOSS_SPAWNED ->
                sounds.play(GameSounds.Cue.ELITE)
            BattleWorld.Event.ELITE_KILLED -> sounds.play(GameSounds.Cue.ELITE)
            BattleWorld.Event.WON -> sounds.play(GameSounds.Cue.VICTORY)
            BattleWorld.Event.LOST -> sounds.play(GameSounds.Cue.DEFEAT)
            // Hits, kills and hurts are carried by particles and shake; adding
            // a sample per frame would only muddy the mix.
            BattleWorld.Event.ENEMY_HIT,
            BattleWorld.Event.ENEMY_KILLED -> Unit
            BattleWorld.Event.HERO_HURT -> Unit
        }
        vibrateFor(event)
    }

    /**
     * Only the moments a thumb would actually feel are wired to the
     * vibrator: every bolt or hit would just buzz constantly and numb the
     * player to it. Durations are short so the phone never feels laggy.
     */
    private fun vibrateFor(event: BattleWorld.Event) {
        if (!hapticsEnabled) return
        val millis = when (event) {
            BattleWorld.Event.HERO_HURT -> 40
            BattleWorld.Event.ELITE_KILLED -> 60
            BattleWorld.Event.WON -> 90
            BattleWorld.Event.LOST -> 90
            BattleWorld.Event.SYNERGY -> 50
            else -> 0
        }
        if (millis > 0) Gdx.input.vibrate(millis)
    }

    private fun handleOverlayTap(x: Float, y: Float) {
        if (world.phase == BattleWorld.Phase.VICTORY || world.phase == BattleWorld.Phase.DEFEAT) {
            callbacks.onExitRequested()
            return
        }

        if (isPaused()) {
            when (pauseMenu.hitTest(x, y)) {
                PauseMenu.Action.RESUME -> {
                    clock.paused = false
                    joystick.release()
                }

                PauseMenu.Action.EXIT -> callbacks.onExitRequested()
                null -> Unit
            }
            return
        }

        if (world.phase != BattleWorld.Phase.WAVE_CLEARED) return
        val choices = world.upgradeChoices
        val index = upgradePanel.hitTest(x, y, choices.size)
        if (index >= 0) {
            world.chooseUpgrade(choices[index])
            sounds.play(GameSounds.Cue.BLESSING)
            joystick.release()
        }
    }

    private fun isPaused(): Boolean =
        ready && clock.paused && world.phase == BattleWorld.Phase.FIGHTING

    /**
     * Android back button. A live run pauses first so the gesture can never
     * throw away progress by accident; pressing it again from the pause menu
     * leaves for the menu.
     *
     * Must be posted onto the render thread by the caller: it touches the clock.
     */
    fun requestBack() {
        if (isPaused() || !ready || world.phase != BattleWorld.Phase.FIGHTING) {
            callbacks.onExitRequested()
            return
        }
        clock.paused = true
        joystick.release()
    }

    private fun reportResultIfFinished() {
        if (resultReported) return
        val finished = world.phase == BattleWorld.Phase.VICTORY ||
            world.phase == BattleWorld.Phase.DEFEAT
        if (!finished) return

        resultReported = true
        callbacks.onRunFinished(
            world.phase == BattleWorld.Phase.VICTORY,
            world.waveIndex + 1 + world.loops * world.waveCount,
            world.kills,
            world.gems.total(),
            world.elapsed.toInt(),
            world.starsEarned(),
        )
    }

    private fun draw() {
        Gdx.gl.glClearColor(0.03f, 0.05f, 0.12f, 1f)
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT)

        viewport.apply()
        batch.projectionMatrix = camera.combined
        batch.begin()

        renderer.draw(batch, world, joystick)
        hud.draw(batch, world)
        drawPhaseOverlay()

        if (showDiagnostics) drawDiagnostics()

        batch.end()
    }

    private fun drawPhaseOverlay() {
        if (world.phase == BattleWorld.Phase.WAVE_CLEARED) {
            upgradePanel.draw(batch, world.upgradeChoices)
            return
        }
        if (isPaused()) {
            pauseMenu.draw(
                batch,
                arenaName = world.level.name,
                wave = world.waveIndex + 1,
                waveCount = world.waveCount,
                kills = world.kills,
                gems = world.gems.total(),
                seconds = world.elapsed.toInt(),
            )
            return
        }

        val message = when (world.phase) {
            BattleWorld.Phase.VICTORY -> "${world.level.name.uppercase()} CLEARED"
            BattleWorld.Phase.DEFEAT -> "THE HERO HAS FALLEN"
            else -> return
        }
        val stars = world.starsEarned()
        val medal = if (world.phase == BattleWorld.Phase.VICTORY) {
            "*".repeat(stars) + ".".repeat(3 - stars) + "   "
        } else {
            ""
        }
        drawBanner(
            message,
            "${medal}Kills ${world.kills}   Gems ${world.gems.total()}   -   tap to continue",
        )
    }

    private fun drawBanner(title: String, subtitle: String) {
        batch.setColor(0.02f, 0.03f, 0.09f, 0.62f)
        batch.draw(primitives.pixel, 0f, worldHeight * 0.38f, worldWidth, 230f)
        batch.color = Color.WHITE

        font.color = Color.valueOf("FFD98CFF")
        font.draw(batch, title, worldWidth / 2f - 210f, worldHeight * 0.55f)
        font.color = Color.valueOf("BCCBE8FF")
        font.draw(batch, subtitle, worldWidth / 2f - 190f, worldHeight * 0.47f)
        font.color = Color.WHITE
    }

    private fun drawDiagnostics() {
        font.color = Color.valueOf("7FE0FFFF")
        font.draw(
            batch,
            "FPS ${Gdx.graphics.framesPerSecond}  kills ${world.kills}  " +
                "gems ${world.gems.total()}",
            worldWidth - 520f,
            worldHeight - 150f,
        )
        font.color = Color.WHITE
    }

    override fun dispose() {
        batch.dispose()
        font.dispose()
        primitives.dispose()
        sprites.dispose()
        sounds.dispose()
        loadingScreen.dispose()
    }

    private companion object {
        /** Long enough to read "READY" at 100 %, short enough not to be a wait. */
        const val FULL_BAR_SECONDS = 0.4f

        val COMBAT_CATEGORIES = setOf(
            "arena", "backgrounds", "heroes", "enemies", "elites", "gems", "vfx",
        )
    }
}
