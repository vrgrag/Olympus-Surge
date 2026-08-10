package com.olympussurge.game.world

/**
 * Permanent bonuses the player brings into a run from the Temple of Olympus.
 *
 * The game module deliberately knows nothing about where these came from: the
 * Android layer resolves saved upgrade levels into plain numbers and hands
 * them over, so combat can be simulated and tested without any storage.
 */
data class RunModifiers(
    val bonusMaxHealth: Float = 0f,
    val damageMultiplier: Float = 1f,
    val moveSpeedMultiplier: Float = 1f,
    val attackSpeedMultiplier: Float = 1f,
    val gemChanceMultiplier: Float = 1f,
    val pickupMultiplier: Float = 1f,
    val damageReduction: Float = 0f,
    val startingGems: Int = 0,
)
