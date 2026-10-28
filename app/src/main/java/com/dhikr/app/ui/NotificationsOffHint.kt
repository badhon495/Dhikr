package com.dhikr.app.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.dhikr.app.R
import com.dhikr.app.ui.theme.DhikrTheme

/**
 * A system-controlled flag (notification block, exact-alarm grant) that the
 * user can flip from a settings screen outside the app. Reads [read] now and
 * again on every resume, so the UI catches up the moment they come back.
 */
@Composable
fun rememberOnResume(read: () -> Boolean): Boolean {
    val currentRead by rememberUpdatedState(read)
    var value by remember { mutableStateOf(currentRead()) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) value = currentRead()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    return value
}

/**
 * Shown under a reminder that is switched on while notifications are blocked
 * for the app (permission denied, or turned off later in system settings).
 */
@Composable
fun NotificationsOffHint(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val colors = DhikrTheme.colors
    val enabled = rememberOnResume { NotificationManagerCompat.from(context).areNotificationsEnabled() }
    if (enabled) return

    Column(modifier = modifier) {
        Text(
            text = stringResource(R.string.reminder_permission_hint),
            fontSize = 12.sp,
            color = colors.text,
        )
        TextButton(onClick = { openNotificationSettings(context) }) {
            Text(
                text = stringResource(R.string.reminder_permission_open_settings),
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = colors.sage,
            )
        }
    }
}

private fun openNotificationSettings(context: Context) {
    val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
    } else {
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
    }
    runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}
