package com.tideo.autobrightness.platform.display

import android.content.Context
import com.tideo.autobrightness.platform.privilege.ShizukuShell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/**
 * D-172: the global "force dark" rendering override (`debug.hwui.force_dark`) — the DarQ-style
 * developer option that dark-renders apps which have no dark theme of their own. The property is
 * shell-writable, so calls try [ShizukuShell] first (the second genuine-runtime Shizuku dependency,
 * after the no-Location SSID strategy) and fall back to a root shell — the same Shizuku → root
 * privilege order as the SSID strategies (task105). Every call returns null when neither channel
 * is available, so callers can degrade gracefully.
 *
 * Semantics the UI/help text must mirror: HWUI reads the property when an app's renderer comes up,
 * so a change only shows once the target app is re-launched; the property resets on reboot (the
 * foreground service re-asserts it at start while the user opt-in is on, D-172).
 */
object ForceDarkController {
    const val PROP = "debug.hwui.force_dark"

    // Glue-review (D-143 class): rapid toggles launch independent apply() coroutines whose Shizuku
    // binds / su spawns can complete OUT OF ORDER — the earlier setprop landing after the later one
    // would leave the prop opposite to the switch, and its stale re-read would overwrite the newer
    // status. kotlinx Mutex is fair (FIFO), so serializing here makes last-submitted-wins hold for
    // every caller (Tools card + the service re-assert alike).
    private val shellMutex = Mutex()

    /**
     * The property's current value, or null when no privileged shell is available. An unset
     * property reads as false (HWUI's default).
     */
    suspend fun read(context: Context): Boolean? =
        exec(context, "getprop $PROP")?.let(::parseForceDarkProp)

    /**
     * Writes the property, then returns the re-read value as verification (so the caller sees the
     * state the renderer will actually pick up), or null when no privileged shell is available.
     */
    suspend fun apply(context: Context, enabled: Boolean): Boolean? =
        exec(context, "setprop $PROP ${if (enabled) "true" else "false"} && getprop $PROP")
            ?.let(::parseForceDarkProp)

    /** Shizuku first, root second (the task105 order the Wi-Fi SSID strategies also mirror). */
    private suspend fun exec(context: Context, script: String): String? = shellMutex.withLock {
        ShizukuShell.exec(context, arrayOf("sh", "-c", script)) ?: rootExec(script)
    }

    /**
     * `su -c` fallback. Gated on exit code 0 so a missing `su` binary or a denied prompt reads as
     * null (unavailable) — never as a legitimate empty/false property read. Bounded end-to-end
     * (DA-017 — PR #91 CI hang: a password-prompting host `su` held stdin/stdout open and the
     * unbounded stdout read blocked forever): stdin is closed so a prompt reads EOF, and the wait
     * is capped with a kill on expiry — su-manager GUI prompts auto-deny inside the cap (Magisk
     * ~10 s). Waiting BEFORE reading is safe here, unlike the dumpsys-sized reads in
     * [WifiSsidStrategies][com.tideo.autobrightness.platform.context.RootWifiSsidStrategy]:
     * the property output is a few bytes, so it can never fill the pipe buffer.
     */
    private suspend fun rootExec(script: String): String? = withContext(Dispatchers.IO) {
        try {
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

/**
 * Mirrors Android's `ParseBool` (system/libbase), which HWUI uses for this property: exactly the
 * lowercase literals `1`, `y`, `yes`, `on`, `true` enable it; anything else (including unset/empty
 * and uppercase variants) is false.
 */
internal fun parseForceDarkProp(raw: String): Boolean =
    raw.trim() in setOf("1", "y", "yes", "on", "true")
