package li.songe.morph.compose

import li.songe.morph.compose.internal.CubicPath
import li.songe.morph.compose.internal.MorphPlan
import li.songe.morph.compose.internal.SampledContour
import li.songe.morph.compose.internal.buildMorphPlan
import li.songe.morph.compose.internal.buildMorphPlanFromSampledSource
import kotlin.math.abs
import kotlin.math.hypot

internal data class InferenceMotionQuality(
    val extraIntersections: Int,
    val maxJunctionGap: Double,
    val stretch: Double,
    val residual: Double,
    val scaleChange: Double,
    val decompositionBends: Int = 0,
) {
    // Hidden elbows at a filled crossing can become visible during motion. A tiny residual
    // improvement should not buy a more complicated interpretation of the original ink.
    val score: Double get() = residual + stretch * 0.5 + maxJunctionGap * 2 + scaleChange * 0.05 + decompositionBends * 0.005
}

internal data class InferenceSelection(
    val plan: MorphPlan,
    val quality: InferenceMotionQuality,
    val baselineQuality: InferenceMotionQuality,
    val consideredPairs: Int,
    val sourceCandidate: Int,
    val targetCandidate: Int,
)

/** Geometry-derived alternatives only. No catalog identity or vector names enter this stage. */
internal fun selectInferredPlan(
    from: List<CubicPath>, to: List<CubicPath>, options: MorphOptions,
    snapshot: List<SampledContour>? = null,
): InferenceSelection? {
    if (options.transitionMode != MorphTransitionMode.ExperimentalStrokeInference) return null
    if ((from + to).all { it.stroke != null }) return null
    if (snapshot != null && from.any { it.stroke == null }) return null
    if (from.size == to.size && from.indices.all { from[it].role == to[it].role && from[it].stroke == to[it].stroke &&
            from[it].points.contentEquals(to[it].points) }) return null
    fun candidates(paths: List<CubicPath>): List<StrokeInference.Candidate?> =
        if (paths.all { it.stroke != null }) { if (paths.size <= 6) listOf(null) else emptyList() }
        else StrokeInference.candidates(paths, options)
    val sources = candidates(from); val targets = candidates(to)
    if (sources.isEmpty() || targets.isEmpty()) return null
    data class Evaluated(val plan: MorphPlan, val quality: InferenceMotionQuality, val source: Int, val target: Int)
    val evaluated = mutableListOf<Evaluated>()
    for ((i, source) in sources.withIndex()) for ((j, target) in targets.withIndex()) {
        val plan = if (snapshot == null) buildMorphPlan(source?.paths ?: from, target?.paths ?: to, options)
            else buildMorphPlanFromSampledSource(snapshot, target?.paths ?: to, options)
        val quality = inferenceMotionQuality(plan)?.copy(decompositionBends = (source?.bends ?: 0) + (target?.bends ?: 0)) ?: continue
        evaluated += Evaluated(plan, quality, i, j)
    }
    val baseline = evaluated.firstOrNull { it.source == 0 && it.target == 0 } ?: return null
    val fewestIntersections = evaluated.minOf { it.quality.extraIntersections }
    val safe = evaluated.filter { it.quality.extraIntersections == fewestIntersections }
    val bestScore = safe.minOf { it.quality.score }
    fun directionCost(plan: MorphPlan): Int = plan.items.count {
        when (options.rotationPreference) {
            MorphRotationPreference.Auto -> false
            MorphRotationPreference.PreferClockwise -> it.theta < -1e-8
            MorphRotationPreference.PreferCounterClockwise -> it.theta > 1e-8
        }
    }
    // Direction breaks quality ties; it cannot select more intersections or an arbitrarily worse fit.
    val best = safe.filter { it.quality.score <= bestScore + 1e-3 }.minWith(
        compareBy<Evaluated> { directionCost(it.plan) }
            .thenBy { (sources[it.source]?.difference?.symmetricDifferenceRatio ?: 0.0) +
                (targets[it.target]?.difference?.symmetricDifferenceRatio ?: 0.0) }
            .thenBy { (sources[it.source]?.bends ?: 0) + (targets[it.target]?.bends ?: 0) }
            .thenBy { it.plan.items.sumOf { item -> abs(item.theta) } }
            .thenBy { it.source }.thenBy { it.target },
    )
    fun describe(candidate: StrokeInference.Candidate?): String = if (candidate == null) "explicit strokes"
        else "added=${candidate.difference.addedArea / candidate.difference.referenceArea}, " +
            "missing=${candidate.difference.missingArea / candidate.difference.referenceArea}, " +
            "boundary/width<=${candidate.boundaryError / candidate.width}"
    val detail = "Candidates=${sources.size}x${targets.size}; selected=${best.source + 1}/${best.target + 1}; " +
        "source(${describe(sources[best.source])}); target(${describe(targets[best.target])}); " +
        "motion score=${best.quality.score}, baseline=${baseline.quality.score}, " +
        "extra crossings=${best.quality.extraIntersections}, junction gap/width=${best.quality.maxJunctionGap}, " +
        "stretch=${best.quality.stretch}, decomposition bends=${best.quality.decompositionBends}. "
    return InferenceSelection(best.plan.withInferenceDecision(true, sources[best.source] != null, targets[best.target] != null, detail),
        best.quality, baseline.quality, evaluated.size, best.source, best.target)
}

/** Bounded motion samples rank alternatives; they are not an all-times intersection proof. */
internal fun inferenceMotionQuality(plan: MorphPlan): InferenceMotionQuality? {
    if (plan.items.any { it.sourceStroke == null || it.targetStroke == null || it.fallbackReason != null }) return null
    val frame = plan.createFrame()
    fun frameAt(t: Double): List<DoubleArray> {
        plan.interpolate(t, frame, MorphInterpolation.Polar)
        // Bound quadratic distance/intersection work independently of the user's render sample count.
        val count = minOf(33, plan.sampleCount)
        return frame.points.map { points -> DoubleArray(count * 2) { i ->
            points[(i / 2 * (plan.sampleCount - 1) / (count - 1)) * 2 + i % 2]
        } }
    }
    val start = frameAt(0.0); val end = frameAt(1.0)
    fun width(i: Int, t: Double) = checkNotNull(plan.items[i].sourceStroke).width * (1 - t) +
        checkNotNull(plan.items[i].targetStroke).width * t
    val connections = mutableListOf<Pair<Int, Int>>()
    for (i in start.indices) for (j in 0 until i) {
        if (polylineDistance(start[i], start[j]) <= (width(i, 0.0) + width(j, 0.0)) / 2 + 1e-8 &&
            polylineDistance(end[i], end[j]) <= (width(i, 1.0) + width(j, 1.0)) / 2 + 1e-8) connections += i to j
    }
    val endpointCrossings = start.indices.map { maxOf(selfIntersections(start[it]), selfIntersections(end[it])) }
    var extra = 0; var gap = 0.0; var stretch = 0.0; var edges = 0
    for (step in 1..7) {
        val t = step / 8.0; val points = frameAt(t)
        if (points.any { p -> p.any { !it.isFinite() } }) return null
        for (i in points.indices) {
            extra += (selfIntersections(points[i]) - endpointCrossings[i]).coerceAtLeast(0)
            fun length(p: DoubleArray, j: Int) = hypot(p[j + 2] - p[j], p[j + 3] - p[j + 1])
            for (j in 0 until points[i].size - 2 step 2) {
                val expected = length(start[i], j) * (1 - t) + length(end[i], j) * t
                if (expected < 1e-12) continue
                val ratio = length(points[i], j) / expected - 1
                stretch += ratio * ratio; edges++
            }
        }
        for ((i, j) in connections) {
            val radius = (width(i, t) + width(j, t)) / 2
            if (radius > 1e-12) gap = maxOf(gap, (polylineDistance(points[i], points[j]) / radius - 1).coerceAtLeast(0.0))
        }
    }
    val result = InferenceMotionQuality(extra, gap, stretch / maxOf(1, edges),
        plan.items.map { it.residual }.average(), plan.items.map { abs(it.logScale) }.average())
    return result.takeIf { it.score.isFinite() }
}

private fun side(p: DoubleArray, a: Int, b: Int, x: Double, y: Double) =
    (p[b] - p[a]) * (y - p[a + 1]) - (p[b + 1] - p[a + 1]) * (x - p[a])

private fun intersects(a: DoubleArray, i: Int, b: DoubleArray, j: Int): Boolean {
    val x = side(a, i, i + 2, b[j], b[j + 1]); val y = side(a, i, i + 2, b[j + 2], b[j + 3])
    val u = side(b, j, j + 2, a[i], a[i + 1]); val v = side(b, j, j + 2, a[i + 2], a[i + 3])
    return x * y < -1e-16 && u * v < -1e-16
}

private fun selfIntersections(p: DoubleArray): Int {
    var count = 0
    for (i in 0 until p.size - 2 step 2) for (j in i + 4 until p.size - 2 step 2)
        if (intersects(p, i, p, j)) count++
    return count
}

private fun pointDistance(x: Double, y: Double, p: DoubleArray, j: Int): Double {
    val dx = p[j + 2] - p[j]; val dy = p[j + 3] - p[j + 1]
    val length = dx * dx + dy * dy
    val t = if (length < 1e-24) 0.0 else (((x - p[j]) * dx + (y - p[j + 1]) * dy) / length).coerceIn(0.0, 1.0)
    return hypot(x - p[j] - dx * t, y - p[j + 1] - dy * t)
}

private fun polylineDistance(a: DoubleArray, b: DoubleArray): Double {
    var result = Double.POSITIVE_INFINITY
    for (i in 0 until a.size - 2 step 2) for (j in 0 until b.size - 2 step 2) {
        if (intersects(a, i, b, j)) return 0.0
        result = minOf(result, pointDistance(a[i], a[i + 1], b, j), pointDistance(a[i + 2], a[i + 3], b, j),
            pointDistance(b[j], b[j + 1], a, i), pointDistance(b[j + 2], b[j + 3], a, i))
    }
    return result
}
