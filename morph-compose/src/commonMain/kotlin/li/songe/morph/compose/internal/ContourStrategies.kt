package li.songe.morph.compose.internal

import li.songe.morph.compose.MorphAppliedStrategy
import li.songe.morph.compose.MorphContourStrategy
import li.songe.morph.compose.MorphFallbackReason
import li.songe.morph.compose.MorphOptions

/** Policy stage: consumes a correspondence and records every strategy decision. */
internal fun selectContourStrategies(
    input: List<ContourPair>, source: List<SampledContour>, target: List<SampledContour>, options: MorphOptions,
): List<ContourPair> {
    val matched = input.map { pair ->
        pair.copy(decision = ContourDecision(
            sourceContours = source.indices.filter { source[it] === pair.source },
            targetContours = target.indices.filter { target[it] === pair.target },
        ))
    }.toMutableList()
    // Only join an unmatched hole to a parent with a verified shared boundary. Other topology
    // changes retain their existing collapse fallback. The doubled seam contributes zero ink.
    val consumed = mutableSetOf<Int>()
    if (options.contourStrategy == MorphContourStrategy.ExperimentalHoleOpening) for (index in matched.indices) {
        val hole = matched[index]
        val disappearing = hole.fallbackReason == MorphFallbackReason.MissingTargetContour
        val appearing = hole.fallbackReason == MorphFallbackReason.MissingSourceContour
        if ((!disappearing && !appearing) || hole.source.role != MorphContourRole.Hole) continue
        val candidates = matched.indices.filter { parentIndex ->
            val parent = matched[parentIndex]
            parent.fallbackReason == null && parent.source.role == MorphContourRole.Shape &&
                sharedBoundaryPair(parent.source, parent.target)?.movingSpans?.isNotEmpty() == true
        }.mapNotNull { parentIndex ->
            val parent = matched[parentIndex]
            val shared = sharedBoundaryPair(parent.source, parent.target) ?: return@mapNotNull null
            val local = if (disappearing) shared.source else shared.target
            val holeContour = if (disappearing) hole.source else hole.target
            // A hole elsewhere in the icon must not be dragged to an unrelated changing edge.
            fun inChangingRegion(x: Double, y: Double) = shared.movingSpans.any { range ->
                val xs = range.map { local.points[(it % options.sampleCount) * 2] }
                val ys = range.map { local.points[(it % options.sampleCount) * 2 + 1] }
                val minX = xs.min() - 1e-7
                val maxX = xs.max() + 1e-7
                val minY = ys.min() - 1e-7
                val maxY = ys.max() + 1e-7
                x in minX..maxX && y in minY..maxY
            }
            val openings = holeOpeningCandidates(if (disappearing) parent.source else parent.target, holeContour)
            val best = openings.filter { inChangingRegion(it.x, it.y) }.mapNotNull opening@ { opening ->
                val joined = resamplePaths(listOf(opening.path), options).single()
                val replacement = if (disappearing) parent.copy(source = joined) else parent.copy(target = joined)
                val anchored = sharedBoundaryPair(replacement.source, replacement.target) ?: return@opening null
                val score = sharedBoundaryFitScore(anchored, options.sampleCount)
                replacement to score
            }.minByOrNull { it.second }?.first ?: return@mapNotNull null
            parentIndex to best
        }
        // Nested or otherwise ambiguous parents are deliberately left to the fallback solver.
        val candidate = candidates.singleOrNull() ?: continue
        matched[candidate.first] = candidate.second.copy(decision = candidate.second.decision.copy(
            sourceContours = candidate.second.decision.sourceContours + hole.decision.sourceContours,
            targetContours = candidate.second.decision.targetContours + hole.decision.targetContours,
            holeOpeningCount = candidate.second.decision.holeOpeningCount + 1,
        ))
        consumed += index
    }
    return matched.filterIndexed { index, _ -> index !in consumed }.map { pair ->
        val shared = if (options.contourStrategy == MorphContourStrategy.Standard) null
            else sharedBoundaryPair(pair.source, pair.target)
        val strategy = when {
            pair.fallbackReason != null -> MorphAppliedStrategy.Collapse
            pair.source.role == MorphContourRole.Stroke -> MorphAppliedStrategy.Centerline
            pair.decision.holeOpeningCount > 0 -> MorphAppliedStrategy.ExperimentalHoleOpening
            shared != null -> MorphAppliedStrategy.SharedBoundary
            else -> MorphAppliedStrategy.Outline
        }
        val reason = when (strategy) {
            MorphAppliedStrategy.Collapse -> "Unmatched contour: ${pair.fallbackReason}; linear shrink/grow."
            MorphAppliedStrategy.Centerline -> "Stroke-only correspondence; filled-contour policy does not apply."
            MorphAppliedStrategy.ExperimentalHoleOpening -> "Explicit opt-in; local unmatched hole has one eligible parent and a valid seam."
            MorphAppliedStrategy.SharedBoundary -> "Ordered common spans support ${shared?.anchorCount} boundary anchors."
            MorphAppliedStrategy.Outline -> if (options.contourStrategy == MorphContourStrategy.Standard)
                "Standard policy: boundary and topology heuristics disabled."
                else "No eligible shared boundary (ambiguous, reordered or insufficient original geometry); standard solver."
        }
        val decision = pair.decision.copy(strategy = strategy, sharedAnchorCount = shared?.anchorCount ?: 0, reason = reason)
        if (shared == null) pair.copy(decision = decision) else pair.copy(source = shared.source, target = shared.target,
            anchored = true, movingSpans = shared.movingSpans, decision = decision)
    }
}
