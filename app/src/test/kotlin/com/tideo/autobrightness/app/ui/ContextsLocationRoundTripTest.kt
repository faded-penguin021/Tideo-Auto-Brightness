package com.tideo.autobrightness.app.ui

import com.tideo.autobrightness.app.settings.ContextTriggers
import com.tideo.autobrightness.app.settings.LocationTrigger
import com.tideo.autobrightness.app.ui.components.coordText
import com.tideo.autobrightness.app.ui.components.summary
import com.tideo.autobrightness.app.ui.screens.locationTriggerOf
import java.util.Locale
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class ContextsLocationRoundTripTest {

    private val original: Locale = Locale.getDefault()

    @AfterTest
    fun restoreLocale() = Locale.setDefault(original)

    private fun roundTrip(stored: LocationTrigger?): LocationTrigger? =
        locationTriggerOf(
            enabled = stored != null,
            latText = coordText(stored?.lat),
            lonText = coordText(stored?.lon),
            radiusText = stored?.radius?.toInt()?.toString() ?: "200",
        )

    @Test
    fun `a saved location rule survives reopening in a comma-decimal locale`() {
        listOf(Locale.GERMANY, Locale.FRANCE, Locale.ITALY, Locale.US, Locale.UK).forEach { locale ->
            Locale.setDefault(locale)
            val stored = LocationTrigger(lat = 52.37021, lon = 4.89517, radius = 200.0)
            val reopened = assertNotNull(roundTrip(stored), "location lost on reopen in $locale")
            assertEquals(stored.lat, reopened.lat, 1e-9, "latitude changed in $locale")
            assertEquals(stored.lon, reopened.lon, 1e-9, "longitude changed in $locale")
            assertEquals(stored.radius, reopened.radius, 1e-9, "radius changed in $locale")
        }
    }

    @Test
    fun `a comma typed on a European keyboard still stores the location`() {
        val trigger = assertNotNull(
            locationTriggerOf(enabled = true, latText = "52,37021", lonText = "4,89517", radiusText = "200"),
            "a comma-decimal field must not drop the whole location trigger",
        )
        assertEquals(52.37021, trigger.lat, 1e-9)
        assertEquals(4.89517, trigger.lon, 1e-9)
    }

    @Test
    fun `the device's own fix round-trips whatever the locale formats`() {
        Locale.setDefault(Locale.GERMANY)
        val trigger = assertNotNull(
            locationTriggerOf(true, coordText(-33.86882), coordText(151.20930), "500"),
            "the fix the device just returned must survive its own field",
        )
        assertEquals(-33.86882, trigger.lat, 1e-9)
        assertEquals(151.20930, trigger.lon, 1e-9)
        assertEquals(500.0, trigger.radius, 1e-9)
    }

    @Test
    fun `an off section or unusable fields still store no location`() {
        assertNull(locationTriggerOf(enabled = false, latText = "52.37", lonText = "4.89", radiusText = "200"))
        assertNull(locationTriggerOf(true, "", "4.89", "200"))
        assertNull(locationTriggerOf(true, "52.37", "", "200"))
        assertNull(locationTriggerOf(true, "north", "4.89", "200"))
        assertNull(locationTriggerOf(true, "52.37", "4.89", "0"))
        assertNull(locationTriggerOf(true, "52.37", "4.89", ""))
    }

    @Test
    fun `the rules list names which coordinates a rule applies to`() {
        Locale.setDefault(Locale.GERMANY)
        val summary = ContextTriggers(location = LocationTrigger(lat = 52.37021, lon = 4.89517, radius = 200.0)).summary()
        assertEquals("near 52.37021, 4.89517 (200 m)", summary)
    }
}
