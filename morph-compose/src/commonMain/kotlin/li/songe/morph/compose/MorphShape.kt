package li.songe.morph.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import li.songe.morph.compose.internal.MorphFrame

/** How the normalized square containing the input viewports maps to a component's bounds. */
public enum class MorphContentScale {
    /** Keep the aspect ratio and center the geometry. */
    Fit,
    /** Scale the normalized square independently on each axis to fill the bounds. */
    FillBounds,
}

/**
 * A morph frame usable by background, border and clip. The shape changes the visual boundary;
 * layout, content placement and pointer hit regions remain the responsibility of the component.
 * Each outline owns its Path, so measuring another size cannot mutate an already returned outline.
 */
public class MorphShape private constructor(
    private val renderer: MorphPathRenderer,
    private val progress: Float,
    private val contentScale: MorphContentScale,
    private val interpolation: MorphInterpolation,
) : Shape {
    public constructor(
        plan: MorphGeometryPlan,
        progress: Float,
        contentScale: MorphContentScale = MorphContentScale.Fit,
        interpolation: MorphInterpolation = MorphInterpolation.Polar,
    ) : this(MorphPathRenderer(plan.delegate), progress, contentScale, interpolation)

    init { require(progress.isFinite()) { "progress must be finite" } }

    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline =
        Outline.Generic(Path().also { renderer.writePath(it, progress, size, contentScale, interpolation) })

    internal companion object {
        fun fromRenderer(renderer: MorphPathRenderer, progress: Float, scale: MorphContentScale, interpolation: MorphInterpolation): MorphShape =
            MorphShape(renderer, progress, scale, interpolation)
    }
}

/** Remembers sampling buffers while producing an immutable shape for each progress value. */
@Composable
public fun rememberMorphShape(
    plan: MorphGeometryPlan,
    progress: Float,
    contentScale: MorphContentScale = MorphContentScale.Fit,
    interpolation: MorphInterpolation = MorphInterpolation.Polar,
): MorphShape {
    val renderer = remember(plan) { MorphPathRenderer(plan.delegate) }
    return remember(renderer, progress, contentScale, interpolation) {
        MorphShape.fromRenderer(renderer, progress, contentScale, interpolation)
    }
}

/** Backgrounds and clips follow interrupted animations from their currently visible boundary. */
@Composable
public fun rememberMorphShape(
    state: MorphGeometryState,
    contentScale: MorphContentScale = MorphContentScale.Fit,
): MorphShape {
    val plan = state.delegate.plan
    val renderer = remember(plan) { MorphPathRenderer(plan) }
    val progress = state.progress
    return remember(renderer, progress, contentScale) {
        MorphShape.fromRenderer(renderer, progress, contentScale, state.delegate.interpolation)
    }
}

/** Reusable frame buffers for Canvas drawing and masks. Confine each writer to one drawing consumer. */
public class MorphPathWriter internal constructor(private val renderer: MorphPathRenderer) {
    /** Replaces [destination] with this frame. Curved endpoints are retained at progress 0 and 1. */
    public fun writePath(
        destination: Path,
        progress: Float,
        size: Size,
        contentScale: MorphContentScale = MorphContentScale.Fit,
        interpolation: MorphInterpolation = MorphInterpolation.Polar,
    ): Unit = renderer.writePath(destination, progress, size, contentScale, interpolation)
}

internal class MorphPathRenderer(private val plan: ImageVectorMorphPlan) {
    private val frame = plan.corePlan.createFrame()
    private val from = plan.sourcePaths
    private val to = plan.targetPaths

    fun writePath(path: Path, progress: Float, size: Size, scale: MorphContentScale, interpolation: MorphInterpolation) {
        require(progress.isFinite()) { "progress must be finite" }
        require(size.width.isFinite() && size.height.isFinite() && size.width >= 0f && size.height >= 0f) {
            "size must be non-negative and finite"
        }
        path.rewind()
        path.fillType = PathFillType.NonZero
        if (size.width == 0f || size.height == 0f) return
        val sx = if (scale == MorphContentScale.Fit) minOf(size.width, size.height) else size.width
        val sy = if (scale == MorphContentScale.Fit) sx else size.height
        val ox = (size.width - sx) / 2f
        val oy = (size.height - sy) / 2f
        fun x(value: Double): Float = ox + value.toFloat() * sx
        fun y(value: Double): Float = oy + value.toFloat() * sy
        // Preserve curves at rest; sampled polylines are only used during the transition.
        val exact = when (progress) { 0f -> from; 1f -> to; else -> null }
        if (exact != null) {
            for (contour in exact) {
                val points = contour.points
                path.moveTo(x(points[0]), y(points[1]))
                for (segment in 0 until contour.segmentCount) {
                    val i = segment * 6 + 2
                    path.cubicTo(x(points[i]), y(points[i + 1]), x(points[i + 2]), y(points[i + 3]), x(points[i + 4]), y(points[i + 5]))
                }
                if (contour.closed) path.close()
            }
        } else {
            plan.corePlan.interpolate(progress.toDouble(), frame, interpolation)
            path.appendMorphFrame(frame, sx, sy, ox, oy)
        }
    }
}

/** Keep shapes, masks and icons on the same compound-path drawing path, including holes. */
internal fun Path.appendMorphFrame(frame: MorphFrame, sx: Float, sy: Float, ox: Float, oy: Float,
    includeContour: (Int) -> Boolean = { true },
) {
    for (contour in 0 until frame.contourCount) {
        if (!includeContour(contour)) continue
        appendMorphContour(frame, contour, sx, sy, ox, oy)
    }
}

/** Append one contour without scanning the remaining contours. */
internal fun Path.appendMorphContour(frame: MorphFrame, contour: Int, sx: Float, sy: Float, ox: Float, oy: Float) {
    fun x(value: Double): Float = ox + value.toFloat() * sx
    fun y(value: Double): Float = oy + value.toFloat() * sy
    val curves = frame.curves[contour]
    if (curves != null && curves.isNotEmpty()) {
        moveTo(x(curves[0]), y(curves[1]))
        for (i in 2 until curves.size step 6) {
            cubicTo(x(curves[i]), y(curves[i + 1]), x(curves[i + 2]), y(curves[i + 3]), x(curves[i + 4]), y(curves[i + 5]))
        }
    } else {
        moveTo(x(frame.x(contour, 0)), y(frame.y(contour, 0)))
        for (point in 1 until frame.sampleCount) lineTo(x(frame.x(contour, point)), y(frame.y(contour, point)))
    }
    if (frame.isClosed(contour)) close()
}
