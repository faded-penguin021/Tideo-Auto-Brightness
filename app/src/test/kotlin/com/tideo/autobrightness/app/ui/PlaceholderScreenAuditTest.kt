package com.tideo.autobrightness.app.ui

import org.junit.Test
import java.io.File
import kotlin.test.assertTrue

/** S12.9c #8: no placeholder "Coming in" copy; deferred screens use TODO marker instead (S13 audit). */
class PlaceholderScreenAuditTest {

    @Test
    fun `no Coming in text in ui screens`() {
        val root = File("src/main/kotlin/com/tideo/autobrightness/app/ui/screens")
        assertTrue(root.isDirectory, "expected screens sources at ${root.absolutePath}")

        val offenders = mutableListOf<String>()
        root.walkTopDown().filter { it.isFile && it.extension == "kt" }.forEach { file ->
            file.readLines().forEachIndexed { idx, line ->
                if (line.contains("coming in", ignoreCase = true)) {
                    offenders += "${file.name}:${idx + 1}: ${line.trim()}"
                }
            }
        }

        assertTrue(
            offenders.isEmpty(),
            "Found placeholder \"coming in\" copy in ui/screens (honest-ify it):\n" +
                offenders.joinToString("\n"),
        )
    }
}
