package com.tideo.autobrightness.platform.brightness

import android.content.Context
import android.content.pm.PackageManager
import com.tideo.autobrightness.platform.privilege.ShizukuShell
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class DisplayBrightnessSample(val field: String, val raw: Double)

interface DisplayBrightnessReader {
    /** The first of AAB's three fields, or [field] alone once a calibration has locked one. */
    suspend fun read(field: String? = null): DisplayBrightnessSample?
}

/** Returns the brightness lines of `dumpsys display`, or null to fall through. */
fun interface DisplayDumpSource {
    suspend fun brightnessLines(): String?
}

// Tasker: "_ReadBrightness" (AAB hand-off export, DD-077) — DUMP, then root, then Shizuku.
class DumpsysDisplayBrightnessReader(private val sources: List<DisplayDumpSource>) : DisplayBrightnessReader {
    constructor(context: Context) : this(
        listOf(DumpGrantDisplaySource(context), RootDisplaySource, ShizukuDisplaySource(context)),
    )

    @Volatile private var preferred = 0

    override suspend fun read(field: String?): DisplayBrightnessSample? {
        val start = preferred
        for (offset in sources.indices) {
            val index = (start + offset) % sources.size
            val lines = try {
                sources[index].brightnessLines()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                null
            } ?: continue
            val sample = DisplayBrightnessDump.parse(lines, field) ?: continue
            preferred = index
            return sample
        }
        return null
    }
}

class DumpGrantDisplaySource(private val context: Context) : DisplayDumpSource {
    override suspend fun brightnessLines(): String? {
        if (context.checkSelfPermission(DUMP) != PackageManager.PERMISSION_GRANTED) return null
        return withContext(Dispatchers.IO) { DisplayBrightnessDump.run(arrayOf("dumpsys", "display")) }
    }

    private companion object {
        const val DUMP = "android.permission.DUMP"
    }
}

object RootDisplaySource : DisplayDumpSource {
    override suspend fun brightnessLines(): String? =
        withContext(Dispatchers.IO) { DisplayBrightnessDump.run(arrayOf("su", "-c", "dumpsys display")) }
}

class ShizukuDisplaySource(private val context: Context) : DisplayDumpSource {
    override suspend fun brightnessLines(): String? =
        ShizukuShell.read(context, ShizukuShell.ReadOperation.DISPLAY_BRIGHTNESS)
}
