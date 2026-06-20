package com.tideo.autobrightness.app.ui.components

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally

/**
 * S13b motion helpers (m3_audit §4 "No motion"). A single owned place for the app's transition specs
 * so S13c wires consistent screen enter/exit, list add/remove, and press feedback rather than ad-hoc
 * per-screen animations. Durations follow the M3 short/medium emphasis scale.
 */
object AabMotion {
    /** M3 "short4" — affordance/press feedback. */
    const val DURATION_SHORT = 150
    /** M3 "medium2" — screen + content transitions. */
    const val DURATION_MEDIUM = 250

    /** Scale factor applied while a hero/CTA surface is pressed (see [HeroNavCard]). */
    const val PRESS_SCALE = 0.97f

    /** Screen/destination enter: fade + a small slide-in from the trailing edge. */
    val screenEnter: EnterTransition =
        fadeIn(tween(DURATION_MEDIUM)) +
            slideInHorizontally(tween(DURATION_MEDIUM)) { full -> full / 12 }

    /** Screen/destination exit: fade + a small slide-out to the leading edge. */
    val screenExit: ExitTransition =
        fadeOut(tween(DURATION_SHORT)) +
            slideOutHorizontally(tween(DURATION_SHORT)) { full -> -full / 12 }

    /** List add/remove placement + a fade for newly composed rows. */
    val listItemEnter: EnterTransition = fadeIn(tween(DURATION_MEDIUM))
    val listItemExit: ExitTransition = fadeOut(tween(DURATION_SHORT))

    /** Spec for animating a changing value (e.g. an [AabCard]/[KeyValueRow] live readout). */
    fun <T> valueSpec(): FiniteAnimationSpec<T> = tween(DURATION_MEDIUM)
}
