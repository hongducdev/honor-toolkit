package vn.hongducdev.honortoolkit.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import vn.hongducdev.honortoolkit.R

private val ToolkitShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

// One variable font file serves every weight, so the app stays at a single ~270 KB asset.
@OptIn(ExperimentalTextApi::class)
private val RoundedFamily = FontFamily(
    listOf(FontWeight.Normal, FontWeight.Medium, FontWeight.SemiBold, FontWeight.Bold).map { weight ->
        Font(R.font.nunito, weight, variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)))
    },
)

private val ToolkitTypography = Typography().let { base ->
    base.copy(
        displayLarge = base.displayLarge.copy(fontFamily = RoundedFamily),
        displayMedium = base.displayMedium.copy(fontFamily = RoundedFamily),
        displaySmall = base.displaySmall.copy(fontFamily = RoundedFamily),
        headlineLarge = base.headlineLarge.copy(fontFamily = RoundedFamily),
        headlineMedium = base.headlineMedium.copy(fontFamily = RoundedFamily),
        headlineSmall = base.headlineSmall.copy(fontFamily = RoundedFamily),
        titleLarge = base.titleLarge.copy(fontFamily = RoundedFamily),
        titleMedium = base.titleMedium.copy(fontFamily = RoundedFamily),
        titleSmall = base.titleSmall.copy(fontFamily = RoundedFamily),
        bodyLarge = base.bodyLarge.copy(fontFamily = RoundedFamily),
        bodyMedium = base.bodyMedium.copy(fontFamily = RoundedFamily),
        bodySmall = base.bodySmall.copy(fontFamily = RoundedFamily),
        labelLarge = base.labelLarge.copy(fontFamily = RoundedFamily),
        labelMedium = base.labelMedium.copy(fontFamily = RoundedFamily),
        labelSmall = base.labelSmall.copy(fontFamily = RoundedFamily),
    )
}

@Composable
fun HonorToolkitTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val colors = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        dark -> darkColorScheme()
        else -> lightColorScheme()
    }
    MaterialTheme(colorScheme = colors, typography = ToolkitTypography, shapes = ToolkitShapes, content = content)
}
