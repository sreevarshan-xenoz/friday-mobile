package dev.pocketdesk.android.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import dev.pocketdesk.android.R

/** FRIDAY's interface type, as on the desktop: Space Grotesk (variable weight). */
private fun grotesk(weight: Int) = Font(
    R.font.space_grotesk,
    FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

val SpaceGrotesk = FontFamily(grotesk(400), grotesk(500), grotesk(600), grotesk(700))

/** FRIDAY's wordmark face (headings that say FRIDAY). */
val Michroma = FontFamily(Font(R.font.michroma))

private val base = Typography()

val Typography = Typography(
    displayLarge = base.displayLarge.copy(fontFamily = SpaceGrotesk),
    displayMedium = base.displayMedium.copy(fontFamily = SpaceGrotesk),
    displaySmall = base.displaySmall.copy(fontFamily = SpaceGrotesk),
    headlineLarge = base.headlineLarge.copy(fontFamily = SpaceGrotesk),
    headlineMedium = base.headlineMedium.copy(fontFamily = SpaceGrotesk),
    headlineSmall = base.headlineSmall.copy(fontFamily = SpaceGrotesk),
    titleLarge = base.titleLarge.copy(fontFamily = SpaceGrotesk, fontWeight = FontWeight.SemiBold),
    titleMedium = base.titleMedium.copy(fontFamily = SpaceGrotesk, fontWeight = FontWeight.SemiBold),
    titleSmall = base.titleSmall.copy(fontFamily = SpaceGrotesk, fontWeight = FontWeight.SemiBold),
    bodyLarge = base.bodyLarge.copy(fontFamily = SpaceGrotesk),
    bodyMedium = base.bodyMedium.copy(fontFamily = SpaceGrotesk),
    bodySmall = base.bodySmall.copy(fontFamily = SpaceGrotesk),
    labelLarge = base.labelLarge.copy(fontFamily = SpaceGrotesk, fontWeight = FontWeight.SemiBold),
    labelMedium = base.labelMedium.copy(fontFamily = SpaceGrotesk),
    labelSmall = base.labelSmall.copy(fontFamily = SpaceGrotesk),
)

/** Stats/workspace HUD: compact monospace so digits do not jitter per frame. */
val HudTextStyle = TextStyle(
    fontFamily = FontFamily.Monospace,
    fontWeight = FontWeight.Medium,
    fontSize = 12.sp,
    letterSpacing = 0.sp,
)

val HudLabelStyle = HudTextStyle.copy(fontSize = 11.sp)
