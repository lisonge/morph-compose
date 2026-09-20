package li.songe.morph.compose

import androidx.compose.ui.graphics.vector.ImageVector
import li.songe.morph.compose.internal.CubicPath
import li.songe.morph.compose.internal.MorphPlan
import li.songe.morph.compose.internal.MorphContourRole

/** A cacheable morph plan built from two Compose [ImageVector] instances. */
public class ImageVectorMorphPlan internal constructor(
    internal val corePlan: MorphPlan,
    internal val from: ImageVector?,
    internal val to: ImageVector,
    internal val defaultWidth: androidx.compose.ui.unit.Dp,
    internal val defaultHeight: androidx.compose.ui.unit.Dp,
    internal val sourcePaths: List<CubicPath>?,
    internal val targetPaths: List<CubicPath>,
) {
    public val contourCount: Int
        get() = corePlan.contourCount

    public val sampleCount: Int
        get() = corePlan.sampleCount

    public val compatibilityReport: MorphCompatibilityReport = corePlan.toCompatibilityReport()

    public fun rotationRadians(contourIndex: Int): Double = corePlan.rotationRadians(contourIndex)

    public fun residual(contourIndex: Int): Double = corePlan.residual(contourIndex)

}

/** Builds a reusable morph plan. Construct it once rather than once per animation frame. */
public fun buildMorphPlan(
    from: ImageVector,
    to: ImageVector,
    options: MorphOptions = MorphOptions(),
    isRtl: Boolean = false,
): ImageVectorMorphPlan = buildVectorPlan(from, to, from.toCubicPaths(isRtl), to.toCubicPaths(isRtl), options)

internal fun buildVectorPlan(
    from: ImageVector,
    to: ImageVector,
    sourcePaths: List<CubicPath>,
    targetPaths: List<CubicPath>,
    options: MorphOptions,
): ImageVectorMorphPlan =
    ImageVectorMorphPlan(
        corePlan = buildVectorCorePlan(sourcePaths, targetPaths, options),
        from = from,
        to = to,
        defaultWidth = maxOf(from.defaultWidth, to.defaultWidth),
        defaultHeight = maxOf(from.defaultHeight, to.defaultHeight),
        sourcePaths = sourcePaths,
        targetPaths = targetPaths,
    )

/** Inspects a vector pair without throwing for unsupported input. */
public fun inspectMorphCompatibility(
    from: ImageVector,
    to: ImageVector,
    options: MorphOptions = MorphOptions(),
    isRtl: Boolean = false,
): MorphCompatibilityReport =
    try {
        buildMorphPlan(from = from, to = to, options = options, isRtl = isRtl).compatibilityReport
    } catch (error: IllegalArgumentException) {
        MorphCompatibilityReport(
            compatibility = MorphCompatibility.Unsupported,
            contours = emptyList(),
            issues = listOf(error.message ?: "Unsupported ImageVector input"),
        )
    }

private fun MorphPlan.toCompatibilityReport(): MorphCompatibilityReport {
    val contours =
        List(contourCount) { index ->
            val fallbackReason = fallbackReason(index)
            MorphContourReport(
                index = index,
                kind = role(index).toPublicKind(),
                interpolation = if (usesPolar(index)) MorphInterpolation.Polar else MorphInterpolation.Linear,
                fallbackReason = fallbackReason,
                residual = residual(index),
                strategy = items[index].decision.strategy,
                sourceContours = items[index].decision.sourceContours,
                targetContours = items[index].decision.targetContours,
                sharedAnchorCount = items[index].decision.sharedAnchorCount,
                localMotionCount = items[index].boundaryMotions.size,
                holeOpeningCount = items[index].decision.holeOpeningCount,
                decision = items[index].decision.reason,
            )
        }
    val compatibility =
        if (contours.all { it.interpolation == MorphInterpolation.Polar }) {
            MorphCompatibility.FullPolar
        } else {
            MorphCompatibility.Hybrid
        }
    val issues =
        contours.mapNotNull { contour ->
            contour.fallbackReason?.let { reason ->
                "Contour ${contour.index} (${contour.kind}) uses Linear interpolation: $reason"
            }
        }
    return MorphCompatibilityReport(compatibility, contours, issues)
}

private fun MorphContourRole.toPublicKind(): MorphContourKind =
    when (this) {
        MorphContourRole.Shape -> MorphContourKind.Shape
        MorphContourRole.Hole -> MorphContourKind.Hole
        MorphContourRole.Stroke -> MorphContourKind.Stroke
    }
