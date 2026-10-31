package vn.hongducdev.honortoolkit.ui.navigation

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp

/** Lets content scroll behind a floating bar: over the bottom [height] it fades into [overlay]. */
fun Modifier.bottomFade(height: Dp, overlay: Color): Modifier =
    if (height.value <= 0f) this else drawWithContent {
        drawContent()
        drawRect(
            Brush.verticalGradient(
                colors = listOf(Color.Transparent, overlay),
                startY = size.height - height.toPx(),
                endY = size.height,
            ),
        )
    }
