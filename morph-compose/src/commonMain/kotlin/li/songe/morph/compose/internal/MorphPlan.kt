package li.songe.morph.compose.internal

import li.songe.morph.compose.MorphFallbackReason
import li.songe.morph.compose.MorphOptions
import li.songe.morph.compose.MorphRotationPreference
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.ln
import kotlin.math.sin
import kotlin.math.sqrt

private const val LengthWeight = 0.35
private const val RotationWeight = 0.05
private const val CornerFeatureWeight = 0.16
private const val EdgeDirectionWeight = 0.08
private const val AlignmentQualityTieTolerance = 1e-3
private const val SelfIntersectionWeight = 0.12
private const val GlobalResidualThreshold = 5e-3
private const val ExactPermutationLimit = 8
private const val ExactInjectionLimit = 100_000.0
private const val MinimumPolarScale = 1e-6
private const val RotationEpsilon = 1e-6

private val alignmentCheckProgress = doubleArrayOf(0.25, 0.5, 0.75)

private data class Center(val x: Double, val y: Double)

private data class Similarity(
    val theta: Double,
    val scale: Double,
    val residual: Double,
)

private data class Alignment(
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

private data class ContourPair(
    val source: SampledContour,
    val target: SampledContour,
    val polarInterpolation: Boolean,
    val fallbackReason: MorphFallbackReason? = null,
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
private fun MorphRotationPreference.rank(theta: Double): Int =
    when {
        this == MorphRotationPreference.Auto -> 0
        abs(theta) <= RotationEpsilon -> 1
        (theta > 0.0) == (this == MorphRotationPreference.PreferClockwise) -> 0
        else -> 2
    }

private fun centroid(points: DoubleArray): Center {
    var x = 0.0
    var y = 0.0
    val count = points.size / 2
    for (index in 0 until count) {
        x += points[index * 2]
        y += points[index * 2 + 1]
    }
    return Center(x / count, y / count)
}

private fun polylineLength(points: DoubleArray): Double {
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

private fun procrustes(
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

private fun alignPair(
    source: SampledContour,
    target: SampledContour,
    rotationPreference: MorphRotationPreference,
): Alignment {
    val sourceCenter = centroid(source.points)
    val targetCenter = centroid(target.points)
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

private fun costMatrix(source: List<SampledContour>, target: List<SampledContour>): Array<DoubleArray> {
    val targetCenters = target.map { centroid(it.points) }
    val targetLengths = target.map { polylineLength(it.points) }
    return Array(source.size) { sourceIndex ->
        val sourceCenter = centroid(source[sourceIndex].points)
        val sourceLength = polylineLength(source[sourceIndex].points)
        DoubleArray(target.size) { targetIndex ->
            val targetCenter = targetCenters[targetIndex]
            hypot(sourceCenter.x - targetCenter.x, sourceCenter.y - targetCenter.y) +
                LengthWeight * abs(sourceLength - targetLengths[targetIndex])
        }
    }
}

private fun bestPermutation(costs: Array<DoubleArray>): IntArray {
    val size = costs.size
    if (size > ExactPermutationLimit) {
        val candidates = mutableListOf<Triple<Double, Int, Int>>()
        for (source in 0 until size) {
            for (target in 0 until size) candidates += Triple(costs[source][target], source, target)
        }
        candidates.sortBy { it.first }
        val result = IntArray(size) { -1 }
        val used = BooleanArray(size)
        for ((_, source, target) in candidates) {
            if (result[source] < 0 && !used[target]) {
                result[source] = target
                used[target] = true
            }
        }
        return result
    }

    val current = IntArray(size) { it }
    var best = current.copyOf()
    var bestCost = Double.POSITIVE_INFINITY

    fun permute(index: Int, accumulated: Double) {
        if (accumulated >= bestCost) return
        if (index == size) {
            bestCost = accumulated
            best = current.copyOf()
            return
        }
        for (candidate in index until size) {
            val temporary = current[index]
            current[index] = current[candidate]
            current[candidate] = temporary
            permute(index + 1, accumulated + costs[index][current[index]])
            val restored = current[index]
            current[index] = current[candidate]
            current[candidate] = restored
        }
    }

    permute(0, 0.0)
    return best
}

private fun bestInjection(costs: Array<DoubleArray>): IntArray {
    val rowCount = costs.size
    val columnCount = costs.first().size
    require(rowCount <= columnCount) { "An injection requires at least as many columns as rows" }
    var candidateCount = 1.0
    repeat(rowCount) { index -> candidateCount *= columnCount - index }
    if (candidateCount > ExactInjectionLimit) {
        val candidates = mutableListOf<Triple<Double, Int, Int>>()
        for (row in 0 until rowCount) {
            for (column in 0 until columnCount) {
                candidates += Triple(costs[row][column], row, column)
            }
        }
        candidates.sortBy { it.first }
        val result = IntArray(rowCount) { -1 }
        val usedColumns = BooleanArray(columnCount)
        for ((_, row, column) in candidates) {
            if (result[row] < 0 && !usedColumns[column]) {
                result[row] = column
                usedColumns[column] = true
            }
        }
        check(result.none { it < 0 }) { "Unable to build a complete contour injection" }
        return result
    }

    val current = IntArray(rowCount)
    val usedColumns = BooleanArray(columnCount)
    var best = IntArray(rowCount)
    var bestCost = Double.POSITIVE_INFINITY

    fun assign(row: Int, accumulated: Double) {
        if (accumulated >= bestCost) return
        if (row == rowCount) {
            bestCost = accumulated
            current.copyInto(best)
            return
        }
        for (column in 0 until columnCount) {
            if (usedColumns[column]) continue
            current[row] = column
            usedColumns[column] = true
            assign(row + 1, accumulated + costs[row][column])
            usedColumns[column] = false
        }
    }

    assign(row = 0, accumulated = 0.0)
    return best
}

private fun collapsedContour(contour: SampledContour): SampledContour {
    val center = centroid(contour.points)
    return SampledContour(
        points =
            DoubleArray(contour.points.size) { index ->
                if (index % 2 == 0) center.x else center.y
            },
        closed = contour.closed,
        role = contour.role,
        featureWeights = contour.featureWeights.copyOf(),
    )
}

private fun matchContours(
    source: List<SampledContour>,
    target: List<SampledContour>,
): List<ContourPair> {
    val pairs = mutableListOf<ContourPair>()
    for (role in MorphContourRole.entries) {
        val sourceByRole = source.filter { it.role == role }
        val targetByRole = target.filter { it.role == role }
        when {
            sourceByRole.isEmpty() ->
                targetByRole.forEach { contour ->
                    pairs +=
                        ContourPair(
                            collapsedContour(contour),
                            contour,
                            polarInterpolation = false,
                            fallbackReason = MorphFallbackReason.MissingSourceContour,
                        )
                }
            targetByRole.isEmpty() ->
                sourceByRole.forEach { contour ->
                    pairs +=
                        ContourPair(
                            contour,
                            collapsedContour(contour),
                            polarInterpolation = false,
                            fallbackReason = MorphFallbackReason.MissingTargetContour,
                        )
                }
            sourceByRole.size == targetByRole.size -> {
                val permutation = bestPermutation(costMatrix(sourceByRole, targetByRole))
                sourceByRole.indices.forEach { index ->
                    pairs +=
                        ContourPair(
                            sourceByRole[index],
                            targetByRole[permutation[index]],
                            polarInterpolation = true,
                        )
                }
            }
            sourceByRole.size < targetByRole.size -> {
                val assignment = bestInjection(costMatrix(sourceByRole, targetByRole))
                val matchedTargets = BooleanArray(targetByRole.size)
                sourceByRole.indices.forEach { sourceIndex ->
                    val targetIndex = assignment[sourceIndex]
                    matchedTargets[targetIndex] = true
                    pairs +=
                        ContourPair(
                            sourceByRole[sourceIndex],
                            targetByRole[targetIndex],
                            polarInterpolation = true,
                        )
                }
                targetByRole.indices.filterNot { matchedTargets[it] }.forEach { targetIndex ->
                    val contour = targetByRole[targetIndex]
                    pairs +=
                        ContourPair(
                            collapsedContour(contour),
                            contour,
                            polarInterpolation = false,
                            fallbackReason = MorphFallbackReason.MissingSourceContour,
                        )
                }
            }
            else -> {
                val assignment = bestInjection(costMatrix(targetByRole, sourceByRole))
                val targetForSource = IntArray(sourceByRole.size) { -1 }
                targetByRole.indices.forEach { targetIndex ->
                    targetForSource[assignment[targetIndex]] = targetIndex
                }
                sourceByRole.indices.forEach { sourceIndex ->
                    val targetIndex = targetForSource[sourceIndex]
                    val contour = sourceByRole[sourceIndex]
                    if (targetIndex >= 0) {
                        pairs +=
                            ContourPair(
                                contour,
                                targetByRole[targetIndex],
                                polarInterpolation = true,
                            )
                    } else {
                        pairs +=
                            ContourPair(
                                contour,
                                collapsedContour(contour),
                                polarInterpolation = false,
                                fallbackReason = MorphFallbackReason.MissingTargetContour,
                            )
                    }
                }
            }
        }
    }
    return pairs
}

private fun applyGlobalAlignment(
    items: List<PlanItem>,
    sampleCount: Int,
    rotationPreference: MorphRotationPreference,
) {
    val totalPoints = items.size * sampleCount
    val allSource = DoubleArray(totalPoints * 2)
    val allTarget = DoubleArray(totalPoints * 2)
    items.forEachIndexed { index, item ->
        item.source.copyInto(allSource, index * sampleCount * 2)
        item.targetOriented.copyInto(allTarget, index * sampleCount * 2)
    }
    val globalSourceCenter = centroid(allSource)
    val global = procrustes(allSource, allTarget, globalSourceCenter, centroid(allTarget))
    if (global.residual >= GlobalResidualThreshold) return
    // Global block transport must not undo a contour's chosen direction.
    if (items.any { rotationPreference.rank(global.theta) > rotationPreference.rank(it.theta) }) return

    val inverseCosine = cos(-global.theta)
    val inverseSine = sin(-global.theta)
    val rotationCosine = cos(global.theta)
    val rotationSine = sin(global.theta)
    for (item in items) {
        var squaredError = 0.0
        var targetEnergy = 0.0
        for (index in 0 until sampleCount) {
            val bx = item.targetOriented[index * 2] - item.targetCenterX
            val by = item.targetOriented[index * 2 + 1] - item.targetCenterY
            item.targetLocal[index * 2] =
                (bx * inverseCosine - by * inverseSine) / global.scale
            item.targetLocal[index * 2 + 1] =
                (bx * inverseSine + by * inverseCosine) / global.scale
            val errorX =
                global.scale *
                    (rotationCosine * item.sourceCentered[index * 2] -
                        rotationSine * item.sourceCentered[index * 2 + 1]) - bx
            val errorY =
                global.scale *
                    (rotationSine * item.sourceCentered[index * 2] +
                        rotationCosine * item.sourceCentered[index * 2 + 1]) - by
            squaredError += errorX * errorX + errorY * errorY
            targetEnergy += bx * bx + by * by
        }
        item.theta = global.theta
        item.logScale = ln(global.scale)
        item.residual = if (targetEnergy > 1e-12) sqrt(squaredError / targetEnergy) else 0.0

        val scale = exp(item.logScale)
        val cosine = cos(item.theta) * scale
        val sine = sin(item.theta) * scale
        val offsetX = item.sourceCenterX - globalSourceCenter.x
        val offsetY = item.sourceCenterY - globalSourceCenter.y
        val rotatedX = offsetX * cosine - offsetY * sine - offsetX
        val rotatedY = offsetX * sine + offsetY * cosine - offsetY
        item.blockTransport =
            BlockTransport(
                offsetX = offsetX,
                offsetY = offsetY,
                driftX = item.targetCenterX - item.sourceCenterX - rotatedX,
                driftY = item.targetCenterY - item.sourceCenterY - rotatedY,
            )
    }
}

/** Builds a reusable polar morph plan from two lists of normalized cubic paths. */
internal fun buildMorphPlan(
    from: List<CubicPath>,
    to: List<CubicPath>,
    options: MorphOptions = MorphOptions(),
): MorphPlan {
    require(from.isNotEmpty() && to.isNotEmpty()) { "Both vectors must contain drawable contours" }
    return buildSampledMorphPlan(resamplePaths(from, options), resamplePaths(to, options), options)
}

/** Builds from an exact sampled source so an interrupted animation keeps its rendered geometry. */
internal fun buildMorphPlanFromSampledSource(
    from: List<SampledContour>,
    to: List<CubicPath>,
    options: MorphOptions,
): MorphPlan {
    require(from.isNotEmpty() && to.isNotEmpty()) { "Both vectors must contain drawable contours" }
    require(from.all { it.points.size == options.sampleCount * 2 }) {
        "Sampled source contours must match MorphOptions.sampleCount"
    }
    return buildSampledMorphPlan(from, resamplePaths(to, options), options)
}

internal fun buildSampledMorphPlan(
    source: List<SampledContour>,
    target: List<SampledContour>,
    options: MorphOptions,
): MorphPlan {
    val pairs = matchContours(source, target)

    val items =
        pairs.map { pair ->
            val sourceContour = pair.source
            val targetContour = pair.target
            val alignment = alignPair(sourceContour, targetContour, options.rotationPreference)
            val polarInterpolation =
                pair.polarInterpolation && alignment.scale > MinimumPolarScale
            val fallbackReason =
                pair.fallbackReason
                    ?: if (polarInterpolation) null else MorphFallbackReason.DegenerateSimilarity
            val interpolationScale = if (polarInterpolation) alignment.scale else 1.0
            val sourceCentered = DoubleArray(options.sampleCount * 2)
            val targetLocal = DoubleArray(options.sampleCount * 2)
            val targetOriented = DoubleArray(options.sampleCount * 2)
            val inverseCosine = cos(-alignment.theta)
            val inverseSine = sin(-alignment.theta)
            for (index in 0 until options.sampleCount) {
                sourceCentered[index * 2] = alignment.source[index * 2] - alignment.sourceCenter.x
                sourceCentered[index * 2 + 1] =
                    alignment.source[index * 2 + 1] - alignment.sourceCenter.y
                val targetX = alignment.target[index * 2] - alignment.targetCenter.x
                val targetY = alignment.target[index * 2 + 1] - alignment.targetCenter.y
                targetLocal[index * 2] =
                    (targetX * inverseCosine - targetY * inverseSine) / interpolationScale
                targetLocal[index * 2 + 1] =
                    (targetX * inverseSine + targetY * inverseCosine) / interpolationScale
                targetOriented[index * 2] = alignment.target[index * 2]
                targetOriented[index * 2 + 1] = alignment.target[index * 2 + 1]
            }
            PlanItem(
                source = alignment.source,
                sourceFeatureWeights = alignment.sourceFeatureWeights,
                sourceCentered = sourceCentered,
                targetLocal = targetLocal,
                targetOriented = targetOriented,
                targetFeatureWeights = alignment.targetFeatureWeights,
                sourceCenterX = alignment.sourceCenter.x,
                sourceCenterY = alignment.sourceCenter.y,
                targetCenterX = alignment.targetCenter.x,
                targetCenterY = alignment.targetCenter.y,
                theta = alignment.theta,
                logScale = ln(interpolationScale),
                residual = alignment.residual,
                closed = sourceContour.closed && targetContour.closed,
                role = sourceContour.role,
                polarInterpolation = polarInterpolation,
                fallbackReason = fallbackReason,
                blockTransport = null,
            )
        }
    val polarItems = items.filter(PlanItem::polarInterpolation)
    if (polarItems.size > 1) {
        applyGlobalAlignment(polarItems, options.sampleCount, options.rotationPreference)
    }
    items.forEachIndexed { index, item -> item.retainCurves(pairs[index].source, pairs[index].target) }
    return MorphPlan(items, options.sampleCount)
}
