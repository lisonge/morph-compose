package li.songe.morph.compose

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathIterator
import androidx.compose.ui.graphics.PathSegment
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathNode
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.sqrt
import li.songe.morph.compose.internal.CubicPath

/** Immutable, filled geometry in an explicit viewport, independent of icon packs and layout size. */
public class MorphGeometry internal constructor(
    internal val vector: ImageVector,
    internal val paths: List<CubicPath>,
)

/**
 * Copies a filled [path] into reusable morph geometry. Open subpaths are implicitly closed.
 * Nested contours are interpreted as alternating shapes and holes, like the vector adapter.
 * Stroke width, caps and joins are not part of this input contract.
 * Conics are approximated by quadratics with at most [conicTolerance] viewport units of error.
 */
public fun morphGeometryOf(
    path: Path,
    viewportSize: Size,
    conicTolerance: Float = 0.01f,
): MorphGeometry {
    require(conicTolerance.isFinite() && conicTolerance > 0f) { "conicTolerance must be positive and finite" }
    val nodes = mutableListOf<PathNode>()
    val iterator = PathIterator(path, PathIterator.ConicEvaluation.AsConic)
    val points = FloatArray(8)
    while (iterator.hasNext()) {
        when (iterator.next(points)) {
            PathSegment.Type.Move -> nodes += PathNode.MoveTo(points[0], points[1])
            PathSegment.Type.Line -> nodes += PathNode.LineTo(points[2], points[3])
            PathSegment.Type.Quadratic -> nodes += PathNode.QuadTo(points[2], points[3], points[4], points[5])
            PathSegment.Type.Cubic -> nodes += PathNode.CurveTo(points[2], points[3], points[4], points[5], points[6], points[7])
            PathSegment.Type.Close -> nodes += PathNode.Close
            PathSegment.Type.Done -> Unit
            PathSegment.Type.Conic -> appendConic(nodes, points, conicTolerance.toDouble())
        }
    }
    return morphGeometryOf(nodes, viewportSize)
}

// Skia-backed PathIterator currently returns conics even with AsQuadratics selected.
// Subdivide rational quadratics in common code so Android, Desktop and Wasm use the same input.
private fun appendConic(nodes: MutableList<PathNode>, points: FloatArray, tolerance: Double) {
    require(points.take(7).all { it.isFinite() } && points[6] > 0f) { "Conic points and positive weight must be finite" }
    fun subdivide(x0: Double, y0: Double, x1: Double, y1: Double, x2: Double, y2: Double, weight: Double, depth: Int) {
        val error = abs((weight - 1) / (4 * (weight + 1))) * hypot(x0 - 2 * x1 + x2, y0 - 2 * y1 + y2)
        if (error <= tolerance) {
            nodes += PathNode.QuadTo(x1.toFloat(), y1.toFloat(), x2.toFloat(), y2.toFloat())
            return
        }
        require(depth < 16) { "Conic tolerance requires too many subdivisions" }
        val divisor = 1 + weight
        val ax = (x0 + weight * x1) / divisor
        val ay = (y0 + weight * y1) / divisor
        val bx = (weight * x1 + x2) / divisor
        val by = (weight * y1 + y2) / divisor
        val mx = (ax + bx) / 2
        val my = (ay + by) / 2
        val nextWeight = sqrt(divisor / 2)
        subdivide(x0, y0, ax, ay, mx, my, nextWeight, depth + 1)
        subdivide(mx, my, bx, by, x2, y2, nextWeight, depth + 1)
    }
    subdivide(points[0].toDouble(), points[1].toDouble(), points[2].toDouble(), points[3].toDouble(),
        points[4].toDouble(), points[5].toDouble(), points[6].toDouble(), 0)
}

/** Copies vector path commands, including SVG-style arcs, into filled morph geometry. */
public fun morphGeometryOf(pathData: List<PathNode>, viewportSize: Size): MorphGeometry {
    require(viewportSize.width.isFinite() && viewportSize.height.isFinite() &&
        viewportSize.width > 0f && viewportSize.height > 0f) { "viewportSize must be positive and finite" }
    val vector = ImageVector.Builder(
        name = "MorphGeometry",
        defaultWidth = viewportSize.width.dp,
        defaultHeight = viewportSize.height.dp,
        viewportWidth = viewportSize.width,
        viewportHeight = viewportSize.height,
    ).addPath(pathData = pathData.toList(), fill = SolidColor(Color.Black)).build()
    // Validate coordinates and topology at the input boundary, before an animation begins.
    val paths = vector.toCubicPaths(isRtl = false)
    require(paths.isNotEmpty()) { "Geometry must contain drawable contours" }
    return MorphGeometry(vector, paths)
}

/** A reusable correspondence between two filled geometries. */
public class MorphGeometryPlan internal constructor(internal val delegate: ImageVectorMorphPlan) {
    public val compatibilityReport: MorphCompatibilityReport get() = delegate.compatibilityReport
    public val contourCount: Int get() = delegate.contourCount
    public val sampleCount: Int get() = delegate.sampleCount

    /** Create one writer per drawing consumer and reuse it across frames. */
    public fun createPathWriter(): MorphPathWriter = MorphPathWriter(MorphPathRenderer(delegate))
}

/** Build once per pair, rather than during drawing. Larger displays may need more samples. */
public fun buildMorphPlan(
    from: MorphGeometry,
    to: MorphGeometry,
    options: MorphOptions = MorphOptions(sampleCount = 128),
): MorphGeometryPlan {
    requireFilledGeometryOptions(options)
    return MorphGeometryPlan(buildVectorPlan(from.vector, to.vector, from.paths, to.paths, options))
}

@Composable
public fun rememberMorphPlan(
    from: MorphGeometry,
    to: MorphGeometry,
    options: MorphOptions = MorphOptions(sampleCount = 128),
): MorphGeometryPlan = remember(from, to, options) { buildMorphPlan(from, to, options) }

/** Reuses the same interruption-safe controller as icons, accepting general filled geometry. */
@Stable
public class MorphGeometryState internal constructor(internal val delegate: MorphIconState) {
    public val progress: Float get() = delegate.progress
    public val isRunning: Boolean get() = delegate.isRunning
    public val compatibilityReport: MorphCompatibilityReport get() = delegate.compatibilityReport

    public suspend fun animateTo(
        target: MorphGeometry,
        animationSpec: AnimationSpec<Float> = spring(),
        motionPolicy: MorphMotionPolicy = MorphMotionPolicy.System,
    ): Unit = delegate.animateTo(target.vector, animationSpec, motionPolicy)

    public suspend fun snapTo(target: MorphGeometry): Unit = delegate.snapTo(target.vector)

    /** Cancels playback and places the controller at a controlled frame; animateTo continues from it. */
    public suspend fun seekTo(from: MorphGeometry, to: MorphGeometry, progress: Float): Unit =
        delegate.seekTo(from.vector, to.vector, progress)
}

@Composable
public fun rememberMorphGeometryState(
    initialGeometry: MorphGeometry,
    options: MorphOptions = MorphOptions(sampleCount = 128),
    interpolation: MorphInterpolation = MorphInterpolation.Polar,
): MorphGeometryState = remember(options, interpolation) {
    requireFilledGeometryOptions(options)
    MorphGeometryState(MorphIconState(initialGeometry.vector, options, false, interpolation))
}

private fun requireFilledGeometryOptions(options: MorphOptions) {
    require(options.transitionMode != MorphTransitionMode.ExperimentalStrokeInference) {
        "ExperimentalStrokeInference currently supports ImageVector/MorphIcon only; geometry and clipping require filled outlines"
    }
}
