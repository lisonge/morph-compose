package li.songe.morph.compose

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.Stroke
import li.songe.morph.compose.internal.strokeAt
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.Dp
import kotlin.math.min

/** Remembers an [ImageVectorMorphPlan], including automatic RTL mirroring. */
@Composable
public fun rememberMorphPlan(
    from: ImageVector,
    to: ImageVector,
    options: MorphOptions = MorphOptions(),
): ImageVectorMorphPlan {
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    return remember(from, to, options, isRtl) {
        buildMorphPlan(from = from, to = to, options = options, isRtl = isRtl)
    }
}

/** Draws a precomputed icon morph at [progress]. Values outside 0..1 are allowed for overshoot. */
@Composable
public fun MorphIcon(
    plan: ImageVectorMorphPlan,
    progress: Float,
    modifier: Modifier = Modifier,
    tint: Color = Color.Black,
    contentDescription: String? = null,
    interpolation: MorphInterpolation = MorphInterpolation.Polar,
) {
    require(progress.isFinite()) { "progress must be finite" }
    val width = plan.defaultWidth
    val height = plan.defaultHeight
    val sizedModifier = modifier.then(Modifier.defaultMorphSize(width, height))
    val effectiveTint = tint.takeUnless { it == Color.Unspecified } ?: Color.Black
    val colorFilter = ColorFilter.tint(effectiveTint)

    val exactFrom = plan.from
    when {
        progress == 0.0f && exactFrom != null ->
            Image(
                imageVector = exactFrom,
                contentDescription = contentDescription,
                modifier = sizedModifier,
                colorFilter = colorFilter,
                contentScale = ContentScale.Fit,
            )
        progress == 1.0f ->
            Image(
                imageVector = plan.to,
                contentDescription = contentDescription,
                modifier = sizedModifier,
                colorFilter = colorFilter,
                contentScale = ContentScale.Fit,
            )
        else -> {
            val frame = remember(plan) { plan.corePlan.createFrame() }
            val path = remember(plan) { Path().apply { fillType = PathFillType.NonZero } }
            val semanticsModifier =
                if (contentDescription == null) {
                    Modifier
                } else {
                    Modifier.semantics {
                        this.contentDescription = contentDescription
                        role = Role.Image
                    }
                }
            Canvas(sizedModifier.then(semanticsModifier)) {
                plan.corePlan.interpolate(progress.toDouble(), frame, interpolation)
                path.reset()
                path.fillType = PathFillType.NonZero
                val extent = min(size.width, size.height)
                val origin = Offset((size.width - extent) / 2.0f, (size.height - extent) / 2.0f)
                path.appendMorphFrame(frame, extent, extent, origin.x, origin.y) {
                    plan.corePlan.items[it].strokeAt(progress.toDouble()) == null
                }
                clipRect {
                    // Recovered strokes came from one filled shape. Composite their ink once,
                    // so a translucent tint does not darken at the inferred stroke crossings.
                    val compositeInk = effectiveTint.alpha < 1f && plan.corePlan.items.any {
                        it.decision.strategy == MorphAppliedStrategy.InferredCenterline
                    }
                    if (compositeInk) drawContext.canvas.saveLayer(Rect(Offset.Zero, size), Paint().apply { alpha = effectiveTint.alpha })
                    val ink = if (compositeInk) effectiveTint.copy(alpha = 1f) else effectiveTint
                    drawPath(path = path, color = ink)
                    plan.corePlan.items.forEachIndexed { index, item ->
                        val stroke = item.strokeAt(progress.toDouble()) ?: return@forEachIndexed
                        if (stroke.width <= 0.0) return@forEachIndexed
                        path.rewind()
                        path.appendMorphContour(frame, index, extent, extent, origin.x, origin.y)
                        drawPath(path, ink, style = Stroke(
                            width = (stroke.width * extent).toFloat(), cap = stroke.cap,
                            join = stroke.join, miter = stroke.miter,
                        ))
                    }
                    if (compositeInk) drawContext.canvas.restore()
                }
            }
        }
    }
}

/** Builds and remembers a plan, then draws its frame at [progress]. */
@Composable
public fun MorphIcon(
    from: ImageVector,
    to: ImageVector,
    progress: Float,
    modifier: Modifier = Modifier,
    tint: Color = Color.Black,
    contentDescription: String? = null,
    options: MorphOptions = MorphOptions(),
    interpolation: MorphInterpolation = MorphInterpolation.Polar,
) {
    MorphIcon(
        plan = rememberMorphPlan(from, to, options),
        progress = progress,
        modifier = modifier,
        tint = tint,
        contentDescription = contentDescription,
        interpolation = interpolation,
    )
}

/** Draws the current frame owned by [state]. */
@Composable
public fun MorphIcon(
    state: MorphIconState,
    modifier: Modifier = Modifier,
    tint: Color = Color.Black,
    contentDescription: String? = null,
) {
    MorphIcon(
        plan = state.plan,
        progress = state.progress,
        modifier = modifier,
        tint = tint,
        contentDescription = contentDescription,
        interpolation = state.interpolation,
    )
}

/** Animates from the currently displayed icon whenever [imageVector] changes. */
@Composable
public fun AnimatedMorphIcon(
    imageVector: ImageVector,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    tint: Color = Color.Black,
    options: MorphOptions = MorphOptions(),
    interpolation: MorphInterpolation = MorphInterpolation.Polar,
    animationSpec: AnimationSpec<Float> =
        spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium,
        ),
    motionPolicy: MorphMotionPolicy = MorphMotionPolicy.System,
) {
    val state =
        rememberMorphIconState(
            initialIcon = imageVector,
            options = options,
            interpolation = interpolation,
        )
    LaunchedEffect(state, imageVector, animationSpec, motionPolicy) {
        state.animateTo(
            target = imageVector,
            animationSpec = animationSpec,
            motionPolicy = motionPolicy,
        )
    }
    MorphIcon(
        state = state,
        modifier = modifier,
        tint = tint,
        contentDescription = contentDescription,
    )
}

/** Animates between [from] and [to] whenever [targetState] changes. */
@Composable
public fun AnimatedMorphIcon(
    from: ImageVector,
    to: ImageVector,
    targetState: Boolean,
    modifier: Modifier = Modifier,
    tint: Color = Color.Black,
    contentDescription: String? = null,
    options: MorphOptions = MorphOptions(),
    interpolation: MorphInterpolation = MorphInterpolation.Polar,
    animationSpec: AnimationSpec<Float> =
        spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium,
        ),
    motionPolicy: MorphMotionPolicy = MorphMotionPolicy.System,
) {
    val state =
        rememberMorphIconState(
            initialIcon = if (targetState) to else from,
            options = options,
            interpolation = interpolation,
        )
    LaunchedEffect(state, from, to, targetState, animationSpec, motionPolicy) {
        state.animateTo(
            target = if (targetState) to else from,
            animationSpec = animationSpec,
            motionPolicy = motionPolicy,
        )
    }
    MorphIcon(
        state = state,
        modifier = modifier,
        tint = tint,
        contentDescription = contentDescription,
    )
}

private fun Modifier.defaultMorphSize(width: Dp, height: Dp): Modifier =
    then(Modifier.size(width, height))
