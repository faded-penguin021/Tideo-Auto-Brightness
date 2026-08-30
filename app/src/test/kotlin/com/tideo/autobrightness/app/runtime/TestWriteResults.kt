package com.tideo.autobrightness.app.runtime

import com.tideo.autobrightness.platform.brightness.BrightnessWriteResult
import com.tideo.autobrightness.platform.brightness.WriteStatus

/** An ACKNOWLEDGED write of [level] that the provider stored as [stored] (default: verbatim). */
internal fun ackWrite(level: Int, stored: Int = level, deviceMax: Int = 255) =
    BrightnessWriteResult(level, level, stored, stored, deviceMax, WriteStatus.ACKNOWLEDGED)

/** A write that landed nowhere we can name: REFUSED, DENIED or WRITTEN_UNACKNOWLEDGED. */
internal fun unlandedWrite(level: Int, status: WriteStatus, deviceMax: Int = 255) =
    BrightnessWriteResult(level, level, null, null, deviceMax, status)
