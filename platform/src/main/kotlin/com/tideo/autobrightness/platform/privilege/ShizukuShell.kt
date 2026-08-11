package com.tideo.autobrightness.platform.privilege

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import rikka.shizuku.Shizuku
import kotlin.concurrent.thread
import kotlin.coroutines.resume

/** Privileged operations via Shizuku (S12.7d, G2R-F41). Wi-Fi status or force-dark only; null on failure. */
object ShizukuShell {
    private const val BIND_TIMEOUT_MS = 4_000L

    private fun isUsable(): Boolean = try {
        Shizuku.pingBinder() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    } catch (_: Throwable) {
        false
    }

    enum class ReadOperation { WIFI_STATUS, FORCE_DARK }

    suspend fun read(context: Context, operation: ReadOperation): String? = call(context) { service ->
        when (operation) {
            ReadOperation.WIFI_STATUS -> service.wifiStatus()
            ReadOperation.FORCE_DARK -> service.readForceDark()
        }
    }

    suspend fun setForceDark(context: Context, enabled: Boolean): String? = call(context) {
        it.setForceDark(enabled)
    }

    private suspend fun call(context: Context, operation: (IShizukuUserService) -> String?): String? {
        if (!isUsable()) return null
        val appContext = context.applicationContext
        val args = Shizuku.UserServiceArgs(
            ComponentName(appContext.packageName, ShizukuUserService::class.java.name),
        )
            .processNameSuffix("aab_shell")
            .debuggable(false)
            .version(2)

        return withTimeoutOrNull(BIND_TIMEOUT_MS) {
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
                // Unbind on the 4 s timeout too (D-145): a bind that never connects would otherwise stay
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
