package com.tideo.autobrightness.app.state

import android.app.Application
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.tideo.autobrightness.app.settings.AabSettings
import com.tideo.autobrightness.app.storage.settingsDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import kotlin.test.assertEquals

/**
 * S12.6b (G2R-F9): `debugLevel` is a GLOBAL preference owned by the Live Debug scene — it must NOT be
 * changed by loading a profile (it is not a task626 snapshot key), exactly like `detectOverrides`
 * (G2-F8). Drives the real DataStore-backed VM under Robolectric.
 */
@RunWith(RobolectricTestRunner::class)
class SettingsViewModelTest {

    private val app = ApplicationProvider.getApplicationContext<Application>()

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    private fun committed(): AabSettings = runBlocking { app.settingsDataStore.data.first() }

    private fun awaitCommitted(predicate: (AabSettings) -> Boolean): AabSettings {
        repeat(100) {
            idle()
            val v = committed()
            if (predicate(v)) return v
            Thread.sleep(10)
        }
        return committed()
    }

    @Test
    fun applyProfile_preservesGlobalDebugLevelAndDetectOverrides() {
        runBlocking {
            app.settingsDataStore.updateData {
                AabSettings(serviceEnabled = false, debugLevel = 5, detectOverrides = true, minBrightness = 3)
            }
        }
        idle()

        val vm = SettingsViewModel(app)
        vm.applyProfile("Battery Saver")

        // The profile's curve params apply, but the global debug + override preferences are kept.
        val result = awaitCommitted { it.minBrightness != 3 }
        assertEquals(5, result.debugLevel, "debugLevel is global — a profile load must not change it")
        assertEquals(true, result.detectOverrides, "detectOverrides is global — preserved across profile load")
    }

    @Test
    fun resetDefaults_preservesGlobalDebugLevel() {
        runBlocking {
            app.settingsDataStore.updateData {
                AabSettings(serviceEnabled = false, debugLevel = 7, minBrightness = 42)
            }
        }
        idle()

        val vm = SettingsViewModel(app)
        vm.resetDefaults()

        val result = awaitCommitted { it.minBrightness != 42 }
        assertEquals(7, result.debugLevel, "a per-screen reset must not reset the global debug category")
    }
}
