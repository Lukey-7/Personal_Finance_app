package com.pft.financetracker.ui.components

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import com.pft.financetracker.ui.theme.reducedMotion

/*
 * Shared-element plumbing: AppNav wraps its NavHost in a SharedTransitionLayout and each destination provides its
 * AnimatedVisibilityScope, so a ledger row's icon and amount can grow into the transaction detail screen. Off when
 * animations are off, or outside the NavHost (previews, the quick-add activity).
 */

@OptIn(ExperimentalSharedTransitionApi::class)
val LocalSharedTransitionScope = compositionLocalOf<SharedTransitionScope?> { null }
val LocalNavAnimatedScope = compositionLocalOf<AnimatedVisibilityScope?> { null }

/** Keys for the parts of a transaction that travel into its detail screen. */
object SharedKeys {
    fun icon(id: Long) = "txn-icon-$id"
    fun amount(id: Long) = "txn-amount-$id"
    fun title(id: Long) = "txn-title-$id"
}

/** Marks this element as the same thing on two screens, so it moves between them instead of cutting. */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun Modifier.sharedElement(key: String): Modifier {
    val shared = LocalSharedTransitionScope.current ?: return this
    val scope = LocalNavAnimatedScope.current ?: return this
    if (reducedMotion) return this
    return with(shared) { this@sharedElement.sharedBounds(rememberSharedContentState(key), scope) }
}
