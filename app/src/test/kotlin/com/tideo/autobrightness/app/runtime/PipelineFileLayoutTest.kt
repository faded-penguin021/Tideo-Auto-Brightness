package com.tideo.autobrightness.app.runtime

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Guard S12.9e decomposition (596-LOC monolith split): orchestrator ≤ ORCHESTRATOR_MAX_LOC, no re-bloat. */
class PipelineFileLayoutTest {

    private val runtimeDir = File("src/main/kotlin/com/tideo/autobrightness/app/runtime")

    private fun lines(name: String): Int {
        val f = File(runtimeDir, name)
        assertTrue("expected $name at ${f.absolutePath}", f.isFile)
        return f.readLines().size
    }

    @Test
    fun controllerSplitIntoFourFiles() {
        listOf(
            "BrightnessPipelineController.kt",
            "PipelineCycleRunner.kt",
            "PipelineDebugEmitter.kt",
            "PanicHandler.kt",
        ).forEach { name ->
            assertTrue("$name must exist after the S12.9e split", File(runtimeDir, name).isFile)
        }
    }

    @Test
    fun orchestratorStaysSmall() {
        val loc = lines("BrightnessPipelineController.kt")
        assertTrue(
            "BrightnessPipelineController.kt is $loc LOC — keep the orchestrator ≤ $ORCHESTRATOR_MAX_LOC " +
                "(push cycle/debug/panic work into PipelineCycleRunner/PipelineDebugEmitter/PanicHandler)",
            loc <= ORCHESTRATOR_MAX_LOC,
        )
    }

    @Test
    fun extractedFilesDoNotReBloat() {
        // Ceilings sized just above the current footprint so a meaningful re-bloat trips the guard.
        mapOf(
            "PipelineCycleRunner.kt" to 420,
            "PipelineDebugEmitter.kt" to 90,
            "PanicHandler.kt" to 70,
        ).forEach { (name, cap) ->
            val loc = lines(name)
            assertTrue("$name is $loc LOC — exceeds the $cap re-bloat ceiling", loc <= cap)
        }
    }

    private companion object {
        // Guard catches RE-BLOAT (cycle/debug/panic creeping back), not new feature wiring. D-139: +4 wiring lines.
        const val ORCHESTRATOR_MAX_LOC = 315
    }
}
