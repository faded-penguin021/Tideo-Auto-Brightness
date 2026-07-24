package com.tideo.autobrightness.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * C1 acceptance (D-158). Verifies the manifest wiring end-to-end: the app's `<application>` is
 * [AabApplication], and its `onCreate` installs the crash-log handler as the process default. If the
 * `android:name` is dropped or `onCreate` stops installing, both assertions fail.
 */
@RunWith(RobolectricTestRunner::class)
class AabApplicationTest {

    @Test fun manifestApplicationInstallsTheCrashLogHandler() {
        val app = ApplicationProvider.getApplicationContext<Context>()
        assertTrue(
            "manifest <application android:name> must be AabApplication",
            app is AabApplication,
        )
        assertTrue(
            "AabApplication.onCreate must install the crash-log handler",
            Thread.getDefaultUncaughtExceptionHandler() is CrashLogHandler,
        )
    }
}
