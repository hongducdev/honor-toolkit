package vn.hongducdev.honortoolkit.core.ui

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import kotlin.math.abs
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.intellij.lang.annotations.Language

@Language("AGSL")
private const val DIRECTIONAL_BLUR = """
    uniform shader content;
    uniform float2 size;
    uniform float magnitude;

    half4 main(float2 coord) {
        half4 sum = half4(0.0);
        for (int i = 0; i < 5; i++) {
            float offset = (float(i) / 4.0 - 0.5) * magnitude;
            sum += content.eval(float2(coord.x, clamp(coord.y + offset, 0.0, size.y)));
        }
        return sum / 5.0;
    }
"""

private const val MAX_BLUR_PX = 18f
private const val BLUR_PER_SCROLL_PX = 0.7f
private const val MIN_VISIBLE_BLUR_PX = 0.75f

/** Smears vertically scrolling content in proportion to scroll speed; no-op below Android 13 or when disabled. */
@Composable
fun Modifier.scrollMotionBlur(enabled: Boolean = true): Modifier {
    if (!enabled || Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return this
    return scrollMotionBlurApi33()
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Composable
private fun Modifier.scrollMotionBlurApi33(): Modifier {
    val scope = rememberCoroutineScope()
    val speed = remember { Animatable(0f) }
    val shader = remember { RuntimeShader(DIRECTIONAL_BLUR) }
    val connection = remember {
        var settle: Job? = null
        object : NestedScrollConnection {
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (consumed.y == 0f) return Offset.Zero
                settle?.cancel()
                settle = scope.launch {
                    speed.snapTo(consumed.y)
                    speed.animateTo(0f, spring(stiffness = Spring.StiffnessLow))
                }
                return Offset.Zero
            }
        }
    }
    return this
        .nestedScroll(connection)
        .graphicsLayer {
            clip = true
            val magnitude = (abs(speed.value) * BLUR_PER_SCROLL_PX).coerceAtMost(MAX_BLUR_PX)
            renderEffect = if (magnitude < MIN_VISIBLE_BLUR_PX) {
                null
            } else {
                shader.setFloatUniform("size", size.width, size.height)
                shader.setFloatUniform("magnitude", magnitude)
                RenderEffect.createRuntimeShaderEffect(shader, "content").asComposeRenderEffect()
            }
        }
}
