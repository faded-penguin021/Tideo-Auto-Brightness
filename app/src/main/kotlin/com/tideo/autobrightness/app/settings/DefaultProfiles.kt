package com.tideo.autobrightness.app.settings

/**
 * Built-in brightness profiles; task592 "_CreateDefaultProfiles" Java L24133–L24360.
 */
object DefaultProfiles {

    // task592 getBaseProfile(); animation defaults differ from task570 init.
    // D-151/D-152: display-toggle fields stay at AabSettings defaults (leave device alone).
    val Default = AabSettings(
        animSteps = 50,            // task592: anim_steps 50 (task570 default is 20)
        minWaitMs = 5,             // task592: min_wait 5 (task570 default is 25)
        maxWaitMs = 30,            // task592: max_wait 30 (task570 default is 65)
        throttleDefaultMs = 1510L, // task592: throttle = 50*30+10 = 1510
        thresholdMidpoint = 3.0,   // task592: midpoint 3.0 (task570 default is log10(10000)=4.0)
        // All other fields use AabSettings() defaults (matching task570 init values)
    )

    // task592: min/max 1/200, scale 0.8, anim 1, delta 2.8, thresh dark/dim/bright 0.5.
    val BatterySaver = Default.copy(
        maxBrightness = 200,
        minBrightness = 1,
        scale = 0.8f,
        animSteps = 1,
        deltaFactor = 2.8f,
        thresholdDark = 0.5f,
        thresholdDim = 0.5f,
        thresholdBright = 0.5f,
    )

    // task592: anim 50, wait 50/100, bright 20/255, delta 0.5, thresh 0.3/0.4, form1a 6, dimming ON.
    val VideoStreaming = Default.copy(
        minWaitMs = 50,
        maxWaitMs = 100,
        minBrightness = 20,
        deltaFactor = 0.5f,
        throttleDefaultMs = 5010L, // 50*100+10
        thresholdBright = 0.3f,
        thresholdDark = 0.4f,
        form1A = 6.0,
        dimmingEnabled = true,
        dimmingThreshold = 20,
    )

    // task592: min 25, offset 15, scale 1.15, anim 10, wait 10, delta 4, form1a 8, zones 55/18000.
    val Outdoors = Default.copy(
        minBrightness = 25,
        offset = 15,
        scale = 1.15f,
        animSteps = 10,
        minWaitMs = 10,
        deltaFactor = 4.0f,
        form1A = 8.0,
        zone1End = 55,
        zone2End = 18000,
    )

    // task592: min 1, wait 60/120, delta 0.8, pwm_sensitive ON, thresh_dark 0.6.
    val NightReading = Default.copy(
        minBrightness = 1,
        pwmSensitive = true,
        dimmingThreshold = 15,
        minWaitMs = 60,
        maxWaitMs = 120,
        deltaFactor = 0.8f,
        throttleDefaultMs = 6010L, // 50*120+10
        thresholdDark = 0.6f,
    )

    // Ordered map used by the profile picker (name → settings)
    val all: Map<String, AabSettings> = mapOf(
        "Default" to Default,
        "Battery Saver" to BatterySaver,
        "Video Streaming" to VideoStreaming,
        "Outdoors" to Outdoors,
        "Night Reading" to NightReading,
    )
}
