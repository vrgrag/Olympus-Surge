package com.olympussurge.core.save

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.olympussurge.core.meta.Leaderboard
import com.olympussurge.core.meta.MetaBonuses
import com.olympussurge.core.meta.RunRecord
import com.olympussurge.core.meta.TempleUpgrade
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json

/** Everything that survives between runs. All of it is earned in combat. */
data class PlayerProfile(
    val displayName: String = "Chosen One",
    val avatar: String = "avatar_warrior",
    val avatarPhotoPath: String? = null,
    val essence: Int = 0,
    val runs: Int = 0,
    val victories: Int = 0,
    val bestWave: Int = 0,
    val totalKills: Int = 0,
    val totalGems: Int = 0,
    val templeLevels: Map<TempleUpgrade, Int> = emptyMap(),
    val leaderboard: Leaderboard = Leaderboard(),
    /** Best star count per level id; a key present means the level was cleared. */
    val levelStars: Map<String, Int> = emptyMap(),
    val musicVolume: Float = 0.6f,
    val sfxVolume: Float = 1f,
    val hapticsEnabled: Boolean = true,
    val showDiagnostics: Boolean = false,
) {
    val bonuses: MetaBonuses get() = MetaBonuses.from(templeLevels)

    fun levelOf(upgrade: TempleUpgrade): Int = templeLevels[upgrade] ?: 0

    fun starsOf(levelId: String): Int = levelStars[levelId] ?: 0

    fun isCleared(levelId: String): Boolean = levelStars.containsKey(levelId)

    val totalStars: Int get() = levelStars.values.sum()

    fun canAfford(upgrade: TempleUpgrade): Boolean {
        val level = levelOf(upgrade)
        return level < upgrade.maxLevel && essence >= upgrade.costAt(level)
    }
}

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore("olympus_profile")

/**
 * Local, offline persistence for meta progression.
 *
 * Preferences DataStore is enough here: the save is a flat handful of counters
 * plus one small JSON blob for the leaderboard, written a few times per
 * session, so a relational store would add build complexity without buying
 * anything.
 */
class PlayerStore(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true }

    val profile: Flow<PlayerProfile> = context.dataStore.data.map { prefs ->
        PlayerProfile(
            displayName = prefs[KEY_NAME] ?: "Chosen One",
            avatar = prefs[KEY_AVATAR] ?: "avatar_warrior",
            avatarPhotoPath = prefs[KEY_AVATAR_PHOTO],
            essence = prefs[KEY_ESSENCE] ?: 0,
            runs = prefs[KEY_RUNS] ?: 0,
            victories = prefs[KEY_VICTORIES] ?: 0,
            bestWave = prefs[KEY_BEST_WAVE] ?: 0,
            totalKills = prefs[KEY_KILLS] ?: 0,
            totalGems = prefs[KEY_GEMS] ?: 0,
            templeLevels = TempleUpgrade.entries.associateWith { upgrade ->
                prefs[templeKey(upgrade)] ?: 0
            }.filterValues { it > 0 },
            leaderboard = prefs[KEY_BOARD]?.let { stored ->
                runCatching { json.decodeFromString<Leaderboard>(stored) }.getOrNull()
            } ?: Leaderboard(),
            levelStars = prefs[KEY_LEVEL_STARS]?.let { stored ->
                runCatching { json.decodeFromString<Map<String, Int>>(stored) }.getOrNull()
            } ?: emptyMap(),
            musicVolume = prefs[KEY_MUSIC] ?: 0.6f,
            sfxVolume = prefs[KEY_SFX] ?: 1f,
            hapticsEnabled = prefs[KEY_HAPTICS] ?: true,
            showDiagnostics = prefs[KEY_DIAGNOSTICS] ?: false,
        )
    }

    /**
     * Folds one finished run into the profile and the board.
     *
     * Returns the record that was stored so the results screen can show the
     * rank it earned.
     */
    suspend fun recordRun(
        victory: Boolean,
        waves: Int,
        kills: Int,
        gems: Int,
        durationSeconds: Int,
        levelId: String = "",
        stars: Int = 0,
    ): RunRecord {
        val essenceEarned = kills * 3 + gems * 10 + if (victory) 250 else 0
        val record = RunRecord(
            timestamp = System.currentTimeMillis(),
            victory = victory,
            waves = waves,
            kills = kills,
            gems = gems,
            essence = essenceEarned,
            durationSeconds = durationSeconds,
            levelId = levelId,
        )

        context.dataStore.edit { prefs ->
            prefs[KEY_RUNS] = (prefs[KEY_RUNS] ?: 0) + 1
            if (victory) prefs[KEY_VICTORIES] = (prefs[KEY_VICTORIES] ?: 0) + 1
            prefs[KEY_BEST_WAVE] = maxOf(prefs[KEY_BEST_WAVE] ?: 0, waves)
            prefs[KEY_KILLS] = (prefs[KEY_KILLS] ?: 0) + kills
            prefs[KEY_GEMS] = (prefs[KEY_GEMS] ?: 0) + gems
            prefs[KEY_ESSENCE] = (prefs[KEY_ESSENCE] ?: 0) + essenceEarned

            val board = prefs[KEY_BOARD]
                ?.let { runCatching { json.decodeFromString<Leaderboard>(it) }.getOrNull() }
                ?: Leaderboard()
            prefs[KEY_BOARD] = json.encodeToString(board.withRun(record))

            // A level keeps the best star count it ever earned, so replaying
            // for the optional goals can only improve the campaign map.
            if (victory && levelId.isNotEmpty()) {
                val stored = prefs[KEY_LEVEL_STARS]
                    ?.let { runCatching { json.decodeFromString<Map<String, Int>>(it) }.getOrNull() }
                    ?: emptyMap()
                val best = maxOf(stored[levelId] ?: 0, stars.coerceAtLeast(1))
                prefs[KEY_LEVEL_STARS] = json.encodeToString(stored + (levelId to best))
            }
        }
        return record
    }

    /** Buys one level of [upgrade]; a no-op when it is maxed or unaffordable. */
    suspend fun purchase(upgrade: TempleUpgrade): Boolean {
        var bought = false
        context.dataStore.edit { prefs ->
            val level = prefs[templeKey(upgrade)] ?: 0
            if (level >= upgrade.maxLevel) return@edit
            val cost = upgrade.costAt(level)
            val essence = prefs[KEY_ESSENCE] ?: 0
            if (essence < cost) return@edit

            prefs[KEY_ESSENCE] = essence - cost
            prefs[templeKey(upgrade)] = level + 1
            bought = true
        }
        return bought
    }

    suspend fun setAvatar(name: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_AVATAR] = name
            prefs.remove(KEY_AVATAR_PHOTO)
        }
    }

    suspend fun setAvatarPhoto(path: String?) {
        context.dataStore.edit { prefs ->
            if (path == null) prefs.remove(KEY_AVATAR_PHOTO) else prefs[KEY_AVATAR_PHOTO] = path
        }
    }

    suspend fun setDisplayName(name: String) {
        val trimmed = name.trim().take(20).ifEmpty { "Chosen One" }
        context.dataStore.edit { prefs -> prefs[KEY_NAME] = trimmed }
    }

    /**
     * There is no music track shipped with the game, so only the effects
     * volume is settable; [musicVolume] stays at its default and is kept
     * around only so a future music track would not need a save migration.
     */
    suspend fun setAudio(sfx: Float) {
        context.dataStore.edit { prefs ->
            prefs[KEY_SFX] = sfx.coerceIn(0f, 1f)
        }
    }

    suspend fun setHaptics(enabled: Boolean) {
        context.dataStore.edit { prefs -> prefs[KEY_HAPTICS] = enabled }
    }

    suspend fun setDiagnostics(enabled: Boolean) {
        context.dataStore.edit { prefs -> prefs[KEY_DIAGNOSTICS] = enabled }
    }

    suspend fun resetProgress() {
        context.dataStore.edit { it.clear() }
    }

    private fun templeKey(upgrade: TempleUpgrade) =
        intPreferencesKey("temple_${upgrade.name.lowercase()}")

    private companion object {
        val KEY_NAME = stringPreferencesKey("name")
        val KEY_AVATAR = stringPreferencesKey("avatar")
        val KEY_AVATAR_PHOTO = stringPreferencesKey("avatar_photo")
        val KEY_ESSENCE = intPreferencesKey("essence")
        val KEY_RUNS = intPreferencesKey("runs")
        val KEY_VICTORIES = intPreferencesKey("victories")
        val KEY_BEST_WAVE = intPreferencesKey("best_wave")
        val KEY_KILLS = intPreferencesKey("kills")
        val KEY_GEMS = intPreferencesKey("gems")
        val KEY_BOARD = stringPreferencesKey("leaderboard")
        val KEY_LEVEL_STARS = stringPreferencesKey("level_stars")
        val KEY_MUSIC = floatPreferencesKey("music")
        val KEY_SFX = floatPreferencesKey("sfx")
        val KEY_HAPTICS = booleanPreferencesKey("haptics")
        val KEY_DIAGNOSTICS = booleanPreferencesKey("diagnostics")
    }
}
