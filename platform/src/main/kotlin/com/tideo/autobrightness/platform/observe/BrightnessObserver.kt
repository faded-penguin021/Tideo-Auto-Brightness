package com.tideo.autobrightness.platform.observe

import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.provider.Settings
import com.tideo.autobrightness.platform.brightness.ScreenBrightnessController
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

// Tasker: prof755 Allow Override / task567 Manual Override detect — ContentObserver fires
// on any external write to SCREEN_BRIGHTNESS; self-writes are filtered via suppress-echo hook.
interface BrightnessObserver {
    /** Emits domain-scale (0–255) brightness values for externally-written changes only. */
    fun externalChanges(): Flow<Int>
}

class AndroidBrightnessObserver(
    private val context: Context,
    private val controller: ScreenBrightnessController,
) : BrightnessObserver {
    private val uri: Uri = Settings.System.getUriFor(Settings.System.SCREEN_BRIGHTNESS)

    override fun externalChanges(): Flow<Int> = callbackFlow {
        // null handler: onChange called on the ContentResolver caller thread.
        // trySend is thread-safe in callbackFlow so no handler dispatch needed.
        val observer = object : ContentObserver(null) {
            override fun onChange(selfChange: Boolean) {
                val raw = Settings.System.getInt(
                    context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, -1
                )
                if (raw >= 0 && !controller.isSelfWrite(raw)) {
                    // Report domain-scale value so callers don't need device-range knowledge.
                    trySend(controller.read())
                }
            }
        }
        context.contentResolver.registerContentObserver(uri, false, observer)
        awaitClose { context.contentResolver.unregisterContentObserver(observer) }
    }
}
