package com.tideo.autobrightness.app.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.rememberNavController
import com.tideo.autobrightness.app.navigation.AppNavGraph
import com.tideo.autobrightness.app.ui.components.AabFlashHost
import com.tideo.autobrightness.app.ui.theme.TideoTheme

@Composable
fun AutoBrightnessApp() {
    TideoTheme {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            // F88: host the in-app tap-to-dismiss flash surface above the nav graph so confirmations
            // ("Applied") and foreground debug flashes can be tapped away (a plain Toast cannot).
            AabFlashHost {
                val navController = rememberNavController()
                AppNavGraph(navController)
            }
        }
    }
}
