package com.tideo.autobrightness.app.runtime

import androidx.annotation.StringRes
import com.tideo.autobrightness.R
import com.tideo.autobrightness.app.runtime.CalibrationFailure.ADAPTIVE
import com.tideo.autobrightness.app.runtime.CalibrationFailure.BATTERY_SAVER
import com.tideo.autobrightness.app.runtime.CalibrationFailure.CEILING_UNCONFIRMED
import com.tideo.autobrightness.app.runtime.CalibrationFailure.INTERRUPTED
import com.tideo.autobrightness.app.runtime.CalibrationFailure.NO_ACCESS
import com.tideo.autobrightness.app.runtime.CalibrationFailure.NO_READING
import com.tideo.autobrightness.app.runtime.CalibrationFailure.READING_LOST
import com.tideo.autobrightness.app.runtime.CalibrationFailure.UNSTEADY
import com.tideo.autobrightness.domain.brightness.DisplayCalibration
import com.tideo.autobrightness.domain.brightness.DisplayCalibration.Point
import com.tideo.autobrightness.platform.brightness.DisplayBrightnessDump
import com.tideo.autobrightness.platform.brightness.DisplayBrightnessRead
import com.tideo.autobrightness.platform.brightness.DisplayBrightnessReader
import com.tideo.autobrightness.platform.brightness.DisplayBrightnessSample
import com.tideo.autobrightness.platform.brightness.DumpStatus
import com.tideo.autobrightness.platform.brightness.ScreenBrightnessController
import com.tideo.autobrightness.platform.brightness.WriteStatus
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

data class DisplayCalibrationRecord(val calibration: DisplayCalibration, val field: String, val hbmBoundary: Double? = null)

/** Why a calibration failed, each with the one message the card and the flash share (DE-032). */
enum class CalibrationFailure(@StringRes val message: Int) {
    NO_ACCESS(R.string.calibration_failed_no_access),
    NO_READING(R.string.calibration_failed_no_reading),
    READING_LOST(R.string.calibration_failed_reading_lost),
    ADAPTIVE(R.string.calibration_failed_adaptive),
    UNSTEADY(R.string.calibration_failed_unsteady),
    CEILING_UNCONFIRMED(R.string.calibration_failed_ceiling_unconfirmed),
    INTERRUPTED(R.string.calibration_failed_interrupted),
    BATTERY_SAVER(R.string.calibration_failed_battery_saver),
}

sealed interface DisplayCalibrationOutcome {
    data class Calibrated(val record: DisplayCalibrationRecord) : DisplayCalibrationOutcome
    data class Failed(val cause: CalibrationFailure) : DisplayCalibrationOutcome
}

interface DisplayCalibrationSink {
    suspend fun started()

    suspend fun finished(outcome: DisplayCalibrationOutcome)
}

object NoDisplayBrightnessReader : DisplayBrightnessReader {
    override suspend fun read(field: String?): DisplayBrightnessSample? = null
}

object NoOpDisplayCalibrationSink : DisplayCalibrationSink {
    override suspend fun started() = Unit
    override suspend fun finished(outcome: DisplayCalibrationOutcome) = Unit
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
    private val batterySaver: () -> Boolean = { false },
) {
    private var lockedField: String? = null
    private var failure: String? = null
    private var cause: CalibrationFailure? = null
    private var unread: CalibrationFailure = NO_READING
    private var corroboration: List<String> = emptyList()

    /** Read by the override gate; a separate hold, since a screen-on re-arms the settle window. */
    @Volatile var active = false
        private set

    // Tasker: "_CalibrateDisplayRange" (AAB hand-off export, DD-077). False: refused before any write.
    suspend fun run(): Boolean {
        if (batterySaver()) {
            trace("refused: battery saver is on")
            withContext(NonCancellable) { sink.finished(DisplayCalibrationOutcome.Failed(BATTERY_SAVER)) }
            return false
        }
        sink.started()
        var record: DisplayCalibrationRecord? = null
        cause = null
        try {
            handoff.release()
            val handedOff = ctx.stateValue.handedOff
            // Never forces MANUAL: a mode write could land after stop()'s restoreMode() (DD-077).
            if (handedOff || !brightness.isManualMode()) {
                trace("refused: " + if (handedOff) "still handed off" else "brightness mode is not manual")
                cause = ADAPTIVE
                return true
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
                    if (!restored) {
                        record = null
                        cause = INTERRUPTED
                    }
                    ctx.armInitialSettle(clock() + PipelineCycleRunner.INITIAL_SETTLE_MS)
                    active = false
                    summarize(record, restored, clock() - began)
                }
            }
        } finally {
            withContext(NonCancellable) {
                sink.finished(record?.let { DisplayCalibrationOutcome.Calibrated(it) } ?: DisplayCalibrationOutcome.Failed(cause ?: INTERRUPTED))
            }
        }
        return true
    }

    private suspend fun sweep(): DisplayCalibrationRecord? {
        lockedField = null
        corroboration = emptyList()
        val points = ArrayList<Point>(DisplayCalibration.LEVELS.size)
        var previous = -1.0
        for ((i, level) in DisplayCalibration.LEVELS.withIndex()) {
            if (!brightness.write(level).landed()) return fail(INTERRUPTED, "level $level: write refused")
            if (i == 0) delay(FIRST_POINT_SETTLE_MS)
            val raw = readStable(level, previous) ?: return fail(unread, "level $level: no stable reading")
            if (raw == previous) {
                if (points.last().level < PLATEAU_FLOOR) return fail(UNSTEADY, "level $level: no stable reading")
                return ceiling(points, raw)
            }
            points += Point(level, raw)
            previous = raw
        }
        return record(points, null)
    }

    private suspend fun ceiling(points: MutableList<Point>, plateau: Double): DisplayCalibrationRecord? {
        if (!brightness.write(TOP).landed()) return fail(INTERRUPTED, "level $TOP: write refused")
        val top = readStable(TOP, plateau) ?: return fail(unread, "level $TOP: no stable reading")
        if (top != plateau) return fail(UNSTEADY, "level $TOP: left the plateau at $plateau")
        if (!DisplayBrightnessDump.isHbmBoundary(corroboration, plateau)) {
            return fail(CEILING_UNCONFIRMED, "plateau at $plateau not corroborated by the dump")
        }
        points[points.lastIndex] = Point(TOP, plateau)
        return record(points, plateau)
    }

    private fun record(points: List<Point>, hbmBoundary: Double?): DisplayCalibrationRecord? {
        val field = lockedField ?: return fail(NO_READING, "no field locked")
        return DisplayCalibration.of(points)?.let { DisplayCalibrationRecord(it, field, hbmBoundary) } ?: fail(UNSTEADY, "readings did not rise")
    }

    private fun fail(cause: CalibrationFailure, reason: String): DisplayCalibrationRecord? {
        this.cause = cause
        failure = reason
        return null
    }

    private fun summarize(record: DisplayCalibrationRecord?, restored: Boolean, elapsedMs: Long) {
        if (corroboration.isNotEmpty()) trace("dump: " + corroboration.joinToString(" | "))
        val outcome = when {
            record != null -> "calibrated, ${record.calibration.points.size} points, field ${record.field}" +
                record.hbmBoundary?.let { ", hbm boundary $it" }.orEmpty()
            !restored -> "failed (restore refused)"
            else -> "failed (${failure ?: "interrupted"})"
        }
        val named = if (record == null) ", cause ${cause ?: INTERRUPTED}" else ""
        trace("summary: $outcome, restore ${if (restored) "landed" else "refused"}, ${elapsedMs}ms$named")
    }

    /** A repeated reading that left [previous]; [previous] itself when the window held on it (DE-030); null sets [unread]. */
    private suspend fun readStable(level: Int, previous: Double): Double? {
        var last: Double? = null
        var held = 0
        var differed = false
        var sampled = false
        var reachable = false
        val wasRead = lockedField != null
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
            val read = read(polls)
            val raw = read.sample?.raw
            if (raw != null) sampled = true
            if (read.attempts.isEmpty() || read.attempts.any { it.status != DumpStatus.UNAVAILABLE }) reachable = true
            if (raw != null && raw == last && raw != previous) {
                accepted = raw
                end = "stable at $raw"
                break
            }
            if (raw != null && raw != previous) differed = true
            held = if (raw == previous) held + 1 else 0
            last = raw
        }
        if (accepted == null && !differed && held >= 2) {
            accepted = previous
            end += ", plateau at $previous"
        }
        unread = when {
            sampled -> UNSTEADY
            wasRead -> READING_LOST
            reachable -> NO_READING
            else -> NO_ACCESS
        }
        trace("level $level: $end after ${clock() - start}ms; polls: ${polls.joinToString(" | ")}")
        return accepted
    }

    private suspend fun read(polls: MutableList<String>): DisplayBrightnessRead {
        val read = reader.trace(lockedField)
        polls += DisplayCalibrationTrace.poll(read)
        val sample: DisplayBrightnessSample = read.sample ?: return read
        if (lockedField == null) lockedField = sample.field
        corroboration = read.corroboration
        return read
    }

    private fun com.tideo.autobrightness.platform.brightness.BrightnessWriteResult.landed() =
        status == WriteStatus.ACKNOWLEDGED || status == WriteStatus.WRITTEN_UNACKNOWLEDGED

    private companion object {
        const val FIRST_POINT_SETTLE_MS = 800L
        const val POLL_MS = 150L
        const val STABLE_WINDOW_MS = 3_000L
        const val MAX_POLLS = (STABLE_WINDOW_MS / POLL_MS).toInt()
        const val PLATEAU_FLOOR = 128
        val TOP = DisplayCalibration.LEVELS.last()
    }
}
