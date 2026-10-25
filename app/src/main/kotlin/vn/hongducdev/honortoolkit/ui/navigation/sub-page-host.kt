package vn.hongducdev.honortoolkit.ui.navigation

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput

val SubPageEnterSpec = tween<Float>(durationMillis = 450, easing = CubicBezierEasing(0.2f, 0f, 0f, 1f))
val SubPageExitSpec = tween<Float>(durationMillis = 320, easing = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f))
val SubPageSettleSpec = tween<Float>(durationMillis = 300)

private const val UnderlayShift = 0.2f
private const val UnderlayScaleDrop = 0.05f
private const val BackShift = 0.1f
private const val BackScaleDrop = 0.05f
private const val PageShadow = 24f

/**
 * Draws [page] over the tabs. [reveal] runs 0 (closed) to 1 (open) and drives the slide; [backProgress] is the
 * system back gesture (0..1) and nudges and shrinks the page while the finger is still down. Both are read
 * while drawing, so animating them never recomposes the page.
 */
@Composable
fun <T : Any> SubPageHost(
    page: T?,
    reveal: () -> Float,
    backProgress: () -> Float,
    modifier: Modifier = Modifier,
    fromTop: Boolean = false,
    content: @Composable (T) -> Unit,
) {
    if (page == null) return
    val background = MaterialTheme.colorScheme.background
    Box(
        modifier = modifier
            .fillMaxSize()
            .graphicsLayer {
                val back = backProgress()
                if (fromTop) {
                    translationY = -(1f - reveal()) * size.height
                } else {
                    translationX = (1f - reveal()) * size.width + back * BackShift * size.width
                }
                scaleX = 1f - back * BackScaleDrop
                scaleY = 1f - back * BackScaleDrop
                shadowElevation = PageShadow
                shape = RectangleShape
                clip = false
            }
            .background(background)
            // Without a pointer node the page would let touches fall through to the tabs underneath.
            .pointerInput(Unit) {},
    ) { content(page) }
}

/** Pushes the tabs back a little while a sub-page slides over them. */
fun Modifier.subPageUnderlay(fromTop: Boolean = false, reveal: () -> Float): Modifier = graphicsLayer {
    val progress = reveal()
    if (fromTop) translationY = progress * UnderlayShift * size.height else translationX = -progress * UnderlayShift * size.width
    scaleX = 1f - progress * UnderlayScaleDrop
    scaleY = 1f - progress * UnderlayScaleDrop
}
