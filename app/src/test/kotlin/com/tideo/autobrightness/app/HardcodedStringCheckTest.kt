package com.tideo.autobrightness.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * i18n ratchet. The app shipped ~97 hardcoded UI strings; D-131 completed the extraction (the S14/D-075
 * backlog of section headers, per-field labels, long-press help, toasts, chart labels) so the app is
 * **fully translatable**. This test now enforces **zero** hardcoded user-facing strings: it fails if a
 * NEW inline `Text("…")`, `toast("…")`, or `contentDescription = "…"` literal appears in the Compose UI
 * tree. Resolve user-facing text via `stringResource(...)` / the resId `toast(...)` overload instead. It
 * also locks the converted notification surface (no literal notification strings).
 */
class HardcodedStringCheckTest {

    private val uiRoot = File("src/main/kotlin/com/tideo/autobrightness/app/ui")
    private val serviceFile =
        File("src/main/kotlin/com/tideo/autobrightness/app/runtime/AmbientMonitoringService.kt")

    // A literal string as the first argument to a Compose Text(), a toast(), or a contentDescription —
    // the three user-facing sinks (single line; comments are stripped before matching).
    private val userFacingLiteral =
        Regex("""(Text|toast)\(\s*"|contentDescription\s*=\s*"""")

    companion object {
        // D-131: the i18n extraction is COMPLETE — no hardcoded user-facing UI literal is allowed.
        // RATCHET STAYS AT 0; any new user-facing string must go through strings.xml.
        private const val CEILING = 0
    }

    @Test
    fun uiTextLiteralsDoNotExceedRatchet() {
        assertTrue("expected UI sources at ${uiRoot.absolutePath}", uiRoot.isDirectory)

        val perFile = mutableMapOf<String, Int>()
        uiRoot.walkTopDown().filter { it.isFile && it.extension == "kt" }.forEach { file ->
            val count = file.readLines().sumOf { raw ->
                val line = raw.substringBefore("//")
                userFacingLiteral.findAll(line).count()
            }
            if (count > 0) perFile[file.path] = count
        }
        val total = perFile.values.sum()

        assertTrue(
            "Hardcoded user-facing UI literals rose to $total (ceiling $CEILING). Extract new user-" +
                "facing strings into strings.xml via stringResource() / the resId toast() overload. Breakdown:\n" +
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
