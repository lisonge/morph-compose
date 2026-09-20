package li.songe.morph.compose.internal

import li.songe.morph.compose.MorphFallbackReason
import li.songe.morph.compose.MorphRotationPreference
import li.songe.morph.compose.MorphStrokeCountStrategy
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.ln

private const val LengthWeight = 0.35
private const val ExactPermutationLimit = 8
private const val ExactInjectionLimit = 100_000.0

internal data class ContourPair(
    val source: SampledContour,
    val target: SampledContour,
    val polarInterpolation: Boolean,
    val fallbackReason: MorphFallbackReason? = null,
    val anchored: Boolean = false,
    val movingSpans: List<IntRange> = emptyList(),
    val decision: ContourDecision = ContourDecision(),
)

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

private fun bestPermutation(
    costs: Array<DoubleArray>,
    tieCosts: Array<DoubleArray>? = null,
): IntArray {
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
    if (tieCosts != null) {
        // Establish the true distance/length optimum first. Only numerical ties may change
        // pairing; otherwise direction preferences could move unrelated strokes across the icon.
        val limit = bestCost + 1e-9
        var bestTieCost = Double.POSITIVE_INFINITY
        fun breakTie(index: Int, cost: Double, tieCost: Double) {
            if (cost > limit || tieCost >= bestTieCost) return
            if (index == size) {
                bestTieCost = tieCost
                best = current.copyOf()
                return
            }
            for (candidate in index until size) {
                val swap = current[index]
                current[index] = current[candidate]
                current[candidate] = swap
                breakTie(index + 1, cost + costs[index][current[index]],
                    tieCost + tieCosts[index][current[index]])
                current[candidate] = current[index]
                current[index] = swap
            }
        }
        breakTie(0, 0.0, 0.0)
    }
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

private fun bestSurjection(costs: Array<DoubleArray>): IntArray {
    val large = costs.size
    val small = costs.first().size
    if (small.toDouble().let { exp(large * ln(it)) } > ExactInjectionLimit) {
        // Reserve an injective cover, then attach remaining strokes to their cheapest match.
        val cover = bestInjection(Array(small) { j -> DoubleArray(large) { i -> costs[i][j] } })
        return IntArray(large) { i -> costs[i].indices.minBy { costs[i][it] } }.also { result ->
            cover.forEachIndexed { j, i -> result[i] = j }
        }
    }
    val current = IntArray(large)
    val counts = IntArray(small)
    var best = current.copyOf()
    var bestCost = Double.POSITIVE_INFINITY
    fun assign(index: Int, cost: Double, covered: Int) {
        if (cost >= bestCost || small - covered > large - index) return
        if (index == large) {
            bestCost = cost
            best = current.copyOf()
            return
        }
        for (j in 0 until small) {
            current[index] = j
            counts[j]++
            assign(index + 1, cost + costs[index][j], covered + if (counts[j] == 1) 1 else 0)
            counts[j]--
        }
    }
    assign(0, 0.0, 0)
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
        stroke = contour.stroke?.copy(width = 0.0),
        featureWeights = contour.featureWeights.copyOf(),
    )
}

internal fun matchContours(
    source: List<SampledContour>,
    target: List<SampledContour>,
    rotationPreference: MorphRotationPreference,
    strokeCountStrategy: MorphStrokeCountStrategy,
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
                val tieCosts = if (role == MorphContourRole.Stroke && sourceByRole.size <= ExactPermutationLimit) {
                    Array(sourceByRole.size) { i ->
                        DoubleArray(targetByRole.size) { j ->
                            val alignment = alignPair(sourceByRole[i], targetByRole[j], rotationPreference)
                            rotationPreference.rank(alignment.theta) + alignment.residual +
                                RotationWeight * abs(alignment.theta) / PI
                        }
                    }
                } else null
                val permutation = bestPermutation(costMatrix(sourceByRole, targetByRole), tieCosts)
                sourceByRole.indices.forEach { index ->
                    pairs +=
                        ContourPair(
                            sourceByRole[index],
                            targetByRole[permutation[index]],
                            polarInterpolation = true,
                        )
                }
            }
            role == MorphContourRole.Stroke && strokeCountStrategy == MorphStrokeCountStrategy.SplitMerge -> {
                // Cover every smaller-side stroke, then split it as needed. Filled contours must
                // continue using injection/collapse because duplication changes their winding.
                val sourceIsSmaller = sourceByRole.size < targetByRole.size
                val small = if (sourceIsSmaller) sourceByRole else targetByRole
                val large = if (sourceIsSmaller) targetByRole else sourceByRole
                val costs = costMatrix(large, small)
                val assignment = bestSurjection(costs)
                large.indices.forEach { index ->
                    val a = small[assignment[index]]
                    val b = large[index]
                    pairs += if (sourceIsSmaller) ContourPair(a, b, true) else ContourPair(b, a, true)
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
