package dev.pocketdesk.android.ui.theme

import android.os.Build
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import dev.pocketdesk.android.R

/** The palette's FRIDAY wallpaper (the desktop's own art), cropped for a phone. */
private fun backdropFor(id: String): Int = when (id) {
    "ember" -> R.drawable.backdrop_ember
    "frost" -> R.drawable.backdrop_frost
    "moss" -> R.drawable.backdrop_moss
    "polar" -> R.drawable.backdrop_polar
    "dawn" -> R.drawable.backdrop_dawn
    else -> R.drawable.backdrop_core
}

/**
 * What the frosted-glass surfaces sit on: the wallpaper, blurred on Android 12+
 * (a softer, darker veil below that), under a veil of the palette's background
 * so text on the glass stays readable.
 */
@Composable
fun FridayBackdrop(palette: FridayPalette, modifier: Modifier = Modifier) {
    val bg = Color(palette.bg)
    val blurs = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    Box(modifier.fillMaxSize().background(bg)) {
        Image(
            painter = painterResource(backdropFor(palette.id)),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize().then(if (blurs) Modifier.blur(18.dp) else Modifier),
        )
        val top = if (palette.light) 0.45f else if (blurs) 0.18f else 0.4f
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(listOf(bg.copy(alpha = top + 0.3f), bg.copy(alpha = top))),
            ),
        )
    }
}
