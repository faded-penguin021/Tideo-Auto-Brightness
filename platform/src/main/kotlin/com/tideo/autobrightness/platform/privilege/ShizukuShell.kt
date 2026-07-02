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

/**
 * Runs one-shot shell commands through a Shizuku-bound privileged process (S12.7d, G2R-F41). Reuses
 * the same documented [ShizukuUserService] / AIDL pattern as [ShizukuGrantGateway] — we never reflect
 * into hidden `Shizuku.newProcess` (owner-reported fragile in factory apps).
 *
 * The only caller is the no-Location SSID path (`cmd wifi status`); it returns null whenever Shizuku
 * is unavailable / unpermitted / the bind fails, so the reader can fall through to the next strategy.
 */
object ShizukuShell {
    private const val BIND_TIMEOUT_MS = 4_000L

    private fun isUsable(): Boolean = try {
        Shizuku.pingBinder() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    } catch (_: Throwable) {
        false
    }

    /** Executes [command] in the privileged process and returns its stdout, or null on any failure. */
    suspend fun exec(context: Context, command: Array<String>): String? {
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
                        // Binder transactions block — run off the (likely main) callback thread.
                        thread(name = "shizuku-shell") {
                            val out = try {
                                if (binder == null || !binder.pingBinder()) {
                                    null
                                } else {
                                    IShizukuUserService.Stub.asInterface(binder).exec(command)
                                }
                            } catch (_: Throwable) {
                                null
                            } finally {
                                // `this` is the enclosing ServiceConnection — a plain thread{} lambda is
                                // not a receiver lambda, so it does not shadow `this` (verified: reviewer's
                                // suggested `this@ServiceConnection` / a captured-`connection` ref both fail
                                // to compile for an anonymous object; `this` is the correct, only clean form).
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
