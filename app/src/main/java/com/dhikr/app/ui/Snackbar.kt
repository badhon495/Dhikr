package com.dhikr.app.ui

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * App-wide transient feedback, provided from the Scaffold in DhikrApp. Read
 * with `LocalSnackbar.current` and call [show] from a click handler or a
 * LaunchedEffect. Safe to call from non-suspend code: it launches on the
 * scope DhikrApp owns, so the message survives the caller leaving
 * composition (e.g. an editor that pops itself right after saving).
 */
class AppSnackbar(
    val hostState: SnackbarHostState,
    private val scope: CoroutineScope,
) {
    /** Shows [message], replacing any one still on screen. [onAction] runs if [actionLabel] is tapped (e.g. Undo). */
    fun show(
        message: String,
        actionLabel: String? = null,
        onAction: (() -> Unit)? = null,
    ) {
        scope.launch {
            hostState.currentSnackbarData?.dismiss()
            val result = hostState.showSnackbar(
                message = message,
                actionLabel = actionLabel,
                duration = if (actionLabel != null) SnackbarDuration.Long else SnackbarDuration.Short,
            )
            if (result == SnackbarResult.ActionPerformed) onAction?.invoke()
        }
    }
}

/** No-op default so @Preview and tests render without a host. */
val LocalSnackbar = staticCompositionLocalOf<AppSnackbar?> { null }
