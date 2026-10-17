package com.tideo.autobrightness.platform.brightness

import java.io.InputStream
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/** Runs `dumpsys display` and keeps only its brightness lines; also used inside the Shizuku user service. */
object DisplayBrightnessDump {
    const val TIMEOUT_SECONDS = 5L
    private const val REAP_MS = 2_000L
    private const val MAX_LINES = 32
    private const val MAX_LINE_CHARS = 512
    private const val MAX_CHARS = 16L * 1024 * 1024

    // Tasker: "_CalibrateDisplayRange" (AAB hand-off export, DD-077), exponent floats added.
    private val FIELD = Regex(
        """(?<![A-Za-z])(mScreenBrightness|mDisplayBrightness|mCurrentScreenBrightness)=([0-9]+(?:\.[0-9]+)?(?:[eE][-+]?[0-9]+)?)""",
    )

    fun run(argv: Array<String>): String? {
        val process = try {
            ProcessBuilder(*argv).redirectErrorStream(true).start()
        } catch (_: Throwable) {
            return null
        }
        return try {
            process.outputStream.close()
            var lines: String? = null
            val reader = thread(name = "aab-display-dump", isDaemon = true) {
                lines = runCatching { filter(process.inputStream) }.getOrNull()
            }
            val exited = process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            if (!exited) process.destroyForcibly()
            // A `su -c` child can outlive its parent and hold the pipe, so close it rather than wait on it.
            reader.join(REAP_MS)
            if (exited && !reader.isAlive && process.exitValue() == 0) lines?.ifBlank { null } else null
        } catch (_: Throwable) {
            null
        } finally {
            runCatching { process.inputStream.close() }
            process.destroy()
        }
    }

    internal fun filter(input: InputStream): String {
        val kept = ArrayList<String>()
        val line = StringBuilder()
        var total = 0L
        fun endLine() {
            if (kept.size < MAX_LINES && FIELD.containsMatchIn(line)) kept += line.toString()
            line.setLength(0)
        }
        input.reader().use { reader ->
            val buffer = CharArray(8 * 1024)
            while (total <= MAX_CHARS) {
                val count = reader.read(buffer)
                if (count < 0) break
                total += count
                for (i in 0 until count) {
                    val c = buffer[i]
                    if (c == '\n') endLine() else if (line.length < MAX_LINE_CHARS) line.append(c)
                }
            }
        }
        endLine()
        return kept.joinToString("\n")
    }

    fun parse(output: String, field: String?): DisplayBrightnessSample? =
        FIELD.findAll(output)
            .filter { field == null || it.groupValues[1] == field }
            .mapNotNull { m -> m.groupValues[2].toDoubleOrNull()?.takeIf { it.isFinite() }?.let { DisplayBrightnessSample(m.groupValues[1], it) } }
            .firstOrNull()
}
