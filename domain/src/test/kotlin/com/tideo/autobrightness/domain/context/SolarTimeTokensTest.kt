package com.tideo.autobrightness.domain.context

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SolarTimeTokensTest {

    @Test
    fun eventOf_matchesByTrimmedPrefix_sunriseFirst() {
        assertEquals("SUNRISE", SolarTimeTokens.eventOf("SUNRISE"))
        assertEquals("SUNRISE", SolarTimeTokens.eventOf("  SUNRISE+30 "))
        assertEquals("SUNSET", SolarTimeTokens.eventOf("SUNSET-15"))
        assertEquals("SUNRISE", SolarTimeTokens.eventOf("SUNRISEx"))
        assertNull(SolarTimeTokens.eventOf("sunrise"))
        assertNull(SolarTimeTokens.eventOf("06:00"))
        assertNull(SolarTimeTokens.eventOf("X SUNSET"))
    }

    @Test
    fun offsetMinutes_keepsTheEvaluatorsParse() {
        assertEquals(30L, SolarTimeTokens.offsetMinutes("SUNRISE+30"))
        assertEquals(-15L, SolarTimeTokens.offsetMinutes("SUNSET-15"))
        assertEquals(30L, SolarTimeTokens.offsetMinutes("SUNRISE + 30"))
        assertEquals(-15L, SolarTimeTokens.offsetMinutes("SUNSET - 15 "))
        assertEquals(0L, SolarTimeTokens.offsetMinutes("SUNRISE+abc"))
        assertEquals(0L, SolarTimeTokens.offsetMinutes("SUNSET-"))
        assertEquals(5L, SolarTimeTokens.offsetMinutes("SUNRISE++5"))
        assertEquals(5L, SolarTimeTokens.offsetMinutes("SUNRISE--5"))
        assertEquals(0L, SolarTimeTokens.offsetMinutes("SUNRISEx"))
        assertEquals(0L, SolarTimeTokens.offsetMinutes("SUNRISE"))
        assertEquals(10L, SolarTimeTokens.offsetMinutes("SUNSET-+10"))
        assertEquals(0L, SolarTimeTokens.offsetMinutes("SUNRISE+9223372036854775808"))
    }

    @Test
    fun resolve_floorsToTheMinute() {
        assertEquals(6 * 3600L, SolarTimeTokens.resolve(21_659L, 0))
        assertEquals(6 * 3600L + 30 * 60, SolarTimeTokens.resolve(21_659L, 30))
        assertEquals(18 * 3600L, SolarTimeTokens.resolve(64_800L, 0))
    }

    @Test
    fun resolve_wrapsAcrossMidnightBothWays() {
        assertEquals(30 * 60L, SolarTimeTokens.resolve(86_399L, 31))
        assertEquals(23 * 3600L + 30 * 60, SolarTimeTokens.resolve(5 * 60L + 10, -35))
        assertEquals(6 * 3600L, SolarTimeTokens.resolve(6 * 3600L, 1440))
        assertEquals(6 * 3600L, SolarTimeTokens.resolve(6 * 3600L, -2880))
    }

    @Test
    fun resolveTimeToken_appliesOffsetAndFloorsPlainTokens() {
        val signals = ContextSignals(sunriseLocalSecs = 21_659L, sunsetLocalSecs = 64_830L)
        assertEquals(6 * 3600L, ContextMatching.resolveTimeToken("SUNRISE", signals))
        assertEquals(18 * 3600L, ContextMatching.resolveTimeToken("SUNSET", signals))
        assertEquals(6 * 3600L + 30 * 60, ContextMatching.resolveTimeToken(" SUNRISE+30", signals))
        assertEquals(17 * 3600L + 45 * 60, ContextMatching.resolveTimeToken("SUNSET-15", signals))
        assertEquals(9 * 3600L + 5 * 60, ContextMatching.resolveTimeToken(" 09 : 05 ", signals))
    }

    @Test
    fun editorOffsetText_seedsFromTheToken() {
        assertEquals("+30", SolarTimeTokens.editorOffsetText("SUNRISE+30"))
        assertEquals("-15", SolarTimeTokens.editorOffsetText("SUNSET-15"))
        assertEquals("+30", SolarTimeTokens.editorOffsetText(" SUNRISE+ 30 "))
        assertEquals("", SolarTimeTokens.editorOffsetText("SUNRISE"))
        assertEquals("--5", SolarTimeTokens.editorOffsetText("SUNRISE--5"))
    }

    @Test
    fun commit_buildsTheSavedToken() {
        assertEquals("SUNRISE", SolarTimeTokens.commit("SUNRISE", ""))
        assertEquals("SUNRISE", SolarTimeTokens.commit("SUNRISE", "  "))
        assertEquals("SUNRISE", SolarTimeTokens.commit("SUNRISE", "0"))
        assertEquals("SUNSET", SolarTimeTokens.commit("SUNSET", "-0"))
        assertEquals("SUNRISE+30", SolarTimeTokens.commit("SUNRISE", "+30"))
        assertEquals("SUNRISE+30", SolarTimeTokens.commit("SUNRISE", " 30 "))
        assertEquals("SUNRISE+7", SolarTimeTokens.commit("SUNRISE", "+007"))
        assertEquals("SUNSET-15", SolarTimeTokens.commit("SUNSET", "-15"))
        assertNull(SolarTimeTokens.commit("SUNRISE", "abc"))
        assertNull(SolarTimeTokens.commit("SUNRISE", "+-5"))
        assertNull(SolarTimeTokens.commit("SUNRISE", "1.5"))
        assertNull(SolarTimeTokens.commit("SUNRISE", "9223372036854775808"))
    }

    @Test
    fun commitAndEditorOffsetText_roundTrip() {
        listOf("+30", "-15", "+1440", "-1").forEach { text ->
            val token = SolarTimeTokens.commit("SUNSET", text)!!
            assertEquals(text, SolarTimeTokens.editorOffsetText(token))
            assertEquals(text.toLong(), SolarTimeTokens.offsetMinutes(token))
        }
    }
}
