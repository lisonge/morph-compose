package li.songe.morph.compose.internal

import li.songe.morph.compose.MorphFallbackReason
import li.songe.morph.compose.MorphInterpolation
import li.songe.morph.compose.MorphAppliedStrategy
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin

internal data class StrokeStyle(
    val width: Double,
    val cap: StrokeCap,
    val join: StrokeJoin,
    val miter: Float,
)

/** Describes how a contour participates in a compound filled path. */
internal enum class MorphContourRole {
    Shape,
    Hole,
    Stroke,
}

/** A chain of cubic Bézier segments packed as P0, C1, C2, P1, C1, C2, P2... */
internal class CubicPath(
    internal val points: DoubleArray,
    internal val closed: Boolean,
    internal val role: MorphContourRole,
    internal val stroke: StrokeStyle? = null,
) {
    internal val segmentCount: Int
        get() = (points.size / 2 - 1) / 3

    /** Returns a defensive copy of the packed cubic coordinates. */
    internal fun copyPackedPoints(): DoubleArray = points.copyOf()
}

/** Creates a validated immutable cubic path, defensively copying [points]. */
internal fun cubicPathOf(
    points: DoubleArray,
    closed: Boolean,
    role: MorphContourRole = if (closed) MorphContourRole.Shape else MorphContourRole.Stroke,
    stroke: StrokeStyle? = null,
): CubicPath {
    require(points.size >= 8 && points.size % 6 == 2) {
        "A cubic path must contain P0 followed by one or more C1/C2/P segments"
    }
    require(points.all(Double::isFinite)) { "Cubic path coordinates must be finite" }
    return CubicPath(points.copyOf(), closed, role, stroke)
}

internal data class SampledContour(
    val points: DoubleArray,
    val closed: Boolean,
    val role: MorphContourRole,
    val featureWeights: DoubleArray = DoubleArray(points.size / 2),
    val curveSource: CurveSource? = null,
    val curveDetails: CurveDetails? = null,
    val stroke: StrokeStyle? = null,
) {
    init {
        require(points.size % 2 == 0) { "Sampled contour points must contain x/y pairs" }
        require(featureWeights.size == points.size / 2) {
            "Feature weights must match the sampled point count"
        }
    }
}

internal data class BlockTransport(
    val offsetX: Double,
    val offsetY: Double,
    val driftX: Double,
    val driftY: Double,
)

internal data class PlanItem(
    val source: DoubleArray,
    val sourceFeatureWeights: DoubleArray,
    val sourceCentered: DoubleArray,
    val targetLocal: DoubleArray,
    val targetOriented: DoubleArray,
    val targetFeatureWeights: DoubleArray,
    val sourceCenterX: Double,
    val sourceCenterY: Double,
    val targetCenterX: Double,
    val targetCenterY: Double,
    var theta: Double,
    var logScale: Double,
    var residual: Double,
    val closed: Boolean,
    val role: MorphContourRole,
    val polarInterpolation: Boolean,
    val fallbackReason: MorphFallbackReason?,
    var blockTransport: BlockTransport?,
    var curveDetails: CurveDetails? = null,
    var targetCurveDetails: CurveDetails? = null,
    var stationaryCurve: CurveSource? = null,
    val sourceStroke: StrokeStyle? = null,
    val targetStroke: StrokeStyle? = null,
    val boundaryMotions: List<BoundaryMotion> = emptyList(),
    val decision: ContourDecision = ContourDecision(),
)

internal data class ContourDecision(
    val strategy: MorphAppliedStrategy = MorphAppliedStrategy.Outline,
    val sourceContours: List<Int> = emptyList(),
    val targetContours: List<Int> = emptyList(),
    val sharedAnchorCount: Int = 0,
    val holeOpeningCount: Int = 0,
    val reason: String = "",
)

internal fun PlanItem.strokeAt(progress: Double): StrokeStyle? {
    val start = sourceStroke ?: return targetStroke
    val end = targetStroke ?: return start
    if (start == end) return start
    val t = progress.coerceIn(0.0, 1.0)
    return (if (t < 0.5) start else end).copy(width = start.width + (end.width - start.width) * t)
}

/** A cacheable mapping between two sets of cubic contours. */
internal class MorphPlan(
    internal val items: List<PlanItem>,
    internal val sampleCount: Int,
) {
    internal val contourCount: Int
        get() = items.size

    internal fun rotationRadians(contourIndex: Int): Double = items[contourIndex].theta

    internal fun residual(contourIndex: Int): Double = items[contourIndex].residual

    internal fun role(contourIndex: Int): MorphContourRole = items[contourIndex].role

    internal fun usesPolar(contourIndex: Int): Boolean = items[contourIndex].polarInterpolation

    internal fun fallbackReason(contourIndex: Int): MorphFallbackReason? =
        items[contourIndex].fallbackReason

    internal fun createFrame(): MorphFrame =
        MorphFrame(
            points = Array(items.size) { DoubleArray(sampleCount * 2) },
            closed = BooleanArray(items.size) { items[it].closed },
            roles = Array(items.size) { items[it].role },
            curves = Array(items.size) { items[it].curveDetails?.let { detail -> DoubleArray(detail.offsets.size) } },
        )

    /** Writes one animation frame without allocating new point arrays. */
    internal fun interpolate(
        progress: Double,
        frame: MorphFrame,
        interpolation: MorphInterpolation = MorphInterpolation.Polar,
    ) {
        require(progress.isFinite()) { "progress must be finite" }
        require(frame.points.size == items.size) { "Frame belongs to a different morph plan" }
        require(frame.points.all { it.size == sampleCount * 2 }) {
            "Frame belongs to a different morph plan"
        }
        when (interpolation) {
            MorphInterpolation.Polar -> interpolatePolar(this, progress, frame.points)
            MorphInterpolation.Linear -> interpolateLinear(this, progress, frame.points)
        }
        items.forEachIndexed { index, item ->
            item.writeCurves(progress, interpolation, frame.points[index], frame.curves[index])
            if (interpolation == MorphInterpolation.Polar) item.boundaryMotions.forEach {
                it.write(item, progress, frame.points[index], frame.curves[index])
            }
        }
    }
}

/** Reusable output storage for a [MorphPlan]. */
internal class MorphFrame(
    internal val points: Array<DoubleArray>,
    private val closed: BooleanArray,
    private val roles: Array<MorphContourRole>,
    internal val curves: Array<DoubleArray?> = arrayOfNulls(points.size),
) {
    internal val contourCount: Int
        get() = points.size

    internal val sampleCount: Int
        get() = points.firstOrNull()?.size?.div(2) ?: 0

    internal fun isClosed(contourIndex: Int): Boolean = closed[contourIndex]

    internal fun role(contourIndex: Int): MorphContourRole = roles[contourIndex]

    internal fun x(contourIndex: Int, pointIndex: Int): Double = points[contourIndex][pointIndex * 2]

    internal fun y(contourIndex: Int, pointIndex: Int): Double = points[contourIndex][pointIndex * 2 + 1]
}

/** Freezes an interpolated frame without a second arc-length resampling pass. */
internal fun MorphPlan.snapshotContours(
    progress: Double,
    interpolation: MorphInterpolation,
): List<SampledContour> {
    val frame = createFrame()
    interpolate(progress, frame, interpolation)
    val featureProgress = progress.coerceIn(0.0, 1.0)
    return List(frame.contourCount) { contour ->
        val item = items[contour]
        SampledContour(
            points = frame.points[contour].copyOf(),
            closed = frame.isClosed(contour),
            role = frame.role(contour),
            stroke = item.strokeAt(progress),
            curveSource = item.stationaryCurve,
            curveDetails = item.curveDetails?.rebase(frame.points[contour], checkNotNull(frame.curves[contour])),
            featureWeights =
                DoubleArray(sampleCount) { point ->
                    item.sourceFeatureWeights[point] +
                        (item.targetFeatureWeights[point] - item.sourceFeatureWeights[point]) *
                        featureProgress
                },
        )
    }
}
