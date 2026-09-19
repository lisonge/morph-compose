package li.songe.morph.compose

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import li.songe.morph.compose.internal.CubicPath
import li.songe.morph.compose.internal.MorphContourRole
import li.songe.morph.compose.internal.SampledContour
import li.songe.morph.compose.internal.cubicPathOf
import kotlin.math.abs
import kotlin.math.hypot

private const val OutlineScale = 1024.0
private data class Point(val x: Double, val y: Double) {
    operator fun plus(p: Point) = Point(x + p.x, y + p.y)
    operator fun minus(p: Point) = Point(x - p.x, y - p.y)
    operator fun times(s: Double) = Point(x * s, y * s)
}

internal fun buildInterruptedVectorPlan(from: List<SampledContour>, to: List<CubicPath>, options: MorphOptions): li.songe.morph.compose.internal.MorphPlan {
    val sourcePaths = from.map { it.asCubicPath() }
    if (!useOutlines(sourcePaths, to, options)) {
        return li.songe.morph.compose.internal.buildMorphPlanFromSampledSource(from, to, options)
    }
    val target = to.expandStrokes(options)
    // Representation changes require one resampling pass of the expanded ink. Keep snapshots
    // already in outline form untouched, including their exact interpolated curve offsets.
    return if (from.any { it.stroke != null }) {
        li.songe.morph.compose.internal.buildMorphPlan(sourcePaths.expandStrokes(options), target, options)
    } else li.songe.morph.compose.internal.buildMorphPlanFromSampledSource(from, target, options)
}

internal fun useOutlines(source: List<CubicPath>, target: List<CubicPath>, options: MorphOptions): Boolean {
    val onlyStrokes = (source + target).all { it.role == MorphContourRole.Stroke && it.stroke != null }
    return when (options.transitionMode) {
        MorphTransitionMode.Auto -> !onlyStrokes
        MorphTransitionMode.Outline -> true
        MorphTransitionMode.Centerline -> {
            require(onlyStrokes) { "Centerline mode requires stroke-only input on both sides" }
            false
        }
    }
}

/** Recover the rendered cubic chain, not a second approximation of an interrupted centerline. */
internal fun SampledContour.asCubicPath(): CubicPath {
    val detail = curveDetails
    if (detail != null) {
        val coordinates = DoubleArray(detail.offsets.size) { i ->
            val point = i / 2
            val axis = i % 2
            val a = points[detail.left[point] * 2 + axis]
            val b = points[detail.right[point] * 2 + axis]
            a + (b - a) * detail.weights[point] + detail.offsets[i]
        }
        return cubicPathOf(coordinates, closed, role, stroke)
    }
    val count = points.size / 2
    val edges = if (closed) count else count - 1
    val coordinates = mutableListOf(points[0], points[1])
    for (i in 0 until edges) for (k in 1..3) for (axis in 0..1) {
        val a = points[i * 2 + axis]
        val b = points[((i + 1) % count) * 2 + axis]
        coordinates += a + (b - a) * k / 3
    }
    return cubicPathOf(coordinates.toDoubleArray(), closed, role, stroke)
}

/** Stroke expansion is common code; Compose's path union removes overlap before topology analysis. */
internal fun List<CubicPath>.expandStrokes(options: MorphOptions): List<CubicPath> {
    if (none { it.stroke != null }) return this
    var ink = Path()
    fun add(path: Path) { ink = Path.combine(PathOperation.Union, ink, path) }
    fun polygon(vararg points: Point) = Path().apply {
        moveTo((points[0].x * OutlineScale).toFloat(), (points[0].y * OutlineScale).toFloat())
        for (p in points.drop(1)) lineTo((p.x * OutlineScale).toFloat(), (p.y * OutlineScale).toFloat())
        close()
    }
    fun disk(p: Point, radius: Double) = Path().apply {
        addOval(Rect(((p.x - radius) * OutlineScale).toFloat(), ((p.y - radius) * OutlineScale).toFloat(),
            ((p.x + radius) * OutlineScale).toFloat(), ((p.y + radius) * OutlineScale).toFloat()))
    }
    // Keep compound fill winding together before unioning any stroke ink with it.
    val fill = Path()
    for (contour in filter { it.stroke == null }) {
        val p = contour.points
        fill.moveTo((p[0] * OutlineScale).toFloat(), (p[1] * OutlineScale).toFloat())
        for (i in 2 until p.size step 6) fill.cubicTo((p[i] * OutlineScale).toFloat(), (p[i + 1] * OutlineScale).toFloat(),
            (p[i + 2] * OutlineScale).toFloat(), (p[i + 3] * OutlineScale).toFloat(),
            (p[i + 4] * OutlineScale).toFloat(), (p[i + 5] * OutlineScale).toFloat())
        fill.close()
    }
    add(fill)
    for (contour in filter { it.stroke != null }) {
        val style = checkNotNull(contour.stroke)
        val radius = style.width / 2
        if (radius <= 0) continue
        val points = mutableListOf<Point>()
        fun flatten(a: Point, b: Point, c: Point, d: Point, depth: Int) {
            val chord = d - a
            val length = hypot(chord.x, chord.y)
            fun distance(p: Point) = if (length < 1e-12) hypot(p.x - a.x, p.y - a.y)
                else abs(chord.x * (p.y - a.y) - chord.y * (p.x - a.x)) / length
            val controlLength = hypot(b.x - a.x, b.y - a.y) + hypot(c.x - b.x, c.y - b.y) + hypot(d.x - c.x, d.y - c.y)
            if ((maxOf(distance(b), distance(c)) <= options.outlineTolerance && controlLength - length <= options.outlineTolerance) || depth == 16) {
                if (points.isEmpty() || hypot(d.x - points.last().x, d.y - points.last().y) > 1e-12) points += d
                return
            }
            val ab = (a + b) * 0.5; val bc = (b + c) * 0.5; val cd = (c + d) * 0.5
            val abc = (ab + bc) * 0.5; val bcd = (bc + cd) * 0.5; val middle = (abc + bcd) * 0.5
            flatten(a, ab, abc, middle, depth + 1)
            flatten(middle, bcd, cd, d, depth + 1)
        }
        fun point(i: Int) = Point(contour.points[i], contour.points[i + 1])
        points += point(0)
        for (i in 0 until contour.points.size - 2 step 6) flatten(point(i), point(i + 2), point(i + 4), point(i + 6), 0)
        if (contour.closed && points.size > 1 && hypot(points.last().x - points.first().x, points.last().y - points.first().y) < 1e-12) points.removeAt(points.lastIndex)
        if (points.size < 2) {
            if (style.cap == StrokeCap.Round) add(disk(points.first(), radius))
            continue
        }
        val edges = if (contour.closed) points.size else points.size - 1
        val directions = List(edges) { i ->
            val delta = points[(i + 1) % points.size] - points[i]
            delta * (1 / hypot(delta.x, delta.y))
        }
        val normals = directions.map { Point(-it.y * radius, it.x * radius) }
        for (i in 0 until edges) {
            var a = points[i]; var b = points[(i + 1) % points.size]
            if (!contour.closed && style.cap == StrokeCap.Square) {
                if (i == 0) a -= directions[i] * radius
                if (i == edges - 1) b += directions[i] * radius
            }
            add(polygon(a + normals[i], b + normals[i], b - normals[i], a - normals[i]))
        }
        for (i in (if (contour.closed) 0 else 1) until (if (contour.closed) points.size else points.size - 1)) {
            val p = points[i]
            val before = (i + edges - 1) % edges
            val after = i % edges
            val u = directions[before]; val v = directions[after]
            val cross = u.x * v.y - u.y * v.x
            if (style.join == StrokeJoin.Round) { add(disk(p, radius)); continue }
            if (abs(cross) < 1e-12) continue
            val side = if (cross > 0) -1.0 else 1.0
            val a = p + normals[before] * side; val b = p + normals[after] * side
            val delta = b - a
            val miter = a + u * ((delta.x * v.y - delta.y * v.x) / cross)
            if (style.join == StrokeJoin.Miter && hypot(miter.x - p.x, miter.y - p.y) <= style.miter * radius) add(polygon(p, a, miter, b))
            else add(polygon(p, a, b))
        }
        if (!contour.closed && style.cap == StrokeCap.Round) {
            add(disk(points.first(), radius)); add(disk(points.last(), radius))
        }
    }
    require(!ink.isEmpty) { "Stroke expansion produced no visible geometry" }
    return morphGeometryOf(ink, Size(OutlineScale.toFloat(), OutlineScale.toFloat()),
        (options.outlineTolerance * OutlineScale / 4).toFloat()).vector.toCubicPaths(false)
}
