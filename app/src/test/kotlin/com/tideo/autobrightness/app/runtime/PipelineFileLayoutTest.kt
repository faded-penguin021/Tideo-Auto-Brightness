package com.tideo.autobrightness.app.runtime

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Guards the S12.9e decomposition of the once-596-LOC `BrightnessPipelineController`: the orchestrator
 * must stay an orchestrator (≤ [ORCHESTRATOR_MAX_LOC]), the cycle work / debug surface / panic effect
 * must remain in their own files, and none of the four may re-bloat back toward the monolith.
 */
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
        const val ORCHESTRATOR_MAX_LOC = 300
    }
}
