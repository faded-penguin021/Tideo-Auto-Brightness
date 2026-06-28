package com.tideo.autobrightness.domain.panic

/**
 * Sensitivity-gated shake validator for the prof769 "Panic (Reset)" task.
 *
 * Tasker: task528 `_PanicButton` A2 "Java Code" (XML — see `tmp/Panic_profile_task.md` A2). After the
 * profile state (Upside-Down ∧ Display-On ∧ `%AAB_Proximity !~ Near`) arms the task, A2 watches the
 * accelerometer for up to 10 s and only lets the panic proceed if a sustained shake accumulates enough
 * "score". The intensity/duration required scales with `%AAB_PanicSensitivity` (0–10):
 *
 *  - `targetScore = sensitivity * 40`, `threshold = sensitivity * 2` (m/s²).
 *  - Each reading's gravity-stripped magnitude `mag` updates a **leaky bucket**: only force *above*
 *    `threshold` earns points, and the bucket constantly leaks, so a half-hearted shake physically
 *    cannot out-pace the decay to reach `targetScore`:
 *      - `mag > threshold` → `score = score * 0.98 + (mag − threshold)` (slow leak while pushing),
 *      - otherwise         → `score = score * 0.90` (fast drain when the user pauses).
 *  - Success the instant `score ≥ targetScore`. A2 returns `should_stop = "false"` (proceed); a 10 s
 *    timeout returns `"true"` (veto — panic does NOT fire). Re-arming after a veto is the caller's job
 *    (the profile is a STATE: flip straight then upside-down again — D-021).
 *  - **`sensitivity == 0` is pass-through** — A2 returns `"false"` immediately, before registering any
 *    listener, so the panic fires with no shake requirement at all.
 *
 * Pure + frame-by-frame so the leaky-bucket timing is golden-tested against the A2 Java without a
 * SensorManager; the platform source computes the magnitude (gravity-stripped linear acceleration) and
 * feeds it here. Sensitivity is clamped 0..10 exactly as the Java does.
 */
class PanicShakeGate(sensitivity: Int) {
    private val sensitivity: Int = sensitivity.coerceIn(0, 10)
    private val targetScore: Double = this.sensitivity * 40.0
    private val threshold: Double = this.sensitivity * 2.0

    private var score: Double = 0.0
    private var completed: Boolean = false

    /** True when no shake is required at all (`sensitivity == 0`): the caller should fire immediately. */
    val isPassThrough: Boolean get() = sensitivity == 0

    /** True once a qualifying shake has accumulated enough score (success latches). */
    val isComplete: Boolean get() = completed

    /**
     * Feed one accelerometer reading's gravity-stripped magnitude (m/s²). Returns true once the
     * accumulated shake score reaches `targetScore` (success), latching thereafter. Pass-through
     * (`sensitivity == 0`) completes on the first sample.
     */
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
