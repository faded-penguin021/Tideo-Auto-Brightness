package com.tideo.autobrightness.domain.brightness

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Guards the S12.9e comment-only change to `BrightnessEngine.kt`: a consolidated rounding-mode header
 * was added, but EVERY `// Tasker: task###` provenance comment must survive (owner-binding — these are
 * the audit trail from XML line to Kotlin). The exact count is frozen so a future edit that drops or
 * silently rewrites a provenance line trips this test.
 */
class ProvenanceCommentCountTest {

    private val engine = File("src/main/kotlin/com/tideo/autobrightness/domain/brightness/BrightnessEngine.kt")

    @Test
    fun provenanceCommentCountIsUnchanged() {
        assertTrue("expected BrightnessEngine.kt at ${engine.absolutePath}", engine.isFile)
        val count = engine.readLines().count { it.contains("// Tasker") }
        assertEquals(
            "BrightnessEngine.kt provenance (`// Tasker`) comment count changed — restore the dropped " +
                "provenance line(s); the S12.9e header is comment-only and must not touch them",
            EXPECTED_PROVENANCE_COMMENTS,
            count,
        )
    }

    private companion object {
        // Frozen at the S12.9e header-consolidation commit (the header itself contains no `// Tasker`).
        const val EXPECTED_PROVENANCE_COMMENTS = 13
    }
}
