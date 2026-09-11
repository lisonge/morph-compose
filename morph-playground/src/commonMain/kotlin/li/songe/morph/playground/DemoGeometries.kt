package li.songe.morph.playground

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import li.songe.morph.compose.morphGeometryOf
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

internal val demoGeometries: List<DemoGeometry> by lazy {
    listOf(
        demo("Circle", Path().apply { addOval(Rect(5f, 5f, 95f, 95f)) }),
        demo("Rounded", Path().apply { addRoundRect(RoundRect(Rect(5f, 5f, 95f, 95f), CornerRadius(20f))) }),
        demo("Bloom", radialPath(8, 0.14)),
        demo("Pebble", radialPath(3, 0.10)),
        demo("Star", radialPath(5, 0.28)),
        demo("Bubble", Path().apply {
            moveTo(22f, 10f); lineTo(78f, 10f)
            cubicTo(89f, 10f, 95f, 17f, 95f, 28f); lineTo(95f, 65f)
            cubicTo(95f, 77f, 89f, 82f, 78f, 82f); lineTo(39f, 82f)
            lineTo(15f, 98f); lineTo(20f, 82f)
            cubicTo(8f, 82f, 5f, 75f, 5f, 65f); lineTo(5f, 28f)
            cubicTo(5f, 17f, 11f, 10f, 22f, 10f); close()
        }),
    )
}

private fun demo(name: String, path: Path): DemoGeometry = DemoGeometry(name, morphGeometryOf(path, Size(100f, 100f)))

/** Smooth, authored radial contours expressed as cubics rather than a cloud of line segments. */
private fun radialPath(lobes: Int, amplitude: Double): Path {
    val count = lobes * 8
    val points = List(count) { i ->
        val angle = 2 * PI * i / count - PI / 2
        val radius = 41 * (1 + amplitude * cos(lobes * (angle + PI / 2))) / (1 + amplitude)
        Pair((50 + radius * cos(angle)).toFloat(), (50 + radius * sin(angle)).toFloat())
    }
    return Path().apply {
        moveTo(points[0].first, points[0].second)
        for (i in points.indices) {
            val p0 = points[(i - 1 + count) % count]
            val p1 = points[i]
            val p2 = points[(i + 1) % count]
            val p3 = points[(i + 2) % count]
            cubicTo(p1.first + (p2.first - p0.first) / 6, p1.second + (p2.second - p0.second) / 6,
                p2.first - (p3.first - p1.first) / 6, p2.second - (p3.second - p1.second) / 6,
                p2.first, p2.second)
        }
        close()
    }
}
