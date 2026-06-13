package com.tideo.autobrightness.app.runtime

import org.junit.Test
import kotlin.test.assertNotNull

class BrightnessTileServiceTest {

    // Robolectric cannot bind a real QS tile (ServiceController casts the TileService binder),
    // so per the S9b brief this is downgraded to an instantiation-only smoke test: the tile
    // service must construct without throwing.
    @Test
    fun instantiates() {
        assertNotNull(BrightnessTileService())
    }
}
