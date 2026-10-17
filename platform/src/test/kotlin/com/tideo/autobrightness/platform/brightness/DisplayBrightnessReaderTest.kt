package com.tideo.autobrightness.platform.brightness

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DisplayBrightnessReaderTest {

    // The owner's CPH2653 (unit 0, 2026-10-16): no mDisplayBrightness line.
    private val oneplus = """
        mScreenBrightnessNormalMaximum=4095.0
        mScreenBrightnessRangeMaximum=4674.0
          mScreenBrightness=1390.0
          mCurrentScreenBrightness=1390.0
    """.trimIndent()

    @Test
    fun parseTakesTheFirstOfAabsThreeFields() {
        assertEquals(DisplayBrightnessSample("mScreenBrightness", 1390.0), DisplayBrightnessDump.parse(oneplus, null))
    }

    @Test
    fun parseNeverReadsTheRangeFields() {
        assertNull(DisplayBrightnessDump.parse("mScreenBrightnessNormalMaximum=4095.0\nmScreenBrightnessRangeMaximum=4674.0", null))
        assertNull(DisplayBrightnessDump.parse("mLastScreenBrightness=12.0", null))
    }

    @Test
    fun parseHonoursALockedField() {
        assertEquals(
            DisplayBrightnessSample("mCurrentScreenBrightness", 1390.0),
            DisplayBrightnessDump.parse(oneplus, "mCurrentScreenBrightness"),
        )
        assertNull(DisplayBrightnessDump.parse(oneplus, "mDisplayBrightness"))
    }

    @Test
    fun parseReadsExponentFloats() {
        assertEquals(DisplayBrightnessSample("mDisplayBrightness", 4.0E-4), DisplayBrightnessDump.parse("mDisplayBrightness=4.0E-4", null))
    }

    @Test
    fun filterKeepsOnlyBrightnessLines() {
        val dump = "Display Power Controller:\n$oneplus\n  mHbmMode=off\n"
        assertEquals("  mScreenBrightness=1390.0\n  mCurrentScreenBrightness=1390.0", DisplayBrightnessDump.filter(dump.byteInputStream()))
    }

    @Test
    fun filterBoundsEachLineAndDrainsPastTheCap() {
        val huge = "x".repeat(100_000) + " mScreenBrightness=1.0"
        val dump = (listOf(huge) + List(40) { "  mScreenBrightness=$it.0" }).joinToString("\n")
        val kept = DisplayBrightnessDump.filter(dump.byteInputStream()).lines()
        assertEquals(32, kept.size)
        assertEquals("  mScreenBrightness=0.0", kept.first())
    }

    @Test
    fun readerFallsThroughAndRemembersTheSourceThatAnswered() = runBlocking {
        val calls = mutableListOf<String>()
        val reader = DumpsysDisplayBrightnessReader(
            listOf(
                DisplayDumpSource { calls += "dump"; null },
                DisplayDumpSource { calls += "root"; "mHbmMode=off" },
                DisplayDumpSource { calls += "shizuku"; oneplus },
            ),
        )
        assertEquals(1390.0, reader.read()?.raw)
        assertEquals(listOf("dump", "root", "shizuku"), calls)
        calls.clear()
        assertEquals(1390.0, reader.read()?.raw)
        assertEquals(listOf("shizuku"), calls)
    }

    @Test
    fun readerReturnsNullWhenNoSourceAnswers() = runBlocking {
        val reader = DumpsysDisplayBrightnessReader(listOf(DisplayDumpSource { null }, DisplayDumpSource { error("boom") }))
        assertNull(reader.read())
    }
}
