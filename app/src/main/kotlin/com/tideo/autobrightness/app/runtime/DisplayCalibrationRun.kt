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
    private val trace: (String) -> Unit = DisplayCalibrationTrace::logcat,
) {
    private var lockedField: String? = null
    private var failure: String? = null
    private var corroboration: List<String> = emptyList()

    /** Read by the override gate; a separate hold, since a screen-on re-arms the settle window. */
    @Volatile var active = false
        private set

    // Tasker: "_CalibrateDisplayRange" (AAB hand-off export, DD-077).
    suspend fun run() {
        sink.started()
        var record: DisplayCalibrationRecord? = null
        try {
            handoff.release()
            val handedOff = ctx.stateValue.handedOff
            // Never forces MANUAL: a mode write could land after stop()'s restoreMode() (DD-077).
            if (handedOff || !brightness.isManualMode()) {
                trace("refused: " + if (handedOff) "still handed off" else "brightness mode is not manual")
                return
            }
            active = true
            dimming.disengage()
            val original = brightness.read()
            val began = clock()
            failure = null
            try {
                record = sweep()
            } finally {
                withContext(NonCancellable) {
                    val restored = brightness.write(original).landed()
                    if (!restored) record = null
                    ctx.armInitialSettle(clock() + PipelineCycleRunner.INITIAL_SETTLE_MS)
                    active = false
                    summarize(record, restored, clock() - began)
                }
            }
        } finally {
            withContext(NonCancellable) { sink.finished(record) }
        }
    }

    private suspend fun sweep(): DisplayCalibrationRecord? {
        lockedField = null
        corroboration = emptyList()
        val points = ArrayList<Point>(DisplayCalibration.LEVELS.size)
        var previous = -1.0
        for ((i, level) in DisplayCalibration.LEVELS.withIndex()) {
            if (!brightness.write(level).landed()) return fail("level $level: write refused")
            if (i == 0) delay(FIRST_POINT_SETTLE_MS)
            val raw = readStable(level, previous) ?: return fail("level $level: no stable reading")
            points += Point(level, raw)
            previous = raw
        }
        val field = lockedField ?: return fail("no field locked")
        return DisplayCalibration.of(points)?.let { DisplayCalibrationRecord(it, field) } ?: fail("readings did not rise")
    }

    private fun fail(reason: String): DisplayCalibrationRecord? {
        failure = reason
        return null
    }

    private fun summarize(record: DisplayCalibrationRecord?, restored: Boolean, elapsedMs: Long) {
        if (corroboration.isNotEmpty()) trace("dump: " + corroboration.joinToString(" | "))
        val outcome = when {
            record != null -> "calibrated, ${record.calibration.points.size} points, field ${record.field}"
            !restored -> "failed (restore refused)"
            else -> "failed (${failure ?: "interrupted"})"
        }
        trace("summary: $outcome, restore ${if (restored) "landed" else "refused"}, ${elapsedMs}ms")
    }

    /** Waits until the reading stops ramping, has left the previous point, and repeats. */
    private suspend fun readStable(level: Int, previous: Double): Double? {
        var last: Double? = null
        val start = clock()
        val polls = ArrayList<String>(MAX_POLLS)
        var end = "poll limit"
        var accepted: Double? = null
        for (poll in 0 until MAX_POLLS) {
            if (clock() - start >= STABLE_WINDOW_MS) {
                end = "window ended"
                break
            }
            delay(POLL_MS)
            val raw = read(polls)
            if (raw != null && raw == last && raw != previous) {
                accepted = raw
                end = "stable at $raw"
                break
            }
            last = raw
        }
        trace("level $level: $end after ${clock() - start}ms; polls: ${polls.joinToString(" | ")}")
        return accepted
    }

    private suspend fun read(polls: MutableList<String>): Double? {
        val read = reader.trace(lockedField)
        polls += DisplayCalibrationTrace.poll(read)
        val sample: DisplayBrightnessSample = read.sample ?: return null
        if (lockedField == null) lockedField = sample.field
        if (read.corroboration.isNotEmpty()) corroboration = read.corroboration
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
