package io.github.deivid22srk.turnipspace.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val TurnipGreen = Color(0xFF5FB56B)
private val TurnipPurple = Color(0xFF9C7BD8)
private val DarkBackground = Color(0xFF12131A)
private val DarkSurface = Color(0xFF1B1D26)
private val LightBackground = Color(0xFFFAF9F6)

private val DarkScheme = darkColorScheme(
    primary = TurnipGreen,
    secondary = TurnipPurple,
    background = DarkBackground,
    surface = DarkSurface,
)

private val LightScheme = lightColorScheme(
    primary = Color(0xFF3E8B49),
    secondary = Color(0xFF6A4FB0),
    background = LightBackground,
)

@Composable
fun TurnipSpaceTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkScheme
        else -> LightScheme
    }
    MaterialTheme(colorScheme = colorScheme, content = content)
}
