package li.songe.morph.compose

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import li.songe.morph.compose.internal.CubicPath
import li.songe.morph.compose.internal.MorphContourRole
import kotlin.math.abs

/** Difference regions are retained for inspection, not just a net area which can cancel out. */
internal data class InkDifference(
    val added: List<CubicPath>,
    val missing: List<CubicPath>,
    val addedArea: Double,
    val missingArea: Double,
    val referenceArea: Double,
) {
    val symmetricDifferenceRatio: Double get() = (addedArea + missingArea) / referenceArea
}

internal fun compareFilledInk(original: List<CubicPath>, rebuilt: List<CubicPath>): InkDifference {
    val scale = 1024f
    fun ink(contours: List<CubicPath>) = Path().apply {
        for (contour in contours) {
            require(contour.closed && contour.stroke == null)
            val p = contour.points
            moveTo((p[0] * scale).toFloat(), (p[1] * scale).toFloat())
            for (i in 2 until p.size step 6) cubicTo(
                (p[i] * scale).toFloat(), (p[i + 1] * scale).toFloat(),
                (p[i + 2] * scale).toFloat(), (p[i + 3] * scale).toFloat(),
                (p[i + 4] * scale).toFloat(), (p[i + 5] * scale).toFloat(),
            )
            close()
        }
    }
    fun contours(path: Path): List<CubicPath> = if (path.isEmpty) emptyList()
        else morphGeometryOf(path, Size(scale, scale)).paths
    val source = ink(original); val target = ink(rebuilt)
    val added = contours(Path.combine(PathOperation.Difference, target, source))
    val missing = contours(Path.combine(PathOperation.Difference, source, target))
    return InkDifference(added, missing, filledArea(added), filledArea(missing), filledArea(original))
}

/** Green's theorem integrated over the cubic polynomial, including holes. */
internal fun filledArea(contours: List<CubicPath>): Double = contours.sumOf { contour ->
    val p = contour.points
    var integral = 0.0
    for (i in 0 until p.size - 2 step 6) {
        fun coefficients(axis: Int): DoubleArray {
            val a = p[i + axis]; val b = p[i + 2 + axis]
            val c = p[i + 4 + axis]; val d = p[i + 6 + axis]
            return doubleArrayOf(a - p[axis], 3 * (b - a), 3 * (a - 2 * b + c), d - a + 3 * (b - c))
        }
        val x = coefficients(0); val y = coefficients(1)
        for (a in 0..3) for (b in 1..3) integral += (x[a] * y[b] - y[a] * x[b]) * b / (a + b)
    }
    abs(integral / 2) * if (contour.role == MorphContourRole.Hole) -1 else 1
}.coerceAtLeast(0.0)
