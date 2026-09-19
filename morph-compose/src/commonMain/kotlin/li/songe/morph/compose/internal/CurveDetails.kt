package li.songe.morph.compose.internal

import li.songe.morph.compose.MorphInterpolation
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.sin

/** Original cubics and the segment + t location of each unchanged arc-length sample. */
internal class CurveSource(val path: CubicPath, val positions: DoubleArray) {
    fun samePath(other: CurveSource): Boolean =
        path.closed == other.path.closed && path.role == other.path.role && path.points.contentEquals(other.path.points)

    fun details(samples: DoubleArray): CurveDetails {
        val left = mutableListOf<Int>()
        val right = mutableListOf<Int>()
        val weights = mutableListOf<Double>()
        val coordinates = mutableListOf<Double>()
        val count = positions.size
        val edges = if (path.closed) count else count - 1
        for (edge in 0 until edges) {
            val next = (edge + 1) % count
            val start = positions[edge]
            val end = if (next == 0) positions[0] + path.segmentCount else positions[next]
            if (start == end) {
                // Coincident source samples can separate after an interruption. Keep their
                // zero-length edge so the target polygon still visits both samples.
                for (point in (if (coordinates.isEmpty()) 0 else 1)..3) {
                    left += edge
                    right += next
                    weights += point / 3.0
                    coordinates += samples[edge * 2]
                    coordinates += samples[edge * 2 + 1]
                }
                continue
            }
            var cursor = start
            while (cursor < end) {
                val segment = floor(cursor).toInt()
                val stop = minOf(end, segment + 1.0)
                val t0 = cursor - segment
                val t1 = stop - segment
                val piece = subCubic(path.points, segment % path.segmentCount, t0, t1)
                for (point in (if (coordinates.isEmpty()) 0 else 1)..3) {
                    left += edge
                    right += next
                    weights += (cursor - start + (stop - cursor) * point / 3.0) / (end - start)
                    coordinates += piece[point * 2]
                    coordinates += piece[point * 2 + 1]
                }
                cursor = stop
            }
        }
        return CurveDetails(left.toIntArray(), right.toIntArray(), weights.toDoubleArray(), DoubleArray(coordinates.size))
            .rebase(samples, coordinates.toDoubleArray())
    }
}

/** A packed cubic chain expressed as offsets from edges of the existing sampled polygon. */
internal class CurveDetails(
    val left: IntArray,
    val right: IntArray,
    val weights: DoubleArray,
    val offsets: DoubleArray,
) {
    fun rebase(samples: DoubleArray, curves: DoubleArray): CurveDetails =
        CurveDetails(left, right, weights, DoubleArray(curves.size) { coordinate ->
            val point = coordinate / 2
            val axis = coordinate % 2
            val a = samples[left[point] * 2 + axis]
            val b = samples[right[point] * 2 + axis]
            curves[coordinate] - (a + (b - a) * weights[point])
        })

    // alignPair can rotate/reverse the sampled source for closed-to-open transitions.
    fun orient(source: DoubleArray, aligned: DoubleArray): CurveDetails {
        if (source === aligned || source.contentEquals(aligned)) return this
        val count = source.size / 2
        for (direction in intArrayOf(1, -1)) {
            for (offset in 0 until count) {
                fun original(index: Int) = ((offset + direction * index) % count + count) % count
                if ((0 until count).all { index ->
                    source[original(index) * 2] == aligned[index * 2] &&
                        source[original(index) * 2 + 1] == aligned[index * 2 + 1]
                }) {
                    val inverse = IntArray(count)
                    for (index in 0 until count) inverse[original(index)] = index
                    return CurveDetails(IntArray(left.size) { inverse[left[it]] },
                        IntArray(right.size) { inverse[right[it]] }, weights, offsets)
                }
            }
        }
        error("Aligned source must be a permutation of its samples")
    }
}

/** Select only after pairing and global alignment; never remove a contour from either solver. */
internal fun PlanItem.retainCurves(source: SampledContour, target: SampledContour) {
    val original = source.curveSource
    val sameGeometry = original != null && target.curveSource?.let(original::samePath) == true
    val stationary = sameGeometry && source.role == target.role && source.closed == target.closed &&
        abs(theta) <= 1e-12 && abs(logScale) <= 1e-12 &&
        this.source.indices.all { abs(this.source[it] - targetOriented[it]) <= 1e-12 } &&
        blockTransport.let { it == null || (abs(it.driftX) <= 1e-12 && abs(it.driftY) <= 1e-12) }
    if (stationary) stationaryCurve = original
    val details = source.curveDetails ?: original?.details(source.points)
    val from = details?.orient(source.points, this.source)
    if (stationary) {
        curveDetails = from
        return
    }
    val to = (target.curveDetails ?: target.curveSource?.details(target.points))
        ?.orient(target.points, targetOriented)
    if (from == null && to == null) return
    // Both ends must use the same cubic segmentation; otherwise switching to the target's
    // original curves at rest exposes the error between those curves and their sampled chords.
    val refined = refineCurves(from, to, this.source.size / 2, closed)
    curveDetails = refined.first
    targetCurveDetails = refined.second
}

private data class EdgePiece(val start: Double, val end: Double, val offsets: DoubleArray)

/** Reorder control offsets into aligned sample-edge order, including reversed traversal. */
private fun CurveDetails.edgePieces(count: Int): Array<MutableList<EdgePiece>> {
    val edges = Array(count) { mutableListOf<EdgePiece>() }
    for (i in 0 until offsets.size - 2 step 6) {
        val control = i / 2 + 1
        val a = left[control]
        val b = right[control]
        var start = (2 * weights[control] - weights[control + 1]).coerceIn(0.0, 1.0)
        var end = (2 * weights[control + 1] - weights[control]).coerceIn(0.0, 1.0)
        val piece = offsets.copyOfRange(i, i + 8)
        val edge: Int
        if (b == (a + 1) % count) {
            edge = a
        } else {
            check(a == (b + 1) % count) { "Curve controls must belong to adjacent samples" }
            edge = b
            val oldStart = start
            start = 1.0 - end
            end = 1.0 - oldStart
            for (p in 0..1) for (axis in 0..1) {
                val value = piece[p * 2 + axis]
                piece[p * 2 + axis] = piece[(3 - p) * 2 + axis]
                piece[(3 - p) * 2 + axis] = value
            }
        }
        if (end > start) edges[edge] += EdgePiece(start, end, piece)
    }
    return edges
}

/** Union the two sets of segment boundaries, subdividing cubics exactly at new boundaries. */
private fun refineCurves(from: CurveDetails?, to: CurveDetails?, count: Int, closed: Boolean): Pair<CurveDetails, CurveDetails> {
    val a = from?.edgePieces(count)
    val b = to?.edgePieces(count)
    val left = mutableListOf<Int>()
    val right = mutableListOf<Int>()
    val weights = mutableListOf<Double>()
    val sourceOffsets = mutableListOf<Double>()
    val targetOffsets = mutableListOf<Double>()
    fun restrict(pieces: List<EdgePiece>, start: Double, end: Double): DoubleArray {
        val middle = (start + end) / 2
        val piece = pieces.firstOrNull { middle >= it.start && middle <= it.end } ?: return DoubleArray(8)
        return subCubic(piece.offsets, 0, ((start - piece.start) / (piece.end - piece.start)).coerceIn(0.0, 1.0),
            ((end - piece.start) / (piece.end - piece.start)).coerceIn(0.0, 1.0))
    }
    for (edge in 0 until if (closed) count else count - 1) {
        val ap = a?.get(edge).orEmpty()
        val bp = b?.get(edge).orEmpty()
        val cuts = (listOf(0.0, 1.0) + (ap + bp).flatMap { listOf(it.start, it.end) }).sorted()
            .fold(mutableListOf<Double>()) { result, value ->
                if (result.isEmpty() || value - result.last() > 1e-12) result += value
                result
            }
        for ((start, end) in cuts.zipWithNext()) {
            val source = restrict(ap, start, end)
            val target = restrict(bp, start, end)
            for (p in (if (left.isEmpty()) 0 else 1)..3) {
                left += edge
                right += (edge + 1) % count
                weights += start + (end - start) * p / 3
                sourceOffsets += source[p * 2]
                sourceOffsets += source[p * 2 + 1]
                targetOffsets += target[p * 2]
                targetOffsets += target[p * 2 + 1]
            }
        }
    }
    fun detail(offsets: List<Double>) = CurveDetails(left.toIntArray(), right.toIntArray(), weights.toDoubleArray(), offsets.toDoubleArray())
    return detail(sourceOffsets) to detail(targetOffsets)
}

internal fun PlanItem.writeCurves(
    progress: Double,
    interpolation: MorphInterpolation,
    samples: DoubleArray,
    output: DoubleArray?,
) {
    val detail = curveDetails ?: return
    val curves = checkNotNull(output)
    // Transport source and target offsets in their respective similarity frames. Clamped detail
    // weights keep overshoot continuous on both sides of the exact endpoint geometry.
    val weight = if (stationaryCurve != null) 1.0 else 1.0 - progress.coerceIn(0.0, 1.0)
    val polar = interpolation == MorphInterpolation.Polar && polarInterpolation
    val angle = if (polar) theta * progress else 0.0
    val scale = if (polar) exp(logScale * progress) else 1.0
    val cosine = cos(angle) * scale * weight
    val sine = sin(angle) * scale * weight
    val target = targetCurveDetails
    val targetWeight = progress.coerceIn(0.0, 1.0)
    val targetAngle = if (polar) theta * (progress - 1.0) else 0.0
    val targetScale = if (polar) exp(logScale * (progress - 1.0)) else 1.0
    val targetCosine = cos(targetAngle) * targetScale * targetWeight
    val targetSine = sin(targetAngle) * targetScale * targetWeight
    for (point in detail.left.indices) {
        val a = detail.left[point] * 2
        val b = detail.right[point] * 2
        val i = point * 2
        val fraction = detail.weights[point]
        val dx = detail.offsets[i]
        val dy = detail.offsets[i + 1]
        curves[i] = samples[a] + (samples[b] - samples[a]) * fraction + dx * cosine - dy * sine
        curves[i + 1] = samples[a + 1] + (samples[b + 1] - samples[a + 1]) * fraction + dx * sine + dy * cosine
        if (target != null) {
            val tx = target.offsets[i]
            val ty = target.offsets[i + 1]
            curves[i] += tx * targetCosine - ty * targetSine
            curves[i + 1] += tx * targetSine + ty * targetCosine
        }
    }
}

// Restrict a cubic to [start, end] using its endpoint values and derivatives (exact subdivision).
private fun subCubic(points: DoubleArray, segment: Int, start: Double, end: Double): DoubleArray {
    val i = segment * 6
    val result = DoubleArray(8)
    for (axis in 0..1) {
        val p0 = points[i + axis]
        val p1 = points[i + 2 + axis]
        val p2 = points[i + 4 + axis]
        val p3 = points[i + 6 + axis]
        fun value(t: Double): Double {
            val u = 1 - t
            return u*u*u*p0 + 3*u*u*t*p1 + 3*u*t*t*p2 + t*t*t*p3
        }
        fun tangent(t: Double): Double {
            val u = 1 - t
            return u*u*(p1-p0) + 2*u*t*(p2-p1) + t*t*(p3-p2)
        }
        result[axis] = value(start)
        result[2 + axis] = result[axis] + tangent(start) * (end - start)
        result[6 + axis] = value(end)
        result[4 + axis] = result[6 + axis] - tangent(end) * (end - start)
    }
    return result
}
