package com.tideo.autobrightness.platform.privilege

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import rikka.shizuku.Shizuku
import kotlin.concurrent.thread

/**
 * Three-state Shizuku readiness, so the onboarding UI can tell *not installed* (offer ADB only) from
 * *installed but not running* (prompt the user to start the Shizuku app) from *running* (offer the
 * one-tap grant). `pingBinder()` alone collapses the first two into a single "unavailable".
 */
enum class ShizukuAvailability { RUNNING, INSTALLED_NOT_RUNNING, NOT_INSTALLED }

/**
 * Shizuku integration for the one-time WRITE_SECURE_SETTINGS grant (D-024: this gateway is the grant
 * channel only; after the grant the dimming path writes Settings.Secure directly, with no Shizuku
 * binder). NB (G3-F9): Shizuku has one *other*, optional runtime use elsewhere — the no-Location
 * Wi-Fi SSID strategy (`ShizukuWifiSsidStrategy` → `cmd wifi status`); that is a separate code path,
 * not this gateway, and the brightness pipeline never binds Shizuku.
 *
 * S11 completes the path S7 stubbed (D-032): request the Shizuku permission, then bind a
 * privileged [ShizukuUserService] (via [Shizuku.bindUserService]) and have it exec `pm grant`.
 * Implemented per the documented Shizuku user-service pattern (AIDL [IShizukuUserService]); we do
 * NOT reflect into hidden `Shizuku.newProcess` (owner-reported to be fragile in factory apps).
 */
object ShizukuGrantGateway {
    private const val REQUEST_CODE = 1001

    /** The Shizuku manager app package (Shizuku + the legacy Sui-less builds both use this id). */
    const val SHIZUKU_PACKAGE = "moe.shizuku.privileged.api"

    /** Outcome of a grant attempt, surfaced to the onboarding UI. */
    sealed interface Result {
        data object Success : Result
        data object Unavailable : Result
        data object PermissionDenied : Result
        data class Failed(val reason: String) : Result
    }

    fun isAvailable(): Boolean = try {
        Shizuku.pingBinder()
    } catch (_: Throwable) {
        false
    }

    /** Whether the Shizuku manager app is installed (regardless of whether its service is running). */
    fun isInstalled(context: Context): Boolean = try {
        context.packageManager.getPackageInfo(SHIZUKU_PACKAGE, 0)
        true
    } catch (_: PackageManager.NameNotFoundException) {
        false
    } catch (_: Throwable) {
        false
    }

    /** Collapse the binder ping + install check into the three-state [ShizukuAvailability]. */
    fun availability(context: Context): ShizukuAvailability = when {
        isAvailable() -> ShizukuAvailability.RUNNING
        isInstalled(context) -> ShizukuAvailability.INSTALLED_NOT_RUNNING
        else -> ShizukuAvailability.NOT_INSTALLED
    }

    private fun hasPermission(): Boolean = try {
        Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    } catch (_: Throwable) {
        false
    }

    /**
     * Requests Shizuku permission if needed, then binds the user service and grants
     * WRITE_SECURE_SETTINGS. [onResult] is always invoked exactly once (it may arrive on a
     * background thread — callers marshal to the UI thread themselves).
     */
    fun requestGrant(context: Context, onResult: (Result) -> Unit) {
        if (!isAvailable()) {
            onResult(Result.Unavailable)
            return
        }
        if (hasPermission()) {
            bindAndGrant(context, onResult)
            return
        }
        val listener = object : Shizuku.OnRequestPermissionResultListener {
            override fun onRequestPermissionResult(requestCode: Int, grantResult: Int) {
                if (requestCode != REQUEST_CODE) return
                // Remove on ANY result for our code — leaving it registered on denial leaks the
                // listener and re-fires it on unrelated future requests.
                Shizuku.removeRequestPermissionResultListener(this)
                if (grantResult == PackageManager.PERMISSION_GRANTED) {
                    bindAndGrant(context, onResult)
                } else {
                    onResult(Result.PermissionDenied)
                }
            }
        }
        Shizuku.addRequestPermissionResultListener(listener)
        try {
            Shizuku.requestPermission(REQUEST_CODE)
        } catch (t: Throwable) {
            Shizuku.removeRequestPermissionResultListener(listener)
            onResult(Result.Failed(t.message ?: t.javaClass.simpleName))
        }
    }

    private fun bindAndGrant(context: Context, onResult: (Result) -> Unit) {
        val appContext = context.applicationContext
        val args = Shizuku.UserServiceArgs(
            ComponentName(appContext.packageName, ShizukuUserService::class.java.name),
        )
            .processNameSuffix("aab_grant")
            .debuggable(false)
            .version(1)

        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                // Binder transactions block — run off the (likely main) callback thread.
                thread(name = "shizuku-grant") {
                    val result = try {
                        if (binder == null || !binder.pingBinder()) {
                            Result.Failed("user service binder unavailable")
                        } else {
                            val service = IShizukuUserService.Stub.asInterface(binder)
                            val diagnostic = service.grantWriteSecureSettings(appContext.packageName)
                            if (diagnostic.isNullOrEmpty()) Result.Success else Result.Failed(diagnostic)
                        }
                    } catch (t: Throwable) {
                        Result.Failed(t.message ?: t.javaClass.simpleName)
                    } finally {
                        runCatching { Shizuku.unbindUserService(args, this, true) }
                    }
                    onResult(result)
                }
            }

            override fun onServiceDisconnected(name: ComponentName?) {}
        }

        try {
            Shizuku.bindUserService(args, connection)
        } catch (t: Throwable) {
            onResult(Result.Failed(t.message ?: t.javaClass.simpleName))
        }
    }
}
