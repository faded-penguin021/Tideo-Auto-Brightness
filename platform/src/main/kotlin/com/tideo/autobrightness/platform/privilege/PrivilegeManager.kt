package com.tideo.autobrightness.platform.privilege

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

// Tasker: task378 _DetectPrivilege — first-hit probe: WRITE_SECURE → WRITE_SETTINGS → NONE.
// D-016: ADB/Shizuku/root are GRANT channels only; elevated truth = checkPermission.
// D-024: adbwp Tasker pref must NOT be read; BASIC = canWrite, ELEVATED = checkPermission.
enum class Tier { NONE, BASIC, ELEVATED }

interface PrivilegeManager {
    fun currentTier(): Tier
    fun tierFlow(): StateFlow<Tier>
    fun refresh()
    /** The ADB `pm grant` command — ALWAYS offered (the no-companion-app grant channel). */
    fun adbGrantInstruction(): String
    fun tryGrantViaRoot(): Boolean
    /**
     * Three-state Shizuku readiness so the UI can offer the one-tap grant (RUNNING), prompt to start
     * the app (INSTALLED_NOT_RUNNING), or hide the Shizuku path entirely (NOT_INSTALLED). The ADB path
     * is offered regardless of this value.
     */
    fun shizukuAvailability(): ShizukuAvailability
    /**
     * Runs the Shizuku grant flow (permission request → user-service `pm grant`). [onResult] reports
     * the outcome for the UI; on success the tier is refreshed before [onResult] fires.
     */
    fun requestShizukuGrant(onResult: (ShizukuGrantGateway.Result) -> Unit)
    /** Intent for the BASIC grant: system "Modify system settings" screen for this app. */
    fun writeSettingsIntent(): Intent
}

class AndroidPrivilegeManager(private val context: Context) : PrivilegeManager {
    private val _tierFlow = MutableStateFlow(detectTier())

    private fun detectTier(): Tier {
        val elevated = context.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) ==
                PackageManager.PERMISSION_GRANTED
        if (elevated) return Tier.ELEVATED
        if (Settings.System.canWrite(context)) return Tier.BASIC
        return Tier.NONE
    }

    override fun currentTier(): Tier = _tierFlow.value

    override fun tierFlow(): StateFlow<Tier> = _tierFlow.asStateFlow()

    override fun refresh() {
        _tierFlow.value = detectTier()
    }

    override fun writeSettingsIntent(): Intent =
        Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:${context.packageName}"))

    override fun adbGrantInstruction(): String =
        "adb shell pm grant ${context.packageName} android.permission.WRITE_SECURE_SETTINGS"

    override fun tryGrantViaRoot(): Boolean = try {
        val process = Runtime.getRuntime().exec(
            arrayOf("su", "-c", "pm grant ${context.packageName} android.permission.WRITE_SECURE_SETTINGS")
        )
        if (process.waitFor() == 0) {
            refresh()
            currentTier() >= Tier.ELEVATED
        } else false
    } catch (_: Exception) {
        false
    }

    override fun shizukuAvailability(): ShizukuAvailability = ShizukuGrantGateway.availability(context)

    // S11 (D-032 closed): full Shizuku grant via a bound user service that execs `pm grant`.
    override fun requestShizukuGrant(onResult: (ShizukuGrantGateway.Result) -> Unit) {
        ShizukuGrantGateway.requestGrant(context) { result ->
            if (result is ShizukuGrantGateway.Result.Success) refresh()
            onResult(result)
        }
    }
}
