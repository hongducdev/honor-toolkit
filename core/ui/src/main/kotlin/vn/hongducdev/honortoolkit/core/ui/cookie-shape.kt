package vn.hongducdev.honortoolkit.core.ui

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.graphics.shapes.CornerRounding
import androidx.graphics.shapes.RoundedPolygon
import androidx.graphics.shapes.star
import androidx.graphics.shapes.toPath

private class PolygonShape(private val polygon: RoundedPolygon) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val path = Path()
        path.addPath(polygon.toPath().asComposePath())
        val scale = androidx.compose.ui.graphics.Matrix().apply { scale(size.width / 2f, size.height / 2f) }
        path.transform(scale)
        path.translate(androidx.compose.ui.geometry.Offset(size.width / 2f, size.height / 2f))
        return Outline.Generic(path)
    }
}

/** Soft scalloped badge in the spirit of Material's expressive "cookie" shapes, built from a rounded star. */
val CookieShape: Shape = PolygonShape(
    RoundedPolygon.star(numVerticesPerRadius = 8, innerRadius = 0.86f, rounding = CornerRounding(0.4f), innerRounding = CornerRounding(0.4f)),
)
