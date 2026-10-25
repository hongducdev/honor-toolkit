package vn.hongducdev.honortoolkit.core.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.sin

private const val WAVE_PERIOD_DP = 28f
private const val WAVE_STEP_PX = 4f

/** Indeterminate sine-wave track. Material3 1.4 keeps its wavy indicator internal, so this draws the same idea directly. */
@Composable
fun WavyProgressIndicator(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = MaterialTheme.colorScheme.secondaryContainer,
    animate: Boolean = rememberMotionEnabled(),
) {
    val phase by rememberInfiniteTransition(label = "wave").animateFloat(
        initialValue = 0f,
        targetValue = if (animate) 1f else 0f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 1200, easing = LinearEasing), RepeatMode.Restart),
        label = "wave-phase",
    )
    Canvas(modifier = modifier.fillMaxWidth().height(14.dp)) {
        val strokeWidth = 4.dp.toPx()
        val amplitude = (size.height - strokeWidth) / 2f
        val periodPx = WAVE_PERIOD_DP.dp.toPx()
        val centerY = size.height / 2f
        drawLine(trackColor, Offset(0f, centerY), Offset(size.width, centerY), strokeWidth, StrokeCap.Round)
        val wave = Path()
        var x = 0f
        while (x <= size.width) {
            val y = centerY + amplitude * sin(2.0 * PI * (x / periodPx - phase)).toFloat()
            if (x == 0f) wave.moveTo(x, y) else wave.lineTo(x, y)
            x += WAVE_STEP_PX
        }
        drawPath(wave, color, style = Stroke(width = strokeWidth, cap = StrokeCap.Round))
    }
}
