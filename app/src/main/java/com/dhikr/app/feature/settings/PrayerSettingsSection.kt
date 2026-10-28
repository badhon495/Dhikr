package com.dhikr.app.feature.settings

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dhikr.app.R
import com.dhikr.app.core.notifications.ReminderNotifications
import com.dhikr.app.core.prayer.AsrMethod
import com.dhikr.app.core.prayer.CalculationMethod
import com.dhikr.app.core.prayer.PRAYER_CITIES
import com.dhikr.app.core.prayer.PRAYER_OFFSET_OPTIONS
import com.dhikr.app.core.prayer.Prayer
import com.dhikr.app.ui.NotificationsOffHint
import com.dhikr.app.ui.minTapTarget
import com.dhikr.app.ui.theme.DhikrTheme
import com.dhikr.app.ui.theme.DialogShape
import com.dhikr.app.ui.theme.PillShape
import java.text.DateFormat
import java.util.Date
import java.util.Locale

/**
 * Prayer-linked reminders (plan.md §28) plus the Hijri date offset. Location
 * is a city pick or typed coordinates — never device location — and all
 * times are computed on-device.
 */
@Composable
internal fun PrayerSettingsSection(
    state: SettingsUiState,
    viewModel: SettingsViewModel,
    onOpenQibla: () -> Unit,
) {
    val colors = DhikrTheme.colors
    val context = LocalContext.current
    val settings = state.prayerSettings
    var showLocationDialog by rememberSaveable { mutableStateOf(false) }
    var routinePickerFor by rememberSaveable { mutableStateOf<Prayer?>(null) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) {}

    Text(
        stringResource(R.string.settings_prayer_desc),
        fontSize = 12.sp,
        color = colors.faint,
        modifier = Modifier.padding(bottom = 10.dp),
    )

    // Location row
    val locationLabel = when {
        !settings.hasLocation -> stringResource(R.string.settings_prayer_location_none)
        settings.locationName != null -> settings.locationName
        else -> String.format(Locale.US, "%.4f, %.4f", settings.latitude, settings.longitude)
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button) { showLocationDialog = true }
            .minTapTarget(),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(stringResource(R.string.settings_prayer_location), fontSize = 14.sp, color = colors.text)
            Text(locationLabel, fontSize = 12.sp, color = colors.faint, modifier = Modifier.padding(top = 2.dp))
        }
        Text(
            stringResource(R.string.settings_prayer_location_change),
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = colors.sage,
        )
    }

    if (!settings.hasLocation) {
        if (showLocationDialog) {
            LocationDialog(
                onPick = { lat, lng, name ->
                    viewModel.onPrayerLocationChange(lat, lng, name)
                    showLocationDialog = false
                },
                onDismiss = { showLocationDialog = false },
            )
        }
        return
    }

    // Qibla direction (needs the location above, nothing else).
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .clickable(role = Role.Button, onClick = onOpenQibla)
            .minTapTarget(),
    ) {
        Text(
            stringResource(R.string.settings_qibla),
            fontSize = 14.sp,
            color = colors.text,
            modifier = Modifier.weight(1f),
        )
        Text(
            stringResource(R.string.settings_qibla_open),
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = colors.sage,
        )
    }

    SubLabel(stringResource(R.string.settings_prayer_method))
    ChoiceRow(
        options = listOf(
            CalculationMethod.KARACHI to stringResource(R.string.settings_prayer_method_karachi),
            CalculationMethod.MWL to stringResource(R.string.settings_prayer_method_mwl),
            CalculationMethod.ISNA to stringResource(R.string.settings_prayer_method_isna),
            CalculationMethod.EGYPT to stringResource(R.string.settings_prayer_method_egypt),
            CalculationMethod.UMM_AL_QURA to stringResource(R.string.settings_prayer_method_umm_al_qura),
        ),
        selected = settings.method,
        onSelect = viewModel::onPrayerMethodChange,
    )

    SubLabel(stringResource(R.string.settings_prayer_asr))
    ChoiceRow(
        options = listOf(
            AsrMethod.STANDARD to stringResource(R.string.settings_prayer_asr_standard),
            AsrMethod.HANAFI to stringResource(R.string.settings_prayer_asr_hanafi),
        ),
        selected = settings.asr,
        onSelect = viewModel::onPrayerAsrChange,
    )

    SubLabel(stringResource(R.string.settings_prayer_offset))
    ChoiceRow(
        options = PRAYER_OFFSET_OPTIONS.map { it to stringResource(R.string.settings_prayer_offset_minutes, it) },
        selected = settings.offsetMinutes,
        onSelect = viewModel::onPrayerOffsetChange,
    )
    Text(
        stringResource(R.string.settings_prayer_offset_hint),
        fontSize = 12.sp,
        color = colors.faint,
        modifier = Modifier.padding(top = 6.dp),
    )

    Column(modifier = Modifier.padding(top = 14.dp)) {
        val timeFormat = remember { DateFormat.getTimeInstance(DateFormat.SHORT) }
        Prayer.entries.forEach { prayer ->
            val time = state.prayerTimesToday[prayer]
            SwitchRow(
                title = stringResource(ReminderNotifications.prayerNameRes(prayer)),
                description = if (time != null) {
                    stringResource(R.string.settings_prayer_time_today, timeFormat.format(Date(time)))
                } else {
                    stringResource(R.string.settings_prayer_time_unavailable)
                },
                checked = settings.isEnabled(prayer),
                onCheckedChange = { enabled ->
                    // hasPermission() is always true below Android 13, so this
                    // only ever asks where POST_NOTIFICATIONS exists.
                    if (enabled && !ReminderNotifications.hasPermission(context)) {
                        permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                    viewModel.onPrayerReminderToggle(prayer, enabled)
                },
            )
            if (settings.isEnabled(prayer)) {
                val routineId = settings.routineFor(prayer)
                val routineName = state.routineOptions.firstOrNull { it.first == routineId }?.second
                    ?: stringResource(R.string.settings_prayer_routine_missing)
                Text(
                    stringResource(R.string.settings_prayer_routine, routineName),
                    fontSize = 12.5.sp,
                    color = colors.sage,
                    // No rounded clip: on a single short line it bites into the
                    // first letter (same issue SwitchRow documents).
                    modifier = Modifier
                        .clickable(role = Role.Button) { routinePickerFor = prayer }
                        .minTapTarget()
                        .padding(vertical = 4.dp),
                )
            }
        }
        if (settings.remindersMask != 0) {
            NotificationsOffHint(modifier = Modifier.padding(top = 6.dp))
        }
        Text(
            stringResource(R.string.settings_prayer_note),
            fontSize = 12.sp,
            color = colors.faint,
            modifier = Modifier.padding(top = 10.dp),
        )
    }

    if (showLocationDialog) {
        LocationDialog(
            onPick = { lat, lng, name ->
                viewModel.onPrayerLocationChange(lat, lng, name)
                showLocationDialog = false
            },
            onDismiss = { showLocationDialog = false },
        )
    }

    routinePickerFor?.let { prayer ->
        RoutinePickerDialog(
            title = stringResource(ReminderNotifications.prayerNameRes(prayer)),
            options = state.routineOptions,
            selectedId = settings.routineFor(prayer),
            onPick = { id ->
                viewModel.onPrayerRoutineChange(prayer, id)
                routinePickerFor = null
            },
            onDismiss = { routinePickerFor = null },
        )
    }
}

/** Evening-before reminder for Sunnah fasts; independent of the location. */
@Composable
internal fun FastingReminderRow(state: SettingsUiState, viewModel: SettingsViewModel) {
    val context = LocalContext.current
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) {}
    Column {
        SwitchRow(
            title = stringResource(R.string.settings_fasting_reminder),
            description = stringResource(R.string.settings_fasting_reminder_desc),
            checked = state.fastingReminderEnabled,
            onCheckedChange = { enabled ->
                if (enabled && !ReminderNotifications.hasPermission(context)) {
                    permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
                viewModel.onFastingReminderChange(enabled)
            },
        )
        if (state.fastingReminderEnabled) NotificationsOffHint()
    }
}

@Composable
private fun RoutinePickerDialog(
    title: String,
    options: List<Pair<String, String>>,
    selectedId: String,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = DhikrTheme.colors
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_prayer_routine_dialog, title)) },
        containerColor = colors.card,
        titleContentColor = colors.text,
        textContentColor = colors.dim,
        shape = DialogShape,
        text = {
            LazyColumn(modifier = Modifier.heightIn(max = 360.dp)) {
                items(options, key = { it.first }) { (id, name) ->
                    LocationOption(name, accent = id == selectedId) { onPick(id) }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.settings_prayer_location_cancel), color = colors.dim)
            }
        },
    )
}

/** Hijri date offset, -2..+2 days, to match local moon sighting. */
@Composable
internal fun HijriAdjustRow(state: SettingsUiState, viewModel: SettingsViewModel) {
    val colors = DhikrTheme.colors
    Text(stringResource(R.string.settings_hijri_adjust), fontSize = 14.sp, color = colors.text)
    Text(
        stringResource(R.string.settings_hijri_adjust_desc),
        fontSize = 12.sp,
        color = colors.faint,
        modifier = Modifier.padding(top = 2.dp, bottom = 10.dp),
    )
    ChoiceRow(
        options = (-2..2).map { it to if (it > 0) "+$it" else "$it" },
        selected = state.hijriAdjustDays,
        onSelect = viewModel::onHijriAdjustChange,
    )
}

@Composable
private fun SubLabel(text: String) {
    Text(
        text,
        fontSize = 14.sp,
        color = DhikrTheme.colors.text,
        modifier = Modifier.padding(top = 14.dp, bottom = 8.dp),
    )
}

/** City list plus a manual-coordinates form. */
@Composable
private fun LocationDialog(
    onPick: (latitude: Double, longitude: Double, name: String?) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = DhikrTheme.colors
    var manual by rememberSaveable { mutableStateOf(false) }
    var latText by rememberSaveable { mutableStateOf("") }
    var lngText by rememberSaveable { mutableStateOf("") }
    val lat = latText.trim().toDoubleOrNull()?.takeIf { it in -90.0..90.0 }
    val lng = lngText.trim().toDoubleOrNull()?.takeIf { it in -180.0..180.0 }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_prayer_location)) },
        containerColor = colors.card,
        titleContentColor = colors.text,
        textContentColor = colors.dim,
        shape = DialogShape,
        text = {
            if (manual) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = latText,
                        onValueChange = { latText = it },
                        label = { Text(stringResource(R.string.settings_prayer_latitude)) },
                        singleLine = true,
                        isError = latText.isNotBlank() && lat == null,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    )
                    OutlinedTextField(
                        value = lngText,
                        onValueChange = { lngText = it },
                        label = { Text(stringResource(R.string.settings_prayer_longitude)) },
                        singleLine = true,
                        isError = lngText.isNotBlank() && lng == null,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    )
                }
            } else {
                LazyColumn(modifier = Modifier.heightIn(max = 360.dp)) {
                    item {
                        LocationOption(stringResource(R.string.settings_prayer_location_manual), accent = true) {
                            manual = true
                        }
                    }
                    items(PRAYER_CITIES, key = { it.name }) { city ->
                        LocationOption(city.name) { onPick(city.latitude, city.longitude, city.name) }
                    }
                }
            }
        },
        confirmButton = {
            if (manual) {
                TextButton(
                    enabled = lat != null && lng != null,
                    onClick = { if (lat != null && lng != null) onPick(lat, lng, null) },
                ) {
                    Text(stringResource(R.string.settings_prayer_location_save), color = colors.sage, fontWeight = FontWeight.SemiBold)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.settings_prayer_location_cancel), color = colors.dim)
            }
        },
    )
}

@Composable
private fun LocationOption(label: String, accent: Boolean = false, onClick: () -> Unit) {
    val colors = DhikrTheme.colors
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(PillShape)
            .clickable(role = Role.Button, onClick = onClick)
            .minTapTarget()
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .then(if (accent) Modifier.background(colors.card) else Modifier),
    ) {
        Text(
            label,
            fontSize = 14.sp,
            color = if (accent) colors.sage else colors.text,
            fontWeight = if (accent) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}
