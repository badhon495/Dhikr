package com.dhikr.app.core.prayer

/**
 * A short built-in list of cities so most users can set prayer reminders
 * without typing coordinates and without granting location permission
 * (plan.md §28). Anyone elsewhere enters coordinates by hand.
 */
data class PrayerCity(val name: String, val latitude: Double, val longitude: Double)

val PRAYER_CITIES: List<PrayerCity> = listOf(
    PrayerCity("Dhaka", 23.8103, 90.4125),
    PrayerCity("Chattogram", 22.3569, 91.7832),
    PrayerCity("Sylhet", 24.8949, 91.8687),
    PrayerCity("Rajshahi", 24.3745, 88.6042),
    PrayerCity("Khulna", 22.8456, 89.5403),
    PrayerCity("Kolkata", 22.5726, 88.3639),
    PrayerCity("Delhi", 28.6139, 77.2090),
    PrayerCity("Karachi", 24.8607, 67.0011),
    PrayerCity("Lahore", 31.5204, 74.3587),
    PrayerCity("Islamabad", 33.6844, 73.0479),
    PrayerCity("Makkah", 21.3891, 39.8579),
    PrayerCity("Madinah", 24.5247, 39.5692),
    PrayerCity("Riyadh", 24.7136, 46.6753),
    PrayerCity("Dubai", 25.2048, 55.2708),
    PrayerCity("Doha", 25.2854, 51.5310),
    PrayerCity("Kuwait City", 29.3759, 47.9774),
    PrayerCity("Cairo", 30.0444, 31.2357),
    PrayerCity("Istanbul", 41.0082, 28.9784),
    PrayerCity("Tehran", 35.6892, 51.3890),
    PrayerCity("Jakarta", -6.2088, 106.8456),
    PrayerCity("Kuala Lumpur", 3.1390, 101.6869),
    PrayerCity("Singapore", 1.3521, 103.8198),
    PrayerCity("Lagos", 6.5244, 3.3792),
    PrayerCity("Casablanca", 33.5731, -7.5898),
    PrayerCity("London", 51.5074, -0.1278),
    PrayerCity("Paris", 48.8566, 2.3522),
    PrayerCity("Berlin", 52.5200, 13.4050),
    PrayerCity("New York", 40.7128, -74.0060),
    PrayerCity("Toronto", 43.6532, -79.3832),
    PrayerCity("Sydney", -33.8688, 151.2093),
)
