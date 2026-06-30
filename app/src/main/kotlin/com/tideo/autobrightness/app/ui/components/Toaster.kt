package com.tideo.autobrightness.app.ui.components

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.tideo.autobrightness.app.runtime.AabFlash

/**
 * Restores Tasker AAB's Flash/toast feedback (G2-F12): a remembered toaster that shows a short Flash.
 * Used for action confirmations (Apply, save/delete, import/export, copy) — help text stays as inline
 * `supportingText`, only confirmations/warnings toast, matching the Tasker scenes.
 *
 * Routes through the shared [AabFlash] channel so these confirmations get the **same AAB-teal styling**
 * as the runtime/debug flashes (consistency fix — previously plain system toasts) and so a new flash
 * cancels the previous one rather than stacking (G2R-F51).
 *
 * D-131 (i18n): invoke with a **string-resource id** (+ optional format args) so user-facing toasts are
 * translatable. The [Context] is captured once at creation, so the resId overload resolves correctly
 * even when called from a non-composable lambda (onClick, callbacks). The `String` overload remains for
 * already-resolved / runtime text only — `HardcodedStringCheckTest` forbids a hardcoded toast string.
 */
class Toaster internal constructor(private val context: Context) : (String) -> Unit {
    override fun invoke(message: String) = AabFlash.show(context, message)
    operator fun invoke(@StringRes resId: Int, vararg formatArgs: Any) =
        AabFlash.show(context, context.getString(resId, *formatArgs))
}

@Composable
fun rememberToaster(): Toaster {
    val context = LocalContext.current
    return remember(context) { Toaster(context) }
}
