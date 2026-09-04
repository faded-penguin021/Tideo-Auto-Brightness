package com.tideo.autobrightness.app.runtime

import com.tideo.autobrightness.platform.brightness.BrightnessWriteResult
import com.tideo.autobrightness.platform.brightness.WriteStatus

/**
 * An ACKNOWLEDGED write of domain [level] that the provider stored as domain [stored].
 * Domain-space only (deviceMax 255), so raw == domain and the fixture cannot conflate the two —
 * a fake that needs a device range must build [BrightnessWriteResult] itself.
 */
internal fun ackWrite(level: Int, stored: Int = level) =
    BrightnessWriteResult(level, level, stored, stored, 255, WriteStatus.ACKNOWLEDGED)

/** A write that landed nowhere we can name: REFUSED, DENIED or WRITTEN_UNACKNOWLEDGED. */
internal fun unlandedWrite(level: Int, status: WriteStatus) =
    BrightnessWriteResult(level, level, null, null, 255, status)
