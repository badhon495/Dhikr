package com.dhikr.app.core.prayer

import android.content.Context
import android.hardware.GeomagneticField
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.view.Surface
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/**
 * Device heading (degrees from TRUE north) from the rotation-vector sensor,
 * corrected for magnetic declination at the saved location. Registered only
 * between [start] and [stop]; the Qibla screen ties those to its own
 * resume/pause so nothing samples sensors in the background.
 */
class QiblaCompass(
    context: Context,
    latitude: Double,
    longitude: Double,
    /** Current [Surface] rotation of the screen showing the compass. Supplied
     *  by the UI from its own view — the application context has no display
     *  (Context.getDisplay() throws there on API 30+). */
    private val displayRotation: () -> Int,
) {

    private val appContext = context.applicationContext
    private val sensorManager = appContext.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private val sensor: Sensor? = sensorManager?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        ?: sensorManager?.getDefaultSensor(Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR)

    // Declination barely changes over years; computed once.
    private val declination: Float = GeomagneticField(
        latitude.toFloat(), longitude.toFloat(), 0f, System.currentTimeMillis(),
    ).declination

    val isSupported: Boolean get() = sensor != null

    private var listener: SensorEventListener? = null

    /**
     * @param onHeading true heading 0..360, smoothed.
     * @param onAccuracy true when the magnetometer reports low/unreliable accuracy.
     */
    fun start(onHeading: (Double) -> Unit, onAccuracy: (lowAccuracy: Boolean) -> Unit) {
        val s = sensor ?: return
        val rotation = FloatArray(9)
        val remapped = FloatArray(9)
        val orientation = FloatArray(3)
        // Circular low-pass on the unit vector so 359 -> 1 doesn't swing through 180.
        var sx = 0.0
        var sy = 0.0
        var primed = false
        val l = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                SensorManager.getRotationMatrixFromVector(rotation, event.values)
                val (axisX, axisY) = when (displayRotation()) {
                    Surface.ROTATION_90 -> SensorManager.AXIS_Y to SensorManager.AXIS_MINUS_X
                    Surface.ROTATION_180 -> SensorManager.AXIS_MINUS_X to SensorManager.AXIS_MINUS_Y
                    Surface.ROTATION_270 -> SensorManager.AXIS_MINUS_Y to SensorManager.AXIS_X
                    else -> SensorManager.AXIS_X to SensorManager.AXIS_Y
                }
                SensorManager.remapCoordinateSystem(rotation, axisX, axisY, remapped)
                SensorManager.getOrientation(remapped, orientation)
                val heading = Math.toRadians(
                    Qibla.normalize(Math.toDegrees(orientation[0].toDouble()) + declination),
                )
                if (!primed) {
                    sx = cos(heading); sy = sin(heading); primed = true
                } else {
                    sx += SMOOTHING * (cos(heading) - sx)
                    sy += SMOOTHING * (sin(heading) - sy)
                }
                onHeading(Qibla.normalize(Math.toDegrees(atan2(sy, sx))))
            }

            override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {
                onAccuracy(
                    accuracy == SensorManager.SENSOR_STATUS_UNRELIABLE ||
                        accuracy == SensorManager.SENSOR_STATUS_ACCURACY_LOW,
                )
            }
        }
        listener = l
        sensorManager?.registerListener(l, s, SensorManager.SENSOR_DELAY_UI)
    }

    fun stop() {
        listener?.let { sensorManager?.unregisterListener(it) }
        listener = null
    }

    private companion object {
        const val SMOOTHING = 0.15
    }
}
