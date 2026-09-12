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
    val details = source.curveDetails ?: if (stationary) original.details(source.points) else null
    curveDetails = details?.orient(source.points, this.source)
}

internal fun PlanItem.writeCurves(
    progress: Double,
    interpolation: MorphInterpolation,
    samples: DoubleArray,
    output: DoubleArray?,
) {
    val detail = curveDetails ?: return
    val curves = checkNotNull(output)
    // Retained static curves never lose detail, including spring overshoot. Interrupted curves
    // transport with the existing contour and smoothly shed detail as they reach its target.
    val weight = if (stationaryCurve != null) 1.0 else 1.0 - progress.coerceIn(0.0, 1.0)
    val polar = interpolation == MorphInterpolation.Polar && polarInterpolation
    val angle = if (polar) theta * progress else 0.0
    val scale = if (polar) exp(logScale * progress) else 1.0
    val cosine = cos(angle) * scale * weight
    val sine = sin(angle) * scale * weight
    for (point in detail.left.indices) {
        val a = detail.left[point] * 2
        val b = detail.right[point] * 2
        val i = point * 2
        val fraction = detail.weights[point]
        val dx = detail.offsets[i]
        val dy = detail.offsets[i + 1]
        curves[i] = samples[a] + (samples[b] - samples[a]) * fraction + dx * cosine - dy * sine
        curves[i + 1] = samples[a + 1] + (samples[b + 1] - samples[a + 1]) * fraction + dx * sine + dy * cosine
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
