package com.tideo.autobrightness.domain.panic

// Shake validator for prof769 Panic (Reset). Leaky-bucket accumulator (task528 A2).
// Sensitivity 0 = pass-through; re-arm via STATE profile (D-021).
class PanicShakeGate(sensitivity: Int) {
    private val sensitivity: Int = sensitivity.coerceIn(0, 10)
    private val targetScore: Double = this.sensitivity * 40.0
    private val threshold: Double = this.sensitivity * 2.0

    private var score: Double = 0.0
    private var completed: Boolean = false

    val isPassThrough: Boolean get() = sensitivity == 0
    val isComplete: Boolean get() = completed

    fun onSample(magnitude: Double): Boolean {
        if (completed) return true
        if (isPassThrough) {
            completed = true
            return true
        }
        score = if (magnitude > threshold) {
            score * 0.98 + (magnitude - threshold)
        } else {
            score * 0.90
        }
        if (score >= targetScore) completed = true
        return completed
    }
}
