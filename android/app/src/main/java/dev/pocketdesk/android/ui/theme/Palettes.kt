package dev.pocketdesk.android.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * FRIDAY's palettes, the same tokens as FRIDAY Shell on the desktop
 * (packaging/linux/shell/common/Theme.qml in project-friday), so the phone
 * and the computer look like one product. Core is FRIDAY's own look.
 */
data class FridayPalette(
    val id: String,
    val bg: Long,
    val surface: Long,
    val raised: Long,
    val overlay: Long,
    val outline: Long,
    val text: Long,
    val subtext: Long,
    val accent: Long,
    val onAccent: Long,
    val signal: Long,
    val error: Long,
    val light: Boolean = false,
)

object Palettes {
    val core = FridayPalette("core", 0xFF111318, 0xFF181B21, 0xFF20232A, 0xFF2A2E36, 0xFF343842,
        0xFFDDE0E6, 0xFFB8BBC3, 0xFFD42D51, 0xFFFFF4F6, 0xFF8EA4C8, 0xFFFF5D6C)
    val ember = FridayPalette("ember", 0xFF0D0F13, 0xFF16191F, 0xFF1E222A, 0xFF272C36, 0xFF323844,
        0xFFECE8E2, 0xFFA39E96, 0xFFFF8F4C, 0xFF1D0F05, 0xFF5FD3BC, 0xFFFF5D6C)
    val frost = FridayPalette("frost", 0xFF0E141B, 0xFF151D27, 0xFF1C2633, 0xFF243142, 0xFF2C3A4D,
        0xFFE6EEF6, 0xFF9FB0C2, 0xFF7CC4FF, 0xFF04121F, 0xFFB29CFF, 0xFFFF6B7A)
    val moss = FridayPalette("moss", 0xFF0F120E, 0xFF171B15, 0xFF1F251C, 0xFF283024, 0xFF323B2D,
        0xFFE9ECDF, 0xFFA7AD99, 0xFFA8D46F, 0xFF11190A, 0xFFE0B458, 0xFFEF6A5A)
    val polar = FridayPalette("polar", 0xFF2E3440, 0xFF3B4252, 0xFF434C5E, 0xFF4C566A, 0xFF4C566A,
        0xFFECEFF4, 0xFFD8DEE9, 0xFF88C0D0, 0xFF242933, 0xFFB48EAD, 0xFFBF616A)
    val dawn = FridayPalette("dawn", 0xFFF7F3EE, 0xFFEFE9E2, 0xFFE6DFD6, 0xFFDCD3C8, 0xFFD2C7BA,
        0xFF2A2521, 0xFF5E554D, 0xFFD9622B, 0xFFFFF7F0, 0xFF1F8F7A, 0xFFC8364A, light = true)

    /** In the order the picker shows them; Core first. */
    val all = listOf(core, ember, frost, moss, polar, dawn)

    fun byId(id: String?): FridayPalette = all.firstOrNull { it.id == id } ?: core
}

/**
 * A Material 3 scheme from a palette: the accent is primary. Frosted glass:
 * the background and plain surfaces are clear (the backdrop shows), and the
 * containers are the palette's surfaces made translucent: filled cards most,
 * sheets and dialogs least, as text scrolls behind them.
 */
fun FridayPalette.colorScheme(): ColorScheme {
    val c = { v: Long -> Color(v) }
    val glass = { v: Long, a: Float -> Color(v).copy(alpha = a) }
    return if (light) {
        lightColorScheme(
            primary = c(accent), onPrimary = c(onAccent),
            primaryContainer = c(raised), onPrimaryContainer = c(text),
            secondary = c(signal), onSecondary = c(onAccent),
            secondaryContainer = c(overlay), onSecondaryContainer = c(text),
            tertiary = c(signal),
            background = Color.Transparent, onBackground = c(text),
            surface = Color.Transparent, onSurface = c(text),
            surfaceVariant = glass(raised, 0.6f), onSurfaceVariant = c(subtext),
            surfaceContainerLowest = glass(bg, 0.94f), surfaceContainerLow = glass(surface, 0.92f),
            surfaceContainer = glass(surface, 0.78f), surfaceContainerHigh = glass(raised, 0.92f),
            surfaceContainerHighest = glass(raised, 0.58f),
            outline = c(outline), outlineVariant = c(overlay),
            error = c(error), onError = c(onAccent),
        )
    } else {
        darkColorScheme(
            primary = c(accent), onPrimary = c(onAccent),
            primaryContainer = c(raised), onPrimaryContainer = c(text),
            secondary = c(signal), onSecondary = c(bg),
            secondaryContainer = c(overlay), onSecondaryContainer = c(text),
            tertiary = c(signal),
            background = Color.Transparent, onBackground = c(text),
            surface = Color.Transparent, onSurface = c(text),
            surfaceVariant = glass(raised, 0.6f), onSurfaceVariant = c(subtext),
            surfaceContainerLowest = glass(bg, 0.94f), surfaceContainerLow = glass(surface, 0.92f),
            surfaceContainer = glass(surface, 0.78f), surfaceContainerHigh = glass(raised, 0.92f),
            surfaceContainerHighest = glass(raised, 0.58f),
            outline = c(outline), outlineVariant = c(overlay),
            error = c(error), onError = c(bg),
        )
    }
}
