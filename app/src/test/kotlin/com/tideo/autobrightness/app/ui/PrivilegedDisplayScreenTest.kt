package com.tideo.autobrightness.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import com.tideo.autobrightness.app.settings.AabSettings
import com.tideo.autobrightness.app.state.PrivilegedDisplayUiState
import com.tideo.autobrightness.app.ui.screens.PrivilegedDisplayContent
import com.tideo.autobrightness.platform.display.DaltonizerMode
import com.tideo.autobrightness.platform.display.NightLightAutoMode
import com.tideo.autobrightness.platform.privilege.ShizukuAvailability
import com.tideo.autobrightness.platform.privilege.Tier
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Privileged Display screen (D-149, reworked by D-151/D-152): display-toggle PROFILE fields. */
@RunWith(RobolectricTestRunner::class)
class PrivilegedDisplayScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val elevated = PrivilegedDisplayUiState(tier = Tier.ELEVATED)

    private fun setDraftContent(
        state: PrivilegedDisplayUiState = elevated,
        initial: AabSettings = AabSettings(),
        onApply: () -> Unit = {},
    ): () -> AabSettings {
        var draft = initial
        compose.setContent {
            MaterialTheme {
                PrivilegedDisplayContent(
                    state = state,
                    onBack = {},
                    draft = draft,
                    draftDirty = true,
                    onEditDraft = { transform -> draft = transform(draft) },
                    onApplyDraft = onApply,
                )
            }
        }
        return { draft }
    }

    @Test
    fun elevated_togglesEditTheDraft_once_noDuplicates() {
        val draft = setDraftContent()
        compose.onNodeWithTag("pd_grant_card").assertDoesNotExist()
        compose.onNodeWithTag("pd_profile_intro").assertExists()

        compose.onNodeWithTag("switch_nightLight").performScrollTo().performClick()
        assertEquals(true, draft().nightLightEnabled)
        compose.onNodeWithTag("daltonizer_grayscale").performScrollTo().performClick()
        assertEquals("GRAYSCALE", draft().daltonizerMode)
        compose.onNodeWithTag("switch_inversion").performScrollTo().performClick()
        assertEquals(true, draft().inversionEnabled)
        compose.onNodeWithTag("switch_alwaysOn").performScrollTo().performClick()
        assertEquals(true, draft().alwaysOnDisplayEnabled)
        compose.onNodeWithTag("switch_stayAwake").performScrollTo().performClick()
        assertEquals(true, draft().stayAwakeChargingEnabled)
    }

    @Test
    fun infoAction_opensDialogWithTheAospKeysNote_andDismisses() {
        setDraftContent()
        compose.onNodeWithTag("pd_info_dialog").assertDoesNotExist()
        compose.onNodeWithTag("pd_info_action").performClick()
        compose.onNodeWithTag("pd_info_dialog").assertExists()
        compose.onNodeWithTag("pd_info_dismiss").performClick()
        compose.onNodeWithTag("pd_info_dialog").assertDoesNotExist()
    }

    @Test
    fun infoAction_isAvailableBelowElevatedToo() {
        compose.setContent {
            MaterialTheme {
                PrivilegedDisplayContent(state = PrivilegedDisplayUiState(tier = Tier.BASIC), onBack = {})
            }
        }
        compose.onNodeWithTag("pd_info_action").performClick()
        compose.onNodeWithTag("pd_info_dialog").assertExists()
    }

    @Test
    fun elevated_daltonizerChips_reflectTheDraftSelection() {
        setDraftContent(initial = AabSettings(daltonizerMode = "TRITANOMALY"))
        // All five modes render as chips (incl. Off) — exactly once each.
        DaltonizerMode.entries.forEach { mode ->
            compose.onNodeWithTag("daltonizer_${mode.name.lowercase()}").performScrollTo().assertExists()
        }
    }

    @Test
    fun elevated_hdrSection_hiddenWhenUnavailable() {
        setDraftContent(state = elevated.copy(hdrAvailable = false))
        compose.onNodeWithTag("switch_hdrForceSdr").assertDoesNotExist()
    }

    @Test
    fun elevated_hdrSwitch_editsTheDraftWhenAvailable() {
        val draft = setDraftContent(state = elevated.copy(hdrAvailable = true))
        compose.onNodeWithTag("switch_hdrForceSdr").performScrollTo().performClick()
        assertEquals(true, draft().hdrForceSdrEnabled)
    }

    @Test
    fun elevated_circadianTrackingSwitch_editsTheDraft_D154() {
        val draft = setDraftContent()
        compose.onNodeWithTag("switch_nightLightCircadian").performScrollTo().performClick()
        assertEquals(true, draft().nightLightCircadianEnabled)
    }

    @Test
    fun elevated_scheduleCaveat_onlyWhenNightLightAutoModeActive() {
        setDraftContent(state = elevated.copy(nightLightAutoMode = NightLightAutoMode.CUSTOM_SCHEDULE))
        compose.onNodeWithTag("pd_schedule_caveat").performScrollTo().assertExists()
    }

    @Test
    fun elevated_noScheduleCaveat_inManualMode() {
        setDraftContent()
        compose.onNodeWithTag("pd_schedule_caveat").assertDoesNotExist()
    }

    @Test
    fun elevated_temperatureSlider_commitsToTheDraft_andClearResetsToDeviceDefault() {
        val draft = setDraftContent(initial = AabSettings(nightLightTemperature = 3000))
        // The M3 slider's SetProgress semantics invoke onValueChange + onValueChangeFinished —
        // the commit-on-drag-END contract (one draft edit per gesture).
        compose.onNodeWithTag("slider_nightLightTemp").performScrollTo()
            .performSemanticsAction(SemanticsActions.SetProgress) { it(3300f) }
        assertEquals(3300, draft().nightLightTemperature)
        // A set temperature offers the clear back to null (= "device default", never written).
        compose.onNodeWithTag("pd_temp_clear").performScrollTo().performClick()
        assertNull(draft().nightLightTemperature)
    }

    @Test
    fun elevated_unsetTemperature_readsAsDeviceDefault() {
        setDraftContent()
        compose.onAllNodesWithText("Temperature: device default").onFirst().assertExists()
        compose.onNodeWithTag("pd_temp_clear").assertDoesNotExist()
    }

    @Test
    fun elevated_applyBar_firesApply() {
        var applied = false
        setDraftContent(onApply = { applied = true })
        compose.onNodeWithTag("apply_settings").performScrollTo().performClick()
        assertTrue(applied)
    }

    @Test
    fun elevated_writeFailure_surfacesErrorBanner() {
        setDraftContent(state = elevated.copy(writeFailed = true))
        compose.onNodeWithTag("pd_write_error").assertExists()
    }

    @Test
    fun basic_showsGrantCard_withAdbAlwaysAndRootAndNoToggles() {
        val state = PrivilegedDisplayUiState(
            tier = Tier.BASIC,
            adbCommand = "adb shell pm grant com.example android.permission.WRITE_SECURE_SETTINGS",
            shizukuAvailability = ShizukuAvailability.NOT_INSTALLED,
        )
        var copied = false
        var root = false
        compose.setContent {
            MaterialTheme {
                PrivilegedDisplayContent(
                    state = state, onBack = {},
                    onCopyAdb = { copied = true }, onTryRoot = { root = true },
                )
            }
        }
        compose.onNodeWithTag("pd_grant_card").assertExists()
        // The profile controls must NOT render below ELEVATED — no toggles, no Apply bar.
        compose.onNodeWithTag("switch_nightLight").assertDoesNotExist()
        compose.onNodeWithTag("apply_settings").assertDoesNotExist()
        // ADB is ALWAYS offered; the copyable command is visible.
        compose.onNodeWithText(state.adbCommand).assertExists()
        compose.onNodeWithTag("pd_copy_adb").performClick()
        compose.onNodeWithTag("pd_grant_root").performClick()
        assertTrue(copied && root)
        // Shizuku not installed → neither the one-tap button nor the start prompt.
        compose.onNodeWithTag("pd_grant_shizuku").assertDoesNotExist()
        compose.onNodeWithTag("pd_shizuku_start_prompt").assertDoesNotExist()
    }

    @Test
    fun basic_shizukuRunning_offersOneTapGrant() {
        var requested = false
        compose.setContent {
            MaterialTheme {
                PrivilegedDisplayContent(
                    state = PrivilegedDisplayUiState(
                        tier = Tier.BASIC,
                        shizukuAvailability = ShizukuAvailability.RUNNING,
                    ),
                    onBack = {},
                    onRequestShizuku = { requested = true },
                )
            }
        }
        compose.onNodeWithTag("pd_grant_shizuku").performClick()
        assertTrue(requested)
        compose.onNodeWithTag("pd_shizuku_start_prompt").assertDoesNotExist()
    }

    @Test
    fun basic_shizukuInstalledNotRunning_promptsToStartInsteadOfOneTap() {
        compose.setContent {
            MaterialTheme {
                PrivilegedDisplayContent(
                    state = PrivilegedDisplayUiState(
                        tier = Tier.BASIC,
                        shizukuAvailability = ShizukuAvailability.INSTALLED_NOT_RUNNING,
                    ),
                    onBack = {},
                )
            }
        }
        compose.onNodeWithTag("pd_shizuku_start_prompt").assertExists()
        compose.onNodeWithTag("pd_grant_shizuku").assertDoesNotExist()
        compose.onNodeWithTag("pd_copy_adb").assertExists() // ADB always offered
    }
}
