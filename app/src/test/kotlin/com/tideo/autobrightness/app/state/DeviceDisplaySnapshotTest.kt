package com.tideo.autobrightness.app.state

import com.tideo.autobrightness.app.settings.AabSettings
import com.tideo.autobrightness.platform.display.DaltonizerMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
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

    // --- DB-039: the re-merge policy. The bug was gating on "draft differs from profile", which the
    // merge itself causes — it fired once and then refused every later device change.

    @Test
    fun `a second external change still reaches the draft`() {
        // The reported sequence: profile has Night Light off, the device is externally on, then the
        // system tile switches it off again while we are backgrounded.
        val profile = AabSettings(nightLightEnabled = false)

        val first = assertNotNull(
            readBackDraft(profile, profile, null, snapshot(nightLight = true)),
            "the first read-back must show the device",
        )
        assertTrue(first.nightLightEnabled)

        // first != profile now — under the old !dirty gate this is where tracking died.
        val second = assertNotNull(
            readBackDraft(first, profile, first, snapshot(nightLight = false)),
            "a device change after the first read-back must still reach the draft",
        )
        assertFalse(second.nightLightEnabled, "the screen must follow the device, not freeze")
    }

    @Test
    fun `a user edit blocks the merge`() {
        val profile = AabSettings(nightLightEnabled = false)
        val merged = assertNotNull(readBackDraft(profile, profile, null, snapshot(nightLight = true)))
        val edited = merged.copy(inversionEnabled = true) // the user touched something

        assertNull(
            readBackDraft(edited, profile, merged, snapshot(nightLight = false)),
            "uncommitted edits must never be clobbered",
        )
    }

    @Test
    fun `discarding resumes tracking`() {
        val profile = AabSettings(nightLightEnabled = false)
        val merged = assertNotNull(readBackDraft(profile, profile, null, snapshot(nightLight = true)))
        val edited = merged.copy(inversionEnabled = true)
        assertNull(readBackDraft(edited, profile, merged, snapshot(nightLight = true)))

        // Discard sets draft back to the committed profile.
        assertNotNull(
            readBackDraft(profile, profile, merged, snapshot(nightLight = true)),
            "after Discard the draft is the profile again, so tracking must resume",
        )
    }

    @Test
    fun `a snapshot the draft already matches changes nothing`() {
        val profile = AabSettings(nightLightEnabled = true)

        assertNull(
            readBackDraft(profile, profile, null, snapshot(nightLight = true)),
            "no edit, no recomposition, when the device already agrees",
        )
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
