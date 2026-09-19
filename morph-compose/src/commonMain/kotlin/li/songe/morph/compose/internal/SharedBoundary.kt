package li.songe.morph.compose.internal

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.exp

internal data class AnchoredContours(val source: SampledContour, val target: SampledContour,
    val movingSpans: List<IntRange>, val anchorCount: Int)

/** Only ordered, same-position vertices can constrain a filled contour. No icon-specific data. */
internal fun sharedBoundaryPair(
    source: SampledContour,
    target: SampledContour,
): AnchoredContours? {
    if (!source.closed || !target.closed || source.role == MorphContourRole.Stroke || source.role != target.role) return null
    val a = source.curveSource?.path ?: return null
    val b = target.curveSource?.path ?: return null
    // Ambiguous/repeated vertices and reordered boundaries fall back to the unconstrained solver.
    fun sameVertex(i: Int, j: Int) =
        abs(a.points[i * 6] - b.points[j * 6]) < 1e-7 &&
            abs(a.points[i * 6 + 1] - b.points[j * 6 + 1]) < 1e-7
    var anchors = (0 until a.segmentCount).mapNotNull { i ->
        val matches = (0 until b.segmentCount).filter { j -> sameVertex(i, j) }
        matches.singleOrNull()?.let { i to it }
    }.filter { (_, j) -> (0 until a.segmentCount).count { sameVertex(it, j) } == 1 }
    val count = source.points.size / 2
    if (anchors.size < 2 || anchors.size > count) return null
    val startB = anchors.first().second
    val orderedB = anchors.map { (it.second - startB + b.segmentCount) % b.segmentCount }
    if (orderedB.zipWithNext().any { (x, y) -> x >= y }) return null

    val lengthsA = DoubleArray(a.segmentCount) { segmentLength(a.points, it) }
    val lengthsB = DoubleArray(b.segmentCount) { segmentLength(b.points, it) }
    fun spans(path: CubicPath, lengths: DoubleArray, axis: Int) = anchors.indices.map { index ->
        val start = if (axis == 0) anchors[index].first else anchors[index].second
        val next = anchors[(index + 1) % anchors.size]
        val end = if (axis == 0) next.first else next.second
        BoundarySpan(path, lengths, start, start + (end - start + path.segmentCount) % path.segmentCount)
    }
    var spansA = spans(a, lengthsA, 0)
    var spansB = spans(b, lengthsB, 1)
    var sharedLength = 0.0
    var sharedSpans = BooleanArray(anchors.size)
    val pointA = DoubleArray(2)
    val pointB = DoubleArray(2)
    for (index in anchors.indices) {
        val sa = spansA[index]
        val sb = spansB[index]
        val shared = (1..7).all { sample ->
            sa.sample(sample / 8.0, pointA, 0)
            sb.sample(sample / 8.0, pointB, 0)
            // Different icon packs approximate the same corner/circle with different cubics.
            // Accept a close boundary, but interpolate its actual coordinates (never snap it).
            abs(pointA[0] - pointB[0]) < 1e-3 && abs(pointA[1] - pointB[1]) < 1e-3
        }
        sharedSpans[index] = shared
        if (shared) sharedLength += minOf(sa.length, sb.length)
    }
    // A coincidental pair of vertices is not evidence of a shared boundary.
    if (sharedLength < minOf(lengthsA.sum(), lengthsB.sum()) * 0.1 || sharedLength < 1e-6) return null
    // An isolated coordinate coincidence inside a changing region is not a fixed anchor.
    // Keep only endpoints supported by an actual common span.
    val retained = anchors.indices.filter { sharedSpans[it] || sharedSpans[(it + anchors.size - 1) % anchors.size] }
    if (retained.size < 2) return null
    if (retained.size != anchors.size) {
        sharedSpans = BooleanArray(retained.size) { sharedSpans[retained[it]] }
        anchors = retained.map { anchors[it] }
        spansA = spans(a, lengthsA, 0)
        spansB = spans(b, lengthsB, 1)
    }
    val weights = anchors.indices.map { maxOf(spansA[it].length, spansB[it].length) }
    val counts = IntArray(anchors.size) { 1 }
    repeat(count - anchors.size) {
        val index = weights.indices.maxBy { weights[it] / counts[it] }
        counts[index]++
    }
    fun sample(path: CubicPath, spans: List<BoundarySpan>): SampledContour {
        val points = DoubleArray(count * 2)
        val positions = DoubleArray(count)
        var cursor = 0
        var turn = 0
        for (index in spans.indices) {
            if (index > 0 && spans[index].start < spans[index - 1].start) turn += path.segmentCount
            for (step in 0 until counts[index]) {
                positions[cursor] = spans[index].sample(step.toDouble() / counts[index], points, cursor * 2) + turn
                cursor++
            }
        }
        return SampledContour(points, true, path.role, curveSource = CurveSource(path, positions))
    }
    var cursor = 0
    val moving = counts.indices.mapNotNull { i ->
        val start = cursor
        cursor += counts[i]
        if (sharedSpans[i]) null else start..cursor
    }
    return AnchoredContours(sample(a, spansA), sample(b, spansB), moving, anchors.size)
}

/** Local similarity for a changing span, corrected to keep its two boundary anchors fixed. */
internal class BoundaryMotion(
    val range: IntRange,
    val ax: Double, val ay: Double, val bx: Double, val by: Double,
    val theta: Double, val logScale: Double,
) {
    private val inverseCosine = cos(theta) * exp(-logScale)
    private val inverseSine = sin(theta) * exp(-logScale)
    private val minimum = DoubleArray(2) { Double.POSITIVE_INFINITY }
    private val maximum = DoubleArray(2) { Double.NEGATIVE_INFINITY }

    fun prepare(item: PlanItem) {
        val detail = item.curveDetails ?: return
        val target = item.targetCurveDetails ?: return
        for (p in detail.left.indices) {
            val edge = detail.left[p]
            if (edge < range.first || edge >= range.last) continue
            for (axis in 0..1) {
                fun value(samples: DoubleArray, offsets: DoubleArray) = samples[edge * 2 + axis] +
                    (samples[detail.right[p] * 2 + axis] - samples[edge * 2 + axis]) * detail.weights[p] + offsets[p * 2 + axis]
                val a = value(item.source, detail.offsets)
                val b = value(item.targetOriented, target.offsets)
                minimum[axis] = minOf(minimum[axis], a, b)
                maximum[axis] = maxOf(maximum[axis], a, b)
            }
        }
    }

    fun write(item: PlanItem, t: Double, points: DoubleArray, curves: DoubleArray?) {
        val c = cos(theta * t) * exp(logScale * t)
        val s = sin(theta * t) * exp(logScale * t)
        fun value(x: Double, y: Double, tx: Double, ty: Double, axis: Int): Double {
            val dx = tx - bx
            val dy = ty - by
            val localX = dx * inverseCosine + dy * inverseSine
            val localY = -dx * inverseSine + dy * inverseCosine
            val px = x - ax + (localX - (x - ax)) * t
            val py = y - ay + (localY - (y - ay)) * t
            return if (axis == 0) ax + (bx - ax) * t + px * c - py * s
                else ay + (by - ay) * t + px * s + py * c
        }
        val n = points.size / 2
        val first = range.first % n
        val last = range.last % n
        fun error(index: Int, axis: Int) = value(item.source[index * 2], item.source[index * 2 + 1],
            item.targetOriented[index * 2], item.targetOriented[index * 2 + 1], axis) -
            (item.source[index * 2 + axis] + (item.targetOriented[index * 2 + axis] - item.source[index * 2 + axis]) * t)
        val startX = error(first, 0)
        val startY = error(first, 1)
        val endX = error(last, 0)
        val endY = error(last, 1)
        fun coordinate(x: Double, y: Double, tx: Double, ty: Double, u: Double, axis: Int): Double {
            val startError = if (axis == 0) startX else startY
            val endError = if (axis == 0) endX else endY
            val result = value(x, y, tx, ty, axis) - startError - (endError - startError) * u
            // Keep changing geometry in its original region; it must not invade a shared body.
            return if (t in 0.0..1.0 && minimum[axis] <= maximum[axis]) result.coerceIn(minimum[axis], maximum[axis]) else result
        }
        for (index in range) {
            val i = index % n
            val u = (index - range.first).toDouble() / (range.last - range.first)
            for (axis in 0..1) points[i * 2 + axis] = coordinate(item.source[i * 2], item.source[i * 2 + 1],
                item.targetOriented[i * 2], item.targetOriented[i * 2 + 1], u, axis)
        }
        val detail = item.curveDetails ?: return
        val target = item.targetCurveDetails ?: return
        if (curves == null) return
        for (p in detail.left.indices) {
            val edge = detail.left[p]
            if (edge < range.first || edge >= range.last) continue
            val next = detail.right[p]
            val fraction = detail.weights[p]
            fun original(samples: DoubleArray, offsets: DoubleArray, axis: Int) = samples[edge * 2 + axis] +
                (samples[next * 2 + axis] - samples[edge * 2 + axis]) * fraction + offsets[p * 2 + axis]
            val x = original(item.source, detail.offsets, 0)
            val y = original(item.source, detail.offsets, 1)
            val tx = original(item.targetOriented, target.offsets, 0)
            val ty = original(item.targetOriented, target.offsets, 1)
            val u = (edge - range.first + fraction) / (range.last - range.first)
            for (axis in 0..1) curves[p * 2 + axis] = coordinate(x, y, tx, ty, u, axis)
        }
    }
}

private class BoundarySpan(val path: CubicPath, val lengths: DoubleArray, val start: Int, val end: Int) {
    val length = (start until end).sumOf { lengths[it % path.segmentCount] }

    fun sample(fraction: Double, output: DoubleArray, offset: Int): Double {
        var distance = length * fraction
        var segment = start
        while (segment < end - 1 && distance > lengths[segment % path.segmentCount]) {
            distance -= lengths[segment % path.segmentCount]
            segment++
        }
        val wrapped = segment % path.segmentCount
        val t = if (fraction == 0.0 || lengths[wrapped] < 1e-12) 0.0
            else invertLength(path.points, wrapped, distance, lengths[wrapped])
        writePoint(path.points, wrapped, t, output, offset)
        return segment + t
    }
}
