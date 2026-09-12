package li.songe.morph.compose

import kotlin.math.PI

/** Controls the geometry generated for a morph plan. */
public data class MorphOptions(
    public val sampleCount: Int = 64,
    public val cornerThresholdRadians: Double = PI / 8.0,
    public val rotationPreference: MorphRotationPreference = MorphRotationPreference.Auto,
) {
    init {
        require(sampleCount >= 2) { "sampleCount must be at least 2" }
        require(cornerThresholdRadians.isFinite() && cornerThresholdRadians >= 0.0) {
            "cornerThresholdRadians must be finite and non-negative"
        }
    }
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
