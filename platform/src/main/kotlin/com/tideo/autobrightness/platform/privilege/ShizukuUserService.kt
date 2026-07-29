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

    override fun grantWriteSecureSettings(): Boolean =
        run(arrayOf("pm", "grant", context.packageName, WRITE_SECURE_SETTINGS), 0) != null

    // These methods deliberately expose operations, not argv. Binder input is either absent or a
    // boolean, so intents, profiles, network data, and UI strings cannot select a command or argument.
    override fun wifiStatus(): String? = run(arrayOf("cmd", "wifi", "status"), WIFI_OUTPUT_LIMIT)

    override fun readForceDark(): String? = run(arrayOf("getprop", FORCE_DARK_PROP), PROP_OUTPUT_LIMIT)

    override fun setForceDark(enabled: Boolean): String? {
        val value = if (enabled) "true" else "false"
        if (run(arrayOf("setprop", FORCE_DARK_PROP, value), 0) == null) return null
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
            process.waitFor()
            stdoutThread.join()
            stderrThread.join()
            null
        } else {
            stdoutThread.join()
            stderrThread.join()
            if (process.exitValue() == 0 && !stdout.failed && !stderr.failed &&
                !stdout.overflow && !stderr.overflow
            ) stdout.text() else null
        }
    } catch (_: Throwable) {
        null
    }

    private class BoundedRead(private val input: InputStream, private val limit: Int) {
        private val bytes = ArrayList<Byte>(minOf(limit, 4096))
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
                        val remaining = limit - bytes.size
                        if (count > remaining) overflow = true
                        for (index in 0 until minOf(count, remaining.coerceAtLeast(0))) bytes.add(buffer[index])
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
        const val WIFI_OUTPUT_LIMIT = 64 * 1024
        const val PROP_OUTPUT_LIMIT = 128
        const val STDERR_LIMIT = 4 * 1024
    }
}
