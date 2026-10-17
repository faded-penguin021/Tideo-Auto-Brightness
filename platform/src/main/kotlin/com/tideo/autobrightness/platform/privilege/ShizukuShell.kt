package com.tideo.autobrightness.platform.privilege

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import com.tideo.autobrightness.platform.brightness.DisplayBrightnessDump
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import rikka.shizuku.Shizuku
import kotlin.concurrent.thread
import kotlin.coroutines.resume

/** Privileged operations via Shizuku (S12.7d, G2R-F41). Wi-Fi status, force-dark, Night Light Kelvin or display brightness only; null on failure. */
object ShizukuShell {
    private const val BIND_TIMEOUT_MS = 4_000L

    // DD-077: one service identity, and each call unbinds it with remove = true, so calls never overlap.
    private val serial = Mutex()

    private fun isUsable(): Boolean = try {
        Shizuku.pingBinder() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    } catch (_: Throwable) {
        false
    }

    enum class ReadOperation { WIFI_STATUS, FORCE_DARK, DISPLAY_BRIGHTNESS }

    suspend fun read(context: Context, operation: ReadOperation): String? = call(context, timeoutFor(operation)) { service ->
        when (operation) {
            ReadOperation.WIFI_STATUS -> service.wifiStatus()
            ReadOperation.FORCE_DARK -> service.readForceDark()
            ReadOperation.DISPLAY_BRIGHTNESS -> service.displayBrightnessLines()
        }
    }

    suspend fun setForceDark(context: Context, enabled: Boolean): String? = call(context) {
        it.setForceDark(enabled)
    }

    suspend fun readNightDisplayTemperature(context: Context): Int? = call(context) {
        it.readNightDisplayTemperature().takeIf { kelvin -> kelvin > 0 }
    }

    suspend fun setNightDisplayTemperature(context: Context, kelvin: Int): Int? = call(context) {
        it.setNightDisplayTemperature(kelvin).takeIf { result -> result > 0 }
    }

    private fun timeoutFor(operation: ReadOperation): Long =
        if (operation == ReadOperation.DISPLAY_BRIGHTNESS) BIND_TIMEOUT_MS + DisplayBrightnessDump.TIMEOUT_SECONDS * 1000 else BIND_TIMEOUT_MS

    private suspend fun <T : Any> call(
        context: Context,
        timeoutMs: Long = BIND_TIMEOUT_MS,
        operation: (IShizukuUserService) -> T?,
    ): T? = serial.withLock { callExclusive(context, timeoutMs, operation) }

    private suspend fun <T : Any> callExclusive(context: Context, timeoutMs: Long, operation: (IShizukuUserService) -> T?): T? {
        if (!isUsable()) return null
        val appContext = context.applicationContext
        val args = Shizuku.UserServiceArgs(
            ComponentName(appContext.packageName, ShizukuUserService::class.java.name),
        )
            .processNameSuffix("aab_shell")
            .debuggable(false)
            .version(4)

        return withTimeoutOrNull(timeoutMs) {
            suspendCancellableCoroutine { cont ->
                val connection = object : ServiceConnection {
                    override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                        // Run off callback thread; `this` is the ServiceConnection (not shadowed by thread{}).
                        thread(name = "shizuku-shell") {
                            val out = try {
                                if (binder == null || !binder.pingBinder()) {
                                    null
                                } else {
                                    operation(IShizukuUserService.Stub.asInterface(binder))
                                }
                            } catch (_: Throwable) {
                                null
                            } finally {
                                runCatching { Shizuku.unbindUserService(args, this, true) }
                            }
                            if (cont.isActive) cont.resume(out)
                        }
                    }

                    override fun onServiceDisconnected(name: ComponentName?) {}
                }
                // Unbind on the timeout too (D-145): a bind that never connects would otherwise stay
                // registered forever. Idempotent vs the onServiceConnected finally-unbind (runCatching).
                cont.invokeOnCancellation { runCatching { Shizuku.unbindUserService(args, connection, true) } }
                try {
                    Shizuku.bindUserService(args, connection)
                } catch (_: Throwable) {
                    if (cont.isActive) cont.resume(null)
                }
            }
        }
    }
}
