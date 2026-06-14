package com.tideo.autobrightness.app.runtime

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class BrightnessTileServiceTest {

    // Robolectric cannot bind a real QS tile (ServiceController casts the TileService binder),
    // so per the S9b brief this is downgraded to an instantiation-only smoke test: the tile
    // service must construct without throwing.
    @Test
    fun instantiates() {
        assertNotNull(BrightnessTileService())
    }

    // S12.7b/G2R-F63: the live (enabled, running, paused) → subtitle mapping the tile renders on
    // every state change. Tested as a pure function since the tile itself cannot be bound.
    @Test
    fun tileSubtitle_mapsLiveState() {
        assertEquals("Off", BrightnessTileService.tileSubtitle(enabled = false, running = false, paused = false))
        assertEquals("Off", BrightnessTileService.tileSubtitle(enabled = false, running = true, paused = true))
        assertEquals("Starting…", BrightnessTileService.tileSubtitle(enabled = true, running = false, paused = false))
        assertEquals("Active", BrightnessTileService.tileSubtitle(enabled = true, running = true, paused = false))
        assertEquals("Paused", BrightnessTileService.tileSubtitle(enabled = true, running = true, paused = true))
    }
}
