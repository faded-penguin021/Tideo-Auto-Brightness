package com.tideo.autobrightness.platform.privilege

import android.content.Context
import android.content.pm.PackageManager
import rikka.shizuku.Shizuku

/**
 * Shizuku integration for one-time WRITE_SECURE_SETTINGS grant (D-024: grant channel only).
 *
 * S7 LIMITATION (STATE.md D-032): Shizuku.newProcess / user-service exec path for the actual
 * `pm grant` command requires a bound user-service component — deferred to S11.
 * What IS landed: binder availability check + permission request. The grant call itself is
 * a TODO comment; S11 completes it. After any successful grant the app reads Settings.Secure
 * directly — Shizuku is not a runtime binder dependency.
 */
object ShizukuGrantGateway {
    private const val REQUEST_CODE = 1001

    fun isAvailable(): Boolean = try {
        Shizuku.pingBinder()
    } catch (_: Exception) {
        false
    }

    /**
     * Requests Shizuku permission then (TODO S11) execs `pm grant WRITE_SECURE_SETTINGS`.
     * [onGranted] is called once the permission arrives; caller should invoke
     * [AndroidPrivilegeManager.refresh] there (passed in by PrivilegeManager).
     */
    fun requestGrant(context: Context, onGranted: () -> Unit) {
        if (!isAvailable()) return
        val listener = object : Shizuku.OnRequestPermissionResultListener {
            override fun onRequestPermissionResult(requestCode: Int, grantResult: Int) {
                if (requestCode == REQUEST_CODE && grantResult == PackageManager.PERMISSION_GRANTED) {
                    // TODO (S11): exec via Shizuku user-service:
                    // "pm grant ${context.packageName} android.permission.WRITE_SECURE_SETTINGS"
                    Shizuku.removeRequestPermissionResultListener(this)
                    onGranted()
                }
            }
        }
        Shizuku.addRequestPermissionResultListener(listener)
        Shizuku.requestPermission(REQUEST_CODE)
    }
}
