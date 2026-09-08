package io.github.deivid22srk.turnipspace

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import io.github.deivid22srk.turnipspace.common.AppLogger
import io.github.deivid22srk.turnipspace.ui.navigation.TurnipSpaceAppRoot
import io.github.deivid22srk.turnipspace.ui.theme.TurnipSpaceTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppLogger.i("MainActivity", "onCreate")
        val startOnHome = (application as TurnipSpaceApp).container.settings.onboardingDone
        setContent {
            TurnipSpaceTheme {
                TurnipSpaceAppRoot(startOnHome = startOnHome)
            }
        }
    }
}
