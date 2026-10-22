package com.dhikr.app.feature.qibla

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.dhikr.app.R
import com.dhikr.app.core.localization.LocalAppLanguage
import com.dhikr.app.core.localization.localizedDigits
import com.dhikr.app.core.prayer.Qibla
import com.dhikr.app.core.prayer.QiblaCompass
import com.dhikr.app.ui.headingSemantics
import com.dhikr.app.ui.minTapTarget
import com.dhikr.app.ui.theme.DhikrTheme
import kotlin.math.abs
import kotlin.math.roundToInt

/** Within this many degrees the screen says "facing the Qibla". */
private const val ON_TARGET_DEGREES = 5.0

/**
 * Qibla direction from the saved prayer location. The bearing is pure math
 * and always shown; the live compass needle appears only on devices with a
 * rotation-vector sensor, and the sensor runs only while this screen is
 * resumed.
 */
@Composable
fun QiblaScreen(latitude: Double?, longitude: Double?, locationName: String?, onBack: () -> Unit) {
    val colors = DhikrTheme.colors
    val lang = LocalAppLanguage.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.bg)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable(role = Role.Button) { onBack() }
                    .minTapTarget()
                    .padding(6.dp),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.action_back),
                    tint = colors.text,
                    modifier = Modifier.size(20.dp),
                )
            }
            Text(
                stringResource(R.string.qibla_title),
                fontSize = 23.sp,
                color = colors.text,
                modifier = Modifier.padding(start = 8.dp).headingSemantics(),
            )
        }

        if (latitude == null || longitude == null) {
            Text(
                stringResource(R.string.qibla_no_location),
                fontSize = 14.sp,
                color = colors.dim,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 48.dp),
            )
            return@Column
        }

        val bearing = remember(latitude, longitude) { Qibla.bearing(latitude, longitude) }
        val context = LocalContext.current
        val view = LocalView.current
        val compass = remember(latitude, longitude, view) {
            QiblaCompass(context, latitude, longitude) {
                view.display?.rotation ?: android.view.Surface.ROTATION_0
            }
        }
        var heading by remember { mutableStateOf<Double?>(null) }
        var lowAccuracy by remember { mutableStateOf(false) }

        val lifecycleOwner = LocalLifecycleOwner.current
        DisposableEffect(lifecycleOwner, compass) {
            val observer = LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_RESUME -> compass.start(
                        onHeading = { heading = it },
                        onAccuracy = { lowAccuracy = it },
                    )
                    Lifecycle.Event.ON_PAUSE -> compass.stop()
                    else -> Unit
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose {
                lifecycleOwner.lifecycle.removeObserver(observer)
                compass.stop()
            }
        }

        Text(
            locationName ?: String.format(java.util.Locale.US, "%.4f, %.4f", latitude, longitude),
            fontSize = 13.sp,
            color = colors.dim,
            modifier = Modifier.padding(top = 8.dp),
        )
        val bearingText = stringResource(R.string.qibla_bearing, bearing.roundToInt().localizedDigits(lang))
        Text(bearingText, fontSize = 15.sp, color = colors.text, modifier = Modifier.padding(top = 4.dp))

        val current = heading
        if (!compass.isSupported) {
            Text(
                stringResource(R.string.qibla_no_sensor),
                fontSize = 13.sp,
                color = colors.faint,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 32.dp),
            )
            return@Column
        }

        // Dial: north-up rose rotated against the heading, arrow toward the Qibla.
        val turn = current?.let { Qibla.turn(it, bearing) }
        val onTarget = turn != null && abs(turn) <= ON_TARGET_DEGREES
        val arrowColor = if (onTarget) colors.sage else colors.terra
        Box(
            modifier = Modifier
                .padding(top = 32.dp)
                .size(260.dp),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(modifier = Modifier.fillMaxSize().rotate(-(current ?: 0.0).toFloat())) {
                val r = size.minDimension / 2f
                drawCircle(color = colors.track, radius = r - 6.dp.toPx(), style = Stroke(width = 6.dp.toPx()))
                // North tick.
                drawLine(
                    color = colors.dim,
                    start = Offset(center.x, center.y - r + 14.dp.toPx()),
                    end = Offset(center.x, center.y - r + 34.dp.toPx()),
                    strokeWidth = 4.dp.toPx(),
                    cap = StrokeCap.Round,
                )
            }
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .rotate((turn ?: 0.0).toFloat()),
            ) {
                val r = size.minDimension / 2f
                val path = Path().apply {
                    moveTo(center.x, center.y - r + 24.dp.toPx())
                    lineTo(center.x - 18.dp.toPx(), center.y)
                    lineTo(center.x + 18.dp.toPx(), center.y)
                    close()
                }
                drawPath(path, color = if (turn == null) colors.track else arrowColor)
                drawCircle(color = colors.card, radius = 26.dp.toPx())
            }
        }

        val status = when {
            turn == null -> stringResource(R.string.qibla_waiting)
            onTarget -> stringResource(R.string.qibla_on_target)
            turn > 0 -> stringResource(R.string.qibla_turn_right, abs(turn).roundToInt().localizedDigits(lang))
            else -> stringResource(R.string.qibla_turn_left, abs(turn).roundToInt().localizedDigits(lang))
        }
        Text(
            status,
            fontSize = 17.sp,
            color = if (onTarget) colors.sage else colors.text,
            modifier = Modifier
                .padding(top = 24.dp)
                .semantics {
                    liveRegion = LiveRegionMode.Polite
                    contentDescription = status
                },
        )
        Column(
            verticalArrangement = Arrangement.spacedBy(6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(top = 16.dp),
        ) {
            if (lowAccuracy) {
                Text(
                    stringResource(R.string.qibla_calibrate),
                    fontSize = 12.5.sp,
                    color = colors.text,
                    textAlign = TextAlign.Center,
                )
            }
            Text(
                stringResource(R.string.qibla_note),
                fontSize = 11.5.sp,
                color = colors.faint,
                textAlign = TextAlign.Center,
            )
        }
    }
}
