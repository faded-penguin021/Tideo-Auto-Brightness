package com.tideo.autobrightness.app.ui.components

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.tideo.autobrightness.app.runtime.AabFlash

/**
 * Restores Tasker AAB's Flash/toast feedback (G2-F12). Used for action confirmations (Apply,
 * save/delete, import/export, copy); help text stays inline as `supportingText`, matching the
 * Tasker scenes. Routes through the shared [AabFlash] channel so confirmations get the same
 * AAB-teal styling as runtime flashes and a new flash cancels the previous rather than stacking
 * (G2R-F51).
 *
 * D-131 (i18n): invoke with a **string-resource id**, plus every format arg that string declares —
 * one short throws at display time (DB-060). The [Context] is captured at creation, so the resId
 * overload resolves from a non-composable lambda too. The `String` overload is for runtime text only —
 * `HardcodedStringCheckTest` forbids a hardcoded toast string.
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
