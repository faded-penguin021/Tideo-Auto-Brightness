package com.tideo.autobrightness.app.ui

import com.tideo.autobrightness.app.ui.screens.formatCoord
import com.tideo.autobrightness.app.ui.screens.parseCoord
import java.util.Locale
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class CircadianCoordinateFieldTest {

    private val original: Locale = Locale.getDefault()

    @AfterTest
    fun restoreLocale() = Locale.setDefault(original)

    private fun roundTrip(value: Double): Double? = parseCoord(formatCoord(value))

    @Test
    fun `a coordinate survives the field round trip in a comma-decimal locale`() {
        listOf(Locale.GERMANY, Locale.FRANCE, Locale.ITALY, Locale.US, Locale.UK).forEach { locale ->
            Locale.setDefault(locale)
            assertEquals(52.37021, assertNotNull(roundTrip(52.37021), "no round trip in $locale"), 1e-9)
            assertEquals(-33.86882, assertNotNull(roundTrip(-33.86882), "no round trip in $locale"), 1e-9)
        }
    }

    @Test
    fun `the formatted coordinate is dot-decimal whatever the locale`() {
        Locale.setDefault(Locale.GERMANY)
        assertEquals("52.37021", formatCoord(52.37021))
        assertEquals("-0.12776", formatCoord(-0.127760))
    }

    @Test
    fun `a comma typed on a European keyboard is read as the decimal point`() {
        assertEquals(52.37, assertNotNull(parseCoord("52,37"), "the filter must admit ',' so 52,37 is not read as 5237"), 1e-9)
        assertEquals(52.37, assertNotNull(parseCoord(" 52.37 ")), 1e-9)
    }

    @Test
    fun `blank and unparseable input stay null so date-only pinning still works`() {
        assertNull(parseCoord(""))
        assertNull(parseCoord("   "))
        assertNull(parseCoord("north"))
        assertNull(parseCoord("-"))
    }
}
