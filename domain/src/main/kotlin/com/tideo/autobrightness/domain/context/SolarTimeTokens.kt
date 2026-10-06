package com.tideo.autobrightness.domain.context

/** The `SUNRISE±N` / `SUNSET±N` time-range token grammar, shared by the evaluator and the rule editor (DD-035). */
object SolarTimeTokens {

    const val SUNRISE = "SUNRISE"
    const val SUNSET = "SUNSET"

    private const val SECONDS_PER_DAY = 86_400L
    private val OFFSET_TEXT = Regex("^[+-]?[0-9]+$")

    fun eventOf(token: String): String? {
        val raw = token.javaTrim()
        return when {
            raw.startsWith(SUNRISE) -> SUNRISE
            raw.startsWith(SUNSET) -> SUNSET
            else -> null
        }
    }

    // Tasker: task43 act12 "_EvaluateContexts V2" rev 2026-10-01 hunks L18-30
    fun offsetMinutes(token: String): Long {
        val plus = token.indexOf('+')
        if (plus >= 0) return token.substring(plus + 1).javaTrim().toLongOrNull() ?: 0L
        val minus = token.indexOf('-')
        if (minus >= 0) return -(token.substring(minus + 1).javaTrim().toLongOrNull() ?: 0L)
        return 0L
    }

    fun resolve(baseLocalSecs: Long, offsetMinutes: Long): Long =
        Math.floorMod(baseLocalSecs + offsetMinutes * 60L, SECONDS_PER_DAY) / 60L * 60L

    fun editorOffsetText(token: String): String {
        val raw = token.trim()
        val plus = raw.indexOf('+')
        if (plus >= 0) return "+" + raw.substring(plus + 1).trim()
        val minus = raw.indexOf('-')
        if (minus >= 0) return "-" + raw.substring(minus + 1).trim()
        return ""
    }

    fun commit(event: String, offsetText: String): String? {
        val raw = offsetText.trim()
        if (raw.isEmpty()) return event
        if (!OFFSET_TEXT.matches(raw)) return null
        val minutes = raw.toLongOrNull() ?: return null
        return when {
            minutes > 0 -> "$event+$minutes"
            minutes < 0 -> "$event$minutes"
            else -> event
        }
    }

    internal fun String.javaTrim(): String = trim { it <= ' ' }
}
