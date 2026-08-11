package com.tideo.autobrightness.app.runtime

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class BrightnessTileServiceTest {

    @Test
    fun instantiates() {
        assertNotNull(BrightnessTileService())
    }

    // S12.7b/G2R-F63: state → subtitle mapping (pure function; tile cannot be bound).
    @Test
    fun tileSubtitle_mapsLiveState() {
        assertEquals("Off", BrightnessTileService.tileSubtitle(enabled = false, running = false, paused = false))
        assertEquals("Off", BrightnessTileService.tileSubtitle(enabled = false, running = true, paused = true))
        assertEquals("Starting…", BrightnessTileService.tileSubtitle(enabled = true, running = false, paused = false))
        assertEquals("Active", BrightnessTileService.tileSubtitle(enabled = true, running = true, paused = false))
        assertEquals("Paused", BrightnessTileService.tileSubtitle(enabled = true, running = true, paused = true))
    }
}
