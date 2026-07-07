package com.tideo.autobrightness.app.control

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** D-157: the opt-in gate for the external intent-control surface must default OFF and round-trip. */
class ControlPrefsStoreTest {

    /** Fresh store on a unique temp file per test; the scope is cancelled after the block. */
    private fun withStore(body: suspend (ControlPrefsStore) -> Unit) {
        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        val file = File.createTempFile("control_prefs_test", ".preferences_pb").apply { delete() }
        val dataStore = PreferenceDataStoreFactory.create(scope = scope) { file }
        try {
            runBlocking { body(ControlPrefsStore(dataStore)) }
        } finally {
            scope.cancel()
            file.delete()
        }
    }

    @Test
    fun defaultsToDisabled() = withStore { store ->
        assertFalse(
            store.externalControlEnabled.first(),
            "external control must default OFF (opt-in, D-156 security posture)",
        )
    }

    @Test
    fun enableRoundTrips() = withStore { store ->
        store.setExternalControlEnabled(true)
        assertTrue(store.externalControlEnabled.first())
        store.setExternalControlEnabled(false)
        assertFalse(store.externalControlEnabled.first(), "opt-out must revert to disabled")
    }
}
