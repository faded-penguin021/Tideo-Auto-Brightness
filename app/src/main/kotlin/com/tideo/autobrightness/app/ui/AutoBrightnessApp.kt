package com.tideo.autobrightness.app.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.rememberNavController
import com.tideo.autobrightness.app.navigation.AppNavGraph
import com.tideo.autobrightness.app.ui.theme.TideoTheme

@Composable
fun AutoBrightnessApp() {
    TideoTheme {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            val navController = rememberNavController()
            AppNavGraph(navController)
        }
    }
}
