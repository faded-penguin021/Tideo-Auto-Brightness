package com.tideo.autobrightness.app.runtime

fun interface ControllerHook {
    fun onContextChanged()
}

// Late-bound hook holder (S12.9e, deliverable #2). Fire before assignment is safe no-op.
class ControllerHookHolder {
    @Volatile
    var hook: ControllerHook? = null

    fun fire() {
        hook?.onContextChanged()
    }
}
