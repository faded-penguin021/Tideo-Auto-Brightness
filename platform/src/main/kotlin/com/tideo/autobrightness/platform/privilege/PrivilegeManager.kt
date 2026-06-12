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
    fun adbGrantInstruction(): String
    fun tryGrantViaRoot(): Boolean
    fun requestShizukuGrant()
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

    // Full Shizuku exec (pm grant via user-service) is wired in S11.
    // This lands the binder-check + permission-request half; ShizukuGrantGateway stubs the exec.
    override fun requestShizukuGrant() {
        ShizukuGrantGateway.requestGrant(context) { refresh() }
    }
}
