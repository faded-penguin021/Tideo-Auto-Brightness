package com.tideo.autobrightness.platform.brightness

import android.content.Context
import android.content.pm.PackageManager
import com.tideo.autobrightness.platform.privilege.ShizukuShell
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class DisplayBrightnessSample(val field: String, val raw: Double)

enum class DumpStatus { UNAVAILABLE, FAILED, NO_FIELD, SAMPLE }

data class DumpAttempt(val source: String, val status: DumpStatus, val sample: DisplayBrightnessSample?, val elapsedMs: Long)

/** One read with every source it tried, and the answering dump's ceiling lines. */
data class DisplayBrightnessRead(
    val sample: DisplayBrightnessSample?,
    val attempts: List<DumpAttempt>,
    val corroboration: List<String>,
)

interface DisplayBrightnessReader {
    /** The first of AAB's three fields, or [field] alone once a calibration has locked one. */
    suspend fun read(field: String? = null): DisplayBrightnessSample?

    suspend fun trace(field: String? = null): DisplayBrightnessRead = DisplayBrightnessRead(read(field), emptyList(), emptyList())
}

sealed interface DumpOutput {
    data object Unavailable : DumpOutput
    data object Failed : DumpOutput
    data class Lines(val text: String) : DumpOutput
}

interface DisplayDumpSource {
    val name: String

    suspend fun dump(): DumpOutput
}

// Tasker: "_ReadBrightness" (AAB hand-off export, DD-077) — DUMP, then root, then Shizuku.
class DumpsysDisplayBrightnessReader(
    private val sources: List<DisplayDumpSource>,
    private val nowMs: () -> Long = { System.nanoTime() / 1_000_000 },
) : DisplayBrightnessReader {
    constructor(context: Context) : this(
        listOf(DumpGrantDisplaySource(context), RootDisplaySource, ShizukuDisplaySource(context)),
    )

    @Volatile private var preferred = 0

    override suspend fun read(field: String?): DisplayBrightnessSample? = trace(field).sample

    override suspend fun trace(field: String?): DisplayBrightnessRead {
        val start = preferred
        val attempts = ArrayList<DumpAttempt>(sources.size)
        for (offset in sources.indices) {
            val index = (start + offset) % sources.size
            val began = nowMs()
            val output = try {
                sources[index].dump()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                DumpOutput.Failed
            }
            val lines = (output as? DumpOutput.Lines)?.text
            val sample = lines?.let { DisplayBrightnessDump.parse(it, field) }
            val status = when {
                output == DumpOutput.Unavailable -> DumpStatus.UNAVAILABLE
                output == DumpOutput.Failed -> DumpStatus.FAILED
                sample == null -> DumpStatus.NO_FIELD
                else -> DumpStatus.SAMPLE
            }
            attempts += DumpAttempt(sources[index].name, status, sample, nowMs() - began)
            if (sample != null && lines != null) {
                preferred = index
                return DisplayBrightnessRead(sample, attempts, DisplayBrightnessDump.corroboration(lines))
            }
        }
        return DisplayBrightnessRead(null, attempts, emptyList())
    }
}

class DumpGrantDisplaySource(private val context: Context) : DisplayDumpSource {
    override val name = "DUMP"

    override suspend fun dump(): DumpOutput {
        if (context.checkSelfPermission(DUMP) != PackageManager.PERMISSION_GRANTED) return DumpOutput.Unavailable
        return withContext(Dispatchers.IO) { DisplayBrightnessDump.run(arrayOf("dumpsys", "display")) }
    }

    private companion object {
        const val DUMP = "android.permission.DUMP"
    }
}

object RootDisplaySource : DisplayDumpSource {
    override val name = "root"

    override suspend fun dump(): DumpOutput =
        withContext(Dispatchers.IO) { DisplayBrightnessDump.run(arrayOf("su", "-c", "dumpsys display")) }
}

class ShizukuDisplaySource(private val context: Context) : DisplayDumpSource {
    override val name = "Shizuku"

    override suspend fun dump(): DumpOutput = ShizukuShell.readDisplayBrightness(context)
}
