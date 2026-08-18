package com.tideo.autobrightness.platform.display

import android.content.Context
import com.tideo.autobrightness.platform.privilege.ShizukuShell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/** Global "force dark" rendering override (D-172): DarQ-style dark-render for apps with no dark theme.
 * Tries Shizuku first (2nd genuine-runtime call, task105), falls back to root. Returns null if unavailable.
 * HWUI reads on app renderer startup, so changes visible after re-launch. Resets on reboot (service re-asserts). */
object ForceDarkController {
    const val PROP = "debug.hwui.force_dark"

    // Rapid toggles can complete out of order (D-143 class). Serialize with fair Mutex for last-wins.
    private val shellMutex = Mutex()

    /** Read property value (null if shell unavailable; unset reads as false). */
    suspend fun read(context: Context): Boolean? =
        exec(context, enabled = null)?.let(::parseForceDarkProp)

    /** Write property, return re-read value as verification (null if shell unavailable). */
    suspend fun apply(context: Context, enabled: Boolean): Boolean? =
        exec(context, enabled)
            ?.let(::parseForceDarkProp)

    /** Shizuku first, root second (the task105 order the Wi-Fi SSID strategies also mirror). */
    private suspend fun exec(context: Context, enabled: Boolean?): String? = shellMutex.withLock {
        val shizuku = if (enabled == null) {
            ShizukuShell.read(context, ShizukuShell.ReadOperation.FORCE_DARK)
        } else {
            ShizukuShell.setForceDark(context, enabled)
        }
        shizuku ?: rootExec(enabled)
    }

    /** `su -c` fallback (gated on exit code 0). Bounded end-to-end with timeout (DA-017 PR #91 CI hang).
     * Stdin closed to force prompt EOF; wait capped with kill-on-expiry. Safe to read after wait (small output). */
    private suspend fun rootExec(enabled: Boolean?): String? = withContext(Dispatchers.IO) {
        try {
            val script = if (enabled == null) "getprop $PROP" else
                "setprop $PROP ${if (enabled) "true" else "false"} && getprop $PROP"
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", script))
            process.outputStream.close()
            if (!process.waitFor(SU_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                process.destroyForcibly()
                return@withContext null
            }
            if (process.exitValue() != 0) return@withContext null
            process.inputStream.bufferedReader().use { it.readText() }
        } catch (_: Throwable) {
            null
        }
    }

    private const val SU_TIMEOUT_SECONDS = 15L
}

/** Mirrors Android ParseBool: only lowercase `1`, `y`, `yes`, `on`, `true` enable it; else false. */
internal fun parseForceDarkProp(raw: String): Boolean =
    raw.trim() in setOf("1", "y", "yes", "on", "true")
