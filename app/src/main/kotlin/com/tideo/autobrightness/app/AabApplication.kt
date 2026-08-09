package com.tideo.autobrightness.app

import android.app.Application
import android.content.Context
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.time.Instant
import java.time.format.DateTimeFormatter

/**
 * Application entry point. Its sole job is to install a process-wide uncaught-exception handler
 * (D-158) that captures the last few crash stack traces to app-private storage, so the owner can
 * copy the most recent one from Tools → Diagnostics after the app restarts. This is a Tasker-
 * independent diagnostic (no parity source): **no telemetry, no network, no FileProvider** — the
 * traces live only under `filesDir/crash` and never leave the device.
 *
 * The runtime graph is still bootstrapped from [MainActivity.onCreate]; this class deliberately does
 * not touch it, to keep process start cheap and the crash handler installed as early as possible.
 */
class AabApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        installCrashLogHandler(CrashLogStore.of(this))
    }
}

/**
 * Installs [CrashLogHandler] as the default uncaught-exception handler, chaining to whatever handler
 * was previously registered (on Android that is the platform killer that ends the process).
 *
 * **Idempotent (glue-review, D-034(c)):** a process that somehow runs this twice will not stack a
 * second [CrashLogHandler] onto the first (which would write the same trace twice and re-invoke the
 * killer twice) — if the current default is already ours we leave it in place. A fresh process
 * (the only case that matters after a crash) starts with the platform handler and gets wrapped once.
 */
internal fun installCrashLogHandler(store: CrashLogStore) {
    val previous = Thread.getDefaultUncaughtExceptionHandler()
    if (previous is CrashLogHandler) return
    Thread.setDefaultUncaughtExceptionHandler(CrashLogHandler(store, previous))
}

/**
 * Records the trace, then **always** delegates to [previous] so the process still dies — a crash
 * handler that swallowed the exception would leave a wedged, half-dead process. Ordering matters
 * (glue-review, D-139): the trace is written FIRST, and delegation runs in `finally`, so the handoff
 * happens even if [CrashLogStore.record] itself throws. [CrashLogStore] additionally guards its own
 * I/O so a full disk or a revoked directory cannot mask the original crash.
 */
internal class CrashLogHandler(
    private val store: CrashLogStore,
    private val previous: Thread.UncaughtExceptionHandler?,
) : Thread.UncaughtExceptionHandler {
    override fun uncaughtException(thread: Thread, throwable: Throwable) {
        try {
            store.record(throwable, thread)
        } finally {
            previous?.uncaughtException(thread, throwable)
        }
    }
}

/**
 * On-disk ring of the [KEEP] newest uncaught-exception traces under a single directory (D-158).
 * All state is the directory itself — nothing is assumed to survive process death (glue-review,
 * D-034(c)), so a trace written by the dying process is read back by the fresh one after restart.
 * Timestamped filenames (`crash-<epochMillis>.txt`) sort chronologically as plain strings.
 */
class CrashLogStore(
    private val dir: File,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    /**
     * Writes a timestamped trace, then prunes to the [KEEP] newest. **Never throws** — it runs on
     * the dying thread and must not mask the crash it is recording (a write failure just means no
     * log this time).
     */
    fun record(throwable: Throwable, thread: Thread? = null) {
        runCatching {
            dir.mkdirs()
            val now = clock()
            File(dir, "$PREFIX$now$SUFFIX").writeText(format(throwable, thread, now))
            prune()
        }
    }

    /** The most recent recorded trace, or null if none (or it can't be read). */
    fun latest(): String? = files().firstOrNull()?.let { runCatching { it.readText() }.getOrNull() }

    /** Whether any trace has been recorded. */
    fun hasAny(): Boolean = files().isNotEmpty()

    /**
     * Recorded trace files, newest first. The epoch-millis in each name is fixed-width (13 digits
     * until year 2286), so a descending lexical sort is a descending chronological sort.
     */
    private fun files(): List<File> =
        (dir.listFiles { f -> f.isFile && f.name.startsWith(PREFIX) && f.name.endsWith(SUFFIX) }
            ?.toList() ?: emptyList())
            .sortedByDescending { it.name }

    private fun prune() {
        files().drop(KEEP).forEach { runCatching { it.delete() } }
    }

    private fun format(throwable: Throwable, thread: Thread?, now: Long): String {
        val trace = StringWriter().also { throwable.printStackTrace(PrintWriter(it)) }.toString()
        val where = thread?.let { " on thread \"${it.name}\"" } ?: ""
        return "Tideo Auto Brightness crash at ${TIMESTAMP.format(Instant.ofEpochMilli(now))}$where\n\n$trace"
    }

    companion object {
        /** Keep the 5 newest traces; older ones are pruned on each [record]. */
        const val KEEP = 5

        /** Sub-directory of `filesDir` the traces live in. */
        const val DIR = "crash"

        private const val PREFIX = "crash-"
        private const val SUFFIX = ".txt"
        private val TIMESTAMP: DateTimeFormatter = DateTimeFormatter.ISO_INSTANT

        fun of(filesDir: File): CrashLogStore = CrashLogStore(File(filesDir, DIR))
        fun of(context: Context): CrashLogStore = of(context.filesDir)
    }
}
