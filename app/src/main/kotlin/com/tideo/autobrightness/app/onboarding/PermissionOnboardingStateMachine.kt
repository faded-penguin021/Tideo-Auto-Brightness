package com.tideo.autobrightness.app.onboarding

/**
 * Ordered permission onboarding state machine.
 *
 * This replaces polling loops by:
 * 1) launching a settings/permission screen once,
 * 2) waiting for lifecycle-aware callback from [OnboardingResultLauncher],
 * 3) re-checking grant state in [onReturnFromStep] when the user returns.
 */
class PermissionOnboardingStateMachine(
    private val gateway: PermissionGateway,
    private val launcher: OnboardingResultLauncher
) {

    private var activeStep: PermissionStep? = null

    val orderedSteps: List<PermissionStep> = listOf(
        PermissionStep.WriteSettings,
        PermissionStep.Overlay,
        PermissionStep.UsageStats,
        PermissionStep.FileAccess,
        PermissionStep.Location,
        PermissionStep.Notifications,
        PermissionStep.IgnoreBatteryOptimizations,
        PermissionStep.ExactAlarm
    )

    /** Returns first pending and eligible step, or null when onboarding is complete. */
    fun nextPendingStep(): PermissionStep? =
        orderedSteps.firstOrNull { step -> step.isEligible(gateway) && !step.isGranted(gateway) }

    /** Starts the next step and launches its intent in a lifecycle-aware way. */
    fun launchNextIfNeeded() {
        val step = nextPendingStep() ?: return
        activeStep = step
        launcher.launch(step.intent(gateway))
    }

    /**
     * Should be called from an ActivityResult callback / ON_RESUME re-entry hook.
     *
     * Re-checks the previously launched step instead of polling in a loop.
     */
    fun onReturnFromStep() {
        val step = activeStep ?: return
        val granted = step.verifyAfterReturn(gateway)

        activeStep = if (granted) {
            null
        } else {
            // Keep same active step so UI can re-show rationale and retry.
            step
        }
    }

    fun currentRationale(): String? = activeStep?.rationale(gateway)
}

/**
 * Thin abstraction over ActivityResultLauncher / registerForActivityResult.
 */
fun interface OnboardingResultLauncher {
    fun launch(request: PermissionIntentRequest)
}

interface PermissionGateway {
    fun canWriteSettings(): Boolean
    fun canDrawOverlays(): Boolean
    fun hasUsageStatsAccess(): Boolean

    /** Scoped storage strategy: either SAF tree/document access or media-specific grants. */
    fun hasScopedFileAccess(): Boolean

    fun needsLocationForFeatures(): Boolean
    fun hasFineOrCoarseLocation(): Boolean
    fun requiresBackgroundLocation(): Boolean
    fun hasBackgroundLocation(): Boolean

    fun notificationsRequired(): Boolean
    fun hasNotificationPermission(): Boolean

    fun batteryOptimizationExemptionRequired(): Boolean
    fun isIgnoringBatteryOptimizations(): Boolean

    fun exactAlarmRequired(): Boolean
    fun hasExactAlarmPermission(): Boolean
}

sealed class PermissionStep(private val id: String) {

    object WriteSettings : PermissionStep("WRITE_SETTINGS") {
        override fun isEligible(gateway: PermissionGateway): Boolean = true
        override fun rationale(gateway: PermissionGateway): String =
            "Allow write system settings so automatic brightness can be applied reliably."

        override fun intent(gateway: PermissionGateway): PermissionIntentRequest =
            PermissionIntentRequest.OpenWriteSettings

        override fun verifyAfterReturn(gateway: PermissionGateway): Boolean = gateway.canWriteSettings()
        override fun isGranted(gateway: PermissionGateway): Boolean = gateway.canWriteSettings()
    }

    object Overlay : PermissionStep("OVERLAY") {
        override fun isEligible(gateway: PermissionGateway): Boolean = true
        override fun rationale(gateway: PermissionGateway): String =
            "Allow overlay access for heads-up diagnostics and quick controls."

        override fun intent(gateway: PermissionGateway): PermissionIntentRequest =
            PermissionIntentRequest.OpenOverlaySettings

        override fun verifyAfterReturn(gateway: PermissionGateway): Boolean = gateway.canDrawOverlays()
        override fun isGranted(gateway: PermissionGateway): Boolean = gateway.canDrawOverlays()
    }

    object UsageStats : PermissionStep("USAGE_STATS") {
        override fun isEligible(gateway: PermissionGateway): Boolean = true
        override fun rationale(gateway: PermissionGateway): String =
            "Usage access improves context awareness for app-based brightness behavior."

        override fun intent(gateway: PermissionGateway): PermissionIntentRequest =
            PermissionIntentRequest.OpenUsageStatsSettings

        override fun verifyAfterReturn(gateway: PermissionGateway): Boolean = gateway.hasUsageStatsAccess()
        override fun isGranted(gateway: PermissionGateway): Boolean = gateway.hasUsageStatsAccess()
    }

    object FileAccess : PermissionStep("FILE_ACCESS") {
        override fun isEligible(gateway: PermissionGateway): Boolean = true
        override fun rationale(gateway: PermissionGateway): String =
            "Grant scoped file access (SAF/media) to import/export profiles without broad storage permission."

        override fun intent(gateway: PermissionGateway): PermissionIntentRequest =
            PermissionIntentRequest.RequestScopedFileAccess

        override fun verifyAfterReturn(gateway: PermissionGateway): Boolean = gateway.hasScopedFileAccess()
        override fun isGranted(gateway: PermissionGateway): Boolean = gateway.hasScopedFileAccess()
    }

    object Location : PermissionStep("LOCATION") {
        override fun isEligible(gateway: PermissionGateway): Boolean = gateway.needsLocationForFeatures()
        override fun rationale(gateway: PermissionGateway): String =
            if (gateway.requiresBackgroundLocation()) {
                "Location is used for sunrise/sunset automation; allow foreground + background location."
            } else {
                "Location is used for sunrise/sunset automation; allow foreground location."
            }

        override fun intent(gateway: PermissionGateway): PermissionIntentRequest =
            if (gateway.requiresBackgroundLocation()) {
                PermissionIntentRequest.RequestForegroundAndBackgroundLocation
            } else {
                PermissionIntentRequest.RequestForegroundLocation
            }

        override fun verifyAfterReturn(gateway: PermissionGateway): Boolean =
            gateway.hasFineOrCoarseLocation() && (!gateway.requiresBackgroundLocation() || gateway.hasBackgroundLocation())

        override fun isGranted(gateway: PermissionGateway): Boolean = verifyAfterReturn(gateway)
    }

    object Notifications : PermissionStep("NOTIFICATIONS") {
        override fun isEligible(gateway: PermissionGateway): Boolean = gateway.notificationsRequired()
        override fun rationale(gateway: PermissionGateway): String =
            "Enable notifications for status, errors, and persistent service indicators."

        override fun intent(gateway: PermissionGateway): PermissionIntentRequest =
            PermissionIntentRequest.RequestNotifications

        override fun verifyAfterReturn(gateway: PermissionGateway): Boolean = gateway.hasNotificationPermission()
        override fun isGranted(gateway: PermissionGateway): Boolean = gateway.hasNotificationPermission()
    }

    object IgnoreBatteryOptimizations : PermissionStep("IGNORE_BATTERY_OPTIMIZATIONS") {
        override fun isEligible(gateway: PermissionGateway): Boolean = gateway.batteryOptimizationExemptionRequired()
        override fun rationale(gateway: PermissionGateway): String =
            "Disable battery optimization for uninterrupted background brightness automation."

        override fun intent(gateway: PermissionGateway): PermissionIntentRequest =
            PermissionIntentRequest.OpenIgnoreBatteryOptimizationSettings

        override fun verifyAfterReturn(gateway: PermissionGateway): Boolean = gateway.isIgnoringBatteryOptimizations()
        override fun isGranted(gateway: PermissionGateway): Boolean = gateway.isIgnoringBatteryOptimizations()
    }

    object ExactAlarm : PermissionStep("EXACT_ALARM") {
        override fun isEligible(gateway: PermissionGateway): Boolean = gateway.exactAlarmRequired()
        override fun rationale(gateway: PermissionGateway): String =
            "Allow exact alarms when strict schedule precision is enabled."

        override fun intent(gateway: PermissionGateway): PermissionIntentRequest =
            PermissionIntentRequest.OpenExactAlarmSettings

        override fun verifyAfterReturn(gateway: PermissionGateway): Boolean = gateway.hasExactAlarmPermission()
        override fun isGranted(gateway: PermissionGateway): Boolean = gateway.hasExactAlarmPermission()
    }

    abstract fun isEligible(gateway: PermissionGateway): Boolean
    abstract fun rationale(gateway: PermissionGateway): String
    abstract fun intent(gateway: PermissionGateway): PermissionIntentRequest
    abstract fun verifyAfterReturn(gateway: PermissionGateway): Boolean
    abstract fun isGranted(gateway: PermissionGateway): Boolean

    override fun toString(): String = id
}

sealed class PermissionIntentRequest {
    data object OpenWriteSettings : PermissionIntentRequest()
    data object OpenOverlaySettings : PermissionIntentRequest()
    data object OpenUsageStatsSettings : PermissionIntentRequest()
    data object RequestScopedFileAccess : PermissionIntentRequest()
    data object RequestForegroundLocation : PermissionIntentRequest()
    data object RequestForegroundAndBackgroundLocation : PermissionIntentRequest()
    data object RequestNotifications : PermissionIntentRequest()
    data object OpenIgnoreBatteryOptimizationSettings : PermissionIntentRequest()
    data object OpenExactAlarmSettings : PermissionIntentRequest()
}
