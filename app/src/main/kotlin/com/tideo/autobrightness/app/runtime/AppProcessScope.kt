package com.tideo.autobrightness.app.runtime

import android.content.BroadcastReceiver
import android.util.Log
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Process-wide supervised coroutine scope for genuinely fire-and-forget runtime work that must
 * outlive any single Activity/Service callback but is still bounded by the process lifetime (S12.9e).
 *
 * Replaces the ad-hoc `CoroutineScope(Dispatchers.*)` instances that were previously allocated
 * per-call and never cancelled — each one a small structured-concurrency leak (its [Job] was rooted
 * nowhere and could neither be observed nor cancelled). A [SupervisorJob] keeps one failed child from
 * cancelling its siblings, and a logging [CoroutineExceptionHandler] surfaces a crash in detached
 * work instead of letting it vanish silently.
 *
 * Scope ownership policy (the audit, deliverable #1):
 *  - Use this for process-scoped fire-and-forget launches with no narrower owner — the
 *    [AutoBrightnessRuntime] bootstrap/health writes and the [BroadcastReceiver.goAsync] receivers.
 *  - Components that own a lifecycle keep their OWN scope and cancel it in `onDestroy`: the
 *    [AmbientMonitoringService] foreground-service scope and the [BrightnessTileService] tile scope
 *    are legitimately-owned (each cancels in `onDestroy`), so they are NOT routed through here.
 */
object AppProcessScope : CoroutineScope {
    private const val TAG = "AppProcessScope"

    private val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
        Log.e(TAG, "Uncaught exception in process-scoped coroutine", throwable)
    }

    override val coroutineContext = SupervisorJob() + Dispatchers.Default + exceptionHandler
}

/**
 * Run [block] off the main thread from a [BroadcastReceiver.onReceive] while keeping the broadcast
 * alive via the platform [BroadcastReceiver.goAsync] (`PendingResult`), finishing it when the work
 * completes (S12.9e). Replaces the detached `CoroutineScope(Dispatchers.Default).launch { }` that
 * returned from `onReceive` *before* its work finished — the process could be killed mid-DataStore
 * read. The work runs on [AppProcessScope] (supervised + logged) and [PendingResult.finish] is always
 * called, even on failure.
 *
 * This is an overload of the platform no-arg `goAsync()`; the lambda form resolves here, the no-arg
 * form (used below) resolves to the framework member.
 */
fun BroadcastReceiver.goAsync(block: suspend () -> Unit) {
    // Non-null under a real system dispatch; null only when a receiver is invoked directly (e.g. a
    // Robolectric unit test calling onReceive), so finish() is called defensively.
    val pendingResult = goAsync()
    AppProcessScope.launch {
        try {
            block()
        } finally {
            pendingResult?.finish()
        }
    }
}
