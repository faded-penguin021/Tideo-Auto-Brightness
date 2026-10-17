package com.tideo.autobrightness.app.runtime

import com.tideo.autobrightness.domain.brightness.DisplayCalibration
import com.tideo.autobrightness.domain.brightness.DisplayCalibration.Point
import com.tideo.autobrightness.platform.brightness.DisplayBrightnessReader
import com.tideo.autobrightness.platform.brightness.DisplayBrightnessSample
import com.tideo.autobrightness.platform.brightness.ScreenBrightnessController
import com.tideo.autobrightness.platform.brightness.WriteStatus
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

data class DisplayCalibrationRecord(val calibration: DisplayCalibration, val field: String)

interface DisplayCalibrationSink {
    suspend fun started()

    /** Null when the run failed; AAB then stores `error` in place of any earlier calibration. */
    suspend fun finished(record: DisplayCalibrationRecord?)
}

object NoDisplayBrightnessReader : DisplayBrightnessReader {
    override suspend fun read(field: String?): DisplayBrightnessSample? = null
}

object NoOpDisplayCalibrationSink : DisplayCalibrationSink {
    override suspend fun started() = Unit
    override suspend fun finished(record: DisplayCalibrationRecord?) = Unit
}

/** Runs as one pipeline event, so no cycle, animation or control event interleaves (DD-077). */
internal class DisplayCalibrationRun(
    private val ctx: PipelineRuntimeContext,
    private val brightness: ScreenBrightnessController,
    private val dimming: DimmingCoordinator,
    private val handoff: HandoffCoordinator,
    private val reader: DisplayBrightnessReader,
    private val sink: DisplayCalibrationSink,
    private val clock: () -> Long,
) {
    private var lockedField: String? = null

    /** Read by the override gate; a separate hold, since a screen-on re-arms the settle window. */
    @Volatile var active = false
        private set

    // Tasker: "_CalibrateDisplayRange" (AAB hand-off export, DD-077).
    suspend fun run() {
        sink.started()
        var record: DisplayCalibrationRecord? = null
        try {
            handoff.release()
            // Never forces MANUAL: a mode write could land after stop()'s restoreMode() (DD-077).
            if (ctx.stateValue.handedOff || !brightness.isManualMode()) return
            active = true
            dimming.disengage()
            val original = brightness.read()
            try {
                record = sweep()
            } finally {
                withContext(NonCancellable) {
                    if (!brightness.write(original).landed()) record = null
                    ctx.armInitialSettle(clock() + PipelineCycleRunner.INITIAL_SETTLE_MS)
                    active = false
                }
            }
        } finally {
            withContext(NonCancellable) { sink.finished(record) }
        }
    }

    private suspend fun sweep(): DisplayCalibrationRecord? {
        lockedField = null
        val points = ArrayList<Point>(DisplayCalibration.LEVELS.size)
        var previous = -1.0
        for ((i, level) in DisplayCalibration.LEVELS.withIndex()) {
            if (!brightness.write(level).landed()) return null
            if (i == 0) delay(FIRST_POINT_SETTLE_MS)
            val raw = readStable(previous) ?: return null
            points += Point(level, raw)
            previous = raw
        }
        val field = lockedField ?: return null
        return DisplayCalibration.of(points)?.let { DisplayCalibrationRecord(it, field) }
    }

    /** Waits until the reading stops ramping, has left the previous point, and repeats. */
    private suspend fun readStable(previous: Double): Double? {
        var last: Double? = null
        val start = clock()
        repeat(MAX_POLLS) {
            if (clock() - start >= STABLE_WINDOW_MS) return null
            delay(POLL_MS)
            val raw = read()
            if (raw != null && raw == last && raw != previous) return raw
            last = raw
        }
        return null
    }

    private suspend fun read(): Double? {
        val sample: DisplayBrightnessSample = reader.read(lockedField) ?: return null
        if (lockedField == null) lockedField = sample.field
        return sample.raw
    }

    private fun com.tideo.autobrightness.platform.brightness.BrightnessWriteResult.landed() =
        status == WriteStatus.ACKNOWLEDGED || status == WriteStatus.WRITTEN_UNACKNOWLEDGED

    private companion object {
        const val FIRST_POINT_SETTLE_MS = 800L
        const val POLL_MS = 150L
        const val STABLE_WINDOW_MS = 3_000L
        const val MAX_POLLS = (STABLE_WINDOW_MS / POLL_MS).toInt()
    }
}
