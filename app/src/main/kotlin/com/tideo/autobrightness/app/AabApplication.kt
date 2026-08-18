package com.tideo.autobrightness.app

import android.app.Application
import android.content.Context
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.time.Instant
import java.time.format.DateTimeFormatter

/** Installs process-wide uncaught-exception handler (D-158) to capture crash traces to app-private storage, no telemetry/network. */
class AabApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        installCrashLogHandler(CrashLogStore.of(this))
    }
}

/** Install crash handler chained to previous (D-034(c) idempotent): skip if already wrapped. */
internal fun installCrashLogHandler(store: CrashLogStore) {
    val previous = Thread.getDefaultUncaughtExceptionHandler()
    if (previous is CrashLogHandler) return
    Thread.setDefaultUncaughtExceptionHandler(CrashLogHandler(store, previous))
}

/** Record trace then delegate to previous handler (D-139: order matters, delegation in finally). */
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

/** On-disk ring of [KEEP] newest traces (D-158, D-034(c)): state is directory; filenames sort chronologically. */
class CrashLogStore(
    private val dir: File,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    /** Write timestamped trace and prune to [KEEP] newest; never throws (runs on dying thread). */
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

    /** Trace files newest first (lexical sort = chronological; epoch-millis fixed-width 13 digits). */
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
