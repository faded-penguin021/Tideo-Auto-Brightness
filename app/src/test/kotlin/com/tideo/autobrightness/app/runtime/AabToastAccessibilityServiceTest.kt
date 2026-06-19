package com.tideo.autobrightness.app.runtime

import android.os.Looper
import org.junit.After
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * S12.9d backfill — the opt-in [AabToastAccessibilityService] (owner-intentional, G2R-F50). Tests its
 * BEHAVIOUR: on connect it becomes the process-wide [AabFlash] global presenter (so flashes draw
 * system-wide), and on unbind it relinquishes it (so the app degrades back to the foreground toast).
 */
@RunWith(RobolectricTestRunner::class)
class AabToastAccessibilityServiceTest {

    // AabFlash is a process singleton; clear the presenter so state does not leak between tests.
    @After fun tearDown() = AabFlash.register(null)

    private fun connectedService(): AabToastAccessibilityService {
        val service = Robolectric.buildService(AabToastAccessibilityService::class.java).create().get()
        // onServiceConnected() is protected (the framework calls it); invoke it reflectively to
        // simulate the system binding the accessibility service.
        AabToastAccessibilityService::class.java.getDeclaredMethod("onServiceConnected")
            .apply { isAccessible = true }
            .invoke(service)
        return service
    }

    @Test
    fun connect_registersGlobalPresenter() {
        AabFlash.register(null)
        assertFalse(AabFlash.isGlobal(), "no global presenter before connect")
        connectedService()
        assertTrue(AabFlash.isGlobal(), "connect makes the service the global flash presenter")
    }

    @Test
    fun unbind_relinquishesGlobalPresenter() {
        val service = connectedService()
        service.onUnbind(null)
        assertFalse(AabFlash.isGlobal(), "unbind degrades back to the foreground surface")
    }

    @Test
    fun show_routesThroughOverlayPresenterWithoutCrashing() {
        connectedService()
        AabFlash.show(RuntimeEnvironment.getApplication(), "hello")
        shadowOf(Looper.getMainLooper()).idle() // run the handler-posted overlay show
        assertTrue(AabFlash.isGlobal())
    }
}
