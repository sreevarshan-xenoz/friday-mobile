package dev.pocketdesk.android.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** FRIDAY's glass panels: rounded like FRIDAY Shell's cards on the desktop. */
val GlassShape = RoundedCornerShape(24.dp)

/**
 * A frosted-glass panel: the palette's raised surface, translucent over the
 * backdrop, with a thin light edge brighter at the top, as light catches glass.
 */
fun Modifier.glass(fill: Color, shape: androidx.compose.ui.graphics.Shape = GlassShape): Modifier =
    this.clip(shape)
        .background(fill)
        .border(
            1.dp,
            Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.16f), Color.White.copy(alpha = 0.03f))),
            shape,
        )

@Composable
fun GlassPanel(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .glass(MaterialTheme.colorScheme.surfaceContainerHighest)
            .padding(vertical = 6.dp),
        content = content,
    )
}
