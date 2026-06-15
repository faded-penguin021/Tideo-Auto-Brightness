package com.tideo.autobrightness.app.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.tideo.autobrightness.app.runtime.AabFlash

/**
 * Restores Tasker AAB's Flash/toast feedback (G2-F12): a remembered `(String) -> Unit` that shows a
 * short Flash. Used for action confirmations (Apply, save/delete, import/export, copy) — help text
 * stays as inline `supportingText`, only confirmations/warnings toast, matching the Tasker scenes.
 *
 * Routes through the shared [AabFlash] channel so these confirmations get the **same AAB-teal styling**
 * as the runtime/debug flashes (consistency fix — previously plain system toasts) and so a new flash
 * cancels the previous one rather than stacking (G2R-F51).
 */
@Composable
fun rememberToaster(): (String) -> Unit {
    val context = LocalContext.current
    return remember(context) {
        { message: String -> AabFlash.show(context, message) }
    }
}
