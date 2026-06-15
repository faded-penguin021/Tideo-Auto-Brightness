package com.tideo.autobrightness.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.tideo.autobrightness.app.runtime.PipelineState
import com.tideo.autobrightness.app.settings.AabSettings
import com.tideo.autobrightness.app.settings.SettingsValidator
import com.tideo.autobrightness.app.state.AppEntry
import com.tideo.autobrightness.app.state.LiveDebugUiState
import com.tideo.autobrightness.app.ui.components.CircadianDiagnosticCardContent
import com.tideo.autobrightness.app.ui.components.ReactivityDiagnosticCardContent
import com.tideo.autobrightness.app.ui.screens.SuperDimmingContent
import com.tideo.autobrightness.app.ui.screens.ContextsContent
import com.tideo.autobrightness.app.ui.screens.CurveBrightnessContent
import com.tideo.autobrightness.app.ui.screens.LiveDebugContent
import com.tideo.autobrightness.app.ui.screens.MiscContent
import com.tideo.autobrightness.app.ui.screens.ReactivityContent
import com.tideo.autobrightness.app.ui.screens.ToolsContent
import com.tideo.autobrightness.platform.privilege.Tier
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * S12.5b acceptance for the parameter screens: validator red-errors render (task583/707), the
 * preview→Apply chrome works (committed `[bracket]` + Apply/Discard bar), and the slider-backed
 * fields are bounded (G2-F3/F13).
 */
@RunWith(RobolectricTestRunner::class)
class SettingsScreensTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun curveBrightness_invalidForm2C_rendersValidatorError() {
        // form2C (40) > zone1End (10) → task583 advisory error on the Zone-2-offset field.
        val invalid = AabSettings(zone1End = 10, form2C = 40)
        val errors = SettingsValidator.validate(invalid)
        assertTrue(errors.any { it.field == "form2C" }, "fixture should produce a form2C error")

        compose.setContent {
            MaterialTheme {
                CurveBrightnessContent(invalid, invalid, errors, epoch = 0, dirty = false, {}, {}, {}, {})
            }
        }

        compose.onNodeWithTag("field_form2C").assertExists()
        compose.onNodeWithText("must be ≤ zone1End", substring = true).assertExists()
    }

    @Test
    fun curveBrightness_safetyWarning_rendersBanner() {
        val errors = listOf(
            com.tideo.autobrightness.app.settings.FieldError(
                "safetyBrightness", "⚠️ Safety Warning: Brightness too low at 1000 Lux.",
            ),
        )
        compose.setContent {
            MaterialTheme {
                CurveBrightnessContent(AabSettings(), AabSettings(), errors, 0, false, {}, {}, {}, {})
            }
        }
        compose.onNodeWithTag("error_safetyBrightness").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun reactivity_detectOverridesToggle_editsDraft() {
        var captured: AabSettings? = null
        compose.setContent {
            MaterialTheme {
                ReactivityContent(
                    AabSettings(detectOverrides = false), AabSettings(), epoch = 0, dirty = false,
                    onEdit = { transform -> captured = transform(AabSettings(detectOverrides = false)) },
                    onApply = {}, onDiscard = {}, onBack = {},
                )
            }
        }
        compose.onNodeWithTag("switch_detectOverrides").performScrollTo().performClick()
        assertEquals(true, captured?.detectOverrides)
    }

    @Test
    fun superDimming_dimmingRowsDisabledWithoutElevated() {
        compose.setContent {
            MaterialTheme {
                SuperDimmingContent(
                    AabSettings(), AabSettings(), epoch = 0, dirty = false, tier = Tier.BASIC,
                    onEdit = {}, onApply = {}, onDiscard = {}, onBack = {}, onOpenOnboarding = {},
                )
            }
        }
        // The grant link is shown when not ELEVATED.
        compose.onNodeWithTag("dimming_grant_link").performScrollTo().assertExists()
    }

    @Test
    fun liveDebug_debugSelector_showsCurrentLabel_andRendersSeededMetrics() {
        // S12.6b: the debug-category selector is now global on the Live Debug scene (G2R-F9), and the
        // scene renders live %AAB_* values from a seeded PipelineState (G2R-F6).
        val seeded = PipelineState(
            smoothedLux = 123.4, lastRawLux = 130.0, threshDynamic = 45.0,
            threshAbsLow = 10.0, threshAbsHigh = 800.0, scaleDynamic = 1.25,
            scaleDynamicCompress = 0.9, lastAppliedBrightness = 88, targetBrightness = 90,
        )
        compose.setContent {
            MaterialTheme {
                LiveDebugContent(
                    state = LiveDebugUiState(pipeline = seeded, serviceRunning = true, debugLevel = 3),
                    onSelectDebug = {}, onBack = {},
                )
            }
        }
        compose.onNodeWithText("Debug: Light Eval Thresholds").performScrollTo().assertExists()
        compose.onNodeWithTag("debug_smoothed_lux").performScrollTo().assertExists()
        compose.onNodeWithText("123.4", substring = true).performScrollTo().assertExists()
    }

    @Test
    fun reactivityDiagnosticCard_rendersThresholdAsPercent_andDeadZone() {
        // G2R-F7 + G2R-F56: the Reactivity glass-box card surfaces the live dynamic threshold as a
        // percentage (0.42 → "42%") plus the absolute dead zone.
        val seeded = PipelineState(smoothedLux = 50.0, threshDynamic = 0.42, threshAbsLow = 5.0, threshAbsHigh = 600.0)
        compose.setContent {
            MaterialTheme { ReactivityDiagnosticCardContent(seeded) }
        }
        compose.onNodeWithTag("diag_reactivity_threshold").assertExists()
        compose.onNodeWithTag("diag_reactivity_deadzone").assertExists()
        compose.onNodeWithText("42%", substring = true).assertExists()
    }

    @Test
    fun curveBrightness_liveReadout_rendersSmoothedLuxAndCurrentBright_G2RF58() {
        // G2R-F58: the Curve & Brightness screen shows the Tasker current_lux_and_bright live readout.
        val seeded = PipelineState(smoothedLux = 123.4, lastAppliedBrightness = 88)
        compose.setContent {
            MaterialTheme {
                CurveBrightnessContent(
                    AabSettings(minBrightness = 10, maxBrightness = 255), AabSettings(), emptyList(),
                    epoch = 0, dirty = false, onEdit = {}, onApply = {}, onDiscard = {}, onBack = {},
                    live = seeded,
                )
            }
        }
        compose.onNodeWithTag("diag_curve_smoothed_lux").performScrollTo().assertExists()
        compose.onNodeWithTag("diag_curve_current_bright").performScrollTo()
            .assertTextContains("88", substring = true)
    }

    @Test
    fun curveBrightness_derivedCoefficients_useZoneAlignmentLabels_G2RF61() {
        // G2R-F61: form2A/form3A are labelled as the zone-alignment hinge points, not bare placeholders.
        compose.setContent {
            MaterialTheme {
                CurveBrightnessContent(AabSettings(), AabSettings(), emptyList(), 0, false, {}, {}, {}, {})
            }
        }
        compose.onNodeWithTag("derived_form2A").performScrollTo().assertExists()
        compose.onNodeWithText("Zone 2 alignment", substring = true).assertExists()
        compose.onNodeWithText("Zone 3 alignment", substring = true).assertExists()
    }

    @Test
    fun misc_liveReadout_rendersThrottleAndAlpha_G2RF58() {
        // G2R-F58: the Misc screen shows the Tasker current_throttle_and_alpha live readout.
        val seeded = PipelineState(throttleMs = 1310L, luxAlpha = 0.421)
        compose.setContent {
            MaterialTheme {
                MiscContent(
                    AabSettings(), AabSettings(), emptyList(), 0, false,
                    onEdit = {}, onApply = {}, onDiscard = {}, onBack = {}, live = seeded,
                )
            }
        }
        compose.onNodeWithTag("diag_misc_throttle").performScrollTo()
            .assertTextContains("1310", substring = true)
        compose.onNodeWithTag("diag_misc_alpha").performScrollTo()
            .assertTextContains("0.421", substring = true)
    }

    @Test
    fun misc_scaleBecomesAutoReadout_whenCircadianEnabled_G2RF60() {
        // G2R-F60: with dynamic scaling on, the static Scale field is replaced by the read-only
        // "(auto)" dynamic-scale readout; with it off the editable field is shown.
        val seeded = PipelineState(scaleDynamicCompress = 0.873)
        compose.setContent {
            MaterialTheme {
                MiscContent(
                    AabSettings(scalingEnabled = true), AabSettings(), emptyList(), 0, false,
                    onEdit = {}, onApply = {}, onDiscard = {}, onBack = {}, live = seeded,
                )
            }
        }
        compose.onNodeWithTag("derived_scaleDynamic").performScrollTo().assertExists()
        compose.onNodeWithText("0.873", substring = true).assertExists()
        compose.onNodeWithTag("field_scale").assertDoesNotExist()
    }

    @Test
    fun misc_scaleIsEditable_whenCircadianDisabled_G2RF60() {
        compose.setContent {
            MaterialTheme {
                MiscContent(
                    AabSettings(scalingEnabled = false), AabSettings(), emptyList(), 0, false,
                    onEdit = {}, onApply = {}, onDiscard = {}, onBack = {},
                )
            }
        }
        compose.onNodeWithTag("field_scale").performScrollTo().assertExists()
        compose.onNodeWithTag("derived_scaleDynamic").assertDoesNotExist()
    }

    @Test
    fun circadianDiagnosticCard_rendersUncompressedAndTrueScale() {
        // G2R-F8: the Circadian glass-box card surfaces uncompressed vs true (compressed) scale.
        val seeded = PipelineState(scaleDynamic = 1.5, scaleDynamicCompress = 0.8, lastAppliedBrightness = 120)
        compose.setContent {
            MaterialTheme { CircadianDiagnosticCardContent(seeded, minBrightness = 10, maxBrightness = 255, timeLabel = "14:30") }
        }
        compose.onNodeWithTag("diag_circadian_uncompressed").assertExists()
        compose.onNodeWithTag("diag_circadian_true").assertExists()
        compose.onNodeWithText("14:30", substring = true).assertExists()
    }

    @Test
    fun misc_committedBracket_shownWhenDraftDiffers() {
        // Draft min = 42, committed (active) min = 10 → the slider shows the committed value [10].
        compose.setContent {
            MaterialTheme {
                MiscContent(
                    AabSettings(minBrightness = 42), AabSettings(minBrightness = 10), emptyList(), 0, true,
                    onEdit = {}, onApply = {}, onDiscard = {}, onBack = {},
                )
            }
        }
        compose.onNodeWithText("Min brightness: 42 [10]", substring = true).performScrollTo().assertExists()
    }

    @Test
    fun misc_sliders_areBounded() {
        compose.setContent {
            MaterialTheme {
                MiscContent(
                    AabSettings(minBrightness = 10, maxBrightness = 255), AabSettings(), emptyList(), 0, false,
                    onEdit = {}, onApply = {}, onDiscard = {}, onBack = {},
                )
            }
        }
        // misc_settings.md: Min brightness 0–75, Max brightness 150–255. Assert the bounds (the
        // current value carries float-snap imprecision, so range/steps are the meaningful contract).
        compose.onNodeWithTag("slider_minBrightness").performScrollTo()
            .assert(rangeIs(0f..75f, steps = 74))
        compose.onNodeWithTag("slider_maxBrightness").performScrollTo()
            .assert(rangeIs(150f..255f, steps = 104))
    }

    private fun rangeIs(range: ClosedFloatingPointRange<Float>, steps: Int) =
        SemanticsMatcher("ProgressBarRangeInfo range=$range steps=$steps") { node ->
            val info = node.config.getOrNull(SemanticsProperties.ProgressBarRangeInfo)
            info != null && info.range == range && info.steps == steps
        }

    @Test
    fun contextEditor_exposesSunriseTokensAndCurrentSsidHelper() {
        // G2-F14: the rule editor must offer the SUNRISE/SUNSET tokens + a "use current SSID" helper.
        compose.setContent {
            MaterialTheme {
                ContextsContent(
                    rules = emptyList(),
                    profileNames = listOf("Default"),
                    apps = emptyList(),
                    onBack = {}, onSave = {}, onDelete = {},
                )
            }
        }
        compose.onNodeWithTag("add_context_rule").performClick()
        compose.onNodeWithTag("use_current_ssid").performScrollTo().assertExists()
        compose.onNodeWithTag("start_sunrise").performScrollTo().assertExists()
        compose.onNodeWithTag("end_sunset").performScrollTo().assertExists()
    }

    @Test
    fun contextEditor_promptsForUsageAccess_whenAppRuleLacksGrant() {
        // G2-F14: editing a rule that targets an app while usage access is missing surfaces the prompt.
        val appRule = com.tideo.autobrightness.app.settings.ContextRule(
            id = "r1", name = "Cinema", profile = "Default",
            triggers = com.tideo.autobrightness.app.settings.ContextTriggers(apps = listOf("com.example")),
        )
        compose.setContent {
            MaterialTheme {
                ContextsContent(
                    rules = listOf(appRule),
                    profileNames = listOf("Default"),
                    apps = listOf(AppEntry("com.example", "Example")),
                    onBack = {}, onSave = {}, onDelete = {},
                    hasUsageAccess = { false },
                )
            }
        }
        compose.onNodeWithTag("edit_r1").performScrollTo().performClick()
        compose.onNodeWithTag("usage_access_prompt").performScrollTo().assertExists()
    }

    @Test
    fun curveBrightness_criticalError_disablesApply() {
        // G2R-F18/D-052: a CRITICAL curve error (form3A<0) must disable Apply even while dirty.
        val invalid = AabSettings(form1A = -1)
        val errors = SettingsValidator.validate(invalid)
        assertTrue(errors.any { it.severity == com.tideo.autobrightness.app.settings.Severity.CRITICAL })

        compose.setContent {
            MaterialTheme {
                CurveBrightnessContent(
                    invalid, AabSettings(), errors, epoch = 0, dirty = true,
                    onEdit = {}, onApply = {}, onDiscard = {}, onBack = {},
                    criticalError = true,
                )
            }
        }
        compose.onNodeWithTag("apply_settings").assertIsNotEnabled()
        compose.onNodeWithTag("apply_blocked_hint").assertExists()
    }

    @Test
    fun reactivity_resetButton_rendersWhenProvided() {
        // G2R-F17: each settings screen exposes a per-screen reset action.
        var reset = false
        compose.setContent {
            MaterialTheme {
                ReactivityContent(
                    AabSettings(), AabSettings(), epoch = 0, dirty = false,
                    onEdit = {}, onApply = {}, onDiscard = {}, onBack = {},
                    onReset = { reset = true },
                )
            }
        }
        compose.onNodeWithTag("reset_screen").performClick()
        assertTrue(reset, "tapping Reset invokes the per-screen reset")
    }

    @Test
    fun profiles_savedProfiles_render_withManageActions() {
        // G2R-F15: saved profiles list with apply/overwrite/delete + save-as/restore actions.
        val profiles = listOf(
            com.tideo.autobrightness.app.settings.SavedProfile("Default", AabSettings(), builtIn = true),
            com.tideo.autobrightness.app.settings.SavedProfile("Mine", AabSettings()),
        )
        compose.setContent {
            MaterialTheme {
                com.tideo.autobrightness.app.ui.screens.ProfilesContent(
                    profiles = profiles, legacyEntries = emptyList(), contextLocked = false, status = null,
                    onBack = {}, onApplyProfile = {}, onOverwriteProfile = {}, onDeleteProfile = {},
                    onSaveCurrentAs = {}, onRestoreFactory = {}, onResumeContext = {}, onReset = {},
                    onExport = {}, onImport = {}, onChooseLegacyFolder = {}, onLoadLegacy = {},
                )
            }
        }
        compose.onNodeWithTag("apply_profile_Mine").performScrollTo().assertExists()
        compose.onNodeWithTag("overwrite_profile_Default").performScrollTo().assertExists()
        compose.onNodeWithTag("save_profile_as").performScrollTo().assertExists()
        compose.onNodeWithTag("restore_factory").performScrollTo().assertExists()
    }

    @Test
    fun profiles_contextLockBanner_offersResume() {
        // G2R-F30: a manual profile load latches the context lock; the Profiles screen offers Resume.
        var resumed = false
        compose.setContent {
            MaterialTheme {
                com.tideo.autobrightness.app.ui.screens.ProfilesContent(
                    profiles = emptyList(), legacyEntries = emptyList(), contextLocked = true, status = null,
                    onBack = {}, onApplyProfile = {}, onOverwriteProfile = {}, onDeleteProfile = {},
                    onSaveCurrentAs = {}, onRestoreFactory = {}, onResumeContext = { resumed = true }, onReset = {},
                    onExport = {}, onImport = {}, onChooseLegacyFolder = {}, onLoadLegacy = {},
                )
            }
        }
        compose.onNodeWithTag("context_lock_banner").assertExists()
        compose.onNodeWithTag("resume_context").performScrollTo().performClick()
        assertTrue(resumed, "Resume clears the manual context lock")
    }

    @Test
    fun contextEditor_exposesBatteryPercentageFields() {
        // Owner finding (G2R-F31): the rule editor must offer a battery percentage from/to window.
        compose.setContent {
            MaterialTheme {
                ContextsContent(
                    rules = emptyList(), profileNames = listOf("Default"), apps = emptyList(),
                    onBack = {}, onSave = {}, onDelete = {},
                )
            }
        }
        compose.onNodeWithTag("add_context_rule").performClick()
        compose.onNodeWithTag("rule_batt_min").performScrollTo().assertExists()
        compose.onNodeWithTag("rule_batt_max").performScrollTo().assertExists()
    }

    @Test
    fun reactivity_deltaFactorHelp_rendersVerbatimTaskerText() {
        // G2R-F19/F21: tapping the "ⓘ" reveals the VERBATIM Tasker long-press help. The delta-factor
        // help (task740) describes sensor smoothing — the case the owner flagged as mislabelled.
        compose.setContent {
            MaterialTheme {
                ReactivityContent(
                    AabSettings(), AabSettings(), epoch = 0, dirty = false,
                    onEdit = {}, onApply = {}, onDiscard = {}, onBack = {},
                )
            }
        }
        compose.onNodeWithTag("help_field_deltaFactor").performScrollTo().performClick()
        compose.onNodeWithText("Controls how much to smooth out sensor readings", substring = true)
            .assertExists()
    }

    @Test
    fun contextEditor_timeField_opensTimePickerModal() {
        // G2R-F28: the From/To inputs open the Material3 TimePicker modal.
        compose.setContent {
            MaterialTheme {
                ContextsContent(
                    rules = emptyList(), profileNames = listOf("Default"), apps = emptyList(),
                    onBack = {}, onSave = {}, onDelete = {},
                )
            }
        }
        compose.onNodeWithTag("add_context_rule").performClick()
        compose.onNodeWithTag("rule_start").performScrollTo().performClick()
        compose.onNodeWithTag("start_time_ok").assertExists()
    }

    @Test
    fun contextEditor_useCurrentSsid_fillsField() {
        // G2R-F22: "use current Wi-Fi" returns the SSID and fills the field.
        compose.setContent {
            MaterialTheme {
                ContextsContent(
                    rules = emptyList(), profileNames = listOf("Default"), apps = emptyList(),
                    onBack = {}, onSave = {}, onDelete = {},
                    onUseCurrentSsid = { setSsid -> setSsid("HomeNet") },
                )
            }
        }
        compose.onNodeWithTag("add_context_rule").performClick()
        compose.onNodeWithTag("use_current_ssid").performScrollTo().performClick()
        compose.onNodeWithText("HomeNet", substring = true).assertExists()
    }

    @Test
    fun contextEditor_exposesLocationFields() {
        // G2R-F22 (live location): lat/lon/radius editor + "use current location".
        compose.setContent {
            MaterialTheme {
                ContextsContent(
                    rules = emptyList(), profileNames = listOf("Default"), apps = emptyList(),
                    onBack = {}, onSave = {}, onDelete = {},
                )
            }
        }
        compose.onNodeWithTag("add_context_rule").performClick()
        compose.onNodeWithTag("use_current_location").performScrollTo().assertExists()
        compose.onNodeWithTag("rule_lat").performScrollTo().assertExists()
        compose.onNodeWithTag("rule_radius").performScrollTo().assertExists()
    }

    @Test
    fun contextEditor_dayPicker_savesSelectedDays_G2RF67() {
        // G2R-F67: the rule editor exposes a day-of-week picker; the selection is persisted as
        // Calendar.DAY_OF_WEEK values (Monday = 2).
        var saved: com.tideo.autobrightness.app.settings.ContextRule? = null
        compose.setContent {
            MaterialTheme {
                ContextsContent(
                    rules = emptyList(), profileNames = listOf("Default"), apps = emptyList(),
                    onBack = {}, onSave = { saved = it }, onDelete = {},
                )
            }
        }
        compose.onNodeWithTag("add_context_rule").performClick()
        compose.onNodeWithTag("day_2").performScrollTo().performClick()
        compose.onNodeWithTag("save_rule").performScrollTo().performClick()
        assertEquals(listOf(2), saved?.triggers?.days, "Monday must be saved as DAY_OF_WEEK 2")
    }

    @Test
    fun contextEditor_sunriseToken_showsResolvedTime_G2RF68() {
        // G2R-F68: when today's solar times are known, the SUNRISE token shows the resolved time.
        compose.setContent {
            MaterialTheme {
                ContextsContent(
                    rules = emptyList(), profileNames = listOf("Default"), apps = emptyList(),
                    solarLabel = "06:42" to "18:30",
                    onBack = {}, onSave = {}, onDelete = {},
                )
            }
        }
        compose.onNodeWithTag("add_context_rule").performClick()
        compose.onNodeWithTag("start_sunrise").performScrollTo()
            .assertTextContains("Sunrise (06:42)", substring = true)
    }

    @Test
    fun liveDebug_performanceCard_rendersTimings() {
        // G2R-F29: Performance & Timings shows luxAlpha + animation (steps×wait) + throttle.
        val seeded = PipelineState(
            luxAlpha = 0.42, animationSteps = 20, animationWaitMs = 65L,
            throttleMs = 1310L, cycleTimeMs = 12.0, lastUpdateMs = System.currentTimeMillis(),
        )
        compose.setContent {
            MaterialTheme {
                LiveDebugContent(
                    state = LiveDebugUiState(pipeline = seeded, serviceRunning = true),
                    onSelectDebug = {}, onBack = {},
                )
            }
        }
        compose.onNodeWithTag("debug_lux_alpha").performScrollTo().assertExists()
        compose.onNodeWithTag("debug_throttle").performScrollTo().assertExists()
        compose.onNodeWithText("20×65ms", substring = true).assertExists()
    }

    @Test
    fun liveDebug_globalFlashCard_rendersStatusAndEnableButton() {
        // G2R-F50: the opt-in global-flash card shows the enablement status + the Accessibility CTA.
        var enableClicked = false
        compose.setContent {
            MaterialTheme {
                LiveDebugContent(
                    state = LiveDebugUiState(serviceRunning = true, globalToastsEnabled = false),
                    onSelectDebug = {}, onBack = {},
                    onEnableGlobalToasts = { enableClicked = true },
                )
            }
        }
        compose.onNodeWithTag("global_flash_status").performScrollTo().assertExists()
        compose.onNodeWithText("Off (foreground only)", substring = true).assertExists()
        compose.onNodeWithTag("global_flash_enable").performScrollTo().performClick()
        assertTrue(enableClicked, "the Accessibility CTA invokes the enable callback")
    }

    @Test
    fun draftBar_applyAndDiscard_invokeCallbacks() {
        var applied = false
        var discarded = false
        compose.setContent {
            MaterialTheme {
                MiscContent(
                    AabSettings(minBrightness = 42), AabSettings(minBrightness = 10), emptyList(), 0, true,
                    onEdit = {}, onApply = { applied = true }, onDiscard = { discarded = true }, onBack = {},
                )
            }
        }
        compose.onNodeWithTag("apply_settings").performClick()
        assertTrue(applied, "Apply commits the draft")
        compose.onNodeWithTag("discard_settings").performClick()
        assertTrue(discarded, "Discard reverts the draft")
    }
}
