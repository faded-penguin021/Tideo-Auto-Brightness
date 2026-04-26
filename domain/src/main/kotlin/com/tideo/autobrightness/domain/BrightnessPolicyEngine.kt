package com.tideo.autobrightness.domain

class BrightnessPolicyEngine {
    fun computeTarget(lux: Float, context: UserContext): Int {
        val base = when {
            lux < 2f -> 8
            lux < 10f -> 20
            lux < 60f -> 40
            lux < 200f -> 65
            else -> 90
        }

        val nightBias = if (context.hourOfDay < 6 || context.hourOfDay >= 22) -10 else 0
        val chargingBias = if (context.isCharging) 5 else 0

        return (base + nightBias + chargingBias).coerceIn(1, 100)
    }
}
