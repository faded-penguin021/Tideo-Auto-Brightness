package com.tideo.autobrightness.app.ui

import com.tideo.autobrightness.app.ui.components.fmtLux
import com.tideo.autobrightness.app.ui.components.fmtPercent
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.util.Locale

class DiagnosticFormatTest {

    private val saved = Locale.getDefault()

    @Before
    fun pinLocale() = Locale.setDefault(Locale.US)

    @After
    fun restoreLocale() = Locale.setDefault(saved)

    @Test
    fun luxShowsTheScaleItWasStoredAt() {
        assertEquals("0.43", fmtLux(0.43))
        assertEquals("1.15", fmtLux(1.15))
        assertEquals("135", fmtLux(135.0))
        assertEquals("0.153", fmtLux(0.153))
        assertEquals("0", fmtLux(0.0))
        assertEquals("59.1", fmtLux(59.1f.toDouble()))
        assertEquals("—", fmtLux(null))
    }

    @Test
    fun aNonFiniteValueIsShownRatherThanThrown() {
        assertEquals("NaN", fmtLux(Double.NaN))
        assertEquals("Infinity%", fmtPercent(Double.POSITIVE_INFINITY))
    }

    @Test
    fun dynamicThresholdIsAPercentAtFullPrecision() {
        assertEquals("30%", fmtPercent(0.3))
        assertEquals("31.3%", fmtPercent(0.313))
        assertEquals("4.5%", fmtPercent(0.045))
        assertEquals("—", fmtPercent(null))
    }

    @Test
    fun theDecimalSeparatorFollowsTheLocale() {
        Locale.setDefault(Locale.GERMANY)
        assertEquals("0,43", fmtLux(0.43))
        assertEquals("31,3%", fmtPercent(0.313))
    }
}
