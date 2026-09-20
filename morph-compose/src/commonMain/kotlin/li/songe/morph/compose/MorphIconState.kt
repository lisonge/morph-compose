package li.songe.morph.compose

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.foundation.MutatorMutex
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.withContext
import li.songe.morph.compose.internal.snapshotContours

/**
 * Stateful controller for morphing through an arbitrary sequence of [ImageVector] targets.
 *
 * A new [animateTo] call interrupts the active animation at its currently rendered geometry and
 * replans from that exact frame, avoiding endpoint jumps during rapid state changes.
 */
@Stable
public class MorphIconState internal constructor(
    initialIcon: ImageVector,
    private val options: MorphOptions,
    internal val isRtl: Boolean,
    public val interpolation: MorphInterpolation,
) {
    private val mutatorMutex: MutatorMutex = MutatorMutex()
    private var velocity: Float = 0.0f

    internal var plan: ImageVectorMorphPlan by
        mutableStateOf(
            buildMorphPlan(
                from = initialIcon,
                to = initialIcon,
                options = options,
                isRtl = isRtl,
            ),
        )
        private set

    /** Current normalized animation progress. Values can overshoot for spring animations. */
    public var progress: Float by mutableFloatStateOf(1.0f)
        private set

    /** Whether [animateTo] is currently producing frames. */
    public var isRunning: Boolean by mutableStateOf(false)
        private set

    /** Destination supplied to the most recent [animateTo] or [snapTo] call. */
    public val targetIcon: ImageVector
        get() = plan.to

    /** Compatibility of the currently planned transition. */
    public val compatibilityReport: MorphCompatibilityReport
        get() = plan.compatibilityReport

    /**
     * Morphs from the currently rendered geometry to [target]. Calling this again interrupts the
     * active transition continuously rather than restarting from its old source icon.
     */
    public suspend fun animateTo(
        target: ImageVector,
        animationSpec: AnimationSpec<Float> =
            spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessMedium,
            ),
        motionPolicy: MorphMotionPolicy = MorphMotionPolicy.System,
    ): Unit {
        mutatorMutex.mutate {
            if (target === plan.to && progress == 1.0f) return@mutate
            if (motionPolicy == MorphMotionPolicy.Never) {
                settleAt(target)
                return@mutate
            }

            plan = createInterruptedPlan(target)
            progress = 0.0f
            isRunning = true
            try {
                val animateBlock: suspend () -> Unit = {
                    animate(
                        initialValue = 0.0f,
                        targetValue = 1.0f,
                        initialVelocity = velocity,
                        animationSpec = animationSpec,
                    ) { value, frameVelocity ->
                        progress = value
                        velocity = frameVelocity
                    }
                }
                when (motionPolicy) {
                    MorphMotionPolicy.Always ->
                        withContext(FullMotionDurationScale) { animateBlock() }
                    MorphMotionPolicy.System -> animateBlock()
                    MorphMotionPolicy.Never -> Unit
                }
                progress = 1.0f
                velocity = 0.0f
            } finally {
                isRunning = false
            }
        }
    }

    /** Cancels any animation and immediately displays [target]. */
    public suspend fun snapTo(target: ImageVector): Unit {
        mutatorMutex.mutate { settleAt(target) }
    }

    internal suspend fun seekTo(from: ImageVector, to: ImageVector, value: Float) {
        require(value.isFinite()) { "progress must be finite" }
        mutatorMutex.mutate {
            if (plan.from !== from || plan.to !== to) {
                plan = buildMorphPlan(from, to, options, isRtl)
            }
            progress = value
            velocity = 0f
            isRunning = false
        }
    }

    private fun createInterruptedPlan(target: ImageVector): ImageVectorMorphPlan {
        if (progress == 1.0f) {
            return buildMorphPlan(
                from = plan.to,
                to = target,
                options = options,
                isRtl = isRtl,
            )
        }
        val frozenSource = plan.corePlan.snapshotContours(progress.toDouble(), interpolation)
        val targetPaths = target.toCubicPaths(isRtl)
        return ImageVectorMorphPlan(
            corePlan =
                buildInterruptedVectorPlan(
                    frozenSource,
                    targetPaths,
                    options,
                ),
            from = null,
            to = target,
            defaultWidth = maxOf(plan.defaultWidth, target.defaultWidth),
            defaultHeight = maxOf(plan.defaultHeight, target.defaultHeight),
            sourcePaths = null,
            targetPaths = targetPaths,
        )
    }

    private fun settleAt(target: ImageVector) {
        plan =
            buildMorphPlan(
                from = target,
                to = target,
                options = options,
                isRtl = isRtl,
            )
        progress = 1.0f
        velocity = 0.0f
        isRunning = false
    }
}

/** Remembers a controller for interruption-safe transitions between arbitrary icons. */
@Composable
public fun rememberMorphIconState(
    initialIcon: ImageVector,
    options: MorphOptions = MorphOptions(),
    interpolation: MorphInterpolation = MorphInterpolation.Polar,
): MorphIconState {
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    return remember(options, interpolation, isRtl) {
        MorphIconState(
            initialIcon = initialIcon,
            options = options,
            isRtl = isRtl,
            interpolation = interpolation,
        )
    }
}

private object FullMotionDurationScale : MotionDurationScale {
    override val scaleFactor: Float = 1.0f
    override val key: CoroutineContext.Key<*> = MotionDurationScale
}
