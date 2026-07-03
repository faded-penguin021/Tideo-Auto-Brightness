package com.tideo.autobrightness.domain.display

import com.tideo.autobrightness.domain.context.BatteryConstraint
import com.tideo.autobrightness.domain.context.ContextSignals
import com.tideo.autobrightness.domain.context.LocationConstraint
import com.tideo.autobrightness.domain.context.TimeRange
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Truth-table tests for [DisplayRulesResolver] (Privileged Display Control, D-149). The merge
 * policy under test: ALL matching enabled rules apply, per-action OR, `null` = no opinion
 * (→ restore); disabled rules fully inert. Trigger semantics are shared with the golden-tested
 * context resolver via `ContextMatching`, so single-dimension edge cases are spot-checked here
 * (overnight day membership, battery sentinel, wifi trim, SUNSET token, location radius) rather
 * than re-proven exhaustively.
 */
class DisplayRulesResolverTest {

    // Wednesday (DAY_OF_WEEK 4) 12:00 local, Netflix foreground, HomeNet wifi, 50% unplugged.
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
        action: DisplayAction = DisplayAction.GRAYSCALE,
        enabled: Boolean = true,
        apps: List<String>? = null,
        wifi: List<String>? = null,
        battery: BatteryConstraint? = null,
        location: LocationConstraint? = null,
        timeRange: TimeRange? = null,
        days: List<Int>? = null,
    ) = DisplayRuleSpec(id, "Rule $id", enabled, action, apps, wifi, battery, location, timeRange, days)

    // ---- the core schedule case from the plan: weekday-night app-scoped grayscale --------------

    /** days=[Mon..Fri] 22:00–06:00 app-scoped: Sat 01:00 is FRIDAY's overnight tail → matches. */
    @Test
    fun weekdayOvernightRule_matchesSaturdayEarlyMorning_fridaysTail() {
        val socialCurfew = rule(
            "curfew",
            apps = listOf("com.netflix.mediaclient"),
            timeRange = TimeRange("22:00", "06:00"),
            days = listOf(2, 3, 4, 5, 6), // Mon..Fri
        )
        val satNight = noon.copy(dayOfWeek = 7, nowSecondsOfDay = 1 * 3600) // Sat 01:00
        val r = DisplayRulesResolver.resolve(listOf(socialCurfew), satNight)
        assertEquals(true, r.grayscale)
        assertNull(r.nightLight)
        assertNull(r.inversion)
    }

    /** Sun 23:00 is SUNDAY's pre-midnight head — Sunday is not a listed day → no match. */
    @Test
    fun weekdayOvernightRule_doesNotMatchSundayLateEvening() {
        val socialCurfew = rule(
            "curfew",
            apps = listOf("com.netflix.mediaclient"),
            timeRange = TimeRange("22:00", "06:00"),
            days = listOf(2, 3, 4, 5, 6),
        )
        val sunNight = noon.copy(dayOfWeek = 1, nowSecondsOfDay = 23 * 3600) // Sun 23:00
        assertNull(DisplayRulesResolver.resolve(listOf(socialCurfew), sunNight).grayscale)
    }

    /** Fri 23:00 (the engage side of the same window) matches; a non-listed app does not. */
    @Test
    fun weekdayOvernightRule_appScopeGates() {
        val socialCurfew = rule(
            "curfew",
            apps = listOf("com.netflix.mediaclient"),
            timeRange = TimeRange("22:00", "06:00"),
            days = listOf(2, 3, 4, 5, 6),
        )
        val friNight = noon.copy(dayOfWeek = 6, nowSecondsOfDay = 23 * 3600)
        assertEquals(true, DisplayRulesResolver.resolve(listOf(socialCurfew), friNight).grayscale)
        val otherApp = friNight.copy(app = "com.other.app")
        assertNull(DisplayRulesResolver.resolve(listOf(socialCurfew), otherApp).grayscale)
    }

    // ---- merge policy: all-matching, per-action OR ---------------------------------------------

    @Test
    fun multiRule_differentActions_bothApply() {
        val r = DisplayRulesResolver.resolve(
            listOf(
                rule("g", action = DisplayAction.GRAYSCALE, apps = listOf("com.netflix.mediaclient")),
                rule("n", action = DisplayAction.NIGHT_LIGHT, timeRange = TimeRange("09:00", "17:00")),
            ),
            noon,
        )
        assertEquals(true, r.grayscale)
        assertEquals(true, r.nightLight)
        assertNull(r.inversion) // no rule holds an opinion → restore
    }

    @Test
    fun sameAction_orAcrossRules_oneMatchSuffices() {
        val r = DisplayRulesResolver.resolve(
            listOf(
                rule("miss", action = DisplayAction.INVERSION, apps = listOf("com.other.app")),
                rule("hit", action = DisplayAction.INVERSION, wifi = listOf("HomeNet")),
            ),
            noon,
        )
        assertEquals(true, r.inversion)
    }

    @Test
    fun noRules_allActionsNull_noBoundary() {
        val r = DisplayRulesResolver.resolve(emptyList(), noon)
        assertNull(r.grayscale)
        assertNull(r.nightLight)
        assertNull(r.inversion)
        assertNull(r.nextBoundary)
    }

    @Test
    fun matchingRule_neverAssertsOtherActions() {
        val r = DisplayRulesResolver.resolve(
            listOf(rule("g", action = DisplayAction.GRAYSCALE, days = listOf(4))),
            noon,
        )
        assertEquals(true, r.grayscale)
        assertNull(r.nightLight)
        assertNull(r.inversion)
    }

    // ---- disabled rules are fully inert --------------------------------------------------------

    @Test
    fun disabledRule_doesNotMatch_andDoesNotSchedule() {
        val r = DisplayRulesResolver.resolve(
            listOf(
                rule(
                    "off",
                    enabled = false,
                    apps = listOf("com.netflix.mediaclient"),
                    timeRange = TimeRange("09:00", "17:00"),
                ),
            ),
            noon,
        )
        assertNull(r.grayscale)
        assertNull(r.nextBoundary) // its 09:00/17:00 endpoints must NOT wake the coordinator
    }

    // ---- nextBoundary via the shared nextWakeTime ----------------------------------------------

    @Test
    fun nextBoundary_nearestFutureEndpoint_matchingRule() {
        // Window 09:00–17:00 at noon: 17:00 is the release boundary, +5h → nearest.
        val r = DisplayRulesResolver.resolve(
            listOf(rule("t", timeRange = TimeRange("09:00", "17:00"))),
            noon,
        )
        assertEquals("17.00", r.nextBoundary)
    }

    @Test
    fun nextBoundary_nonMatchingEnabledRuleStillSchedulesItsEngageEdge() {
        // Window 02:00–03:00 at noon: no match now, but 02:00 tomorrow (+14h) beats 03:00 (+15h).
        val r = DisplayRulesResolver.resolve(
            listOf(rule("t", timeRange = TimeRange("02:00", "03:00"))),
            noon,
        )
        assertNull(r.grayscale)
        assertEquals("02.00", r.nextBoundary)
    }

    @Test
    fun nextBoundary_nullWhenNoRuleHasTimeRange() {
        val r = DisplayRulesResolver.resolve(
            listOf(rule("a", apps = listOf("com.netflix.mediaclient"))),
            noon,
        )
        assertEquals(true, r.grayscale)
        assertNull(r.nextBoundary)
    }

    // ---- shared trigger semantics spot-checks (full matrix lives in ContextOverrideResolverTest)

    @Test
    fun batteryUnknownSentinel_neverMatches_D108() {
        val saver = rule("b", battery = BatteryConstraint(max = 20))
        val unknown = noon.copy(batteryPercent = -1)
        assertNull(DisplayRulesResolver.resolve(listOf(saver), unknown).grayscale)
        // A real 0% reading is a genuine low battery and does match.
        val dead = noon.copy(batteryPercent = 0)
        assertEquals(true, DisplayRulesResolver.resolve(listOf(saver), dead).grayscale)
    }

    @Test
    fun wifiTrimmedCompare() {
        val r = DisplayRulesResolver.resolve(listOf(rule("w", wifi = listOf("  HomeNet  "))), noon)
        assertEquals(true, r.grayscale)
    }

    @Test
    fun sunsetToken_resolvesFromSignals() {
        val evening = noon.copy(nowSecondsOfDay = 19 * 3600) // SUNSET=18:00
        val nightLight = rule("s", action = DisplayAction.NIGHT_LIGHT, timeRange = TimeRange("SUNSET", "23:00"))
        assertNull(DisplayRulesResolver.resolve(listOf(nightLight), noon).nightLight)
        assertEquals(true, DisplayRulesResolver.resolve(listOf(nightLight), evening).nightLight)
    }

    @Test
    fun location_insideRadiusMatches_outsideDoesNot() {
        val here = noon.copy(lat = 51.5000, lon = -0.1000)
        val inside = rule("l", location = LocationConstraint(51.5001, -0.1001, 150.0))
        val outside = rule("l", location = LocationConstraint(51.6000, -0.2000, 150.0))
        assertEquals(true, DisplayRulesResolver.resolve(listOf(inside), here).grayscale)
        assertNull(DisplayRulesResolver.resolve(listOf(outside), here).grayscale)
    }

    @Test
    fun multiTrigger_allDimensionsMustMatch() {
        val r = DisplayRulesResolver.resolve(
            listOf(
                rule(
                    "m",
                    apps = listOf("com.netflix.mediaclient"),
                    battery = BatteryConstraint(min = 80), // fails at 50%
                ),
            ),
            noon,
        )
        assertNull(r.grayscale)
    }

    // ---- DisplayResolution.desired accessor ----------------------------------------------------

    @Test
    fun desiredAccessor_mapsEveryAction() {
        val r = DisplayResolution(grayscale = true, nightLight = null, inversion = true)
        assertEquals(true, r.desired(DisplayAction.GRAYSCALE))
        assertNull(r.desired(DisplayAction.NIGHT_LIGHT))
        assertEquals(true, r.desired(DisplayAction.INVERSION))
        // Exhaustiveness: every enum entry resolves through the accessor without throwing.
        DisplayAction.entries.forEach { assertTrue(r.desired(it) == true || r.desired(it) == null) }
    }
}
