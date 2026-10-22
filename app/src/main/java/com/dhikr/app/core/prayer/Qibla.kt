package com.dhikr.app.core.prayer

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/** Great-circle direction to the Kaaba. Pure — no sensors, no network. */
object Qibla {

    private const val KAABA_LAT = 21.4225
    private const val KAABA_LNG = 39.8262

    /** Initial bearing from (latitude, longitude) to the Kaaba, degrees from true north, 0..360. */
    fun bearing(latitude: Double, longitude: Double): Double {
        val phi1 = Math.toRadians(latitude)
        val phi2 = Math.toRadians(KAABA_LAT)
        val dLambda = Math.toRadians(KAABA_LNG - longitude)
        val y = sin(dLambda) * cos(phi2)
        val x = cos(phi1) * sin(phi2) - sin(phi1) * cos(phi2) * cos(dLambda)
        return normalize(Math.toDegrees(atan2(y, x)))
    }

    /** Signed degrees to turn from [heading] to face [bearing]: -180..180, positive = clockwise. */
    fun turn(heading: Double, bearing: Double): Double {
        val d = normalize(bearing - heading)
        return if (d > 180.0) d - 360.0 else d
    }

    fun normalize(degrees: Double): Double = ((degrees % 360.0) + 360.0) % 360.0
}
