package com.tideo.autobrightness.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * S12.9d i18n ratchet. The app shipped ~97 hardcoded `Text("…")` UI strings; S12.9d extracted the
 * high-priority surfaces (notification strings, screen titles, Dashboard labels/buttons) into
 * `strings.xml`, and deferred the rest (section headers, per-field labels, long-press help) to S14.
 *
 * This test is a heuristic gate, not a full audit: it counts the inline `Text("…")` literals still
 * in the Compose UI tree and fails if the count rises above the post-S12.9d baseline — so a NEW
 * hardcoded UI string is caught, while the documented backlog is allowed. S14 lowers [CEILING] as it
 * extracts more. It also locks the converted notification surface (no literal notification strings).
 */
class HardcodedStringCheckTest {

    private val uiRoot = File("src/main/kotlin/com/tideo/autobrightness/app/ui")
    private val serviceFile =
        File("src/main/kotlin/com/tideo/autobrightness/app/runtime/AmbientMonitoringService.kt")

    // A literal string as the first argument to a Compose Text() — `Text("…"` (single line, mirrors
    // the project's own DeadApiCheckTest line-based scan). Comments are stripped before matching.
    private val textLiteral = Regex("""Text\(\s*"""")

    companion object {
        // Post-S12.9d count of inline Text("…") literals under app/ui. RATCHET DOWN ONLY (S14 owns
        // the remaining backlog of section headers / field labels / help text — STATE.md D-075).
        private const val CEILING = 92
    }

    @Test
    fun uiTextLiteralsDoNotExceedRatchet() {
        assertTrue("expected UI sources at ${uiRoot.absolutePath}", uiRoot.isDirectory)

        val perFile = mutableMapOf<String, Int>()
        uiRoot.walkTopDown().filter { it.isFile && it.extension == "kt" }.forEach { file ->
            val count = file.readLines().sumOf { raw ->
                val line = raw.substringBefore("//")
                textLiteral.findAll(line).count()
            }
            if (count > 0) perFile[file.path] = count
        }
        val total = perFile.values.sum()

        assertTrue(
            "Hardcoded Text(\"…\") UI literals rose to $total (ceiling $CEILING). Extract new user-" +
                "facing strings into strings.xml via stringResource(). Breakdown:\n" +
                perFile.entries.sortedByDescending { it.value }.joinToString("\n") { "  ${it.value}\t${it.key}" },
            total <= CEILING,
        )
    }

    @Test
    fun notificationStringsAreExtracted() {
        assertTrue("expected service at ${serviceFile.absolutePath}", serviceFile.isFile)
        val offenders = serviceFile.readLines().withIndex().filter { (_, raw) ->
            val line = raw.substringBefore("//")
            Regex("""setContent(Title|Text)\(\s*"""").containsMatchIn(line) ||
                Regex("""addAction\(\s*0\s*,\s*"""").containsMatchIn(line)
        }.map { (i, raw) -> "${serviceFile.path}:${i + 1}: ${raw.trim()}" }

        assertEquals(
            "Notification strings must come from strings.xml (S12.9d):\n${offenders.joinToString("\n")}",
            emptyList<String>(), offenders,
        )
    }
}
