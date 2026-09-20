package li.songe.morph.compose.internal

import li.songe.morph.compose.MorphFallbackReason
import li.songe.morph.compose.MorphAppliedStrategy
import li.songe.morph.compose.MorphOptions
import li.songe.morph.compose.MorphRotationPreference
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.sin
import kotlin.math.sqrt

private const val GlobalResidualThreshold = 5e-3

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
    val correspondence = matchContours(source, target, options.rotationPreference, options.strokeCountStrategy)
    val pairs = selectContourStrategies(correspondence, source, target, options)

    val items =
        pairs.map { pair ->
            val sourceContour = pair.source
            val targetContour = pair.target
            val alignment = if (pair.anchored) {
                val sourceCenter = centroid(sourceContour.points)
                val targetCenter = centroid(targetContour.points)
                Alignment(sourceCenter, targetCenter, sourceContour.points, sourceContour.featureWeights,
                    targetContour.points, targetContour.featureWeights, 0.0, 1.0,
                    procrustes(sourceContour.points, targetContour.points, sourceCenter, targetCenter).residual)
            } else alignPair(sourceContour, targetContour, options.rotationPreference)
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
                sourceStroke = sourceContour.stroke,
                targetStroke = targetContour.stroke,
                decision = if (fallbackReason == MorphFallbackReason.DegenerateSimilarity) pair.decision.copy(
                    strategy = MorphAppliedStrategy.Collapse, reason = "Degenerate similarity; linear interpolation.",
                ) else pair.decision,
                boundaryMotions = pair.movingSpans.mapNotNull { range ->
                    fun extract(points: DoubleArray) = DoubleArray(range.count() * 2) { i ->
                        points[((range.first + i / 2) % options.sampleCount) * 2 + i % 2]
                    }
                    val a = extract(alignment.source)
                    val b = extract(alignment.target)
                    val ac = centroid(a)
                    val bc = centroid(b)
                    val fit = procrustes(a, b, ac, bc)
                    if (fit.scale <= MinimumPolarScale) null else
                        BoundaryMotion(range, ac.x, ac.y, bc.x, bc.y, fit.theta, ln(fit.scale))
                },
            )
        }
    val polarItems = items.filterIndexed { index, item -> item.polarInterpolation && !pairs[index].anchored }
    if (polarItems.size > 1) {
        applyGlobalAlignment(polarItems, options.sampleCount, options.rotationPreference)
    }
    items.forEachIndexed { index, item ->
        item.retainCurves(pairs[index].source, pairs[index].target)
        item.boundaryMotions.forEach { it.prepare(item) }
    }
    return MorphPlan(items, options.sampleCount)
}

internal fun sharedBoundaryFitScore(anchored: AnchoredContours, sampleCount: Int): Double {
    return anchored.movingSpans.sumOf { range ->
        fun extract(points: DoubleArray) = DoubleArray(range.count() * 2) { i ->
            points[((range.first + i / 2) % sampleCount) * 2 + i % 2]
        }
        val a = extract(anchored.source.points)
        val b = extract(anchored.target.points)
        val fit = procrustes(a, b, centroid(a), centroid(b))
        fit.residual + 0.05 * abs(ln(maxOf(fit.scale, MinimumPolarScale)))
    }
}
