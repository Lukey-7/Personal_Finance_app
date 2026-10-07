package com.pft.financetracker.ui.theme

import android.os.Build
import android.provider.Settings
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalView

/*
 * Motion in the Material 3 Expressive manner: springs, not durations. Spatial movement (sheets, rows settling in,
 * a card opening) gets a little bounce; colour and fade are critically damped so nothing flickers. When the system's
 * "Remove animations" is on, every spec here snaps instead.
 */

/** True when the person has turned animations off (Settings › Accessibility › Remove animations). */
val LocalReducedMotion = staticCompositionLocalOf { false }

/** Whether animations are off right now. */
val reducedMotion: Boolean @Composable @ReadOnlyComposable get() = LocalReducedMotion.current

object Motion {
    /** Sheets, cards opening, the Add button changing shape: a quick spring with a hint of overshoot. */
    fun <T> spatial(): FiniteAnimationSpec<T> = spring(dampingRatio = 0.78f, stiffness = 420f)

    /** Small things that move: a segment thumb, a chip, a swipe snapping back. */
    fun <T> spatialFast(): FiniteAnimationSpec<T> = spring(dampingRatio = 0.72f, stiffness = Spring.StiffnessMediumLow * 2.5f)

    /** Colour, opacity and numbers rolling: no bounce. */
    fun <T> effects(): FiniteAnimationSpec<T> = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)

    /** Big numbers settling on a new value. */
    fun <T> roll(): FiniteAnimationSpec<T> = spring(dampingRatio = 0.86f, stiffness = 260f)
}

/** [spec], or an instant snap when animations are off. */
@Composable @ReadOnlyComposable
fun <T> motion(spec: FiniteAnimationSpec<T>): FiniteAnimationSpec<T> = if (LocalReducedMotion.current) snap() else spec

/** Same as [motion], for an AnimationSpec that may repeat (a shimmer): no animation at all when they are off. */
@Composable @ReadOnlyComposable
fun <T> motionOrNull(spec: AnimationSpec<T>): AnimationSpec<T>? = if (LocalReducedMotion.current) null else spec

private fun animationsOff(view: View): Boolean =
    runCatching { Settings.Global.getFloat(view.context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f }.getOrDefault(false)

/** Reads the system setting once per composition root and hands it down. */
@Composable
internal fun ProvideMotion(content: @Composable () -> Unit) {
    val view = LocalView.current
    val off = remember(view) { if (view.isInEditMode) false else animationsOff(view) }
    CompositionLocalProvider(LocalReducedMotion provides off, content = content)
}

/** The three haptics the app uses: a firm confirm on save, a light tick on a choice, a buzz when input is refused. */
class Haptics internal constructor(private val view: View) {
    fun confirm() { view.performHapticFeedback(HapticFeedbackConstants.CONFIRM) }
    fun tick() {
        view.performHapticFeedback(if (Build.VERSION.SDK_INT >= 34) HapticFeedbackConstants.SEGMENT_TICK else HapticFeedbackConstants.CLOCK_TICK)
    }
    fun reject() { view.performHapticFeedback(HapticFeedbackConstants.REJECT) }
    fun longPress() { view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS) }
}

@Composable
fun rememberHaptics(): Haptics {
    val view = LocalView.current
    return remember(view) { Haptics(view) }
}
