package com.tideo.autobrightness.platform.display

import android.content.Context

/**
 * Death-safe per-action restore latch for the display schedule rules (D-150, the D-134/D-144
 * pattern): while a schedule holds a display action ON, the pre-engage device state is persisted
 * here so a process death cannot orphan the engagement — the coordinator's startup residual sweep
 * (or the normal release edge) restores from THIS record, never from an in-memory field. A present
 * key IS the "engaged" truth (Tasker-persisted-global semantics, D-144: process start must read
 * the durable state, not assume "off"); the value is the state to restore on release.
 */
interface DisplayRestoreLatch {
    /** The persisted pre-engage state for [actionKey], or null when the action is not engaged. */
    fun preState(actionKey: String): String?

    fun save(actionKey: String, preState: String)

    fun clear(actionKey: String)
}

class SharedPrefsDisplayRestoreLatch(context: Context) : DisplayRestoreLatch {
    // commit() (not apply()) — durability against an imminent process death is the whole point
    // (D-134 precedent); at most a few one-string writes per schedule edge.
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override fun preState(actionKey: String): String? = prefs.getString(actionKey, null)

    override fun save(actionKey: String, preState: String) {
        prefs.edit().putString(actionKey, preState).commit()
    }

    override fun clear(actionKey: String) {
        prefs.edit().remove(actionKey).commit()
    }

    private companion object {
        const val PREFS_NAME = "display_rules_restore"
    }
}
