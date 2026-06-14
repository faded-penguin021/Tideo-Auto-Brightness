package com.tideo.autobrightness.app.ui.components

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * Restores Tasker AAB's Flash/toast feedback (G2-F12): a remembered `(String) -> Unit` that shows a
 * short Toast. Used for action confirmations (Apply, save/delete, import/export, copy) — help text
 * stays as inline `supportingText`, only confirmations/warnings toast, matching the Tasker scenes.
 */
@Composable
fun rememberToaster(): (String) -> Unit {
    val context = LocalContext.current
    return remember(context) {
        { message: String -> Toast.makeText(context, message, Toast.LENGTH_SHORT).show() }
    }
}
