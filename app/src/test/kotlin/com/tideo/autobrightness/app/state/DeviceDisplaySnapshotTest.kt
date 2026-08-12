package com.tideo.autobrightness.app.state

import com.tideo.autobrightness.app.settings.AabSettings
import com.tideo.autobrightness.platform.display.DaltonizerMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** DB-034: draft merge rules for the Privileged Display device read-back. */
class DeviceDisplaySnapshotTest {

    private fun snapshot(
        nightLight: Boolean = false,
        temperatureK: Int? = null,
        daltonizer: DaltonizerMode = DaltonizerMode.OFF,
        inversion: Boolean = false,
        alwaysOn: Boolean = false,
        stayAwake: Boolean = false,
        hdrForceSdr: Boolean? = false,
    ) = DeviceDisplaySnapshot(nightLight, temperatureK, daltonizer, inversion, alwaysOn, stayAwake, hdrForceSdr)

    @Test
    fun `device values replace the stored profile fields`() {
        val merged = AabSettings().withDeviceSnapshot(
            snapshot(
                nightLight = true,
                daltonizer = DaltonizerMode.DEUTERANOMALY,
                inversion = true,
                alwaysOn = true,
                stayAwake = true,
                hdrForceSdr = true,
            ),
        )

        assertTrue(merged.nightLightEnabled)
        assertEquals("DEUTERANOMALY", merged.daltonizerMode)
        assertTrue(merged.inversionEnabled)
        assertTrue(merged.alwaysOnDisplayEnabled)
        assertTrue(merged.stayAwakeChargingEnabled)
        assertTrue(merged.hdrForceSdrEnabled)
    }

    @Test
    fun `temperature is taken from the device when circadian is off`() {
        val merged = AabSettings(nightLightTemperature = 3000)
            .withDeviceSnapshot(snapshot(temperatureK = 2700))

        assertEquals(2700, merged.nightLightTemperature)
    }

    @Test
    fun `temperature is left alone while circadian owns the key`() {
        // The coordinator's ticker writes the ramp; reading it back would freeze one sample.
        val merged = AabSettings(nightLightCircadianEnabled = true, nightLightTemperature = 3000)
            .withDeviceSnapshot(snapshot(temperatureK = 2700))

        assertEquals(3000, merged.nightLightTemperature)
        assertTrue(merged.nightLightCircadianEnabled)
    }

    @Test
    fun `an unset device temperature clears the draft to device default`() {
        val merged = AabSettings(nightLightTemperature = 3000)
            .withDeviceSnapshot(snapshot(temperatureK = null))

        assertNull(merged.nightLightTemperature)
    }

    @Test
    fun `circadian flag is never device-sourced`() {
        val merged = AabSettings(nightLightCircadianEnabled = true).withDeviceSnapshot(snapshot())

        assertTrue(merged.nightLightCircadianEnabled)
    }

    @Test
    fun `unavailable HDR leaves the stored field untouched`() {
        val on = AabSettings(hdrForceSdrEnabled = true).withDeviceSnapshot(snapshot(hdrForceSdr = null))
        val off = AabSettings(hdrForceSdrEnabled = false).withDeviceSnapshot(snapshot(hdrForceSdr = null))

        assertTrue(on.hdrForceSdrEnabled)
        assertFalse(off.hdrForceSdrEnabled)
    }

    @Test
    fun `non-display settings survive the merge`() {
        val merged = AabSettings(panicSensitivity = 3, debugLevel = 8, serviceEnabled = true)
            .withDeviceSnapshot(snapshot(nightLight = true))

        assertEquals(3, merged.panicSensitivity)
        assertEquals(8, merged.debugLevel)
        assertTrue(merged.serviceEnabled)
    }
}
