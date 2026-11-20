package com.dhikr.app.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer

/**
 * Keeps a screen's content invisible until its first real data has arrived,
 * then fades it in.
 *
 * Tab screens start from a default UI state (empty lists, zero totals) and
 * receive their Room/DataStore data a few frames later — usually mid-way
 * through the tab-switch slide. Without this, that default state rendered
 * first and visibly popped (empty-state hints, "0" totals) before the real
 * content replaced it.
 *
 * Place it after the screen's `.background(...)` so the background stays
 * drawn while only the content fades. Alpha is read inside graphicsLayer, so
 * the fade never recomposes the screen. If [loaded] is already true on first
 * composition (e.g. a ViewModel that survived), content shows immediately.
 */
@Composable
fun Modifier.revealWhenLoaded(loaded: Boolean): Modifier {
    val reduced = LocalReducedMotion.current
    val alpha = remember { Animatable(if (loaded) 1f else 0f) }
    LaunchedEffect(loaded, reduced) {
        if (!loaded) return@LaunchedEffect
        if (reduced) {
            alpha.snapTo(1f)
        } else {
            alpha.animateTo(1f, tween(Motion.FAST_MS, easing = Motion.StandardEasing))
        }
    }
    return this.graphicsLayer { this.alpha = alpha.value }
}
