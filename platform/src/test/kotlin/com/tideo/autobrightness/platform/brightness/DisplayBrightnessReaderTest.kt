package com.tideo.autobrightness.platform.brightness

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DisplayBrightnessReaderTest {

    // The owner's CPH2653 (unit 0, 2026-10-16): no mDisplayBrightness line.
    private val oneplus = """
        mScreenBrightnessNormalMaximum=4095.0
        mScreenBrightnessRangeMaximum=4674.0
          mScreenBrightness=1390.0
          mCurrentScreenBrightness=1390.0
    """.trimIndent()

    // The #157 POCO F4's ceiling lines (DE-027), as its dump prints them.
    private val f4Ceiling = """
          mCachedBrightnessInfo.brightnessMax=0.49975574
          mCachedBrightnessInfo.hbmTransitionPoint=0.49975574
          mCachedBrightnessInfo.brightnessMaxReason =0
          mAppliedLowPower=true
          mHbmData=HBM{minLux: 6001.0, transition: 0.49975574, timeWindow: 0ms}
    """.trimIndent()

    private fun source(label: String, block: () -> DumpOutput) = object : DisplayDumpSource {
        override val name = label
        override suspend fun dump() = block()
    }

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
    fun parseNeverReadsTheCeilingLines() {
        assertNull(DisplayBrightnessDump.parse(f4Ceiling, null))
    }

    @Test
    fun filterKeepsOnlyBrightnessLines() {
        val dump = "Display Power Controller:\n$oneplus\n  mHbmMode=off\n"
        assertEquals("  mScreenBrightness=1390.0\n  mCurrentScreenBrightness=1390.0", DisplayBrightnessDump.filter(dump.byteInputStream()))
    }

    @Test
    fun filterKeepsTheCeilingLines_andCorroborationReturnsOnlyThem() {
        val dump = "Display Power Controller:\n$f4Ceiling\n  mHbmMode=off\n  mScreenBrightness=9.7704E-4\n"
        val kept = DisplayBrightnessDump.filter(dump.byteInputStream())
        assertEquals(f4Ceiling.lines() + "  mScreenBrightness=9.7704E-4", kept.lines())
        assertEquals(f4Ceiling.lines().map { it.trim() }, DisplayBrightnessDump.corroboration(kept))
        assertEquals(DisplayBrightnessSample("mScreenBrightness", 9.7704E-4), DisplayBrightnessDump.parse(kept, null))
    }

    @Test
    fun theF4sCeilingLines_verifyItsTransitionPoint_withTheSpacedMaxReason() {
        val lines = DisplayBrightnessDump.corroboration(f4Ceiling)
        assertTrue(DisplayBrightnessDump.isHbmBoundary(lines, 0.49975574))
        assertFalse("battery saver's 0.9 plateau (DE-027)", DisplayBrightnessDump.isHbmBoundary(lines, 0.44978))
    }

    @Test
    fun aBoundaryNeedsAPrintedTransition_andNoOtherCapThatIsPrinted() {
        fun verified(vararg lines: String) = DisplayBrightnessDump.isHbmBoundary(lines.toList(), 0.63)
        assertTrue(verified("mHbmData=HBM{minLux: 10000.0, transition: 0.63, timeWindow: 0ms}"))
        assertTrue(verified("mCachedBrightnessInfo.hbmTransitionPoint=0.63", "mCachedBrightnessInfo.brightnessMax=0.63"))
        assertFalse(verified("mAppliedLowPower=false"))
        assertFalse(verified("mCachedBrightnessInfo.brightnessMax=0.63"))
        assertFalse(verified("mCachedBrightnessInfo.hbmTransitionPoint=Infinity", "mCachedBrightnessInfo.brightnessMax=0.63"))
        assertFalse(verified("mCachedBrightnessInfo.hbmTransitionPoint=0.63", "mHbmData=HBM{minLux: 10000.0, transition: 0.7, timeWindow: 0ms}"))
        assertFalse(verified("hbmTransitionPoint=0.63 mHbmData=HBM{minLux: 10000.0, transition: 0.7, timeWindow: 0ms}"))
        assertFalse(verified("mCachedBrightnessInfo.hbmTransitionPoint=0.63", "mCachedBrightnessInfo.brightnessMax=0.5"))
        assertFalse(verified("mCachedBrightnessInfo.hbmTransitionPoint=0.63", "mCachedBrightnessInfo.brightnessMaxReason =1"))
        assertFalse(verified("mCachedBrightnessInfo.hbmTransitionPoint=0.63", "mCachedBrightnessInfo.brightnessMaxReason=THERMAL"))
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
    fun ceilingLinesHaveTheirOwnCap_andNeverCrowdOutBrightnessLines() {
        val ceiling = List(20) { "  mAppliedLowPower=false" }
        val brightness = List(40) { "  mScreenBrightness=$it.0" }
        val kept = DisplayBrightnessDump.filter((ceiling + brightness).joinToString("\n").byteInputStream()).lines()
        assertEquals(8, kept.count { it.contains("mAppliedLowPower") })
        assertEquals(32, kept.count { it.contains("mScreenBrightness") })
    }

    @Test
    fun aBrightnessLinePastTheCapNeverEntersAsACeilingLine() {
        val dump = (List(32) { "  mDisplayBrightness=$it.0" } + "  mHbmData={mScreenBrightness=0.5}").joinToString("\n")
        val kept = DisplayBrightnessDump.filter(dump.byteInputStream())
        assertNull(DisplayBrightnessDump.parse(kept, "mScreenBrightness"))
        assertEquals(emptyList<String>(), DisplayBrightnessDump.corroboration(kept))
    }

    @Test
    fun readerFallsThroughAndRemembersTheSourceThatAnswered() = runBlocking {
        val calls = mutableListOf<String>()
        val reader = DumpsysDisplayBrightnessReader(
            listOf(
                source("dump") { calls += "dump"; DumpOutput.Unavailable },
                source("root") { calls += "root"; DumpOutput.Lines("mHbmMode=off") },
                source("shizuku") { calls += "shizuku"; DumpOutput.Lines(oneplus) },
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
        val reader = DumpsysDisplayBrightnessReader(listOf(source("a") { DumpOutput.Failed }, source("b") { error("boom") }))
        assertNull(reader.read())
    }

    @Test
    fun traceReportsEachSourcesStatusAndElapsedTime() = runBlocking {
        var now = 0L
        val reader = DumpsysDisplayBrightnessReader(
            listOf(
                source("DUMP") { now += 5_000; DumpOutput.Failed },
                source("root") { DumpOutput.Unavailable },
                source("Shizuku") { now += 40; DumpOutput.Lines("mHbmMode=off") },
                source("other") { now += 7; DumpOutput.Lines("$f4Ceiling\n  mScreenBrightness=0.5") },
            ),
            nowMs = { now },
        )
        val read = reader.trace()
        assertEquals(
            listOf(
                DumpAttempt("DUMP", DumpStatus.FAILED, null, 5_000),
                DumpAttempt("root", DumpStatus.UNAVAILABLE, null, 0),
                DumpAttempt("Shizuku", DumpStatus.NO_FIELD, null, 40),
                DumpAttempt("other", DumpStatus.SAMPLE, DisplayBrightnessSample("mScreenBrightness", 0.5), 7),
            ),
            read.attempts,
        )
        assertEquals(DisplayBrightnessSample("mScreenBrightness", 0.5), read.sample)
        assertEquals(f4Ceiling.lines().map { it.trim() }, read.corroboration)
    }

    @Test
    fun aThrowingSourceIsReportedAsFailed() = runBlocking {
        val reader = DumpsysDisplayBrightnessReader(listOf(source("root") { error("boom") }), nowMs = { 0L })
        assertEquals(listOf(DumpAttempt("root", DumpStatus.FAILED, null, 0)), reader.trace().attempts)
    }
}
