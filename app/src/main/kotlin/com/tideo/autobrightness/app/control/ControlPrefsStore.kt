package com.tideo.autobrightness.app.control

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * D-157: opt-in gate for the external intent-control surface (Tasker / MacroDroid). When
 * [externalControlEnabled] is **off** the exported `ControlReceiver` ignores every action — this is
 * the receiver's FIRST check, the OFF-ignores-everything security property (a D-147-style negative
 * test pins it). Default **off — opt-in** (the D-105 `geoIpEnabled` pattern): an external control
 * surface is not exposed unless the user explicitly enables it in Tools.
 *
 * Deliberately its OWN preferences store, **not** an `AabSettings` field: the profile
 * apply/import chokepoints (`applyProfile`, `replaceAll`, `resetDefaults`, legacy import) then can
 * never flip it, and there is no schema/clamp/drift-test churn.
 */
class ControlPrefsStore(private val dataStore: DataStore<Preferences>) {
    /** Whether the exported [ControlReceiver] honours external broadcasts. Default OFF (opt-in). */
    val externalControlEnabled: Flow<Boolean> = dataStore.data.map { it[EXTERNAL_CONTROL] ?: false }

    /** Opt in to (or back out of) the external intent-control surface. */
    suspend fun setExternalControlEnabled(enabled: Boolean) {
        dataStore.edit { it[EXTERNAL_CONTROL] = enabled }
    }

    /**
     * D-172: the force-dark opt-in (`debug.hwui.force_dark` via Shizuku/root). Default OFF. Lives here —
     * not in `AabSettings` — for the same reason as [externalControlEnabled]: it is a device-level
     * app opt-in that profile apply/import/reset must never flip. While ON, the service re-asserts
     * the property at start (it resets on reboot); while OFF the app never touches the property.
     */
    val forceDarkEnabled: Flow<Boolean> = dataStore.data.map { it[FORCE_DARK] ?: false }

    /** Opt in to (or back out of) the Shizuku force-dark override. */
    suspend fun setForceDarkEnabled(enabled: Boolean) {
        dataStore.edit { it[FORCE_DARK] = enabled }
    }

    private companion object {
        val EXTERNAL_CONTROL = booleanPreferencesKey("external_control_enabled")
        val FORCE_DARK = booleanPreferencesKey("force_dark_enabled")
    }
}
