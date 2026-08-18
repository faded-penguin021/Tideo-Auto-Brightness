package com.tideo.autobrightness.app.runtime

import android.content.BroadcastReceiver
import android.util.Log
import com.tideo.autobrightness.BuildConfig
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Process-wide supervised coroutine scope for fire-and-forget runtime work (S12.9e). */
object AppProcessScope : CoroutineScope {
    private const val TAG = "AppProcessScope"

    private val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
        if (BuildConfig.DEBUG) {
            Log.e(TAG, "Uncaught exception in process-scoped coroutine", throwable)
        }
    }

    override val coroutineContext = SupervisorJob() + Dispatchers.Default + exceptionHandler
}

/** Run block off-thread from BroadcastReceiver, keeping broadcast alive until work completes (S12.9e). */
fun BroadcastReceiver.goAsync(block: suspend () -> Unit) {
    val pendingResult = goAsync()
    try {
        AppProcessScope.launch {
            try {
                block()
            } finally {
                pendingResult?.finish()
            }
        }
    } catch (failure: Throwable) {
        pendingResult?.finish()
        throw failure
    }
}
