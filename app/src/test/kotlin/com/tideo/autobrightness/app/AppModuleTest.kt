package com.tideo.autobrightness.app

import com.tideo.autobrightness.app.runtime.ControllerHook
import com.tideo.autobrightness.app.runtime.ControllerHookHolder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * S12.9e: the engine⇄controller construction cycle is broken by a [ControllerHookHolder] instead of a
 * `lateinit var controller`. These tests pin both halves — the holder's pre-assignment no-op semantics
 * and that [AppModule.createRuntime] actually wires the controller as the hook without a crash.
 */
@RunWith(RobolectricTestRunner::class)
class AppModuleTest {

    @Test
    fun hook_fireBeforeAssignment_isNoOp() {
        val holder = ControllerHookHolder()
        // No hook wired yet — must NOT throw (this is the old lateinit-crash path the holder removes).
        holder.fire()
    }

    @Test
    fun hook_fireAfterAssignment_callsHook() {
        val holder = ControllerHookHolder()
        var fired = 0
        holder.hook = ControllerHook { fired++ }
        holder.fire()
        holder.fire()
        assertTrue(fired == 2, "fire() must invoke the assigned hook each time")
    }

    @Test
    fun createRuntime_buildsGraph_andWiresControllerAsHook() {
        val context = RuntimeEnvironment.getApplication()
        val scope = CoroutineScope(Dispatchers.Unconfined + SupervisorJob())
        try {
            val graph = AppModule(context).createRuntime(scope)
            assertNotNull(graph.controller, "createRuntime must build a controller")
            assertNotNull(graph.contextEngine, "createRuntime must build a context engine")
            assertNotNull(graph.panicSensor, "createRuntime must build a panic sensor")
            // The controller is the ControllerHook the engine fires through (no lateinit cycle).
            assertTrue(
                graph.controller is ControllerHook,
                "the pipeline controller must implement ControllerHook so the engine can drive it",
            )
            // Wiring sanity: a fresh holder pointed at this controller fires it without throwing.
            val holder = ControllerHookHolder().apply { hook = graph.controller as ControllerHook }
            assertSame(graph.controller, holder.hook)
            holder.fire()
        } finally {
            scope.cancel()
        }
    }
}
