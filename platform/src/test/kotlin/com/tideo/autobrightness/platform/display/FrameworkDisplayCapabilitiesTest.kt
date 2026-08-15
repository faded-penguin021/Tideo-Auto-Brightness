package com.tideo.autobrightness.platform.display

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FrameworkDisplayCapabilitiesTest {
    private fun capabilities(
        booleans: Map<String, Boolean> = emptyMap(),
        strings: Map<String, String> = emptyMap(),
    ) = frameworkDisplayCapabilities(
        booleanResource = { booleans[it] ?: false },
        stringResource = { strings[it].orEmpty() },
    )

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
