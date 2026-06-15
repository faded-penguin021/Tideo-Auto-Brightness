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

    // G2R-F70: a legacy "Load" must COMMIT the parsed settings into the active DataStore and trigger a
    // reapply (exactly like a profile apply) — not merely register the profile by name. Drives the same
    // SettingsViewModel.replaceAll() the Profiles screen calls with the deserialized legacy config.
    @Test
    fun replaceAll_commitsParsedLegacyValues_andTriggersReapply() {
        runBlocking {
            app.settingsDataStore.updateData {
                AabSettings(serviceEnabled = true, minBrightness = 99, maxBrightness = 255, scale = 1.0f)
            }
        }
        idle()

        val parsed = com.tideo.autobrightness.app.settings.TaskerLegacyProfileSerializer.deserialize(
            """{ "misc": { "min_bright": 12.0, "max_bright": 180.0, "scale": 0.7 } }""",
        )

        val vm = SettingsViewModel(app)
        vm.replaceAll(parsed)

        // The parsed values land in the active settings…
        val result = awaitCommitted { it.minBrightness == 12 }
        assertEquals(12, result.minBrightness, "legacy load must commit the parsed min brightness")
        assertEquals(180, result.maxBrightness)
        assertEquals(0.7f, result.scale, 0.001f)
        assertEquals(true, result.serviceEnabled, "the live service flag is preserved across a load")

        // …and a re-evaluate is requested (service running), so the change takes effect immediately.
        idle()
        val started = shadowOf(app).nextStartedService
        assertEquals(
            com.tideo.autobrightness.app.runtime.AmbientMonitoringService.ACTION_REAPPLY,
            started?.action,
            "a legacy load with the service on must trigger an immediate reapply",
        )
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
