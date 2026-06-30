package com.tideo.autobrightness.domain.context

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Table-driven unit tests for [ContextOverrideResolver], mirroring contexts_spec §4 PASS 3/4 and the
 * precedence matrix (priority → specificity → array order) 1:1 against task43's Java.
 */
class ContextOverrideResolverTest {

    // Wed 2026-..; DAY_OF_WEEK 4 (Wednesday), 12:00 local, in Cinema wifi, 50% battery, unplugged.
    private val noon = ContextSignals(
        app = "com.netflix.mediaclient",
        batteryPercent = 50,
        plugged = false,
        dayOfWeek = 4,
        nowSecondsOfDay = 12 * 3600,
        wifi = "HomeNet",
        sunriseLocalSecs = 6 * 3600,
        sunsetLocalSecs = 18 * 3600,
    )

    private fun rule(
        id: String,
        profile: String = "P_$id",
        priority: Int = 0,
        apps: List<String>? = null,
        wifi: List<String>? = null,
        battery: BatteryConstraint? = null,
        location: LocationConstraint? = null,
        timeRange: TimeRange? = null,
        days: List<Int>? = null,
    ) = ContextRuleSpec(id, "Rule $id", profile, priority, apps, wifi, battery, location, timeRange, days)

    // ---- single-dimension matching -----------------------------------------------------------

    @Test
    fun appMatch_appliesProfile() {
        val r = resolve(listOf(rule("a", apps = listOf("com.netflix.mediaclient"))))
        assertEquals("P_a", r.targetProfile)
        assertEquals("Rule a", r.activeContextName)
        assertEquals("a", r.matchedRuleId)
    }

    @Test
    fun appMismatch_fallsBackToUserProfile() {
        val r = resolve(listOf(rule("a", apps = listOf("com.other.app"))))
        assertEquals("MyProfile", r.targetProfile)
        assertNull(r.activeContextName)
        assertNull(r.matchedRuleId)
    }

    @Test
    fun wifiMatch_trimmedCompare() {
        val r = resolve(listOf(rule("w", wifi = listOf("  HomeNet  "))))
        assertEquals("P_w", r.targetProfile)
    }

    @Test
    fun batteryRange_inclusiveBounds() {
        assertEquals("P_b", resolve(listOf(rule("b", battery = BatteryConstraint(min = 50, max = 50)))).targetProfile)
        assertNull(resolve(listOf(rule("b", battery = BatteryConstraint(min = 51)))).activeContextName)
        assertNull(resolve(listOf(rule("b", battery = BatteryConstraint(max = 49)))).activeContextName)
    }

    @Test
    fun batteryOnPower_mustMatchPluggedState() {
        assertNull(resolve(listOf(rule("b", battery = BatteryConstraint(onPower = true)))).activeContextName)
        assertEquals("P_b", resolve(listOf(rule("b", battery = BatteryConstraint(onPower = false)))).targetProfile)
    }

    @Test
    fun batteryUnknown_negativePercent_neverMatchesBatteryRule() {
        // D-108: the service-start seed snapshot reports -1 ("no reading yet"). A max-only saver rule
        // (battery <= 20, unplugged) must NOT match the sentinel — otherwise the Battery Saver profile
        // flashes for one cycle before the real reading arrives. A real 0% (dead battery) still matches.
        val unknown = noon.copy(batteryPercent = -1)
        val saver = rule("sv", profile = "SAVER", battery = BatteryConstraint(max = 20))
        assertNull(ContextOverrideResolver.resolve(listOf(saver), unknown, userProfile = "MyProfile").activeContextName)
        // onPower-only rule is likewise suppressed while the reading is unknown.
        assertNull(
            ContextOverrideResolver.resolve(
                listOf(rule("p", battery = BatteryConstraint(onPower = false))),
                unknown,
                userProfile = "MyProfile",
            ).activeContextName,
        )
        // A real reading of 0% (not the sentinel) is a genuine low battery and DOES match the saver.
        val dead = noon.copy(batteryPercent = 0)
        assertEquals("SAVER", ContextOverrideResolver.resolve(listOf(saver), dead, userProfile = "MyProfile").targetProfile)
    }

    @Test
    fun location_insideRadiusMatches_outsideDoesNot() {
        val here = noon.copy(lat = 51.5000, lon = -0.1000)
        val inside = rule("l", location = LocationConstraint(51.5001, -0.1001, 150.0))
        val outside = rule("l", location = LocationConstraint(51.6000, -0.2000, 150.0))
        assertEquals("P_l", ContextOverrideResolver.resolve(listOf(inside), here).targetProfile)
        assertNull(ContextOverrideResolver.resolve(listOf(outside), here).activeContextName)
    }

    // ---- time-of-day + days ------------------------------------------------------------------

    @Test
    fun timeRange_daytimeWindowMatchesAtNoon() {
        val r = resolve(listOf(rule("t", timeRange = TimeRange("09:00", "17:00"))))
        assertEquals("P_t", r.targetProfile)
    }

    @Test
    fun timeRange_outsideWindowNoMatch() {
        val r = resolve(listOf(rule("t", timeRange = TimeRange("13:00", "17:00"))))
        assertNull(r.activeContextName)
    }

    @Test
    fun overnightRange_postMidnightTailUsesYesterdayMembership() {
        // 22:00 → 06:00; now 02:00 Wednesday (day 4). Today-tail uses YESTERDAY (Tue=3) membership.
        val night = noon.copy(nowSecondsOfDay = 2 * 3600)
        val rTueOnly = rule("n", timeRange = TimeRange("22:00", "06:00"), days = listOf(3))
        assertEquals("P_n", ContextOverrideResolver.resolve(listOf(rTueOnly), night).targetProfile)
        // Restrict to Wednesday only: the 02:00 tail belongs to Tuesday, so no match.
        val rWedOnly = rule("n", timeRange = TimeRange("22:00", "06:00"), days = listOf(4))
        assertNull(ContextOverrideResolver.resolve(listOf(rWedOnly), night).activeContextName)
    }

    @Test
    fun sunsetToken_resolvesToSignalSolarTime() {
        // SUNSET=18:00; range SUNSET→23:00. At 12:00 no match; at 19:00 match.
        val r1 = resolve(listOf(rule("s", timeRange = TimeRange("SUNSET", "23:00"))))
        assertNull(r1.activeContextName)
        val evening = noon.copy(nowSecondsOfDay = 19 * 3600)
        assertEquals("P_s", ContextOverrideResolver.resolve(listOf(rule("s", timeRange = TimeRange("SUNSET", "23:00"))), evening).targetProfile)
    }

    @Test
    fun daysOnly_matchesOnlyListedDay() {
        assertEquals("P_d", resolve(listOf(rule("d", days = listOf(4)))).targetProfile) // Wed
        assertNull(resolve(listOf(rule("d", days = listOf(2)))).activeContextName)       // Mon only
    }

    // ---- precedence: priority → specificity → array order ------------------------------------

    @Test
    fun precedence_highestPriorityWins() {
        val rules = listOf(
            rule("lo", profile = "LOW", priority = 1, apps = listOf("com.netflix.mediaclient")),
            rule("hi", profile = "HIGH", priority = 5, apps = listOf("com.netflix.mediaclient")),
        )
        assertEquals("HIGH", resolve(rules).targetProfile)
        assertEquals("hi", resolve(rules).matchedRuleId)
    }

    @Test
    fun precedence_tiePriority_higherSpecificityWins() {
        val rules = listOf(
            rule("one", profile = "ONE", priority = 2, apps = listOf("com.netflix.mediaclient")),
            rule("two", profile = "TWO", priority = 2, apps = listOf("com.netflix.mediaclient"), wifi = listOf("HomeNet")),
        )
        // both priority 2; "two" matches 2 dimensions vs 1 → wins.
        assertEquals("TWO", resolve(rules).targetProfile)
    }

    @Test
    fun precedence_tiePriorityAndSpecificity_firstInArrayWins() {
        val rules = listOf(
            rule("first", profile = "FIRST", priority = 0, apps = listOf("com.netflix.mediaclient")),
            rule("second", profile = "SECOND", priority = 0, apps = listOf("com.netflix.mediaclient")),
        )
        assertEquals("FIRST", resolve(rules).targetProfile)
    }

    @Test
    fun multiTrigger_allDimensionsMustMatch() {
        // app matches but battery does not → rule rejected, fall back.
        val r = resolve(
            listOf(
                rule(
                    "m",
                    apps = listOf("com.netflix.mediaclient"),
                    battery = BatteryConstraint(min = 80),
                ),
            ),
        )
        assertNull(r.activeContextName)
    }

    // ---- override / fallback semantics -------------------------------------------------------

    @Test
    fun overrideActive_skipsSwitchButStillComputesWakeTime() {
        val rules = listOf(rule("a", apps = listOf("com.netflix.mediaclient"), timeRange = TimeRange("09:00", "17:00")))
        val r = ContextOverrideResolver.resolve(rules, noon, overrideActive = true, userProfile = "MyProfile")
        assertNull(r.targetProfile)
        assertNull(r.activeContextName)
        // Wake time still computed: next future endpoint after 12:00 is 17:00.
        assertEquals("17.00", r.nextContextTime)
    }

    @Test
    fun noMatch_missingUserProfileCollapsesToDefault() {
        val r = ContextOverrideResolver.resolve(
            listOf(rule("a", apps = listOf("com.other"))),
            noon,
            userProfile = "GoneProfile",
            profileExists = { false },
        )
        assertEquals("Default", r.targetProfile)
    }

    @Test
    fun emptyRules_fallsBackToUserProfile_noWakeTime() {
        val r = ContextOverrideResolver.resolve(emptyList(), noon, userProfile = "MyProfile")
        assertEquals("MyProfile", r.targetProfile)
        assertNull(r.nextContextTime)
    }

    // ---- next wake time (task43 L459-475) ----------------------------------------------------

    @Test
    fun nextWakeTime_picksNearestFutureEndpoint() {
        // endpoints 09:00 & 17:00; now 12:00 → 09:00 is past (wraps to +21h), 17:00 is in +5h → nearest 17:00.
        val r = resolve(listOf(rule("t", timeRange = TimeRange("09:00", "17:00"))))
        assertEquals("17.00", r.nextContextTime)
    }

    @Test
    fun nextWakeTime_allPastWrapsToEarliestTomorrow() {
        val morning = noon.copy(nowSecondsOfDay = 20 * 3600) // 20:00
        val r = ContextOverrideResolver.resolve(
            listOf(rule("t", timeRange = TimeRange("09:00", "17:00"))),
            morning,
        )
        // both 09:00 and 17:00 are past; nearest wrap = 09:00 next day.
        assertEquals("09.00", r.nextContextTime)
    }

    @Test
    fun wakeTimes_collectedEvenFromNonMatchingRules() {
        // This rule's time window does not match now (02:00-03:00) but its endpoints still schedule.
        val r = resolve(listOf(rule("t", timeRange = TimeRange("02:00", "03:00"))))
        assertNull(r.activeContextName)
        assertTrue(r.nextContextTime == "02.00" || r.nextContextTime == "03.00")
    }

    // Owner report (2026-06-30): "Charging" (priority 81, time + on-power) vs "Low battery"
    // (priority 80, battery 0-30%). At 15:17 while charging at 27% BOTH rules match; the higher
    // priority (Charging, 81) must win regardless of specificity. Verifies priority is the primary
    // key (specificity is only a same-priority tie-break) — Charging here also has HIGHER specificity
    // (2 vs 1), so no ordering of priority/specificity could pick Low battery; if it ever loses, the
    // bug is upstream (evaluation not re-running while both match), not in this resolver.
    @Test
    fun higherPriorityWins_evenWhenBothMatch_chargingOverLowBattery() {
        val signals = noon.copy(
            batteryPercent = 27,
            plugged = true,
            nowSecondsOfDay = 15 * 3600 + 17 * 60,
        )
        val charging = rule(
            "charging", profile = "Outdoors", priority = 81,
            timeRange = TimeRange("07:00", "21:00"),
            battery = BatteryConstraint(onPower = true),
        )
        val lowBattery = rule(
            "low_batt", profile = "Battery Saver", priority = 80,
            battery = BatteryConstraint(min = 0, max = 30),
        )
        // Both rule orders must pick Charging (priority decides, not list order or specificity).
        listOf(listOf(charging, lowBattery), listOf(lowBattery, charging)).forEach { rules ->
            val r = ContextOverrideResolver.resolve(rules, signals, userProfile = "MyProfile")
            assertEquals("Outdoors", r.targetProfile)
            assertEquals("Rule charging", r.activeContextName)
        }
    }

    private fun resolve(rules: List<ContextRuleSpec>) =
        ContextOverrideResolver.resolve(rules, noon, userProfile = "MyProfile")
}
