package com.tideo.autobrightness.platform.privilege

import android.content.Context
import java.io.InputStream
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/**
 * The Shizuku user-service implementation (S11, closes D-032). Shizuku's server instantiates this
 * class by name (via [Shizuku.UserServiceArgs]) inside a process that holds adb-shell (uid 2000) or
 * root privileges, then exposes its [IShizukuUserService.Stub] binder back to the app. Running
 * `pm grant` here therefore succeeds exactly as the documented adb command does — without the app
 * itself ever holding WRITE_SECURE_SETTINGS at install time.
 *
 * DA-031: the required public single-Context constructor also supplies the only package name eligible for
 * the grant. The binder caller cannot nominate another package.
 *
 * We exec `pm grant` rather than calling IPackageManager.grantRuntimePermission because
 * WRITE_SECURE_SETTINGS is a signature|privileged permission, not a runtime (dangerous) one, so the
 * runtime-grant path does not apply to it; `pm grant` is the canonical channel (D-016).
 */
class ShizukuUserService(private val context: Context) : IShizukuUserService.Stub() {

    override fun destroy() {
        // Shizuku calls this (transaction 16777114) to tear the user service process down.
        System.exit(0)
    }

    // DB-005: DISCARD_OUTPUT, not 0. A limit of 0 made "produced any stdout at all" indistinguishable
    // from "overflowed its bound", and overflow is treated as failure — so a `pm grant` that
    // succeeded while printing a single warning line was reported as a failed grant, sending the user
    // back to the adb instructions for a permission they already had. Output we do not READ must not
    // be output we FAIL on.
    override fun grantWriteSecureSettings(): Boolean =
        run(arrayOf("pm", "grant", context.packageName, WRITE_SECURE_SETTINGS), DISCARD_OUTPUT) != null

    // These methods deliberately expose operations, not argv. Binder input is either absent or a
    // boolean, so intents, profiles, network data, and UI strings cannot select a command or argument.
    override fun wifiStatus(): String? = run(arrayOf("cmd", "wifi", "status"), WIFI_OUTPUT_LIMIT)

    override fun readForceDark(): String? = run(arrayOf("getprop", FORCE_DARK_PROP), PROP_OUTPUT_LIMIT)

    override fun setForceDark(enabled: Boolean): String? {
        val value = if (enabled) "true" else "false"
        if (run(arrayOf("setprop", FORCE_DARK_PROP, value), DISCARD_OUTPUT) == null) return null
        return readForceDark()
    }

    /** Fixed-command executor: bounded output, bounded lifetime, and no shell-language parsing. */
    private fun run(argv: Array<String>, stdoutLimit: Int): String? = try {
        val process = Runtime.getRuntime().exec(argv)
        process.outputStream.close()
        val stdout = BoundedRead(process.inputStream, stdoutLimit)
        val stderr = BoundedRead(process.errorStream, STDERR_LIMIT)
        val stdoutThread = thread(name = "aab-priv-stdout") { stdout.read() }
        val stderrThread = thread(name = "aab-priv-stderr") { stderr.read() }
        if (!process.waitFor(COMMAND_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
            process.destroyForcibly()
            // DB-005: bounded joins. `waitFor()` and `join()` with no argument are unbounded waits,
            // so the "command timeout" was a bound on the command, not on this call — a reader thread
            // stuck on a pipe that never closes would hold the caller past the timeout it was
            // promised. Reaping is best effort; the timeout already decided the result is null.
            process.waitFor(REAP_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            stdoutThread.join(REAP_TIMEOUT_MS)
            stderrThread.join(REAP_TIMEOUT_MS)
            null
        } else {
            stdoutThread.join(REAP_TIMEOUT_MS)
            stderrThread.join(REAP_TIMEOUT_MS)
            if (process.exitValue() == 0 && !stdout.failed && !stderr.failed &&
                !stdout.overflow && !stderr.overflow
            ) stdout.text() else null
        }
    } catch (_: Throwable) {
        null
    }

    private class BoundedRead(private val input: InputStream, private val limit: Int) {
        /** [DISCARD_OUTPUT]: drain the pipe (so the child never blocks writing) but keep nothing. */
        private val discard = limit == DISCARD_OUTPUT
        private val bytes = java.io.ByteArrayOutputStream(if (discard) 0 else minOf(limit, 4096))
        @Volatile var overflow = false
            private set
        @Volatile var failed = false
            private set

        fun read() {
            try {
                input.use {
                    val buffer = ByteArray(4096)
                    while (true) {
                        val count = it.read(buffer)
                        if (count < 0) break
                        if (discard) continue
                        val remaining = limit - bytes.size()
                        if (count > remaining) overflow = true
                        val keep = minOf(count, remaining.coerceAtLeast(0))
                        if (keep > 0) bytes.write(buffer, 0, keep)
                    }
                }
            } catch (_: Throwable) {
                failed = true
            }
        }

        fun text(): String = bytes.toByteArray().toString(Charsets.UTF_8)
    }

    private companion object {
        const val WRITE_SECURE_SETTINGS = "android.permission.WRITE_SECURE_SETTINGS"
        const val FORCE_DARK_PROP = "debug.hwui.force_dark"
        const val COMMAND_TIMEOUT_SECONDS = 10L

        /** DB-005: post-kill reaping bound, so no path here waits without a limit. */
        const val REAP_TIMEOUT_SECONDS = 2L
        const val REAP_TIMEOUT_MS = REAP_TIMEOUT_SECONDS * 1000

        /** Run for effect: read nothing, and do not treat incidental output as failure (DB-005). */
        const val DISCARD_OUTPUT = -1
        const val WIFI_OUTPUT_LIMIT = 64 * 1024
        const val PROP_OUTPUT_LIMIT = 128
        const val STDERR_LIMIT = 4 * 1024
    }
}
