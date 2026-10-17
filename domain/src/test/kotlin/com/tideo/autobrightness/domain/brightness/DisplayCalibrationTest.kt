package com.tideo.autobrightness.domain.brightness

import com.tideo.autobrightness.domain.brightness.DisplayCalibration.Point
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class DisplayCalibrationTest {

    // A 0–4095 panel calibrated linearly, as on the owner's CPH2653 (normal maximum 4095).
    private val linear = assertNotNull(
        DisplayCalibration.of(DisplayCalibration.LEVELS.map { Point(it, Math.round(it / 255.0 * 4095).toDouble()) }),
    )

    @Test
    fun interpolatesBetweenPoints() {
        assertEquals(47, linear.levelFor(755.0))
        assertEquals(128, linear.levelFor(2056.0))
    }

    @Test
    fun extrapolatesPastThe255PointWithTheLastSlope() {
        assertEquals(255, linear.levelFor(4095.0))
        assertEquals(256, linear.levelFor(4112.0))
        assertEquals(291, linear.levelFor(4674.0), "the OEM's 4674 boost ceiling")
    }

    @Test
    fun extrapolatesInAabsOperationOrder() {
        val cal = assertNotNull(DisplayCalibration.of(listOf(Point(225, 0.7480639866617081), Point(255, 0.8006997624311302))))
        assertEquals(284, cal.levelFor(0.8524582752710618), "slope first, as _ReadBrightness; multiplying first gives 285")
    }

    @Test
    fun keepsItsOwnCopyOfThePoints() {
        val points = mutableListOf(Point(1, 1.0), Point(255, 2.0))
        val cal = assertNotNull(DisplayCalibration.of(points))
        points.clear()
        assertEquals(255, cal.levelFor(2.0))
    }

    @Test
    fun clampsBelowTheFirstPoint() {
        assertEquals(1, linear.levelFor(0.0))
        assertEquals(1, linear.levelFor(16.0))
    }

    @Test
    fun followsANonLinearPanel() {
        val gamma = assertNotNull(
            DisplayCalibration.of(
                listOf(Point(1, 0.002), Point(128, 0.2), Point(255, 0.8)),
            ),
        )
        assertEquals(192, gamma.levelFor(0.5))
        assertEquals(297, gamma.levelFor(1.0))
    }

    @Test
    fun rejectsInvalidReadings() {
        assertNull(linear.levelFor(-1.0))
        assertNull(linear.levelFor(Double.NaN))
        assertNull(linear.levelFor(Double.POSITIVE_INFINITY))
    }

    @Test
    fun rejectsCalibrationsReadBrightnessWouldReject() {
        assertNull(DisplayCalibration.of(listOf(Point(1, 10.0))))
        assertNull(DisplayCalibration.of(listOf(Point(1, 10.0), Point(5, 10.0))))
        assertNull(DisplayCalibration.of(listOf(Point(1, 10.0), Point(5, 9.0))))
        assertNull(DisplayCalibration.of(listOf(Point(1, 10.0), Point(5, Double.NaN))))
    }

    @Test
    fun encodesAsAabsValueRawList() {
        val cal = assertNotNull(DisplayCalibration.of(listOf(Point(1, 16.0), Point(255, 4095.0))))
        assertEquals("1:16.0,255:4095.0", cal.encode())
        assertEquals(cal.points, DisplayCalibration.decode(cal.encode())?.points)
        assertEquals(linear.points, DisplayCalibration.decode(linear.encode())?.points)
    }

    @Test
    fun decodeRejectsMalformedText() {
        assertNull(DisplayCalibration.decode(""))
        assertNull(DisplayCalibration.decode("error"))
        assertNull(DisplayCalibration.decode("running"))
        assertNull(DisplayCalibration.decode("1:16.0,255"))
        assertNull(DisplayCalibration.decode("1:16.0,255:x"))
    }
}
