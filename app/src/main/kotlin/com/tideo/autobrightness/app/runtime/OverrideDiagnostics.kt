package com.tideo.autobrightness.app.runtime

import com.tideo.autobrightness.platform.brightness.BrightnessWriteResult
import com.tideo.autobrightness.platform.brightness.WriteStatus

// DC-007 diagnostic assembly + DC-004's baseline rule, split out of the cycle runner's LOC ceiling.
internal fun PipelineState.buildOverrideDiagnostic(
    source: OverrideSource,
    disposition: OverrideDisposition,
    observed: Int,
    settled: Int,
    manualMode: Boolean,
    timestampMs: Long,
) = OverrideDiagnostic(
    source = source,
    disposition = disposition,
    observed = observed,
    settled = settled,
    expected = lastAppliedBrightness,
    manualMode = manualMode,
    write = lastBrightnessWrite,
    timestampMs = timestampMs,
)

// DC-004: acknowledged wins; unconfirmed records what we asked; unlanded keeps the previous baseline.
internal fun baselineAfter(result: BrightnessWriteResult?, current: Int?): Int? =
    when (result?.status) {
        WriteStatus.ACKNOWLEDGED -> result.acknowledgedDomain
        WriteStatus.WRITTEN_UNACKNOWLEDGED -> result.requestedDomain
        else -> current
    }
