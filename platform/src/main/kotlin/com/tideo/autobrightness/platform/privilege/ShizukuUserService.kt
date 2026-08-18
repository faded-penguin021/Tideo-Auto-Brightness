package com.tideo.autobrightness.platform.privilege

import android.content.Context
import java.io.InputStream
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

// Shizuku user-service (S11, D-032): exec `pm grant` in privileged process.
// DA-031: single-Context constructor supplies the only package name eligible for grant.

class ShizukuUserService(private val context: Context) : IShizukuUserService.Stub() {

    override fun destroy() {
        System.exit(0)
    }

    // DB-005: DISCARD_OUTPUT (not 0); prevent output from being treated as failure.
    override fun grantWriteSecureSettings(): Boolean =
        run(arrayOf("pm", "grant", context.packageName, WRITE_SECURE_SETTINGS), DISCARD_OUTPUT) != null

    override fun wifiStatus(): String? = run(arrayOf("cmd", "wifi", "status"), WIFI_OUTPUT_LIMIT)

    override fun readForceDark(): String? = run(arrayOf("getprop", FORCE_DARK_PROP), PROP_OUTPUT_LIMIT)

    override fun setForceDark(enabled: Boolean): String? {
        val value = if (enabled) "true" else "false"
        if (run(arrayOf("setprop", FORCE_DARK_PROP, value), DISCARD_OUTPUT) == null) return null
        return readForceDark()
    }

    private fun run(argv: Array<String>, stdoutLimit: Int): String? = try {
        val process = Runtime.getRuntime().exec(argv)
        process.outputStream.close()
        val stdout = BoundedRead(process.inputStream, stdoutLimit)
        val stderr = BoundedRead(process.errorStream, STDERR_LIMIT)
        val stdoutThread = thread(name = "aab-priv-stdout") { stdout.read() }
        val stderrThread = thread(name = "aab-priv-stderr") { stderr.read() }
        if (!process.waitFor(COMMAND_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
            process.destroyForcibly()
            // DB-005: bounded joins to prevent stuck reader threads (reaping best-effort).
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
        const val REAP_TIMEOUT_SECONDS = 2L
        const val REAP_TIMEOUT_MS = REAP_TIMEOUT_SECONDS * 1000
        const val DISCARD_OUTPUT = -1
        const val WIFI_OUTPUT_LIMIT = 64 * 1024
        const val PROP_OUTPUT_LIMIT = 128
        const val STDERR_LIMIT = 4 * 1024
    }
}
