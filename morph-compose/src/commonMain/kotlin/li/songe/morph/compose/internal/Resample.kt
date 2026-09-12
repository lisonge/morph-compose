package li.songe.morph.compose.internal

import li.songe.morph.compose.MorphOptions

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.round

private data class CornerFeature(
    val segment: Int,
    val weight: Double,
)

private data class ResampledPath(
    val points: DoubleArray,
    val featureWeights: DoubleArray,
    val curveSource: CurveSource? = null,
)

private val gaussNodes =
    doubleArrayOf(
        0.18343464249564978,
        0.525532409916329,
        0.7966664774136267,
        0.9602898564975363,
    )

private val gaussWeights =
    doubleArrayOf(
        0.362683783378362,
        0.31370664587788727,
        0.22238103445337448,
        0.10122853629037626,
    )

private fun speed(points: DoubleArray, segment: Int, t: Double): Double {
    val index = 6 * segment
    val u = 1.0 - t
    val c0 = 3.0 * u * u
    val c1 = 6.0 * u * t
    val c2 = 3.0 * t * t
    val dx =
        c0 * (points[index + 2] - points[index]) +
            c1 * (points[index + 4] - points[index + 2]) +
            c2 * (points[index + 6] - points[index + 4])
    val dy =
        c0 * (points[index + 3] - points[index + 1]) +
            c1 * (points[index + 5] - points[index + 3]) +
            c2 * (points[index + 7] - points[index + 5])
    return hypot(dx, dy)
}

private fun segmentLength(
    points: DoubleArray,
    segment: Int,
    endT: Double = 1.0,
): Double {
    val half = endT / 2.0
    var sum = 0.0
    for (index in gaussNodes.indices) {
        val node = gaussNodes[index]
        sum +=
            gaussWeights[index] *
                (speed(points, segment, half + half * node) +
                    speed(points, segment, half - half * node))
    }
    return sum * half
}

private fun writePoint(
    points: DoubleArray,
    segment: Int,
    t: Double,
    output: DoubleArray,
    outputIndex: Int,
) {
    val index = 6 * segment
    val u = 1.0 - t
    val b0 = u * u * u
    val b1 = 3.0 * u * u * t
    val b2 = 3.0 * u * t * t
    val b3 = t * t * t
    output[outputIndex] =
        b0 * points[index] +
            b1 * points[index + 2] +
            b2 * points[index + 4] +
            b3 * points[index + 6]
    output[outputIndex + 1] =
        b0 * points[index + 1] +
            b1 * points[index + 3] +
            b2 * points[index + 5] +
            b3 * points[index + 7]
}

private fun tangent(
    points: DoubleArray,
    segment: Int,
    atEnd: Boolean,
): Pair<Double, Double>? {
    val index = 6 * segment
    val base = if (atEnd) index + 6 else index
    val sign = if (atEnd) -1.0 else 1.0
    val offsets = if (atEnd) intArrayOf(4, 2, 0) else intArrayOf(2, 4, 6)
    for (offset in offsets) {
        val dx = sign * (points[index + offset] - points[base])
        val dy = sign * (points[index + offset + 1] - points[base + 1])
        if (dx * dx + dy * dy > 1e-18) return dx to dy
    }
    return null
}

private fun detectCornerFeatures(path: CubicPath, threshold: Double): List<CornerFeature> {
    val points = path.points
    val segmentCount = path.segmentCount
    val active = (0 until segmentCount).filter { segmentLength(points, it) > 1e-9 }
    if (active.isEmpty()) return emptyList()
    val corners = mutableMapOf<Int, Double>()

    fun test(first: Int, second: Int) {
        val incoming = tangent(points, first, atEnd = true) ?: return
        val outgoing = tangent(points, second, atEnd = false) ?: return
        val angle =
            abs(
                atan2(
                    incoming.first * outgoing.second - incoming.second * outgoing.first,
                    incoming.first * outgoing.first + incoming.second * outgoing.second,
                ),
            )
        if (angle > threshold) {
            corners[second] = maxOf(corners[second] ?: 0.0, (angle / PI).coerceIn(0.0, 1.0))
        }
    }

    for (index in 0 until active.lastIndex) test(active[index], active[index + 1])
    if (path.closed && active.size > 1) test(active.last(), active.first())
    return corners.entries.sortedBy { it.key }.map { CornerFeature(it.key, it.value) }
}

internal fun detectCorners(path: CubicPath, threshold: Double): IntArray =
    detectCornerFeatures(path, threshold).map(CornerFeature::segment).toIntArray()

private fun invertLength(
    points: DoubleArray,
    segment: Int,
    targetLength: Double,
    fullLength: Double,
): Double {
    if (targetLength <= 0.0) return 0.0
    if (targetLength >= fullLength) return 1.0
    var low = 0.0
    var high = 1.0
    var t = targetLength / fullLength
    repeat(12) {
        val error = segmentLength(points, segment, t) - targetLength
        if (abs(error) < 1e-10 * fullLength + 1e-14) return t
        if (error > 0.0) high = t else low = t
        val derivative = speed(points, segment, t)
        val candidate = if (derivative > 1e-12) t - error / derivative else (low + high) / 2.0
        t = if (candidate > low && candidate < high) candidate else (low + high) / 2.0
    }
    return t
}

internal fun resamplePath(
    path: CubicPath,
    sampleCount: Int,
    cornerThreshold: Double,
): DoubleArray = resamplePathWithFeatures(path, sampleCount, cornerThreshold).points

private fun resamplePathWithFeatures(
    path: CubicPath,
    sampleCount: Int,
    cornerThreshold: Double,
): ResampledPath {
    val points = path.points
    val segmentCount = path.segmentCount
    val output = DoubleArray(sampleCount * 2)
    val featureWeights = DoubleArray(sampleCount)
    val positions = DoubleArray(sampleCount)

    fun fillDegenerate(): ResampledPath {
        for (index in 0 until sampleCount) {
            output[index * 2] = points[0]
            output[index * 2 + 1] = points[1]
        }
        return ResampledPath(output, featureWeights)
    }

    if (segmentCount < 1) return fillDegenerate()
    val lengths = DoubleArray(segmentCount)
    var totalLength = 0.0
    for (segment in 0 until segmentCount) {
        lengths[segment] = segmentLength(points, segment)
        totalLength += lengths[segment]
    }
    if (totalLength < 1e-12) return fillDegenerate()

    val cornerFeatures = detectCornerFeatures(path, cornerThreshold)
    val corners = cornerFeatures.map(CornerFeature::segment)
    val cornerWeights = cornerFeatures.associate { it.segment to it.weight }
    val anchors =
        if (path.closed) {
            if (corners.isEmpty()) listOf(0) else corners
        } else {
            (listOf(0) + corners + segmentCount).distinct().sorted()
        }
    val runs = mutableListOf<Pair<Int, Int>>()
    if (path.closed) {
        for (index in anchors.indices) {
            runs += anchors[index] to if (index < anchors.lastIndex) anchors[index + 1] else anchors.first() + segmentCount
        }
    } else {
        for (index in 0 until anchors.lastIndex) runs += anchors[index] to anchors[index + 1]
    }
    val runLengths =
        runs.map { (start, end) ->
            var length = 0.0
            for (segment in start until end) length += lengths[segment % segmentCount]
            length
        }
    val intervals = if (path.closed) sampleCount else sampleCount - 1
    require(runs.size <= intervals) {
        "sampleCount=$sampleCount is too small for ${runs.size} corner runs"
    }

    val runTotal = runLengths.sum().takeIf { it > 0.0 } ?: 1.0
    val ideal = runLengths.map { intervals * it / runTotal }
    val counts = ideal.map { maxOf(1, it.toInt()) }.toMutableList()
    var remainder = intervals - counts.sum()
    if (remainder > 0) {
        val order =
            ideal.indices.sortedWith(
                compareByDescending<Int> { round((ideal[it] - ideal[it].toInt()) * 1e9) }
                    .thenBy { it },
            )
        repeat(remainder) { counts[order[it % order.size]]++ }
    }
    while (remainder < 0) {
        val largest = counts.indices.maxBy { counts[it] }
        if (counts[largest] <= 1) break
        counts[largest]--
        remainder++
    }

    var writeIndex = 0
    for (runIndex in runs.indices) {
        val (start, end) = runs[runIndex]
        val count = counts[runIndex]
        val runLength = runLengths[runIndex]
        val vertexIndex = 6 * (start % segmentCount)
        output[writeIndex * 2] = points[vertexIndex]
        output[writeIndex * 2 + 1] = points[vertexIndex + 1]
        positions[writeIndex] = start.toDouble()
        featureWeights[writeIndex] = cornerWeights[start % segmentCount] ?: 0.0
        writeIndex++
        var segment = start
        var accumulated = 0.0
        for (pointIndex in 1 until count) {
            val target = runLength * pointIndex / count
            while (segment < end - 1 && accumulated + lengths[segment % segmentCount] < target) {
                accumulated += lengths[segment % segmentCount]
                segment++
            }
            val wrappedSegment = segment % segmentCount
            val length = lengths[wrappedSegment]
            val t =
                if (length > 1e-12) {
                    invertLength(points, wrappedSegment, target - accumulated, length)
                } else {
                    0.0
                }
            writePoint(points, wrappedSegment, t, output, writeIndex * 2)
            positions[writeIndex] = segment + t
            writeIndex++
        }
    }
    if (!path.closed) {
        val vertexIndex = 6 * segmentCount
        output[writeIndex * 2] = points[vertexIndex]
        output[writeIndex * 2 + 1] = points[vertexIndex + 1]
        positions[writeIndex] = segmentCount.toDouble()
    }
    return ResampledPath(output, featureWeights, CurveSource(path, positions))
}

internal fun resamplePaths(paths: List<CubicPath>, options: MorphOptions): List<SampledContour> =
    paths.map { path ->
        val sampled =
            resamplePathWithFeatures(path, options.sampleCount, options.cornerThresholdRadians)
        SampledContour(
            points = sampled.points,
            closed = path.closed,
            role = path.role,
            featureWeights = sampled.featureWeights,
            curveSource = sampled.curveSource,
        )
    }
