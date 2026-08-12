package com.olympussurge.game.oracle.ui.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import kotlinx.coroutines.delay

/**
 * Ticks a 0..3 phase every 300 ms — used by the "Loading . . ."
 * caption on [com.olympussurge.game.oracle.ui.OracleLoadingScreen].
 * Full cycle = 1200 ms per guide §"Screen Layout: OracleLoadingScreen".
 */
@Composable
fun rememberDotsPhase(): State<Int> {
    val phase = remember { mutableStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(300)
            phase.value = (phase.value + 1) % 4
        }
    }
    return phase
}
