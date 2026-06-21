package com.tideo.autobrightness.domain.brightness

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Guards the `BrightnessEngine.kt` provenance trail (owner-binding — these `// Tasker: task###` comments
 * are the audit trail from XML line to Kotlin).
 *
 * It used to assert an EXACT `// Tasker` line count, which was weak: it had to be bumped on every
 * legitimate provenance addition (S14 owner critique), and a delete-one/add-one edit kept the count
 * unchanged so a real removal slipped through. This version is **content-based** so honest additions
 * never trip it while a targeted removal does:
 *  - a FLOOR on provenance density (catches wholesale stripping), and
 *  - PRESENCE of the highest-risk semantic anchors — the parity-critical rounding modes and each core
 *    ported task ID — so dropping a specific critical provenance line is caught regardless of the total.
 */
class ProvenanceTest {

    private val engine = File("src/main/kotlin/com/tideo/autobrightness/domain/brightness/BrightnessEngine.kt")
    private val text by lazy { engine.readText() }

    @Test
    fun provenanceFloorIsPresent() {
        assertTrue("expected BrightnessEngine.kt at ${engine.absolutePath}", engine.isFile)
        val count = engine.readLines().count { it.contains("// Tasker") }
        // Floor, not an exact count: adding provenance is always fine; this catches mass stripping only.
        assertTrue(
            "BrightnessEngine.kt has only $count `// Tasker` provenance lines (floor $PROVENANCE_FLOOR) — " +
                "provenance was stripped, not added",
            count >= PROVENANCE_FLOOR,
        )
    }

    @Test
    fun parityCriticalRoundingProvenanceSurvives() {
        // The rounding choices are the parity-critical ones (D-028/D-030 gaps); each must stay documented
        // in the engine so a future edit can't silently change WHY it rounds the way it does.
        listOf("Math.round", "HALF_UP", "round3").forEach { anchor ->
            assertTrue("BrightnessEngine.kt must keep the `$anchor` rounding-mode provenance", text.contains(anchor))
        }
    }

    @Test
    fun coreTaskReferencesSurvive() {
        // Each core pipeline task ported into the engine must keep a reference; deleting one would erase
        // the XML→Kotlin link for that piece of logic.
        listOf("task535", "task543", "task544", "task546", "task548", "task661").forEach { task ->
            assertTrue("BrightnessEngine.kt lost the provenance reference to $task", text.contains(task))
        }
    }

    private companion object {
        // A floor, deliberately below the current density so legitimate additions never trip it.
        const val PROVENANCE_FLOOR = 13
    }
}
