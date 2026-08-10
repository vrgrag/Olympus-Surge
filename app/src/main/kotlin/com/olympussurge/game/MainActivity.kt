package com.olympussurge.game

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.olympussurge.core.meta.RunRecord
import com.olympussurge.core.save.PlayerProfile
import com.olympussurge.core.save.PlayerStore
import com.olympussurge.game.boot.Warmup
import com.olympussurge.game.config.Levels
import com.olympussurge.game.ui.AvatarPreset
import com.olympussurge.game.ui.CampaignScreen
import com.olympussurge.game.ui.LeaderboardScreen
import com.olympussurge.game.ui.MenuScreen
import com.olympussurge.game.ui.OlympusTheme
import com.olympussurge.game.ui.PrivacyScreen
import com.olympussurge.game.ui.ProfileScreen
import com.olympussurge.game.ui.RunResultScreen
import com.olympussurge.game.ui.SettingsScreen
import com.olympussurge.game.ui.SupportScreen
import com.olympussurge.game.ui.TempleScreen
import com.olympussurge.game.util.importAvatarPhoto
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        goFullscreen()
        setContent {
            OlympusTheme {
                OlympusApp()
            }
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) goFullscreen()
    }

    /** Hides the status and navigation bars; they return on a swipe. */
    private fun goFullscreen() {
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.systemBars())
    }
}

private enum class Screen {
    MENU, CAMPAIGN, RESULT, PROFILE, TEMPLE, LEADERBOARD, SETTINGS, PRIVACY, SUPPORT
}

@Composable
private fun OlympusApp() {
    val context = LocalContext.current
    val store = remember { PlayerStore(context) }
    val scope = rememberCoroutineScope()

    // The splash already read the save, so the menu never flashes empty stats.
    val profile by store.profile.collectAsState(initial = Warmup.profile ?: PlayerProfile())
    var screen by remember { mutableStateOf(Screen.MENU) }
    var lastRecord by remember { mutableStateOf<RunRecord?>(null) }
    var lastRank by remember { mutableStateOf<Int?>(null) }
    var lastStars by remember { mutableStateOf(0) }

    val battleLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { activityResult ->
        val data = activityResult.data ?: return@rememberLauncherForActivityResult
        if (!data.getBooleanExtra(BattleActivity.EXTRA_FINISHED, false)) {
            return@rememberLauncherForActivityResult
        }

        scope.launch {
            lastStars = data.getIntExtra(BattleActivity.EXTRA_STARS, 0)
            val record = store.recordRun(
                victory = data.getBooleanExtra(BattleActivity.EXTRA_VICTORY, false),
                waves = data.getIntExtra(BattleActivity.EXTRA_WAVES, 0),
                kills = data.getIntExtra(BattleActivity.EXTRA_KILLS, 0),
                gems = data.getIntExtra(BattleActivity.EXTRA_GEMS, 0),
                durationSeconds = data.getIntExtra(BattleActivity.EXTRA_DURATION, 0),
                levelId = data.getStringExtra(BattleActivity.EXTRA_LEVEL_ID).orEmpty(),
                stars = lastStars,
            )
            lastRecord = record
            // Read back after the write so the rank reflects the updated board.
            lastRank = store.profile.first().leaderboard.rankOf(record)
            screen = Screen.RESULT
        }
    }

    val photoLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val path = importAvatarPhoto(context, uri)
            if (path != null) store.setAvatarPhoto(path)
        }
    }

    var lastLevelId by remember { mutableStateOf(Levels.all.first().id) }
    val startRun = { levelId: String ->
        lastLevelId = levelId
        battleLauncher.launch(BattleActivity.intent(context, profile, levelId))
    }
    // "Continue" jumps to the deepest arena the player has unlocked.
    val nextLevel = {
        Levels.all.lastOrNull { Levels.isUnlocked(it, profile.levelStars.keys) }
            ?: Levels.all.first()
    }

    // Without this the system back gesture would close the app from every
    // sub-screen instead of stepping back. The legal pages are reached from
    // settings, so back there returns to settings rather than the menu.
    BackHandler(enabled = screen != Screen.MENU) {
        screen = when (screen) {
            Screen.PRIVACY, Screen.SUPPORT -> Screen.SETTINGS
            else -> Screen.MENU
        }
    }

    when (screen) {
        Screen.MENU -> MenuScreen(
            profile = profile,
            nextLevelName = nextLevel().name,
            onStartRun = { startRun(nextLevel().id) },
            onOpenCampaign = { screen = Screen.CAMPAIGN },
            onOpenTemple = { screen = Screen.TEMPLE },
            onOpenLeaderboard = { screen = Screen.LEADERBOARD },
            onOpenProfile = { screen = Screen.PROFILE },
            onOpenSettings = { screen = Screen.SETTINGS },
        )

        Screen.CAMPAIGN -> CampaignScreen(
            profile = profile,
            onBack = { screen = Screen.MENU },
            onPlay = { level -> startRun(level.id) },
        )

        Screen.RESULT -> RunResultScreen(
            record = lastRecord ?: RunRecord(0L, false, 0, 0, 0, 0),
            rank = lastRank,
            stars = lastStars,
            levelName = Levels.byId(lastLevelId).name,
            onRetry = {
                screen = Screen.MENU
                startRun(lastLevelId)
            },
            onHome = { screen = Screen.MENU },
            onOpenCampaign = { screen = Screen.CAMPAIGN },
            onOpenLeaderboard = { screen = Screen.LEADERBOARD },
        )

        Screen.PROFILE -> ProfileScreen(
            profile = profile,
            onBack = { screen = Screen.MENU },
            onRename = { name -> scope.launch { store.setDisplayName(name) } },
            onPickPreset = { preset: AvatarPreset ->
                scope.launch { store.setAvatar(preset.key) }
            },
            onPickPhoto = {
                photoLauncher.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                )
            },
            onClearPhoto = { scope.launch { store.setAvatarPhoto(null) } },
        )

        Screen.TEMPLE -> TempleScreen(
            profile = profile,
            onBack = { screen = Screen.MENU },
            onPurchase = { upgrade -> scope.launch { store.purchase(upgrade) } },
        )

        Screen.LEADERBOARD -> LeaderboardScreen(
            profile = profile,
            highlightTimestamp = lastRecord?.timestamp,
            onBack = { screen = Screen.MENU },
        )

        Screen.SETTINGS -> SettingsScreen(
            profile = profile,
            versionName = BuildConfig.VERSION_NAME,
            onBack = { screen = Screen.MENU },
            onSfxChange = { sfx -> scope.launch { store.setAudio(sfx) } },
            onHapticsChange = { enabled -> scope.launch { store.setHaptics(enabled) } },
            onDiagnosticsChange = { enabled -> scope.launch { store.setDiagnostics(enabled) } },
            onResetProgress = { scope.launch { store.resetProgress() } },
            onOpenPrivacy = { screen = Screen.PRIVACY },
            onOpenSupport = { screen = Screen.SUPPORT },
        )

        Screen.PRIVACY -> PrivacyScreen(onBack = { screen = Screen.SETTINGS })

        Screen.SUPPORT -> SupportScreen(
            versionName = BuildConfig.VERSION_NAME,
            onBack = { screen = Screen.SETTINGS },
        )
    }
}
