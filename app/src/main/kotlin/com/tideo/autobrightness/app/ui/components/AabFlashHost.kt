package com.tideo.autobrightness.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.tideo.autobrightness.app.runtime.AabFlash
import com.tideo.autobrightness.app.ui.theme.AabTeal
import kotlinx.coroutines.delay

/**
 * In-app tap-to-dismiss flash surface (G2R-F88).
 * Registers as [AabFlash] foreground presenter while composed (app foreground).
 * Falls back to Toast when neither this nor the global overlay exists.
 */
@Composable
fun AabFlashHost(content: @Composable () -> Unit) {
    var message by remember { mutableStateOf<String?>(null) }

    DisposableEffect(Unit) {
        val presenter = object : AabFlash.Presenter {
            override fun show(text: String) { message = text }
            override fun hide() { message = null }
        }
        AabFlash.registerForeground(presenter)
        onDispose {
            AabFlash.registerForeground(null)
            message = null
        }
    }

    Box(Modifier.fillMaxSize()) {
        content()
        message?.let { text ->
            LaunchedEffect(text) {
                // Auto-dismiss after flash duration; re-arm on text change.
                delay(FLASH_DURATION_MS)
                if (message == text) message = null
            }
            FlashPill(text) { message = null }
        }
    }
}

@Composable
fun FlashPill(text: String, onDismiss: () -> Unit) {
    Box(Modifier.fillMaxSize()) {
        Surface(
            color = AabTeal,
            contentColor = Color.White,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 80.dp, start = 24.dp, end = 24.dp)
                .clickable { onDismiss() }
                // D-156: polite live region for TalkBack; announces without stealing focus.
                .semantics { liveRegion = LiveRegionMode.Polite }
                .testTag("aab_flash"),
        ) {
            Text(text, modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp))
        }
    }
}

private const val FLASH_DURATION_MS = 2_500L
