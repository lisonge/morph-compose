package li.songe.morph.compose.internal

import li.songe.morph.compose.MorphRotationPreference
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.sqrt

internal const val RotationWeight = 0.05
private const val CornerFeatureWeight = 0.16
private const val EdgeDirectionWeight = 0.08
private const val AlignmentQualityTieTolerance = 1e-3
private const val SelfIntersectionWeight = 0.12
internal const val MinimumPolarScale = 1e-6
private const val RotationEpsilon = 1e-6

private val alignmentCheckProgress = doubleArrayOf(0.25, 0.5, 0.75)

internal data class Center(val x: Double, val y: Double)

internal data class Similarity(
    val theta: Double,
    val scale: Double,
    val residual: Double,
)

internal data class Alignment(
    val sourceCenter: Center,
    val targetCenter: Center,
    val source: DoubleArray,
    val sourceFeatureWeights: DoubleArray,
    val target: DoubleArray,
    val targetFeatureWeights: DoubleArray,
    val theta: Double,
    val scale: Double,
    val residual: Double,
)

private data class AlignmentCandidate(
    val direction: Int,
    val offset: Int,
    val similarity: Similarity,
    val qualityScore: Double,
    val tieBreakScore: Double,
    val foldCount: Int,
)

// Screen coordinates have a downward y axis, so positive angles rotate clockwise.
internal fun MorphRotationPreference.rank(theta: Double): Int =
    when {
        this == MorphRotationPreference.Auto -> 0
        abs(theta) <= RotationEpsilon -> 1
        (theta > 0.0) == (this == MorphRotationPreference.PreferClockwise) -> 0
        else -> 2
    }

internal fun centroid(points: DoubleArray): Center {
    var x = 0.0
    var y = 0.0
    val count = points.size / 2
    for (index in 0 until count) {
        x += points[index * 2]
        y += points[index * 2 + 1]
    }
    return Center(x / count, y / count)
}

internal fun polylineLength(points: DoubleArray): Double {
    var length = 0.0
    for (index in 1 until points.size / 2) {
        length +=
            hypot(
                points[index * 2] - points[index * 2 - 2],
                points[index * 2 + 1] - points[index * 2 - 1],
            )
    }
    return length
}

private fun reversePoints(points: DoubleArray): DoubleArray {
    val count = points.size / 2
    return DoubleArray(points.size).also { output ->
        for (index in 0 until count) {
            output[index * 2] = points[(count - 1 - index) * 2]
            output[index * 2 + 1] = points[(count - 1 - index) * 2 + 1]
        }
    }
}

private fun rotatePoints(points: DoubleArray, offset: Int): DoubleArray {
    val count = points.size / 2
    return DoubleArray(points.size).also { output ->
        for (index in 0 until count) {
            val sourceIndex = (index + offset) % count
            output[index * 2] = points[sourceIndex * 2]
            output[index * 2 + 1] = points[sourceIndex * 2 + 1]
        }
    }
}

private fun reverseValues(values: DoubleArray): DoubleArray =
    DoubleArray(values.size) { index -> values[values.lastIndex - index] }

private fun rotateValues(values: DoubleArray, offset: Int): DoubleArray =
    DoubleArray(values.size) { index -> values[(index + offset) % values.size] }

private fun featureMismatch(source: DoubleArray, target: DoubleArray): Double {
    var mismatch = 0.0
    var totalWeight = 0.0
    for (index in source.indices) {
        mismatch += abs(source[index] - target[index])
        totalWeight += maxOf(source[index], target[index])
    }
    return if (totalWeight > 1e-12) mismatch / totalWeight else 0.0
}

private fun edgeDirectionMismatch(
    source: DoubleArray,
    target: DoubleArray,
    closed: Boolean,
    theta: Double,
): Double {
    val pointCount = source.size / 2
    val edgeCount = if (closed) pointCount else pointCount - 1
    val cosine = cos(theta)
    val sine = sin(theta)
    var mismatch = 0.0
    var totalWeight = 0.0
    for (index in 0 until edgeCount) {
        val next = (index + 1) % pointCount
        val sourceX = source[next * 2] - source[index * 2]
        val sourceY = source[next * 2 + 1] - source[index * 2 + 1]
        val targetX = target[next * 2] - target[index * 2]
        val targetY = target[next * 2 + 1] - target[index * 2 + 1]
        val sourceLength = hypot(sourceX, sourceY)
        val targetLength = hypot(targetX, targetY)
        if (sourceLength <= 1e-12 || targetLength <= 1e-12) continue

        val rotatedSourceX = sourceX * cosine - sourceY * sine
        val rotatedSourceY = sourceX * sine + sourceY * cosine
        val dot =
            ((rotatedSourceX * targetX + rotatedSourceY * targetY) /
                (sourceLength * targetLength)).coerceIn(-1.0, 1.0)
        val weight = sqrt(sourceLength * targetLength)
        mismatch += (1.0 - dot) * 0.5 * weight
        totalWeight += weight
    }
    return if (totalWeight > 1e-12) mismatch / totalWeight else 0.0
}

private fun properIntersectionCount(points: DoubleArray): Int {
    val pointCount = points.size / 2
    var intersections = 0

    fun side(
        ax: Double,
        ay: Double,
        bx: Double,
        by: Double,
        px: Double,
        py: Double,
    ): Double = (bx - ax) * (py - ay) - (by - ay) * (px - ax)

    for (first in 0 until pointCount) {
        val firstNext = (first + 1) % pointCount
        for (second in first + 1 until pointCount) {
            val secondNext = (second + 1) % pointCount
            if (first == secondNext || firstNext == second) continue
            val ax = points[first * 2]
            val ay = points[first * 2 + 1]
            val bx = points[firstNext * 2]
            val by = points[firstNext * 2 + 1]
            val cx = points[second * 2]
            val cy = points[second * 2 + 1]
            val dx = points[secondNext * 2]
            val dy = points[secondNext * 2 + 1]
            val firstC = side(ax, ay, bx, by, cx, cy)
            val firstD = side(ax, ay, bx, by, dx, dy)
            val secondA = side(cx, cy, dx, dy, ax, ay)
            val secondB = side(cx, cy, dx, dy, bx, by)
            if (firstC * firstD < -1e-12 && secondA * secondB < -1e-12) {
                intersections++
            }
        }
    }
    return intersections
}

private fun interpolationFoldCount(
    source: DoubleArray,
    target: DoubleArray,
    sourceCenter: Center,
    targetCenter: Center,
    similarity: Similarity,
    closed: Boolean,
): Int {
    if (!closed) return 0
    val linear = DoubleArray(source.size)
    val polarLocal = DoubleArray(source.size)
    val inverseCosine = cos(-similarity.theta)
    val inverseSine = sin(-similarity.theta)
    var folds = 0
    for (progress in alignmentCheckProgress) {
        for (index in 0 until source.size / 2) {
            val arrayIndex = index * 2
            val sourceX = source[arrayIndex]
            val sourceY = source[arrayIndex + 1]
            val targetX = target[arrayIndex]
            val targetY = target[arrayIndex + 1]
            linear[arrayIndex] = sourceX + (targetX - sourceX) * progress
            linear[arrayIndex + 1] = sourceY + (targetY - sourceY) * progress

            val centeredTargetX = targetX - targetCenter.x
            val centeredTargetY = targetY - targetCenter.y
            val targetLocalX =
                (centeredTargetX * inverseCosine - centeredTargetY * inverseSine) /
                    similarity.scale
            val targetLocalY =
                (centeredTargetX * inverseSine + centeredTargetY * inverseCosine) /
                    similarity.scale
            val sourceLocalX = sourceX - sourceCenter.x
            val sourceLocalY = sourceY - sourceCenter.y
            polarLocal[arrayIndex] =
                sourceLocalX + (targetLocalX - sourceLocalX) * progress
            polarLocal[arrayIndex + 1] =
                sourceLocalY + (targetLocalY - sourceLocalY) * progress
        }
        folds += properIntersectionCount(linear)
        folds += properIntersectionCount(polarLocal)
    }
    return folds
}

internal fun procrustes(
    source: DoubleArray,
    target: DoubleArray,
    sourceCenter: Center,
    targetCenter: Center,
): Similarity {
    var sxx = 0.0
    var sxy = 0.0
    var syx = 0.0
    var syy = 0.0
    var sourceEnergy = 0.0
    var targetEnergy = 0.0
    for (index in 0 until source.size / 2) {
        val ax = source[index * 2] - sourceCenter.x
        val ay = source[index * 2 + 1] - sourceCenter.y
        val bx = target[index * 2] - targetCenter.x
        val by = target[index * 2 + 1] - targetCenter.y
        sxx += ax * bx
        sxy += ax * by
        syx += ay * bx
        syy += ay * by
        sourceEnergy += ax * ax + ay * ay
        targetEnergy += bx * bx + by * by
    }
    val theta = atan2(sxy - syx, sxx + syy)
    val numerator = cos(theta) * (sxx + syy) + sin(theta) * (sxy - syx)
    var scale = if (sourceEnergy > 1e-12) numerator / sourceEnergy else 1.0
    if (scale <= MinimumPolarScale || !scale.isFinite()) scale = MinimumPolarScale
    val squaredResidual =
        maxOf(0.0, scale * scale * sourceEnergy - 2.0 * scale * numerator + targetEnergy)
    val residual = if (targetEnergy > 1e-12) sqrt(squaredResidual / targetEnergy) else 0.0
    return Similarity(theta, scale, residual)
}

internal fun alignPair(
    source: SampledContour,
    target: SampledContour,
    rotationPreference: MorphRotationPreference,
): Alignment {
    val sourceCenter = centroid(source.points)
    val targetCenter = centroid(target.points)
    // Identity is a contract, not a candidate to trade against fold/feature scores. A contour
    // can already self-intersect; minimizing those folds must not animate it into another shape.
    if (source.points.contentEquals(target.points) && source.featureWeights.contentEquals(target.featureWeights)) return Alignment(
        sourceCenter, targetCenter, source.points, source.featureWeights,
        target.points, target.featureWeights, 0.0, 1.0, 0.0,
    )
    val varySource = source.closed && !target.closed
    val base = if (varySource) source.points else target.points
    val baseFeatureWeights =
        if (varySource) source.featureWeights else target.featureWeights
    val offsets = if (source.closed || target.closed) base.size / 2 else 1
    val canReverse =
        if (varySource) {
            source.role == MorphContourRole.Stroke
        } else {
            target.role == MorphContourRole.Stroke
        }
    val directions = if (canReverse || (!source.closed && !target.closed)) 2 else 1
    var lowestQualityScore = Double.POSITIVE_INFINITY
    var bestTieBreakScore = Double.POSITIVE_INFINITY
    var selectedQualityScore = Double.POSITIVE_INFINITY
    var best = base
    var bestFeatureWeights = baseFeatureWeights
    var bestSimilarity = Similarity(theta = 0.0, scale = 1.0, residual = 0.0)
    var bestFoldCount = 0
    val candidates =
        if (rotationPreference == MorphRotationPreference.Auto) null
        else mutableListOf<AlignmentCandidate>()

    for (direction in 0 until directions) {
        val walked = if (direction == 1) reversePoints(base) else base
        val walkedFeatureWeights =
            if (direction == 1) reverseValues(baseFeatureWeights) else baseFeatureWeights
        for (offset in 0 until offsets) {
            val candidate = if (offset == 0) walked else rotatePoints(walked, offset)
            val candidateFeatureWeights =
                if (offset == 0) walkedFeatureWeights else rotateValues(walkedFeatureWeights, offset)
            val alignedSource = if (varySource) candidate else source.points
            val alignedSourceFeatures =
                if (varySource) candidateFeatureWeights else source.featureWeights
            val alignedTarget = if (varySource) target.points else candidate
            val alignedTargetFeatures =
                if (varySource) target.featureWeights else candidateFeatureWeights
            val similarity =
                procrustes(alignedSource, alignedTarget, sourceCenter, targetCenter)
            val foldCount =
                interpolationFoldCount(
                    alignedSource,
                    alignedTarget,
                    sourceCenter,
                    targetCenter,
                    similarity,
                    source.closed && target.closed,
                )
            val qualityScore = similarity.residual + SelfIntersectionWeight * foldCount
            val tieBreakScore =
                CornerFeatureWeight *
                    featureMismatch(alignedSourceFeatures, alignedTargetFeatures) +
                    EdgeDirectionWeight *
                    edgeDirectionMismatch(
                        alignedSource,
                        alignedTarget,
                        source.closed && target.closed,
                        similarity.theta,
                    ) +
                    RotationWeight * abs(similarity.theta) / PI
            candidates?.add(
                AlignmentCandidate(
                    direction, offset, similarity, qualityScore, tieBreakScore, foldCount,
                ),
            )
            lowestQualityScore = minOf(lowestQualityScore, qualityScore)
            val selectedCandidateIsOutsideTie =
                selectedQualityScore > lowestQualityScore + AlignmentQualityTieTolerance
            val candidateIsInsideTie =
                qualityScore <= lowestQualityScore + AlignmentQualityTieTolerance
            if (
                selectedCandidateIsOutsideTie ||
                (candidateIsInsideTie && tieBreakScore < bestTieBreakScore)
            ) {
                best = candidate
                bestFeatureWeights = candidateFeatureWeights
                bestSimilarity = similarity
                bestTieBreakScore = tieBreakScore
                selectedQualityScore = qualityScore
                bestFoldCount = foldCount
            }
        }
    }
    // Keep Auto's result for an exact non-rotating similarity, including an icon morphing to itself.
    // Select only after the global minimum is known, so enumeration order cannot admit a poorer fit.
    if (candidates != null &&
        !(abs(bestSimilarity.theta) <= RotationEpsilon && bestSimilarity.residual <= RotationEpsilon)
    ) {
        val preferred = candidates
            .filter {
                it.qualityScore <= lowestQualityScore + AlignmentQualityTieTolerance &&
                    it.foldCount <= bestFoldCount
            }
            .minWithOrNull(
                compareBy<AlignmentCandidate> { rotationPreference.rank(it.similarity.theta) }
                    .thenBy { it.tieBreakScore },
            )
        if (preferred != null) {
            // Retain candidate metadata only; keeping each rotated array would use quadratic memory.
            val walked = if (preferred.direction == 1) reversePoints(base) else base
            val walkedFeatures =
                if (preferred.direction == 1) reverseValues(baseFeatureWeights) else baseFeatureWeights
            best = if (preferred.offset == 0) walked else rotatePoints(walked, preferred.offset)
            bestFeatureWeights =
                if (preferred.offset == 0) walkedFeatures else rotateValues(walkedFeatures, preferred.offset)
            bestSimilarity = preferred.similarity
        }
    }
    return if (varySource) {
        Alignment(
            sourceCenter,
            targetCenter,
            best,
            bestFeatureWeights,
            target.points,
            target.featureWeights,
            bestSimilarity.theta,
            bestSimilarity.scale,
            bestSimilarity.residual,
        )
    } else {
        Alignment(
            sourceCenter,
            targetCenter,
            source.points,
            source.featureWeights,
            best,
            bestFeatureWeights,
            bestSimilarity.theta,
            bestSimilarity.scale,
            bestSimilarity.residual,
        )
    }
}
