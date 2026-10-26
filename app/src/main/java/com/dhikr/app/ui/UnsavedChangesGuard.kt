package com.dhikr.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import com.dhikr.app.R
import com.dhikr.app.ui.theme.DhikrTheme
import com.dhikr.app.ui.theme.DialogShape

/**
 * Asks before leaving an editor with unsaved edits. [snapshot] reduces the
 * editor's UI state to just the user-editable fields, [ready] says the state
 * has finished loading (so the loaded values count as the baseline, not as
 * edits). Returns the back action to wire to the screen's back button; the
 * system back gesture is intercepted here too.
 */
@Composable
fun <T> unsavedChangesGuard(
    state: T,
    ready: Boolean,
    snapshot: (T) -> Any,
    onBack: () -> Unit,
): () -> Unit {
    val colors = DhikrTheme.colors
    val baseline = remember(ready) { if (ready) snapshot(state) else null }
    val dirty = ready && baseline != null && snapshot(state) != baseline
    var askDiscard by remember { mutableStateOf(false) }

    BackHandler(enabled = dirty) { askDiscard = true }

    if (askDiscard) {
        AlertDialog(
            onDismissRequest = { askDiscard = false },
            title = { Text(stringResource(R.string.unsaved_title)) },
            text = { Text(stringResource(R.string.unsaved_body)) },
            containerColor = colors.card,
            titleContentColor = colors.text,
            textContentColor = colors.dim,
            shape = DialogShape,
            confirmButton = {
                TextButton(onClick = {
                    askDiscard = false
                    onBack()
                }) {
                    Text(stringResource(R.string.unsaved_discard), color = colors.terra, fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { askDiscard = false }) {
                    Text(stringResource(R.string.unsaved_keep_editing), color = colors.dim, fontWeight = FontWeight.SemiBold)
                }
            },
        )
    }

    return { if (dirty) askDiscard = true else onBack() }
}
