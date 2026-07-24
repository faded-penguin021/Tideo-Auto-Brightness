package com.tideo.autobrightness.platform.display

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * D-172: the force-dark prop parse mirrors Android's ParseBool (only the exact lowercase enable
 * literals count), and both controller calls degrade to null when neither Shizuku nor root is
 * available — the contract the Tools card's "not reachable" state and the service's
 * fire-and-forget re-assert both rely on.
 */
@RunWith(RobolectricTestRunner::class)
class ForceDarkControllerTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Test
    fun parseAcceptsExactlyTheParseBoolEnableLiterals() {
        listOf("true", "1", "y", "yes", "on", " true\n").forEach {
            assertEquals(true, parseForceDarkProp(it), "expected '$it' to parse as enabled")
        }
        listOf("false", "0", "", "TRUE", "True", "off", "no", "garbage").forEach {
            assertEquals(false, parseForceDarkProp(it), "expected '$it' to parse as disabled")
        }
    }

    @Test
    fun readIsNullWhenNoPrivilegedShellIsAvailable() = runTest {
        // Robolectric has no Shizuku binder (exec short-circuits to null); the host's `su` cannot
        // grant root under test — missing binary → throw, password prompt → EOF on closed stdin,
        // a stall → the bounded wait kills it (DA-017: an earlier unbounded read blocked forever
        // on GitHub's password-prompting `su`, hanging CI at the job cap). Every one of those must
        // surface as null (unknown), never as false (known-off).
        assertNull(ForceDarkController.read(context))
    }

    @Test
    fun applyIsNullWhenNoPrivilegedShellIsAvailable() = runTest {
        assertNull(ForceDarkController.apply(context, enabled = true))
    }
}
