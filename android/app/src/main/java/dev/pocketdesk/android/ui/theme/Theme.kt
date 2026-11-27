package dev.pocketdesk.android.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import dev.pocketdesk.android.Prefs

/**
 * App theme: FRIDAY's palette the person chose (Core unless they pick
 * another; the same palettes as FRIDAY Shell on the desktop). Material You
 * wallpaper colors replace it only when switched on (Android 12+).
 * Reading [Prefs.observedVersion] re-composes on pref changes.
 */
@Composable
fun PocketdeskTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    Prefs.observedVersion()

    val palette = Palettes.byId(Prefs.palette(context))
    val dynamic = Prefs.dynamicColor(context) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val systemDark = isSystemInDarkTheme()
    val colorScheme: ColorScheme = when {
        dynamic && systemDark -> dynamicDarkColorScheme(context)
        dynamic -> dynamicLightColorScheme(context)
        else -> palette.colorScheme()
    }
    // The status and navigation bar icons follow the palette, not the phone's
    // light/dark setting: dark icons on Core's obsidian would vanish.
    val lightBars = if (dynamic) !systemDark else palette.light
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? android.app.Activity)?.window ?: return@SideEffect
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = lightBars
                isAppearanceLightNavigationBars = lightBars
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content,
    )
}
