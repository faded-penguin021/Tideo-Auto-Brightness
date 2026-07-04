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

/**
 * Privileged Display screen (D-149, Segment 2; D-151 profile section): at ELEVATED the manual
 * toggles render and route through their callbacks, and the profile section edits the D-151
 * display-toggle draft fields; below ELEVATED the screen self-guards with the grant card offering
 * all three grant channels (adb always; Shizuku one-tap only with a live binder; root). Drives the
 * stateless [PrivilegedDisplayContent] directly (no ViewModel / real settings needed).
 */
@RunWith(RobolectricTestRunner::class)
class PrivilegedDisplayScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val elevated = PrivilegedDisplayUiState(tier = Tier.ELEVATED)

    @Test
    fun elevated_rendersToggles_andFiresCallbacks() {
        var nightLight: Boolean? = null
        var daltonizer: DaltonizerMode? = null
        var inversion: Boolean? = null
        compose.setContent {
            MaterialTheme {
                PrivilegedDisplayContent(
                    state = elevated,
                    onBack = {},
                    onSetNightLight = { nightLight = it },
                    onSetDaltonizer = { daltonizer = it },
                    onSetInversion = { inversion = it },
                )
            }
        }
        // No grant card at ELEVATED.
        compose.onNodeWithTag("pd_grant_card").assertDoesNotExist()

        compose.onNodeWithTag("switch_nightLight").performScrollTo().performClick()
        assertEquals(true, nightLight)
        compose.onNodeWithTag("daltonizer_grayscale").performScrollTo().performClick()
        assertEquals(DaltonizerMode.GRAYSCALE, daltonizer)
        compose.onNodeWithTag("switch_inversion").performScrollTo().performClick()
        assertEquals(true, inversion)
        compose.onNodeWithTag("switch_alwaysOn").performScrollTo().assertExists()
        compose.onNodeWithTag("switch_stayAwake").performScrollTo().assertExists()
        // The AOSP-keys / OEM-variance info card is always present.
        compose.onNodeWithTag("pd_info_card").performScrollTo().assertExists()
    }

    @Test
    fun elevated_daltonizerChips_reflectSelection() {
        compose.setContent {
            MaterialTheme {
                PrivilegedDisplayContent(
                    state = elevated.copy(daltonizer = DaltonizerMode.TRITANOMALY),
                    onBack = {},
                )
            }
        }
        // All five modes render as chips (incl. Off).
        DaltonizerMode.entries.forEach { mode ->
            compose.onNodeWithTag("daltonizer_${mode.name.lowercase()}").performScrollTo().assertExists()
        }
    }

    @Test
    fun elevated_hdrSection_hiddenWhenUnavailable_shownAndClickableWhenAvailable() {
        compose.setContent {
            MaterialTheme {
                PrivilegedDisplayContent(state = elevated.copy(hdrAvailable = false), onBack = {})
            }
        }
        compose.onNodeWithTag("switch_hdrForceSdr").assertDoesNotExist()
    }

    @Test
    fun elevated_hdrSwitch_firesCallbackWhenAvailable() {
        var hdr: Boolean? = null
        compose.setContent {
            MaterialTheme {
                PrivilegedDisplayContent(
                    state = elevated.copy(hdrAvailable = true),
                    onBack = {},
                    onSetHdrForceSdr = { hdr = it },
                )
            }
        }
        compose.onNodeWithTag("switch_hdrForceSdr").performScrollTo().performClick()
        assertEquals(true, hdr)
    }

    @Test
    fun elevated_scheduleCaveat_onlyWhenNightLightAutoModeActive() {
        compose.setContent {
            MaterialTheme {
                PrivilegedDisplayContent(
                    state = elevated.copy(nightLightAutoMode = NightLightAutoMode.CUSTOM_SCHEDULE),
                    onBack = {},
                )
            }
        }
        compose.onNodeWithTag("pd_schedule_caveat").performScrollTo().assertExists()
    }

    @Test
    fun elevated_noScheduleCaveat_inManualMode() {
        compose.setContent {
            MaterialTheme { PrivilegedDisplayContent(state = elevated, onBack = {}) }
        }
        compose.onNodeWithTag("pd_schedule_caveat").assertDoesNotExist()
    }

    @Test
    fun elevated_temperatureSlider_commitsOnDragEnd_andShowsDeviceDefaultWhenUnset() {
        var committed: Int? = null
        compose.setContent {
            MaterialTheme {
                PrivilegedDisplayContent(
                    state = elevated.copy(nightLightTemperature = null),
                    onBack = {},
                    onSetNightLightTemperature = { committed = it },
                )
            }
        }
        // Never-set temperature reads as the device default, not a fabricated number (the manual
        // slider AND the D-151 profile slider both park there for an unset value).
        compose.onAllNodesWithText("Temperature: device default").onFirst().assertExists()
        // The M3 slider's SetProgress semantics invoke onValueChange + onValueChangeFinished —
        // the commit-on-drag-END contract (one settings write per gesture).
        compose.onNodeWithTag("slider_nightLightTemp").performScrollTo()
            .performSemanticsAction(SemanticsActions.SetProgress) { it(3300f) }
        assertEquals(3300, committed)
    }

    @Test
    fun elevated_writeFailure_surfacesErrorBanner() {
        compose.setContent {
            MaterialTheme {
                PrivilegedDisplayContent(state = elevated.copy(writeFailed = true), onBack = {})
            }
        }
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
        // The toggles must NOT render below ELEVATED.
        compose.onNodeWithTag("switch_nightLight").assertDoesNotExist()
        // ADB is ALWAYS offered; the copyable command is visible.
        compose.onNodeWithText(state.adbCommand).assertExists()
        compose.onNodeWithTag("pd_copy_adb").performClick()
        compose.onNodeWithTag("pd_grant_root").performClick()
        assertTrue(copied && root)
        // Shizuku not installed → neither the one-tap button nor the start prompt.
        compose.onNodeWithTag("pd_grant_shizuku").assertDoesNotExist()
        compose.onNodeWithTag("pd_shizuku_start_prompt").assertDoesNotExist()
    }

    // --- Profile section (D-151, replaces the D-150 Schedules section) --------------------------

    @Test
    fun elevated_profileSection_editsFlowIntoDraft() {
        var draft = AabSettings()
        compose.setContent {
            MaterialTheme {
                PrivilegedDisplayContent(
                    state = elevated, onBack = {},
                    draft = draft,
                    onEditDraft = { transform -> draft = transform(draft) },
                )
            }
        }
        compose.onNodeWithTag("pd_profile_card").performScrollTo().assertExists()
        compose.onNodeWithTag("switch_profile_nightLight").performScrollTo().performClick()
        assertEquals(true, draft.nightLightEnabled)
        compose.onNodeWithTag("profile_daltonizer_grayscale").performScrollTo().performClick()
        assertEquals("GRAYSCALE", draft.daltonizerMode)
        compose.onNodeWithTag("switch_profile_inversion").performScrollTo().performClick()
        assertEquals(true, draft.inversionEnabled)
    }

    @Test
    fun elevated_profileTemperature_commitsToDraft_andClearButtonResetsToDeviceDefault() {
        var draft = AabSettings(nightLightTemperature = 3000)
        compose.setContent {
            MaterialTheme {
                PrivilegedDisplayContent(
                    state = elevated, onBack = {},
                    draft = draft,
                    onEditDraft = { transform -> draft = transform(draft) },
                )
            }
        }
        compose.onNodeWithTag("slider_profile_nightLightTemp").performScrollTo()
            .performSemanticsAction(SemanticsActions.SetProgress) { it(3300f) }
        assertEquals(3300, draft.nightLightTemperature)
        // A set temperature offers the "use device temperature" clear back to null (= no opinion).
        compose.onNodeWithTag("pd_profile_temp_clear").performScrollTo().performClick()
        assertNull(draft.nightLightTemperature)
    }

    @Test
    fun elevated_profileApplyBar_firesApply_onlyWhenDirty() {
        var applied = false
        compose.setContent {
            MaterialTheme {
                PrivilegedDisplayContent(
                    state = elevated, onBack = {},
                    draftDirty = true,
                    onApplyDraft = { applied = true },
                )
            }
        }
        compose.onNodeWithTag("apply_settings").performScrollTo().performClick()
        assertTrue(applied)
    }

    @Test
    fun basic_hidesProfileSection() {
        compose.setContent {
            MaterialTheme {
                PrivilegedDisplayContent(
                    state = PrivilegedDisplayUiState(tier = Tier.BASIC),
                    onBack = {},
                )
            }
        }
        compose.onNodeWithTag("pd_profile_card").assertDoesNotExist()
        compose.onNodeWithTag("switch_profile_nightLight").assertDoesNotExist()
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
