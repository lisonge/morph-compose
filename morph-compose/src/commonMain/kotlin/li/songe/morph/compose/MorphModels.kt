package li.songe.morph.compose

import kotlin.math.PI

/** Controls the geometry generated for a morph plan. */
public data class MorphOptions(
    public val sampleCount: Int = 64,
    public val cornerThresholdRadians: Double = PI / 8.0,
    public val rotationPreference: MorphRotationPreference = MorphRotationPreference.Auto,
    public val transitionMode: MorphTransitionMode = MorphTransitionMode.Auto,
    public val strokeCountStrategy: MorphStrokeCountStrategy = MorphStrokeCountStrategy.SplitMerge,
    public val outlineTolerance: Double = 0.0001,
    public val contourStrategy: MorphContourStrategy = MorphContourStrategy.SharedBoundary,
) {
    init {
        require(sampleCount >= 2) { "sampleCount must be at least 2" }
        require(outlineTolerance.isFinite() && outlineTolerance > 0.0 && outlineTolerance <= 0.01) {
            "outlineTolerance must be in (0, 0.01] normalized viewport units"
        }
        require(cornerThresholdRadians.isFinite() && cornerThresholdRadians >= 0.0) {
            "cornerThresholdRadians must be finite and non-negative"
        }
    }
}

/** Filled-contour policy. Independent of stroke/outline representation selection. */
public enum class MorphContourStrategy {
    /** Ordinary contour matching and similarity interpolation; no boundary or topology heuristics. */
    Standard,
    /** Constrain verified shared boundary spans; unmatched holes still shrink/grow. */
    SharedBoundary,
    /** Opt-in: also try joining unmatched holes to a changing parent through a zero-width seam. */
    ExperimentalHoleOpening,
}

/** The strategy actually selected for one output contour, after eligibility checks. */
public enum class MorphAppliedStrategy {
    Centerline,
    Outline,
    SharedBoundary,
    ExperimentalHoleOpening,
    Collapse,
    /** Explicit polygon-to-stroke recovery passed the reconstruction checks. */
    InferredCenterline,
}

/** Selects a common representation before contour matching. */
public enum class MorphTransitionMode {
    /** Use centerlines only when both inputs contain exclusively strokes; otherwise use outlines. */
    Auto,
    /** Expand strokes and merge their overlapping ink before matching filled contours. */
    Outline,
    /** Require stroke-only inputs; reject filled input rather than silently removing its geometry. */
    Centerline,
    /** Opt-in: recover flat-capped straight strokes from verified near-uniform polygonal ink; otherwise use Auto. */
    ExperimentalStrokeInference,
}

/** How unequal stroke counts behave in centerline mode. Filled contours always preserve winding. */
public enum class MorphStrokeCountStrategy {
    SplitMerge,
    Collapse,
}

/**
 * Chooses between similarly clean contour correspondences. Directions refer to the displayed
 * coordinate system (including RTL mirroring). This is a preference, not a forced spin: a poorer
 * fit is rejected and exact translation/scaling does not acquire an unnecessary rotation.
 * Linear interpolation uses the selected correspondence but does not promise a rotation direction.
 */
public enum class MorphRotationPreference {
    Auto,
    PreferClockwise,
    PreferCounterClockwise,
}

/** Selects the production polar morph or a raw coordinate comparison. */
public enum class MorphInterpolation {
    Polar,
    Linear,
}

/** Summarizes whether a pair can use Polar interpolation throughout. */
public enum class MorphCompatibility {
    FullPolar,
    Hybrid,
    Unsupported,
}

/** Public contour classification used by compatibility diagnostics. */
public enum class MorphContourKind {
    Shape,
    Hole,
    Stroke,
}

/** Explains why one contour cannot use Polar interpolation. */
public enum class MorphFallbackReason {
    MissingSourceContour,
    MissingTargetContour,
    DegenerateSimilarity,
}

/** Diagnostic information for one matched or synthesized contour. */
public data class MorphContourReport(
    public val index: Int,
    public val kind: MorphContourKind,
    public val interpolation: MorphInterpolation,
    public val fallbackReason: MorphFallbackReason? = null,
    public val residual: Double,
    public val strategy: MorphAppliedStrategy = MorphAppliedStrategy.Outline,
    public val sourceContours: List<Int> = emptyList(),
    public val targetContours: List<Int> = emptyList(),
    public val sharedAnchorCount: Int = 0,
    public val localMotionCount: Int = 0,
    public val holeOpeningCount: Int = 0,
    public val decision: String = "",
)

/** A non-throwing compatibility verdict for an ImageVector pair. */
public data class MorphCompatibilityReport(
    public val compatibility: MorphCompatibility,
    public val contours: List<MorphContourReport>,
    public val issues: List<String> = emptyList(),
) {
    public val isSupported: Boolean
        get() = compatibility != MorphCompatibility.Unsupported
}

/** Controls how stateful morph animations respond to reduced-motion preferences. */
public enum class MorphMotionPolicy {
    /** Animate even when the host duration scale disables motion. */
    Always,

    /** Honor the host Compose motion-duration scale. */
    System,

    /** Snap to the destination without animating. */
    Never,
}
