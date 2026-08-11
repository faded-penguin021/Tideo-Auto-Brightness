package com.tideo.autobrightness.app.ui.components

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally

/** Motion helpers (S13b, S13c): screen/content transitions, M3 durations. */
object AabMotion {
    const val DURATION_SHORT = 150  // M3 "short4".
    const val DURATION_MEDIUM = 250  // M3 "medium2".
    const val PRESS_SCALE = 0.97f

    val screenEnter: EnterTransition =
        fadeIn(tween(DURATION_MEDIUM)) +
            slideInHorizontally(tween(DURATION_MEDIUM)) { full -> full / 12 }

    val screenExit: ExitTransition =
        fadeOut(tween(DURATION_SHORT)) +
            slideOutHorizontally(tween(DURATION_SHORT)) { full -> -full / 12 }

    val listItemEnter: EnterTransition = fadeIn(tween(DURATION_MEDIUM))
    val listItemExit: ExitTransition = fadeOut(tween(DURATION_SHORT))

    fun <T> valueSpec(): FiniteAnimationSpec<T> = tween(DURATION_MEDIUM)
}
