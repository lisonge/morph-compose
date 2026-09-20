package li.songe.morph.compose

import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathNode
import androidx.compose.ui.graphics.vector.VectorGroup
import androidx.compose.ui.graphics.vector.VectorPath
import li.songe.morph.compose.internal.CubicPath
import li.songe.morph.compose.internal.MorphContourRole
import li.songe.morph.compose.internal.StrokeStyle
import li.songe.morph.compose.internal.cubicPathOf
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

private const val Epsilon = 1e-12
private const val Tau = 2.0 * PI
private const val TopologyFlatness = 1e-4
private const val TopologyMaxDepth = 12

private data class AffineTransform(
    val a: Double = 1.0,
    val b: Double = 0.0,
    val c: Double = 0.0,
    val d: Double = 1.0,
    val translateX: Double = 0.0,
    val translateY: Double = 0.0,
) {
    fun x(x: Double, y: Double): Double = a * x + c * y + translateX

    fun y(x: Double, y: Double): Double = b * x + d * y + translateY

    /** Returns this transform composed with [inner]: `this(inner(point))`. */
    operator fun times(inner: AffineTransform): AffineTransform =
        AffineTransform(
            a = a * inner.a + c * inner.b,
            b = b * inner.a + d * inner.b,
            c = a * inner.c + c * inner.d,
            d = b * inner.c + d * inner.d,
            translateX = a * inner.translateX + c * inner.translateY + translateX,
            translateY = b * inner.translateX + d * inner.translateY + translateY,
        )
}

private fun translation(x: Double, y: Double): AffineTransform =
    AffineTransform(translateX = x, translateY = y)

private fun scale(x: Double, y: Double): AffineTransform = AffineTransform(a = x, d = y)

private fun rotation(degrees: Double): AffineTransform {
    val radians = degrees * PI / 180.0
    val cosine = cos(radians)
    val sine = sin(radians)
    return AffineTransform(a = cosine, b = sine, c = -sine, d = cosine)
}

private fun VectorGroup.localTransform(): AffineTransform =
    translation(translationX.toDouble(), translationY.toDouble()) *
        translation(pivotX.toDouble(), pivotY.toDouble()) *
        rotation(rotation.toDouble()) *
        scale(scaleX.toDouble(), scaleY.toDouble()) *
        translation(-pivotX.toDouble(), -pivotY.toDouble())

private data class RawCubic(val points: DoubleArray, val closed: Boolean)

private class CubicBuilder(
    startX: Double,
    startY: Double,
    private val transform: AffineTransform,
) {
    private val points = mutableListOf<Double>()
    val startX: Double = startX
    val startY: Double = startY
    var currentX: Double = startX
        private set
    var currentY: Double = startY
        private set

    init {
        addPoint(startX, startY)
    }

    private fun addPoint(x: Double, y: Double) {
        points += transform.x(x, y)
        points += transform.y(x, y)
    }

    fun cubic(
        control1X: Double,
        control1Y: Double,
        control2X: Double,
        control2Y: Double,
        x: Double,
        y: Double,
    ) {
        addPoint(control1X, control1Y)
        addPoint(control2X, control2Y)
        addPoint(x, y)
        currentX = x
        currentY = y
    }

    fun line(x: Double, y: Double) {
        if (abs(x - currentX) < Epsilon && abs(y - currentY) < Epsilon) return
        cubic(
            currentX + (x - currentX) / 3.0,
            currentY + (y - currentY) / 3.0,
            currentX + 2.0 * (x - currentX) / 3.0,
            currentY + 2.0 * (y - currentY) / 3.0,
            x,
            y,
        )
    }

    fun quadratic(controlX: Double, controlY: Double, x: Double, y: Double) {
        cubic(
            currentX + 2.0 * (controlX - currentX) / 3.0,
            currentY + 2.0 * (controlY - currentY) / 3.0,
            x + 2.0 * (controlX - x) / 3.0,
            y + 2.0 * (controlY - y) / 3.0,
            x,
            y,
        )
    }

    fun arc(
        radiusXValue: Double,
        radiusYValue: Double,
        rotationDegrees: Double,
        largeArc: Boolean,
        positiveArc: Boolean,
        x: Double,
        y: Double,
    ) {
        val sourceX = currentX
        val sourceY = currentY
        if (abs(x - sourceX) < Epsilon && abs(y - sourceY) < Epsilon) return
        var radiusX = abs(radiusXValue)
        var radiusY = abs(radiusYValue)
        if (radiusX < Epsilon || radiusY < Epsilon) {
            line(x, y)
            return
        }

        val phi = rotationDegrees * PI / 180.0
        val cosinePhi = cos(phi)
        val sinePhi = sin(phi)
        val halfX = (sourceX - x) / 2.0
        val halfY = (sourceY - y) / 2.0
        val sourcePrimeX = cosinePhi * halfX + sinePhi * halfY
        val sourcePrimeY = -sinePhi * halfX + cosinePhi * halfY
        val lambda =
            sourcePrimeX * sourcePrimeX / (radiusX * radiusX) +
                sourcePrimeY * sourcePrimeY / (radiusY * radiusY)
        if (lambda > 1.0) {
            val adjustment = sqrt(lambda)
            radiusX *= adjustment
            radiusY *= adjustment
        }

        val radiusXSquared = radiusX * radiusX
        val radiusYSquared = radiusY * radiusY
        val primeXSquared = sourcePrimeX * sourcePrimeX
        val primeYSquared = sourcePrimeY * sourcePrimeY
        val denominator = radiusXSquared * primeYSquared + radiusYSquared * primeXSquared
        val radicand =
            if (denominator < Epsilon) {
                0.0
            } else {
                max(
                    0.0,
                    (radiusXSquared * radiusYSquared -
                        radiusXSquared * primeYSquared -
                        radiusYSquared * primeXSquared) / denominator,
                )
            }
        val coefficient = (if (largeArc == positiveArc) -1.0 else 1.0) * sqrt(radicand)
        val centerPrimeX = coefficient * radiusX * sourcePrimeY / radiusY
        val centerPrimeY = -coefficient * radiusY * sourcePrimeX / radiusX
        val centerX =
            cosinePhi * centerPrimeX - sinePhi * centerPrimeY + (sourceX + x) / 2.0
        val centerY =
            sinePhi * centerPrimeX + cosinePhi * centerPrimeY + (sourceY + y) / 2.0
        val startAngle =
            atan2(
                (sourcePrimeY - centerPrimeY) / radiusY,
                (sourcePrimeX - centerPrimeX) / radiusX,
            )
        var sweepAngle =
            atan2(
                (-sourcePrimeY - centerPrimeY) / radiusY,
                (-sourcePrimeX - centerPrimeX) / radiusX,
            ) - startAngle
        if (!positiveArc && sweepAngle > 0.0) sweepAngle -= Tau
        if (positiveArc && sweepAngle < 0.0) sweepAngle += Tau

        val sliceCount = max(1, ceil(abs(sweepAngle) / (PI / 2.0) - 1e-9).toInt())
        val delta = sweepAngle / sliceCount
        val alpha = 4.0 / 3.0 * tan(delta / 4.0)
        fun ellipseX(angle: Double): Double =
            centerX +
                radiusX * cos(angle) * cosinePhi - radiusY * sin(angle) * sinePhi
        fun ellipseY(angle: Double): Double =
            centerY +
                radiusX * cos(angle) * sinePhi + radiusY * sin(angle) * cosinePhi
        fun derivativeX(angle: Double): Double =
            -radiusX * sin(angle) * cosinePhi - radiusY * cos(angle) * sinePhi
        fun derivativeY(angle: Double): Double =
            -radiusX * sin(angle) * sinePhi + radiusY * cos(angle) * cosinePhi

        var previousAngle = startAngle
        var previousX = sourceX
        var previousY = sourceY
        for (slice in 1..sliceCount) {
            val nextAngle = startAngle + delta * slice
            val nextX = if (slice == sliceCount) x else ellipseX(nextAngle)
            val nextY = if (slice == sliceCount) y else ellipseY(nextAngle)
            cubic(
                previousX + alpha * derivativeX(previousAngle),
                previousY + alpha * derivativeY(previousAngle),
                nextX - alpha * derivativeX(nextAngle),
                nextY - alpha * derivativeY(nextAngle),
                nextX,
                nextY,
            )
            previousAngle = nextAngle
            previousX = nextX
            previousY = nextY
        }
    }

    fun finish(close: Boolean): RawCubic? {
        if (close) line(startX, startY)
        return if (points.size >= 8) RawCubic(points.toDoubleArray(), close) else null
    }
}

private fun VectorPath.toCubics(
    transform: AffineTransform,
    inputLabel: String,
): List<RawCubic> {
    require((fill is SolidColor && stroke == null) || (fill == null && stroke is SolidColor)) {
        "$inputLabel requires a solid fill-only or stroke-only path"
    }
    require(stroke == null || (strokeLineWidth.isFinite() && strokeLineWidth > 0f)) {
        "$inputLabel requires a positive finite stroke width"
    }
    require(
        abs(trimPathStart) < 1e-6f &&
            abs(trimPathEnd - 1.0f) < 1e-6f &&
            abs(trimPathOffset) < 1e-6f,
    ) { "$inputLabel uses trim-path values, which are not supported" }

    val output = mutableListOf<RawCubic>()
    var builder: CubicBuilder? = null
    var currentX = 0.0
    var currentY = 0.0
    var subpathStartX = 0.0
    var subpathStartY = 0.0
    var lastControlX = 0.0
    var lastControlY = 0.0
    var previousKind = ' '

    fun openBuilder(): CubicBuilder =
        builder ?: CubicBuilder(currentX, currentY, transform).also {
            builder = it
            subpathStartX = currentX
            subpathStartY = currentY
        }

    fun finish(close: Boolean = fill != null) {
        builder?.finish(close = close)?.let(output::add)
        builder = null
    }

    for (node in pathData) {
        when (node) {
            is PathNode.MoveTo -> {
                finish()
                currentX = node.x.toDouble()
                currentY = node.y.toDouble()
                subpathStartX = currentX
                subpathStartY = currentY
                builder = CubicBuilder(currentX, currentY, transform)
                previousKind = ' '
            }
            is PathNode.RelativeMoveTo -> {
                finish()
                currentX += node.dx.toDouble()
                currentY += node.dy.toDouble()
                subpathStartX = currentX
                subpathStartY = currentY
                builder = CubicBuilder(currentX, currentY, transform)
                previousKind = ' '
            }
            is PathNode.LineTo -> {
                openBuilder().line(node.x.toDouble(), node.y.toDouble())
                currentX = node.x.toDouble()
                currentY = node.y.toDouble()
                previousKind = ' '
            }
            is PathNode.RelativeLineTo -> {
                val x = currentX + node.dx
                val y = currentY + node.dy
                openBuilder().line(x, y)
                currentX = x
                currentY = y
                previousKind = ' '
            }
            is PathNode.HorizontalTo -> {
                openBuilder().line(node.x.toDouble(), currentY)
                currentX = node.x.toDouble()
                previousKind = ' '
            }
            is PathNode.RelativeHorizontalTo -> {
                val x = currentX + node.dx
                openBuilder().line(x, currentY)
                currentX = x
                previousKind = ' '
            }
            is PathNode.VerticalTo -> {
                openBuilder().line(currentX, node.y.toDouble())
                currentY = node.y.toDouble()
                previousKind = ' '
            }
            is PathNode.RelativeVerticalTo -> {
                val y = currentY + node.dy
                openBuilder().line(currentX, y)
                currentY = y
                previousKind = ' '
            }
            is PathNode.CurveTo -> {
                openBuilder().cubic(
                    node.x1.toDouble(),
                    node.y1.toDouble(),
                    node.x2.toDouble(),
                    node.y2.toDouble(),
                    node.x3.toDouble(),
                    node.y3.toDouble(),
                )
                lastControlX = node.x2.toDouble()
                lastControlY = node.y2.toDouble()
                currentX = node.x3.toDouble()
                currentY = node.y3.toDouble()
                previousKind = 'C'
            }
            is PathNode.RelativeCurveTo -> {
                val control1X = currentX + node.dx1
                val control1Y = currentY + node.dy1
                val control2X = currentX + node.dx2
                val control2Y = currentY + node.dy2
                val x = currentX + node.dx3
                val y = currentY + node.dy3
                openBuilder().cubic(control1X, control1Y, control2X, control2Y, x, y)
                lastControlX = control2X
                lastControlY = control2Y
                currentX = x
                currentY = y
                previousKind = 'C'
            }
            is PathNode.ReflectiveCurveTo -> {
                val control1X = if (previousKind == 'C') 2.0 * currentX - lastControlX else currentX
                val control1Y = if (previousKind == 'C') 2.0 * currentY - lastControlY else currentY
                openBuilder().cubic(
                    control1X,
                    control1Y,
                    node.x1.toDouble(),
                    node.y1.toDouble(),
                    node.x2.toDouble(),
                    node.y2.toDouble(),
                )
                lastControlX = node.x1.toDouble()
                lastControlY = node.y1.toDouble()
                currentX = node.x2.toDouble()
                currentY = node.y2.toDouble()
                previousKind = 'C'
            }
            is PathNode.RelativeReflectiveCurveTo -> {
                val control1X = if (previousKind == 'C') 2.0 * currentX - lastControlX else currentX
                val control1Y = if (previousKind == 'C') 2.0 * currentY - lastControlY else currentY
                val control2X = currentX + node.dx1
                val control2Y = currentY + node.dy1
                val x = currentX + node.dx2
                val y = currentY + node.dy2
                openBuilder().cubic(control1X, control1Y, control2X, control2Y, x, y)
                lastControlX = control2X
                lastControlY = control2Y
                currentX = x
                currentY = y
                previousKind = 'C'
            }
            is PathNode.QuadTo -> {
                openBuilder().quadratic(
                    node.x1.toDouble(),
                    node.y1.toDouble(),
                    node.x2.toDouble(),
                    node.y2.toDouble(),
                )
                lastControlX = node.x1.toDouble()
                lastControlY = node.y1.toDouble()
                currentX = node.x2.toDouble()
                currentY = node.y2.toDouble()
                previousKind = 'Q'
            }
            is PathNode.RelativeQuadTo -> {
                val controlX = currentX + node.dx1
                val controlY = currentY + node.dy1
                val x = currentX + node.dx2
                val y = currentY + node.dy2
                openBuilder().quadratic(controlX, controlY, x, y)
                lastControlX = controlX
                lastControlY = controlY
                currentX = x
                currentY = y
                previousKind = 'Q'
            }
            is PathNode.ReflectiveQuadTo -> {
                val controlX = if (previousKind == 'Q') 2.0 * currentX - lastControlX else currentX
                val controlY = if (previousKind == 'Q') 2.0 * currentY - lastControlY else currentY
                openBuilder().quadratic(controlX, controlY, node.x.toDouble(), node.y.toDouble())
                lastControlX = controlX
                lastControlY = controlY
                currentX = node.x.toDouble()
                currentY = node.y.toDouble()
                previousKind = 'Q'
            }
            is PathNode.RelativeReflectiveQuadTo -> {
                val controlX = if (previousKind == 'Q') 2.0 * currentX - lastControlX else currentX
                val controlY = if (previousKind == 'Q') 2.0 * currentY - lastControlY else currentY
                val x = currentX + node.dx
                val y = currentY + node.dy
                openBuilder().quadratic(controlX, controlY, x, y)
                lastControlX = controlX
                lastControlY = controlY
                currentX = x
                currentY = y
                previousKind = 'Q'
            }
            is PathNode.ArcTo -> {
                openBuilder().arc(
                    node.horizontalEllipseRadius.toDouble(),
                    node.verticalEllipseRadius.toDouble(),
                    node.theta.toDouble(),
                    node.isMoreThanHalf,
                    node.isPositiveArc,
                    node.arcStartX.toDouble(),
                    node.arcStartY.toDouble(),
                )
                currentX = node.arcStartX.toDouble()
                currentY = node.arcStartY.toDouble()
                previousKind = ' '
            }
            is PathNode.RelativeArcTo -> {
                val x = currentX + node.arcStartDx
                val y = currentY + node.arcStartDy
                openBuilder().arc(
                    node.horizontalEllipseRadius.toDouble(),
                    node.verticalEllipseRadius.toDouble(),
                    node.theta.toDouble(),
                    node.isMoreThanHalf,
                    node.isPositiveArc,
                    x,
                    y,
                )
                currentX = x
                currentY = y
                previousKind = ' '
            }
            PathNode.Close -> {
                finish(close = true)
                currentX = subpathStartX
                currentY = subpathStartY
                previousKind = ' '
            }
        }
    }
    finish()
    return output
}

private fun flattenForTopology(points: DoubleArray): DoubleArray {
    require(points.all(Double::isFinite)) { "Cubic path coordinates must be finite" }
    var minX = Double.POSITIVE_INFINITY
    var minY = Double.POSITIVE_INFINITY
    var maxX = Double.NEGATIVE_INFINITY
    var maxY = Double.NEGATIVE_INFINITY
    for (index in points.indices step 2) {
        minX = min(minX, points[index])
        minY = min(minY, points[index + 1])
        maxX = max(maxX, points[index])
        maxY = max(maxY, points[index + 1])
    }
    val tolerance = max(max(maxX - minX, maxY - minY) * TopologyFlatness, Epsilon)
    val output = mutableListOf(points[0], points[1])

    fun distance(x1: Double, y1: Double, x2: Double, y2: Double): Double {
        val dx = x2 - x1
        val dy = y2 - y1
        return sqrt(dx * dx + dy * dy)
    }

    fun distanceToLine(
        x: Double,
        y: Double,
        x1: Double,
        y1: Double,
        x2: Double,
        y2: Double,
    ): Double {
        val dx = x2 - x1
        val dy = y2 - y1
        val length = sqrt(dx * dx + dy * dy)
        return if (length > Epsilon) abs(dy * (x - x1) - dx * (y - y1)) / length else distance(x, y, x1, y1)
    }

    fun flatten(
        x0: Double,
        y0: Double,
        x1: Double,
        y1: Double,
        x2: Double,
        y2: Double,
        x3: Double,
        y3: Double,
        depth: Int,
    ) {
        val chordLength = distance(x0, y0, x3, y3)
        val controlLength =
            distance(x0, y0, x1, y1) + distance(x1, y1, x2, y2) + distance(x2, y2, x3, y3)
        val flatEnough =
            max(distanceToLine(x1, y1, x0, y0, x3, y3), distanceToLine(x2, y2, x0, y0, x3, y3)) <=
                tolerance && controlLength - chordLength <= tolerance
        if (flatEnough || depth >= TopologyMaxDepth) {
            output += x3
            output += y3
            return
        }

        val x01 = (x0 + x1) / 2.0
        val y01 = (y0 + y1) / 2.0
        val x12 = (x1 + x2) / 2.0
        val y12 = (y1 + y2) / 2.0
        val x23 = (x2 + x3) / 2.0
        val y23 = (y2 + y3) / 2.0
        val x012 = (x01 + x12) / 2.0
        val y012 = (y01 + y12) / 2.0
        val x123 = (x12 + x23) / 2.0
        val y123 = (y12 + y23) / 2.0
        val x0123 = (x012 + x123) / 2.0
        val y0123 = (y012 + y123) / 2.0
        flatten(x0, y0, x01, y01, x012, y012, x0123, y0123, depth + 1)
        flatten(x0123, y0123, x123, y123, x23, y23, x3, y3, depth + 1)
    }

    val segmentCount = (points.size - 2) / 6
    for (segment in 0 until segmentCount) {
        val index = segment * 6
        flatten(
            points[index],
            points[index + 1],
            points[index + 2],
            points[index + 3],
            points[index + 4],
            points[index + 5],
            points[index + 6],
            points[index + 7],
            depth = 0,
        )
    }
    return output.toDoubleArray()
}

private fun signedArea(points: DoubleArray): Double {
    val count = points.size / 2
    var area = 0.0
    for (index in 0 until count) {
        val next = (index + 1) % count
        val x1 = points[index * 2]
        val y1 = points[index * 2 + 1]
        val x2 = points[next * 2]
        val y2 = points[next * 2 + 1]
        area += x1 * y2 - x2 * y1
    }
    return area / 2.0
}

private fun contains(points: DoubleArray, x: Double, y: Double): Boolean {
    val count = points.size / 2
    var inside = false
    var previous = count - 1
    for (index in 0 until count) {
        val xi = points[index * 2]
        val yi = points[index * 2 + 1]
        val xj = points[previous * 2]
        val yj = points[previous * 2 + 1]
        if ((yi > y) != (yj > y) && x < (xj - xi) * (y - yi) / (yj - yi) + xi) {
            inside = !inside
        }
        previous = index
    }
    return inside
}

private fun reverse(points: DoubleArray): DoubleArray {
    val segmentCount = (points.size / 2 - 1) / 3
    val output = DoubleArray(points.size)
    output[0] = points[points.lastIndex - 1]
    output[1] = points[points.lastIndex]
    var write = 2
    for (segment in segmentCount - 1 downTo 0) {
        val offset = segment * 6
        output[write++] = points[offset + 4]
        output[write++] = points[offset + 5]
        output[write++] = points[offset + 2]
        output[write++] = points[offset + 3]
        output[write++] = points[offset]
        output[write++] = points[offset + 1]
    }
    return output
}

private fun normalizeTopology(rawContours: List<RawCubic>): List<CubicPath> {
    // A filled point or collinear out-and-back chain has no ink. Do not let it become a
    // synthetic hole in another contour. Test control points, not signed area: a genuine
    // self-intersecting shape can have zero signed area while still containing filled lobes.
    val contours = rawContours.filter { contour ->
        val p = contour.points
        val farthest = (2 until p.size step 2).maxBy { i ->
            (p[i] - p[0]) * (p[i] - p[0]) + (p[i + 1] - p[1]) * (p[i + 1] - p[1])
        }
        val dx = p[farthest] - p[0]
        val dy = p[farthest + 1] - p[1]
        val length = kotlin.math.hypot(dx, dy)
        length > 0.0 && (2 until p.size step 2).any { i ->
            abs(dx * (p[i + 1] - p[1]) - dy * (p[i] - p[0])) > length * length * 1e-12
        }
    }
    val flattened = contours.map { flattenForTopology(it.points) }
    return contours.mapIndexed { index, contour ->
        val x = contour.points[0]
        val y = contour.points[1]
        val nestingDepth =
            contours.indices.count { other -> other != index && contains(flattened[other], x, y) }
        val role = if (nestingDepth % 2 == 0) MorphContourRole.Shape else MorphContourRole.Hole
        val area = signedArea(flattened[index])
        val shouldReverse =
            (role == MorphContourRole.Shape && area < 0.0) ||
                (role == MorphContourRole.Hole && area > 0.0)
        cubicPathOf(if (shouldReverse) reverse(contour.points) else contour.points, true, role)
    }
}

internal fun ImageVector.toCubicPaths(isRtl: Boolean): List<CubicPath> {
    val vectorLabel = "ImageVector '$name'"
    require(viewportWidth > 0.0f && viewportHeight > 0.0f) {
        "$vectorLabel has non-positive viewport dimensions"
    }
    val fitScale = 1.0 / max(viewportWidth.toDouble(), viewportHeight.toDouble())
    var rootTransform =
        translation(
            (1.0 - viewportWidth * fitScale) / 2.0,
            (1.0 - viewportHeight * fitScale) / 2.0,
        ) * scale(fitScale, fitScale)
    if (autoMirror && isRtl) {
        rootTransform = translation(1.0, 0.0) * scale(-1.0, 1.0) * rootTransform
    }
    val contours = mutableListOf<CubicPath>()
    var pathIndex = 0

    fun visit(group: VectorGroup, parentTransform: AffineTransform) {
        require(group.clipPathData.isEmpty()) {
            "$vectorLabel group '${group.name}' uses a clip path, which is not supported"
        }
        val transform = parentTransform * group.localTransform()
        for (child in group) {
            when (child) {
                is VectorGroup -> visit(child, transform)
                is VectorPath -> {
                    val pathLabel = child.name.ifBlank { "#$pathIndex" }
                    pathIndex += 1
                    val label = "$vectorLabel path '$pathLabel'"
                    val raw = child.toCubics(transform, label)
                    if (child.stroke != null) {
                        val sx = kotlin.math.hypot(transform.a, transform.b)
                        val sy = kotlin.math.hypot(transform.c, transform.d)
                        require(sx > Epsilon && abs(sx - sy) <= 1e-6 * sx &&
                            abs(transform.a * transform.c + transform.b * transform.d) <= 1e-6 * sx * sy) {
                            "$label uses a non-uniform stroke transform, which is not supported"
                        }
                        val style = StrokeStyle(child.strokeLineWidth * sx, child.strokeLineCap,
                            child.strokeLineJoin, child.strokeLineMiter)
                        contours += raw.map { cubicPathOf(it.points, it.closed, MorphContourRole.Stroke, style) }
                    } else {
                        contours += normalizeTopology(raw)
                    }
                }
            }
        }
    }

    visit(root, rootTransform)
    return contours
}
