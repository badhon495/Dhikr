package com.dhikr.app.core.prayer

import org.junit.Assert.assertEquals
import org.junit.Test

class QiblaTest {

    // Reference bearings (degrees from true north), widely published.
    @Test
    fun dhaka() = assertEquals(277.5, Qibla.bearing(23.8103, 90.4125), 1.0)

    @Test
    fun london() = assertEquals(119.0, Qibla.bearing(51.5074, -0.1278), 1.0)

    @Test
    fun newYork() = assertEquals(58.5, Qibla.bearing(40.7128, -74.0060), 1.0)

    @Test
    fun alwaysInRange() {
        val b = Qibla.bearing(-33.8688, 151.2093)
        assert(b >= 0.0 && b < 360.0)
    }

    @Test
    fun turnToFaceIsShortestSignedAngle() {
        assertEquals(-20.0, Qibla.turn(heading = 10.0, bearing = 350.0), 1e-9)
        assertEquals(30.0, Qibla.turn(heading = 340.0, bearing = 10.0), 1e-9)
        assertEquals(0.0, Qibla.turn(heading = 90.0, bearing = 90.0), 1e-9)
    }
}
