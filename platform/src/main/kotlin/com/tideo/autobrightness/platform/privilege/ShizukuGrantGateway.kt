package com.tideo.autobrightness.platform.privilege

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import rikka.shizuku.Shizuku
import java.util.Timer
import java.util.TimerTask
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

/**
 * Three-state Shizuku readiness, so the onboarding UI can tell *not installed* (offer ADB only) from
 * *installed but not running* (prompt the user to start the Shizuku app) from *running* (offer the
 * one-tap grant). `pingBinder()` alone collapses the first two into a single "unavailable".
 */
enum class ShizukuAvailability { RUNNING, INSTALLED_NOT_RUNNING, NOT_INSTALLED }

/** D-024/D-032: WRITE_SECURE_SETTINGS grant via Shizuku user service (IShizukuUserService).
 * G3-F9: no-Location Wi-Fi SSID strategy separate (never used by brightness pipeline). */
object ShizukuGrantGateway {
    private const val REQUEST_CODE = 1001

    /**
     * DB-005: wall-clock bound on the permission prompt. Long enough for a user to find the Shizuku
     * dialog, read it and decide; short enough that a dismissed prompt does not strand the caller
     * (and the listener) for the life of the process.
     */
    private const val PROMPT_TIMEOUT_MS = 120_000L

    /** DB-005: one grant flow at a time — all requests share [REQUEST_CODE]. */
    private val grantInFlight = AtomicBoolean(false)
    private const val BIND_TIMEOUT_MS = 15_000L

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

    /** Request Shizuku permission, bind user service, grant WRITE_SECURE_SETTINGS. onResult called once. */
    fun requestGrant(context: Context, onResult: (Result) -> Unit) {
        // DB-005: single-flight (shared REQUEST_CODE, one grant to obtain).
        if (!grantInFlight.compareAndSet(false, true)) {
            onResult(Result.Failed("A Shizuku grant is already in progress"))
            return
        }
        val settled = AtomicBoolean(false)
        var promptTimer: Timer? = null
        val complete: (Result) -> Unit = { result ->
            if (settled.compareAndSet(false, true)) {
                promptTimer?.cancel()
                grantInFlight.set(false)
                onResult(result)
            }
        }

        if (!isAvailable()) {
            complete(Result.Unavailable)
            return
        }
        if (hasPermission()) {
            bindAndGrant(context, complete)
            return
        }

        val listener = object : Shizuku.OnRequestPermissionResultListener {
            override fun onRequestPermissionResult(requestCode: Int, grantResult: Int) {
                if (requestCode != REQUEST_CODE) return
                Shizuku.removeRequestPermissionResultListener(this)
                if (grantResult == PackageManager.PERMISSION_GRANTED) {
                    bindAndGrant(context, complete)
                } else {
                    complete(Result.PermissionDenied)
                }
            }
        }
        Shizuku.addRequestPermissionResultListener(listener)
        // DB-005: bound prompt timeout (dismissed dialog produces no callback).
        promptTimer = Timer("shizuku-prompt-timeout", true).apply {
            schedule(
                object : TimerTask() {
                    override fun run() {
                        runCatching { Shizuku.removeRequestPermissionResultListener(listener) }
                        complete(Result.Failed("Shizuku permission prompt timed out"))
                    }
                },
                PROMPT_TIMEOUT_MS,
            )
        }
        try {
            Shizuku.requestPermission(REQUEST_CODE)
        } catch (t: Throwable) {
            Shizuku.removeRequestPermissionResultListener(listener)
            complete(Result.Failed(t.message ?: t.javaClass.simpleName))
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

        val completed = AtomicBoolean(false)
        val timer = Timer("shizuku-grant-timeout", true)
        lateinit var connection: ServiceConnection
        fun finish(result: Result) {
            if (!completed.compareAndSet(false, true)) return
            timer.cancel()
            runCatching { Shizuku.unbindUserService(args, connection, true) }
            onResult(result)
        }
        connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                thread(name = "shizuku-grant") {
                    val result = try {
                        if (binder == null || !binder.pingBinder()) {
                            Result.Failed("user service binder unavailable")
                        } else {
                            val service = IShizukuUserService.Stub.asInterface(binder)
                            if (service.grantWriteSecureSettings()) Result.Success
                            else Result.Failed("secure-settings grant failed")
                        }
                    } catch (t: Throwable) {
                        Result.Failed(t.message ?: t.javaClass.simpleName)
                    }
                    finish(result)
                }
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                finish(Result.Failed("user service disconnected"))
            }
        }

        try {
            Shizuku.bindUserService(args, connection)
            timer.schedule(object : TimerTask() {
                override fun run() = finish(Result.Failed("user service timed out"))
            }, BIND_TIMEOUT_MS)
        } catch (t: Throwable) {
            finish(Result.Failed("user service bind failed"))
        }
    }
}
