package com.tideo.autobrightness.app.settings

/**
 * Built-in brightness profiles seeded by Tasker task592 `_CreateDefaultProfiles`.
 *
 * All profiles start from [Default] (= task592's `getBaseProfile()`). Values that differ from
 * [AabSettings] constructor defaults (task570) are noted with their task592 source.
 *
 * Tasker: task592 "_CreateDefaultProfiles" Java L24133–L24360.
 */
object DefaultProfiles {

    // task592 getBaseProfile() — note: animation defaults differ from task570 init values
    val Default = AabSettings(
        animSteps = 50,            // task592: anim_steps 50 (task570 default is 20)
        minWaitMs = 5,             // task592: min_wait 5 (task570 default is 25)
        maxWaitMs = 30,            // task592: max_wait 30 (task570 default is 65)
        throttleDefaultMs = 1510L, // task592: throttle = 50*30+10 = 1510
        thresholdMidpoint = 3.0,   // task592: midpoint 3.0 (task570 default is log10(10000)=4.0)
        // All other fields use AabSettings() defaults (matching task570 init values)
    )

    // task592: max_bright 200, min_bright 1, scale 0.8, anim_steps 1, delta_factor 2.8;
    // thresh dark/dim/bright all 0.5; circadian+superdimming off
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

    // task592: anim_steps 50, min/max_wait 50/100, min/max_bright 20/255, delta_factor 0.5,
    // throttle 5010; thresh_bright 0.3, thresh_dark 0.4; form1a 6, form2b 8.8;
    // circadian off; superdimming ON (threshold 20)
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

    // task592: min_bright 25, offset 15, scale 1.15, anim_steps 10, min_wait 10, delta_factor 4;
    // form1a 8, z1_end 55, z2_end 18000; superdimming off
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

    // task592: superdimming pwm_sensitive ON (enabled false), threshold 15;
    // min_bright 1, min/max_wait 60/120, delta_factor 0.8, throttle 6010; thresh_dark 0.6; circadian off
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
