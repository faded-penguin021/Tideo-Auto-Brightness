package com.tideo.autobrightness.app

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Guards against re-introducing dead `Build.VERSION.SDK_INT` branches (S12.9a). minSdk is 31, so any
 * `SDK_INT >= X` / `SDK_INT < X` where `X <= 31` is statically constant and the alternate branch is
 * dead code. Branches against API > 31 (notably `TIRAMISU` = 33, which still gates POST_NOTIFICATIONS
 * on real API 31/32 devices) are LIVE and must NOT be flagged.
 */
class DeadApiCheckTest {

    private val minSdk = 31

    // Resolves the VERSION_CODES names that can appear at or below our supported range.
    private val versionCodes = mapOf(
        "BASE" to 1, "GINGERBREAD" to 9, "HONEYCOMB" to 11, "ICE_CREAM_SANDWICH" to 14,
        "JELLY_BEAN" to 16, "KITKAT" to 19, "LOLLIPOP" to 21, "M" to 23, "N" to 24,
        "O" to 26, "O_MR1" to 27, "P" to 28, "Q" to 29, "R" to 30, "S" to 31,
        "S_V2" to 32, "TIRAMISU" to 33, "UPSIDE_DOWN_CAKE" to 34, "VANILLA_ICE_CREAM" to 35,
    )

    private val pattern = Regex(
        """SDK_INT\s*(>=|>|<=|<|==)\s*(?:Build\.VERSION_CODES\.(\w+)|(\d+))""",
    )

    @Test
    fun noDeadSdkIntBranches() {
        val root = File("src/main/kotlin")
        assertTrue("expected app main sources at ${root.absolutePath}", root.isDirectory)

        val offenders = mutableListOf<String>()
        root.walkTopDown().filter { it.isFile && it.extension == "kt" }.forEach { file ->
            file.readLines().forEachIndexed { idx, raw ->
                // Strip line comments so provenance notes that quote old code can't false-positive.
                val line = raw.substringBefore("//")
                pattern.findAll(line).forEach { m ->
                    val op = m.groupValues[1]
                    val api = m.groupValues[2].takeIf { it.isNotEmpty() }?.let { versionCodes[it] }
                        ?: m.groupValues[3].toIntOrNull()
                    if (api != null && isAlwaysConstant(op, api)) {
                        offenders += "${file.path}:${idx + 1}: $line".trim()
                    }
                }
            }
        }

        assertTrue(
            "Dead SDK_INT branch(es) found (minSdk=$minSdk). Call the API directly / drop the guard:\n" +
                offenders.joinToString("\n"),
            offenders.isEmpty(),
        )
    }

    /** True when `SDK_INT <op> api` is statically constant given `SDK_INT >= minSdk`. */
    private fun isAlwaysConstant(op: String, api: Int): Boolean = when (op) {
        ">=" -> api <= minSdk      // always true
        ">" -> api < minSdk        // always true
        "<" -> api <= minSdk       // always false
        "<=" -> api < minSdk       // always false
        "==" -> api < minSdk       // always false
        else -> false
    }

    /** Sanity-checks the predicate so the LIVE TIRAMISU guards are provably never flagged. */
    @Test
    fun tiramisuGuardsAreNotFlagged() {
        val tiramisu = versionCodes.getValue("TIRAMISU")
        assertTrue(!isAlwaysConstant(">=", tiramisu))
        assertTrue(!isAlwaysConstant("<", tiramisu))
        // And a genuinely dead one IS caught.
        assertTrue(isAlwaysConstant(">=", versionCodes.getValue("O")))
        assertTrue(isAlwaysConstant("<", versionCodes.getValue("O")))
    }
}
