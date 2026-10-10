package com.tideo.autobrightness.platform.display

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FrameworkDisplayCapabilitiesTest {
    private fun capabilities(
        booleans: Map<String, Boolean> = emptyMap(),
        strings: Map<String, String> = emptyMap(),
        integers: Map<String, Int> = emptyMap(),
    ) = frameworkDisplayCapabilities(
        booleanResource = { booleans[it] ?: false },
        stringResource = { strings[it].orEmpty() },
        integerResource = { integers[it] },
    )

    private fun kelvin(min: Int? = null, max: Int? = null, default: Int? = null) = capabilities(
        integers = buildMap {
            min?.let { put("config_nightDisplayColorTemperatureMin", it) }
            max?.let { put("config_nightDisplayColorTemperatureMax", it) }
            default?.let { put("config_nightDisplayColorTemperatureDefault", it) }
        },
    ).nightLightRange

    @Test
    fun `framework integer resolves its value and is null when missing or unreadable`() {
        val found: (String, String, String) -> Int = { name, type, pkg ->
            if (name == "config_nightDisplayColorTemperatureMin" && type == "integer" && pkg == "android") 5 else 0
        }
        assertEquals(1800, frameworkInteger("config_nightDisplayColorTemperatureMin", found) { 1800 })
        assertNull(frameworkInteger("missing", found) { 1800 })
        assertNull(frameworkInteger("config_nightDisplayColorTemperatureMin", found) { error("unreadable") })
    }

    @Test
    fun `Night Light range follows a device whose config diverges from AOSP`() {
        assertEquals(NightLightKelvinRange(1800, 5000, 3000), kelvin(min = 1800, max = 5000, default = 3000))
    }

    @Test
    fun `absent Night Light config falls back to AOSP value by value`() {
        assertEquals(NightLightKelvinRange.AOSP, kelvin())
        assertEquals(NightLightKelvinRange(2000, 4082, 2850), kelvin(min = 2000))
    }

    @Test
    fun `Night Light range stays inside the write rails and the default inside the range`() {
        assertEquals(NightLightKelvinRange(686, 4082, 2850), kelvin(min = 686))
        assertEquals(NightLightKelvinRange(686, 4082, 2850), kelvin(min = 500))
        assertEquals(NightLightKelvinRange(2596, 7308, 2850), kelvin(max = 12_000))
        assertEquals(NightLightKelvinRange(2596, 4082, 4082), kelvin(default = 6500))
    }

    @Test
    fun `a degenerate Night Light pair keeps the AOSP range`() {
        assertEquals(NightLightKelvinRange.AOSP, kelvin(min = 4000, max = 3000))
        assertEquals(NightLightKelvinRange.AOSP, kelvin(min = 3000, max = 3000))
    }

    @Test
    fun `framework boolean resolves true and false and fails closed when missing or unreadable`() {
        val found: (String, String, String) -> Int = { name, type, pkg ->
            if (name == "config_nightDisplayAvailable" && type == "bool" && pkg == "android") 7 else 0
        }
        assertTrue(frameworkBoolean("config_nightDisplayAvailable", found) { true })
        assertFalse(frameworkBoolean("config_nightDisplayAvailable", found) { false })
        assertFalse(frameworkBoolean("missing", found) { true })
        assertFalse(frameworkBoolean("config_nightDisplayAvailable", found) { error("unreadable") })
    }

    @Test
    fun `framework string resolves content and fails closed when missing or unreadable`() {
        val found: (String, String, String) -> Int = { name, type, pkg ->
            if (name == "config_dozeComponent" && type == "string" && pkg == "android") 9 else 0
        }
        assertTrue(frameworkString("config_dozeComponent", found) { "component" }.isNotEmpty())
        assertTrue(frameworkString("missing", found) { "component" }.isEmpty())
        assertTrue(frameworkString("config_dozeComponent", found) { error("unreadable") }.isEmpty())
    }

    @Test
    fun `Night Light follows the exact framework boolean and missing fails closed`() {
        assertTrue(capabilities(booleans = mapOf("config_nightDisplayAvailable" to true)).nightLightAvailable)
        assertFalse(capabilities(booleans = mapOf("config_nightDisplayAvailable" to false)).nightLightAvailable)
        assertFalse(capabilities().nightLightAvailable)
    }

    @Test
    fun `AOD requires both framework flag and ambient display component`() {
        assertTrue(
            capabilities(
                booleans = mapOf("config_dozeAlwaysOnDisplayAvailable" to true),
                strings = mapOf("config_dozeComponent" to "com.android.systemui/.doze.DozeService"),
            ).alwaysOnDisplayAvailable,
        )
        assertFalse(
            capabilities(
                booleans = mapOf("config_dozeAlwaysOnDisplayAvailable" to true),
            ).alwaysOnDisplayAvailable,
        )
        assertFalse(
            capabilities(
                strings = mapOf("config_dozeComponent" to "com.android.systemui/.doze.DozeService"),
            ).alwaysOnDisplayAvailable,
        )
    }
}
