package com.tideo.autobrightness.domain.brightness

import kotlin.math.roundToInt

/** `%AAB_BrightCal`: brightness levels against the raw `dumpsys display` value each produced (DD-077). */
class DisplayCalibration private constructor(val points: List<Point>) {
    data class Point(val level: Int, val raw: Double)

    // Tasker: "_ReadBrightness" (AAB hand-off export, DD-077).
    fun levelFor(raw: Double): Int? {
        if (raw < 0 || !raw.isFinite()) return null
        val first = points.first()
        val last = points.last()
        val level = when {
            raw <= first.raw -> first.level.toDouble()
            raw > last.raw -> {
                val previous = points[points.size - 2]
                val slope = (last.level - previous.level) / (last.raw - previous.raw)
                last.level + (raw - last.raw) * slope
            }
            else -> {
                val upper = points.indexOfFirst { raw <= it.raw }
                val lower = points[upper - 1]
                val fraction = (raw - lower.raw) / (points[upper].raw - lower.raw)
                lower.level + fraction * (points[upper].level - lower.level)
            }
        }
        return level.takeIf { it.isFinite() }?.roundToInt()
    }

    fun encode(): String = points.joinToString(",") { "${it.level}:${it.raw}" }

    companion object {
        // Tasker: "_CalibrateDisplayRange" (AAB hand-off export, DD-077).
        val LEVELS = listOf(1, 5, 10, 25, 50, 75, 100, 128, 150, 175, 200, 225, 255)
        const val HBM_ABOVE = 255

        /** Null unless every value is finite and raw strictly increases, as `_ReadBrightness` requires. */
        fun of(points: List<Point>): DisplayCalibration? {
            if (points.size < 2) return null
            if (points.any { !it.raw.isFinite() }) return null
            if (points.zipWithNext().any { (a, b) -> b.raw <= a.raw }) return null
            return DisplayCalibration(points.toList())
        }

        fun decode(encoded: String): DisplayCalibration? = runCatching {
            of(
                encoded.split(",").map { part ->
                    val (level, raw) = part.trim().split(":").also { require(it.size == 2) }
                    Point(level.trim().toInt(), raw.trim().toDouble())
                },
            )
        }.getOrNull()
    }
}
