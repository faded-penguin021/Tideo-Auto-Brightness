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

    internal interface UserServiceBinding {
        fun isUsable(): Boolean
        fun bind(context: Context, connection: ServiceConnection)
        fun unbind(context: Context, connection: ServiceConnection)
    }

    internal var binding: UserServiceBinding = ShizukuBinding

    private class Held(val connection: ServiceConnection, val service: IShizukuUserService)

    // DE-001: one binding, held while it answers and released on any failure; calls never overlap.
    private val serial = Mutex()
    private var held: Held? = null

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
    ): T? = serial.withLock { callExclusive(context.applicationContext, timeoutMs, operation) }

    private suspend fun <T : Any> callExclusive(appContext: Context, timeoutMs: Long, operation: (IShizukuUserService) -> T?): T? {
        if (!binding.isUsable()) {
            release(appContext)
            return null
        }
        return withTimeoutOrNull(timeoutMs) {
            val current = held?.takeIf { it.service.asBinder().pingBinder() }
                ?: run { release(appContext); connect(appContext) }
                ?: return@withTimeoutOrNull null
            held = current
            var answered = false
            try {
                val out = onWorker { operation(current.service) }
                answered = out.isSuccess
                out.getOrNull()
            } finally {
                if (!answered) release(appContext)
            }
        }
    }

    private fun release(appContext: Context) {
        val released = held ?: return
        held = null
        runCatching { binding.unbind(appContext, released.connection) }
    }

    private suspend fun connect(appContext: Context): Held? = suspendCancellableCoroutine { cont ->
        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                if (binder != null && cont.isActive) cont.resume(Held(this, IShizukuUserService.Stub.asInterface(binder)))
            }

            override fun onServiceDisconnected(name: ComponentName?) {}
        }
        // D-145: a bind that never connects would otherwise stay registered forever.
        cont.invokeOnCancellation { runCatching { binding.unbind(appContext, connection) } }
        try {
            binding.bind(appContext, connection)
        } catch (_: Throwable) {
            runCatching { binding.unbind(appContext, connection) }
            if (cont.isActive) cont.resume(null)
        }
    }

    /** Off the caller's thread, so a timeout returns while a binder call is still blocked. */
    private suspend fun <T> onWorker(block: () -> T): Result<T> = suspendCancellableCoroutine { cont ->
        thread(name = "shizuku-shell") {
            val out = runCatching(block)
            if (cont.isActive) cont.resume(out)
        }
    }

    private object ShizukuBinding : UserServiceBinding {
        override fun isUsable(): Boolean = try {
            Shizuku.pingBinder() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        } catch (_: Throwable) {
            false
        }

        override fun bind(context: Context, connection: ServiceConnection) = Shizuku.bindUserService(args(context), connection)

        override fun unbind(context: Context, connection: ServiceConnection) =
            Shizuku.unbindUserService(args(context), connection, true)

        private fun args(context: Context) = Shizuku.UserServiceArgs(
            ComponentName(context.packageName, ShizukuUserService::class.java.name),
        )
            .processNameSuffix("aab_shell")
            .debuggable(false)
            .daemon(false)
            .version(4)
    }
}
