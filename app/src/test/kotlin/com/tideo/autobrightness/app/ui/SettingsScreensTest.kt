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
import androidx.compose.ui.unit.dp
import com.tideo.autobrightness.app.runtime.PipelineState
import com.tideo.autobrightness.app.settings.AabSettings
import com.tideo.autobrightness.app.settings.ExperimentDateLocation
import com.tideo.autobrightness.app.settings.SavedProfile
import com.tideo.autobrightness.app.settings.SettingsValidator
import com.tideo.autobrightness.app.state.AppEntry
import com.tideo.autobrightness.app.state.LiveDebugUiState
import com.tideo.autobrightness.app.ui.components.ChartPager
import com.tideo.autobrightness.app.ui.components.ChartSlot
import com.tideo.autobrightness.app.ui.components.CircadianDiagnosticCardContent
import com.tideo.autobrightness.app.ui.components.ReactivityDiagnosticCardContent
import com.tideo.autobrightness.app.ui.components.EmptyState
import com.tideo.autobrightness.app.ui.components.SettingsDiffList
import com.tideo.autobrightness.app.ui.screens.CircadianDateLocationCard
import com.tideo.autobrightness.app.ui.screens.LoadProfileDialog
import com.tideo.autobrightness.app.ui.screens.SuperDimmingContent
import com.tideo.autobrightness.app.ui.screens.ContextsContent
import com.tideo.autobrightness.app.ui.components.FlashPill
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

/** S12.5b: parameter screens with validator errors, preview/apply, and bounded sliders. */
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
        compose.onNodeWithTag("dimming_grant_link").performScrollTo().assertExists()
    }

    @Test
    fun superDimming_liveReadout_rendersRelAndAbs_G2RF58() {
        // G2R-F58: the Super Dimming screen shows live dimmingCurrent (rel) / dimmingDS (abs).
        val seeded = PipelineState(dimmingCurrent = 12.3, dimmingDS = 45.6, lastAppliedBrightness = 8)
        compose.setContent {
            MaterialTheme {
                SuperDimmingContent(
                    AabSettings(), AabSettings(), epoch = 0, dirty = false, tier = Tier.ELEVATED,
                    live = seeded,
                    onEdit = {}, onApply = {}, onDiscard = {}, onBack = {}, onOpenOnboarding = {},
                )
            }
        }
        compose.onNodeWithTag("diag_dimming_rel").performScrollTo().assertTextContains("12.3", substring = true)
        compose.onNodeWithTag("diag_dimming_abs").performScrollTo().assertTextContains("45.6", substring = true)
    }

    @Test
    fun inAppFlash_isTapToDismiss_G2RF88() {
        // G2R-F88: tappable pill that dismisses on tap (not a Toast which passes clicks through).
        var dismissed = false
        compose.setContent {
            MaterialTheme { FlashPill("Applied") { dismissed = true } }
        }
        compose.onNodeWithTag("aab_flash").assertExists()
        compose.onNodeWithTag("aab_flash").performClick()
        assertTrue(dismissed, "tapping the flash should dismiss it (F88)")
    }

    @Test
    fun misc_negativeLuxAlpha_isClampedToZeroInDisplay_G2RF86() {
        // G2R-F86: transient negative alpha shows as "0.000" (display clamp; engine value unclamped per D-010(a)).
        val seeded = PipelineState(throttleMs = 1310L, luxAlpha = -0.42)
        compose.setContent {
            MaterialTheme {
                MiscContent(
                    AabSettings(), AabSettings(), emptyList(), 0, false,
                    onEdit = {}, onApply = {}, onDiscard = {}, onBack = {}, live = seeded,
                )
            }
        }
        compose.onNodeWithTag("diag_misc_alpha").performScrollTo().assertTextContains("0.000", substring = true)
    }

    @Test
    fun liveDebug_debugSelector_showsCurrentLabel_andRendersSeededMetrics() {
        // S12.6b: global debug-category selector (G2R-F9); renders seeded pipeline state (G2R-F6).
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
        // G2R-F7/F56: surfaces live dynamic threshold as % (0.42 → "42%") plus dead zone.
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
    fun curveBrightness_liveReadout_showsPerceivedBrightness_whenPwmSensitiveFloors_D117() {
        // D-117: PWM-sensitive mode: lastAppliedBrightness is floored hardware value; targetBrightness is perceived value. Readout shows perceived (33), not hardware floor (88).
        val seeded = PipelineState(smoothedLux = 123.4, lastAppliedBrightness = 88, targetBrightness = 33)
        compose.setContent {
            MaterialTheme {
                CurveBrightnessContent(
                    AabSettings(minBrightness = 10, maxBrightness = 255), AabSettings(), emptyList(),
                    epoch = 0, dirty = false, onEdit = {}, onApply = {}, onDiscard = {}, onBack = {},
                    live = seeded,
                )
            }
        }
        compose.onNodeWithTag("diag_curve_current_bright").performScrollTo()
            .assertTextContains("33", substring = true)
    }

    @Test
    fun curveBrightness_derivedCoefficients_useZoneAlignmentLabels_G2RF61() {
        // G2R-F61: form2A/form3A labeled as zone-alignment hinge points.
        compose.setContent {
            MaterialTheme {
                CurveBrightnessContent(AabSettings(), AabSettings(), emptyList(), 0, false, {}, {}, {}, {})
            }
        }
        compose.onNodeWithTag("derived_form2A").performScrollTo().assertExists()
        compose.onNodeWithText("ZONE 2 ALIGNMENT", substring = true).assertExists()
        compose.onNodeWithText("ZONE 3 ALIGNMENT", substring = true).assertExists()
    }

    @Test
    fun misc_liveReadout_rendersThrottleAndAlpha_G2RF58() {
        // G2R-F58: Misc screen shows current_throttle_and_alpha live readout.
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
        // G2R-F60: dynamic scaling on → read-only "(auto)" scale; off → editable field.
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
        // G2R-F8: Circadian glass-box card surfaces uncompressed vs true (compressed) scale.
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
        // Draft min = 42, committed min = 10 → slider shows [10].
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
        // Assert bounds: Min 0–75, Max 150–255 (float-snap imprecision, so range/steps are meaningful).
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
        // G2-F14: rule editor offers SUNRISE/SUNSET tokens + "use current SSID" helper.
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
        compose.onNodeWithTag("trigger_toggle_wifi").performScrollTo().performClick()
        compose.onNodeWithTag("trigger_toggle_time").performScrollTo().performClick()
        compose.onNodeWithTag("use_current_ssid").performScrollTo().assertExists()
        compose.onNodeWithTag("start_sunrise").performScrollTo().assertExists()
        compose.onNodeWithTag("end_sunset").performScrollTo().assertExists()
    }

    @Test
    fun contextEditor_promptsForUsageAccess_whenAppRuleLacksGrant() {
        // G2-F14: rule targeting app without usage access surfaces prompt.
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
        // G2R-F18/D-052: CRITICAL curve error (form2A<0 from form1A<0) disables Apply even while dirty.
        val invalid = AabSettings(form1A = -1.0)
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
        // G2R-F17: per-screen reset action.
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
        // G2R-F15: profiles list with apply/overwrite/delete/save-as/restore.
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
        compose.onNodeWithTag("profile_menu_Default").performScrollTo().performClick()
        compose.onNodeWithTag("overwrite_profile_Default").assertExists()
        compose.onNodeWithTag("manage_section").performScrollTo().performClick()
        compose.onNodeWithTag("save_profile_as").performScrollTo().assertExists()
        compose.onNodeWithTag("restore_factory").performScrollTo().assertExists()
    }

    @Test
    fun profiles_deleteAndOverwrite_requireConfirmation_D114() {
        // D-114: delete/overwrite must confirm first (Tasker behavior).
        var deleted: String? = null
        var overwritten: String? = null
        val profiles = listOf(com.tideo.autobrightness.app.settings.SavedProfile("Mine", AabSettings()))
        compose.setContent {
            MaterialTheme {
                com.tideo.autobrightness.app.ui.screens.ProfilesContent(
                    profiles = profiles, legacyEntries = emptyList(), contextLocked = false, status = null,
                    onBack = {}, onApplyProfile = {}, onOverwriteProfile = { overwritten = it }, onDeleteProfile = { deleted = it },
                    onSaveCurrentAs = {}, onRestoreFactory = {}, onResumeContext = {}, onReset = {},
                    onExport = {}, onImport = {}, onChooseLegacyFolder = {}, onLoadLegacy = {},
                )
            }
        }
        compose.onNodeWithTag("profile_menu_Mine").performScrollTo().performClick()
        compose.onNodeWithTag("delete_profile_Mine").performClick()
        assertEquals(null, deleted, "delete must not fire before confirmation")
        compose.onNodeWithTag("confirm_delete_Mine").performClick()
        assertEquals("Mine", deleted, "confirming the dialog deletes")

        compose.onNodeWithTag("profile_menu_Mine").performScrollTo().performClick()
        compose.onNodeWithTag("overwrite_profile_Mine").performClick()
        assertEquals(null, overwritten, "overwrite must not fire before confirmation")
        compose.onNodeWithTag("confirm_overwrite_Mine").performClick()
        assertEquals("Mine", overwritten, "confirming the dialog overwrites")
    }

    @Test
    fun contexts_deleteRule_requiresConfirmation_D114() {
        // D-114: delete rule requires confirmation (Tasker behavior).
        var deleted: String? = null
        val rule = com.tideo.autobrightness.app.settings.ContextRule(
            id = "r1", name = "Cinema", profile = "Default",
            triggers = com.tideo.autobrightness.app.settings.ContextTriggers(),
        )
        compose.setContent {
            MaterialTheme {
                ContextsContent(
                    rules = listOf(rule), profileNames = listOf("Default"), apps = emptyList(),
                    onBack = {}, onSave = {}, onDelete = { deleted = it },
                )
            }
        }
        compose.onNodeWithTag("delete_r1").performScrollTo().performClick()
        assertEquals(null, deleted, "delete must not fire before confirmation")
        compose.onNodeWithTag("confirm_delete_r1").performClick()
        assertEquals("r1", deleted, "confirming the dialog deletes the rule")
    }

    @Test
    fun profiles_contextLockBanner_offersResume() {
        // G2R-F30: manual profile load latches context lock; Profiles screen offers Resume.
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
        // G2R-F31: rule editor offers battery percentage from/to window.
        compose.setContent {
            MaterialTheme {
                ContextsContent(
                    rules = emptyList(), profileNames = listOf("Default"), apps = emptyList(),
                    onBack = {}, onSave = {}, onDelete = {},
                )
            }
        }
        compose.onNodeWithTag("add_context_rule").performClick()
        compose.onNodeWithTag("trigger_toggle_battery").performScrollTo().performClick()
        compose.onNodeWithTag("rule_batt_min").performScrollTo().assertExists()
        compose.onNodeWithTag("rule_batt_max").performScrollTo().assertExists()
    }

    @Test
    fun reactivity_deltaFactorHelp_rendersVerbatimTaskerText() {
        // G2R-F19/F21: tapping "ⓘ" reveals VERBATIM Tasker long-press help for delta-factor (task740 sensor smoothing).
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
        // G2R-F28: From/To inputs open Material3 TimePicker modal.
        compose.setContent {
            MaterialTheme {
                ContextsContent(
                    rules = emptyList(), profileNames = listOf("Default"), apps = emptyList(),
                    onBack = {}, onSave = {}, onDelete = {},
                )
            }
        }
        compose.onNodeWithTag("add_context_rule").performClick()
        compose.onNodeWithTag("trigger_toggle_time").performScrollTo().performClick()
        compose.onNodeWithTag("rule_start").performScrollTo().performClick()
        compose.onNodeWithTag("start_time_ok").assertExists()
    }

    @Test
    fun contextEditor_clearTime_nullsTimeRange_G2RF72() {
        // G2R-F72: "Clear time" blanks both fields, nulling saved rule's timeRange (time-agnostic again).
        val rule = com.tideo.autobrightness.app.settings.ContextRule(
            id = "r1", name = "Evening", profile = "Default",
            triggers = com.tideo.autobrightness.app.settings.ContextTriggers(
                timeRange = listOf("08:00", "20:00"),
            ),
        )
        var saved: com.tideo.autobrightness.app.settings.ContextRule? = null
        compose.setContent {
            MaterialTheme {
                ContextsContent(
                    rules = listOf(rule), profileNames = listOf("Default"), apps = emptyList(),
                    onBack = {}, onSave = { saved = it }, onDelete = {},
                )
            }
        }
        compose.onNodeWithTag("edit_r1").performScrollTo().performClick()
        compose.onNodeWithTag("clear_time").performScrollTo().performClick()
        compose.onNodeWithTag("save_rule").performScrollTo().performClick()
        assertEquals(null, saved?.triggers?.timeRange, "Clear time must null the saved time range")
    }

    @Test
    fun contextEditor_useCurrentSsid_fillsField() {
        // G2R-F22: "use current Wi-Fi" fills SSID field.
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
        compose.onNodeWithTag("trigger_toggle_wifi").performScrollTo().performClick()
        compose.onNodeWithTag("use_current_ssid").performScrollTo().performClick()
        compose.onNodeWithText("HomeNet", substring = true).assertExists()
    }

    @Test
    fun contextEditor_exposesLocationFields() {
        // G2R-F22: lat/lon/radius editor + "use current location" (live location).
        compose.setContent {
            MaterialTheme {
                ContextsContent(
                    rules = emptyList(), profileNames = listOf("Default"), apps = emptyList(),
                    onBack = {}, onSave = {}, onDelete = {},
                )
            }
        }
        compose.onNodeWithTag("add_context_rule").performClick()
        compose.onNodeWithTag("trigger_toggle_location").performScrollTo().performClick()
        compose.onNodeWithTag("use_current_location").performScrollTo().assertExists()
        compose.onNodeWithTag("rule_lat").performScrollTo().assertExists()
        compose.onNodeWithTag("rule_radius").performScrollTo().assertExists()
    }

    @Test
    fun contextEditor_triggersCollapsedByDefault_radiusDefaults200_G3() {
        // G3: new rule starts with triggers collapsed; Location radius pre-filled to 200 m.
        compose.setContent {
            MaterialTheme {
                ContextsContent(
                    rules = emptyList(), profileNames = listOf("Default"), apps = emptyList(),
                    onBack = {}, onSave = {}, onDelete = {},
                )
            }
        }
        compose.onNodeWithTag("add_context_rule").performClick()
        compose.onNodeWithTag("rule_wifi").assertDoesNotExist()
        compose.onNodeWithTag("trigger_toggle_location").performScrollTo().performClick()
        compose.onNodeWithTag("rule_radius").performScrollTo().assertTextContains("200", substring = true)
    }

    @Test
    fun contextEditor_dayPicker_savesSelectedDays_G2RF67() {
        // G2R-F67: rule editor exposes day-of-week picker; selection persisted as DAY_OF_WEEK (Monday = 2).
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
        compose.onNodeWithTag("trigger_toggle_time").performScrollTo().performClick()
        compose.onNodeWithTag("day_2").performScrollTo().performClick()
        compose.onNodeWithTag("save_rule").performScrollTo().performClick()
        assertEquals(listOf(2), saved?.triggers?.days, "Monday must be saved as DAY_OF_WEEK 2")
    }

    @Test
    fun contextEditor_sunriseToken_showsResolvedTime_G2RF68() {
        // G2R-F68: SUNRISE token shows resolved time when solar times are known.
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
        compose.onNodeWithTag("trigger_toggle_time").performScrollTo().performClick()
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
        // G2R-F50: opt-in global-flash card shows enablement status + Accessibility CTA.
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
    fun liveDebug_panicSensitivityCard_rendersSliderAndValue() {
        // D-116: the GLOBAL %AAB_PanicSensitivity slider sits at the bottom of the Live Debug scene.
        compose.setContent {
            MaterialTheme {
                LiveDebugContent(
                    state = LiveDebugUiState(serviceRunning = true, panicSensitivity = 8),
                    onSelectDebug = {}, onBack = {},
                )
            }
        }
        compose.onNodeWithTag("panic_sensitivity_card").performScrollTo().assertExists()
        compose.onNodeWithTag("panic_sensitivity_slider").performScrollTo().assertExists()
        compose.onNodeWithText("Level: 8 / 10", substring = true).performScrollTo().assertExists()
    }

    @Test
    fun liveDebug_panicSensitivityCard_showsPassThroughAtZero() {
        // 0 is pass-through (the panic fires with no shake requirement) — surfaced distinctly.
        compose.setContent {
            MaterialTheme {
                LiveDebugContent(
                    state = LiveDebugUiState(serviceRunning = true, panicSensitivity = 0),
                    onSelectDebug = {}, onBack = {},
                )
            }
        }
        // "(pass-through)" (parenthesised) is unique to the value label — the help text says
        // "= pass-through (disables …" so a bare "pass-through" would match two nodes.
        compose.onNodeWithText("(pass-through)", substring = true).performScrollTo().assertExists()
    }

    @Test
    fun curveChart_legend_distinguishesReferenceAndCurve_G2RF66() {
        // F66/F69: a committed snapshot differing from the draft gives a fixed dashed "Reference" line
        // alongside the live "Curve"; the legend names both.
        val draft = AabSettings(form1A = 8.0)
        val committed = AabSettings(form1A = 5.0)
        compose.setContent {
            MaterialTheme {
                CurveBrightnessContent(draft, committed, emptyList(), epoch = 0, dirty = true, {}, {}, {}, {})
            }
        }
        compose.onNodeWithTag("legend_Curve").assertExists()
        compose.onNodeWithTag("legend_Reference").assertExists()
    }

    @Test
    fun curveChart_overrideDeleteDialog_confirmsDeletion_G2RF36() {
        // F36: the tap-to-delete confirm dialog shows the lux/brightness pair and fires onConfirm.
        var confirmed = false
        compose.setContent {
            MaterialTheme {
                com.tideo.autobrightness.app.ui.screens.OverridePointDeleteDialog(
                    point = com.tideo.autobrightness.domain.wizard.OverridePoint(123.0, 88.0),
                    onConfirm = { confirmed = true },
                    onDismiss = {},
                )
            }
        }
        compose.onNodeWithText("123", substring = true).assertExists()
        compose.onNodeWithText("88", substring = true).assertExists()
        compose.onNodeWithTag("override_delete_confirm").performClick()
        assertTrue(confirmed, "confirming the dialog deletes the point")
    }

    @Test
    fun toolsWizard_under9RealPoints_doesNotRunAndWarns_G2RF62() {
        // G2R-F62: with only 7 real recorded points the wizard must NOT fit (the domain engine would
        // otherwise inject ghost priors to clear its own ≥9 gate). The run callback must not fire.
        var ran = false
        val sevenPoints = (1..7).map {
            com.tideo.autobrightness.domain.wizard.OverridePoint(it * 10.0, it * 20.0)
        }
        compose.setContent {
            MaterialTheme {
                ToolsContent(
                    onBack = {},
                    onRunWizard = { _, _ -> ran = true; null },
                    onApplyWizard = {},
                    recordedPoints = sevenPoints,
                )
            }
        }
        compose.onNodeWithTag("run_wizard").performScrollTo().performClick()
        assertTrue(!ran, "the wizard must not run with fewer than 9 real points")
        compose.onNodeWithText("need ≥ 9 real points", substring = true).assertExists()
    }

    @Test
    fun toolsWizard_previewGraphButton_passesTheFit_D125() {
        // D-125: "Preview graph" hands the wizard's actual fit to the preview path (which loads it into
        // the Curve & Brightness draft) — it is no longer an auto-fit that fires merely from ≥ 9 points.
        // The button only appears after a successful run, so the previewed result must be that fit.
        var previewed: com.tideo.autobrightness.domain.wizard.CurveSuggestionResult? = null
        val ninePoints = (1..9).map {
            com.tideo.autobrightness.domain.wizard.OverridePoint(it * 10.0, it * 20.0)
        }
        val stubResult = com.tideo.autobrightness.domain.wizard.CurveSuggestionResult(
            zone1End = 35L, zone2End = 10_000L, form1a = "5.0", form2a = "1.0", form2b = "8.8",
            form2c = "18", form2d = 0L, form3a = "1.0", diagnosticsLog = "ok", qualityLines = listOf("R²=0.99"),
        )
        compose.setContent {
            MaterialTheme {
                ToolsContent(
                    onBack = {},
                    onRunWizard = { _, _ -> stubResult },
                    onApplyWizard = {},
                    recordedPoints = ninePoints,
                    onPreviewGraph = { previewed = it },
                )
            }
        }
        compose.onNodeWithTag("run_wizard").performScrollTo().performClick()
        compose.onNodeWithTag("preview_graph").performScrollTo().performClick()
        assertEquals(stubResult, previewed, "Preview graph forwards the wizard's fit, not an auto-fit")
    }

    @Test
    fun settingsDiffList_highlightsChangedFromDefault_G2RF38() {
        // G2R-F38: Tasker dashboard settings list flags changed-vs-default (minBrightness 99); summary counts it.
        compose.setContent {
            MaterialTheme { SettingsDiffList(AabSettings(minBrightness = 99)) }
        }
        compose.onNodeWithTag("settings_diff_summary")
            .assertTextContains("1 setting", substring = true)
        compose.onNodeWithTag("diffval_%AAB_MinBright").performScrollTo()
            .assertTextContains("99", substring = true)
    }

    @Test
    fun settingsDiffList_allDefaults_reportsNoChanges_G2RF38() {
        compose.setContent {
            MaterialTheme { SettingsDiffList(AabSettings()) }
        }
        compose.onNodeWithText("All settings at factory defaults", substring = true).assertExists()
    }

    @Test
    fun loadProfileDialog_showsDiffAndConfirms_G2RF38() {
        // G2R-F38: "Load Anyway" modal previews profile settings; Apply fires onConfirm.
        var confirmed = false
        compose.setContent {
            MaterialTheme {
                LoadProfileDialog(
                    profile = SavedProfile("Bright", AabSettings(maxBrightness = 255, scale = 1.5f)),
                    onDismiss = {},
                    onConfirm = { confirmed = true },
                )
            }
        }
        compose.onNodeWithTag("settings_diff_list").assertExists()
        compose.onNodeWithTag("confirm_load_profile").performClick()
        assertTrue(confirmed, "Apply in the load modal confirms the load")
    }

    @Test
    fun circadianDateLocationCard_defaultsToTodayAndCurrentLocation_G2RF39() {
        // G2R-F39: unpinned fields default to today + current location.
        compose.setContent {
            MaterialTheme {
                CircadianDateLocationCard(
                    value = ExperimentDateLocation(),
                    todayDate = "2026-06-15",
                    currentLatLon = 55.95000 to -3.19000,
                    onSet = { _, _, _ -> },
                    onUseLiveData = {},
                )
            }
        }
        compose.onNodeWithTag("exp_status").assertTextContains("Live data", substring = true)
        compose.onNodeWithTag("exp_date_value").assertTextContains("2026-06-15", substring = true)
        compose.onNodeWithTag("exp_lat").assertTextContains("55.95000", substring = true)
        compose.onNodeWithTag("exp_lon").assertTextContains("-3.19000", substring = true)
    }

    @Test
    fun circadianDateLocationCard_setFixed_emitsDateAndCoords_G2RF39() {
        var captured: Triple<String?, Double?, Double?>? = null
        compose.setContent {
            MaterialTheme {
                CircadianDateLocationCard(
                    value = ExperimentDateLocation(date = "2025-12-21", latitude = 51.5, longitude = 0.0),
                    todayDate = "2026-06-15",
                    currentLatLon = null,
                    onSet = { d, la, lo -> captured = Triple(d, la, lo) },
                    onUseLiveData = {},
                )
            }
        }
        compose.onNodeWithTag("exp_status").assertTextContains("Fixed", substring = true)
        compose.onNodeWithTag("exp_set").performClick()
        assertEquals(Triple("2025-12-21", 51.5, 0.0), captured)
    }

    @Test
    fun circadianDateLocationCard_setDateOnly_emitsNullCoords_G2RF39() {
        // F39: fixed date with blank coords pins date only (live location); coords null.
        var captured: Triple<String?, Double?, Double?>? = null
        compose.setContent {
            MaterialTheme {
                CircadianDateLocationCard(
                    value = ExperimentDateLocation(),
                    todayDate = "2026-06-15",
                    currentLatLon = null, // no current fix → lat/lon fields blank
                    onSet = { d, la, lo -> captured = Triple(d, la, lo) },
                    onUseLiveData = {},
                )
            }
        }
        // Picking a day is what pins the date; the picker opens on the displayed day.
        compose.onNodeWithTag("exp_date_value").performClick()
        compose.onNodeWithTag("exp_date_ok").performClick()
        compose.onNodeWithTag("exp_set").performClick()
        assertEquals(Triple("2026-06-15", null, null), captured)
    }

    @Test
    fun circadianDateLocationCard_setLocationOnly_emitsNullDate_DB084() {
        // DB-084: fixed location with a live date, as Tasker's picker allows.
        var captured: Triple<String?, Double?, Double?>? = null
        compose.setContent {
            MaterialTheme {
                CircadianDateLocationCard(
                    value = ExperimentDateLocation(),
                    todayDate = "2026-06-15",
                    currentLatLon = 55.95 to -3.19, // prefills the coord fields
                    onSet = { d, la, lo -> captured = Triple(d, la, lo) },
                    onUseLiveData = {},
                )
            }
        }
        compose.onNodeWithTag("exp_set").performClick()
        assertEquals(Triple(null, 55.95, -3.19), captured)
    }

    @Test
    fun circadianDateLocationCard_liveDateButton_unpinsDate_DB084() {
        // DB-084: "Live date" releases a pinned date without touching the pinned coordinates.
        var captured: Triple<String?, Double?, Double?>? = null
        compose.setContent {
            MaterialTheme {
                CircadianDateLocationCard(
                    value = ExperimentDateLocation(date = "2025-12-21", latitude = 51.5, longitude = 0.0),
                    todayDate = "2026-06-15",
                    currentLatLon = null,
                    onSet = { d, la, lo -> captured = Triple(d, la, lo) },
                    onUseLiveData = {},
                )
            }
        }
        compose.onNodeWithTag("exp_date_live").performClick()
        compose.onNodeWithTag("exp_set").performClick()
        assertEquals(Triple(null, 51.5, 0.0), captured)
    }

    @Test
    fun circadianDateLocationCard_locationOnlyStatus_saysLiveDate_DB084() {
        compose.setContent {
            MaterialTheme {
                CircadianDateLocationCard(
                    value = ExperimentDateLocation(latitude = 51.5, longitude = 0.0),
                    todayDate = "2026-06-15",
                    currentLatLon = null,
                    onSet = { _, _, _ -> },
                    onUseLiveData = {},
                )
            }
        }
        compose.onNodeWithTag("exp_status").assertTextContains("live date", substring = true)
    }

    @Test
    fun chartPager_rendersSlotsWithSwipeIndicator_G2RF81() {
        // G2R-F81: multi-graph screen pages between relevant charts (dots per page), not vertical stack.
        compose.setContent {
            MaterialTheme {
                ChartPager(
                    listOf(
                        ChartSlot("First", "chart_a") { EmptyState("First", testTag = "chart_a") },
                        ChartSlot("Second", "chart_b") { EmptyState("Second", testTag = "chart_b") },
                    ),
                )
            }
        }
        compose.onNodeWithTag("chart_pager").assertExists()
        compose.onNodeWithTag("chart_pager_dot_0").assertExists()
        compose.onNodeWithTag("chart_pager_dot_1").assertExists()
        compose.onNodeWithTag("chart_a").assertExists()
    }

    @Test
    fun reactivity_graphAboveSettings_andGroupedByGraph_G2RF81F82() {
        // G2R-F81/F82: reactivity screen hosts chart pager above settings; groups threshold fields by graph.
        compose.setContent {
            MaterialTheme {
                ReactivityContent(
                    AabSettings(), AabSettings(), epoch = 0, dirty = false,
                    onEdit = {}, onApply = {}, onDiscard = {}, onBack = {},
                )
            }
        }
        compose.onNodeWithTag("chart_pager").performScrollTo().assertExists()
        compose.onNodeWithTag("group_Reactivity curve").performScrollTo().assertExists()
        compose.onNodeWithTag("group_Smoothing α").performScrollTo().assertExists()
    }

    @Test
    fun superDimming_chartPagerRendersAboveSettings_G2RF81() {
        // G2R-F81: dimming chart sits above settings.
        compose.setContent {
            MaterialTheme {
                SuperDimmingContent(
                    AabSettings(), AabSettings(), epoch = 0, dirty = false, tier = Tier.ELEVATED,
                    onEdit = {}, onApply = {}, onDiscard = {}, onBack = {}, onOpenOnboarding = {},
                )
            }
        }
        compose.onNodeWithTag("chart_pager").assertExists()
        compose.onNodeWithTag("group_Dimming curve").performScrollTo().assertExists()
    }

    @Test
    fun contextEditor_sunsetToken_rendersResolvedTimeOnOneLine_G2RF68() {
        // G2R-F68: "Sunset (HH:MM)" renders full resolved-time label on one line (no vertical wrap).
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
        compose.onNodeWithTag("trigger_toggle_time").performScrollTo().performClick()
        compose.onNodeWithTag("end_sunset").performScrollTo()
            .assertTextContains("Sunset (18:30)", substring = true)
    }

    @Test
    fun profilesContextsMerge_showsBothSurfaces_andEditsRuleInModal_S12_9f() {
        // S12.9f (D-070): merged Profiles & Contexts surface hosts both; rule editing in modal.
        var savedRule: com.tideo.autobrightness.app.settings.ContextRule? = null
        val rule = com.tideo.autobrightness.app.settings.ContextRule(
            id = "r1", name = "Cinema", profile = "Movies",
            triggers = com.tideo.autobrightness.app.settings.ContextTriggers(wifi = listOf("Home")),
        )
        compose.setContent {
            MaterialTheme {
                com.tideo.autobrightness.app.ui.components.SettingsColumn(
                    androidx.compose.foundation.layout.PaddingValues(0.dp),
                ) {
                    com.tideo.autobrightness.app.ui.screens.ProfilesBody(
                        profiles = listOf(SavedProfile("Movies", AabSettings())),
                        legacyEntries = emptyList(), contextLocked = false, status = null,
                        onApplyProfile = {}, onOverwriteProfile = {}, onDeleteProfile = {},
                        onSaveCurrentAs = {}, onRestoreFactory = {}, onResumeContext = {}, onReset = {},
                        onExport = {}, onImport = {}, onChooseLegacyFolder = {}, onLoadLegacy = {},
                    )
                    com.tideo.autobrightness.app.ui.screens.ContextRulesSection(
                        rules = listOf(rule), profileNames = listOf("Movies"), apps = emptyList(),
                        onSave = { savedRule = it }, onDelete = {},
                    )
                }
            }
        }

        compose.onNodeWithTag("profile_Movies").performScrollTo().assertExists()
        compose.onNodeWithTag("rule_r1").performScrollTo().assertExists()
        compose.onNodeWithTag("edit_r1").performScrollTo().performClick()
        compose.onNodeWithTag("rule_editor_modal").assertExists()
        compose.onNodeWithTag("rule_name").performScrollTo().assertExists()
        compose.onNodeWithTag("save_rule").performScrollTo().performClick()
        assertEquals("Cinema", savedRule?.name, "saving the rule in the modal routes through onSave")
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
