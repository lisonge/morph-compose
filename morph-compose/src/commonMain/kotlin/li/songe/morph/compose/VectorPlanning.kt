package li.songe.morph.compose

import li.songe.morph.compose.internal.CubicPath
import li.songe.morph.compose.internal.SampledContour
import li.songe.morph.compose.internal.MorphPlan
import li.songe.morph.compose.internal.MorphContourRole
import li.songe.morph.compose.internal.buildMorphPlan as buildCoreMorphPlan
import li.songe.morph.compose.internal.buildMorphPlanFromSampledSource

internal fun buildVectorCorePlan(from: List<CubicPath>, to: List<CubicPath>, options: MorphOptions): MorphPlan {
    val inferred = selectInferredPlan(from, to, options)
    if (inferred != null) return inferred.plan
    val plan = if (useOutlines(from, to, options)) buildCoreMorphPlan(from.expandStrokes(options), to.expandStrokes(options), options)
        else buildCoreMorphPlan(from, to, options)
    return plan.withRepresentationFallback(options)
}

internal fun buildInterruptedVectorPlan(from: List<SampledContour>, to: List<CubicPath>, options: MorphOptions): MorphPlan {
    val sourcePaths = from.map { it.asCubicPath() }
    // Never reinterpret an interrupted filled silhouette: keep the exact visible snapshot.
    if (from.all { it.stroke != null }) {
        val inferred = selectInferredPlan(sourcePaths, to, options, from)
        if (inferred != null) return inferred.plan
    }
    if (!useOutlines(sourcePaths, to, options)) {
        return buildMorphPlanFromSampledSource(from, to, options).withRepresentationFallback(options)
    }
    val target = to.expandStrokes(options)
    // Representation changes require one resampling pass of the expanded ink. Keep snapshots
    // already in outline form untouched, including their exact interpolated curve offsets.
    val plan = if (from.any { it.stroke != null }) {
        buildCoreMorphPlan(sourcePaths.expandStrokes(options), target, options)
    } else buildMorphPlanFromSampledSource(from, target, options)
    return plan.withRepresentationFallback(options)
}

private fun MorphPlan.withRepresentationFallback(options: MorphOptions): MorphPlan =
    if (options.transitionMode == MorphTransitionMode.ExperimentalStrokeInference)
        withInferenceDecision(false, false, false) else this

internal fun useOutlines(source: List<CubicPath>, target: List<CubicPath>, options: MorphOptions): Boolean {
    val onlyStrokes = (source + target).all { it.role == MorphContourRole.Stroke && it.stroke != null }
    return when (options.transitionMode) {
        MorphTransitionMode.Auto, MorphTransitionMode.ExperimentalStrokeInference -> !onlyStrokes
        MorphTransitionMode.Outline -> true
        MorphTransitionMode.Centerline -> {
            require(onlyStrokes) { "Centerline mode requires stroke-only input on both sides" }
            false
        }
    }
}
