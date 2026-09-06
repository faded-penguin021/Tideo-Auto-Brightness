package com.tideo.autobrightness.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

// i18n ratchet (D-131): zero hardcoded user-facing strings. Use stringResource()/resId toast() instead.
class HardcodedStringCheckTest {

    private val uiRoot = File("src/main/kotlin/com/tideo/autobrightness/app/ui")
    private val serviceFile =
        File("src/main/kotlin/com/tideo/autobrightness/app/runtime/AmbientMonitoringService.kt")

    // A literal string as the first argument to a Compose Text(), a toast(), or a contentDescription —
    // the three user-facing sinks (single line; comments are stripped before matching).
    private val userFacingLiteral =
        Regex("""(Text|toast)\(\s*"|contentDescription\s*=\s*"""")

    private val wrapperLabelLiteral =
        Regex("""(Metric|DiagnosticCard)\(\s*"|Text\(\s*if[^"\n]*"""")

    companion object {
        private const val CEILING = 0

        private const val WRAPPER_CEILING = 29
    }

    private fun countLiterals(pattern: Regex, wrapped: Boolean): Map<String, Int> {
        assertTrue("expected UI sources at ${uiRoot.absolutePath}", uiRoot.isDirectory)
        val perFile = mutableMapOf<String, Int>()
        uiRoot.walkTopDown().filter { it.isFile && it.extension == "kt" }.forEach { file ->
            val lines = file.readLines().map { it.substringBefore("//") }
            val count =
                if (wrapped) pattern.findAll(lines.joinToString("\n")).count()
                else lines.sumOf { pattern.findAll(it).count() }
            if (count > 0) perFile[file.path] = count
        }
        return perFile
    }

    private fun breakdown(perFile: Map<String, Int>) =
        perFile.entries.sortedByDescending { it.value }.joinToString("\n") { "  ${it.value}\t${it.key}" }

    @Test
    fun uiTextLiteralsDoNotExceedRatchet() {
        val perFile = countLiterals(userFacingLiteral, wrapped = false)
        val total = perFile.values.sum()

        assertTrue(
            "Hardcoded user-facing UI literals rose to $total (ceiling $CEILING). Extract new user-" +
                "facing strings into strings.xml via stringResource() / the resId toast() overload. Breakdown:\n" +
                breakdown(perFile),
            total <= CEILING,
        )
    }

    @Test
    fun wrapperLabelLiteralsDoNotExceedRatchet() {
        val perFile = countLiterals(wrapperLabelLiteral, wrapped = true)
        val total = perFile.values.sum()

        assertTrue(
            "DC-040: hardcoded labels in wrapper composables rose to $total (ceiling $WRAPPER_CEILING). A " +
                "title or label passed to Metric()/DiagnosticCard(), or a literal inside Text(if …), is " +
                "as unlocalized as one passed to Text() — use stringResource(). Lower the ceiling when " +
                "you extract the remaining ones; never raise it. This scan spans line breaks, so " +
                "wrapping the call is not an escape. Breakdown:\n" + breakdown(perFile),
            total <= WRAPPER_CEILING,
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
