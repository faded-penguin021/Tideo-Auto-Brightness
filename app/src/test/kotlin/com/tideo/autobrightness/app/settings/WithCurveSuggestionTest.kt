package com.tideo.autobrightness.app.settings

import kotlin.test.Test
import kotlin.test.assertEquals

class WithCurveSuggestionTest {

    @Test
    fun `form1A lands exactly, the Int fields round and nothing else moves`() {
        val base = AabSettings(maxBrightness = 200, offset = 3)
        val fit = base.toBrightnessCurveConfig().copy(
            form1A = 4.123456789, zone1End = 41.5, form2B = 7.25, form2C = 12.49, zone2End = 2_999.6,
        )
        val applied = base.withCurveSuggestion(fit)
        assertEquals(
            base.copy(form1A = 4.123456789, zone1End = 42, form2B = 7.25f, form2C = 12, zone2End = 3_000),
            applied,
        )
    }
}
