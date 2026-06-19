package com.tideo.autobrightness.app.ui

import org.junit.Test
import java.io.File
import kotlin.test.assertTrue

/**
 * S12.9c #8: no screen may show the internal "Coming in <segment>" placeholder copy. Deferred screens
 * are honest ("not available yet") and carry a `// TODO(S13): implement` marker in source instead of
 * leaking the segment plan into the UI.
 */
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
