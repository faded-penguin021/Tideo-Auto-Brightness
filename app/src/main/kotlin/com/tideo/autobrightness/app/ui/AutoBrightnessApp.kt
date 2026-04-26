package com.tideo.autobrightness.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.navigation.compose.rememberNavController
import com.tideo.autobrightness.app.navigation.AppNavGraph

@Composable
fun AutoBrightnessApp() {
    val navController = rememberNavController()
    MaterialTheme {
        Surface {
            AppNavGraph(navController)
        }
    }
}
