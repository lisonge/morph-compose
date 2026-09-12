package li.songe.morph.compose

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.group
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp
import li.songe.morph.compose.internal.MorphContourRole
import kotlin.math.hypot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ImageVectorAdapterTest {
    @Test
    fun rotationPreferenceUsesDisplayedDirectionAfterRtlMirroring() {
        fun bar(angle: Float) = ImageVector.Builder(
            name = "bar$angle", defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f, autoMirror = true,
        ).apply {
            group(rotate = angle, pivotX = 12f, pivotY = 12f) {
                path(fill = SolidColor(Color.Black)) {
                    moveTo(4f, 11f)
                    lineTo(20f, 11f)
                    lineTo(20f, 13f)
                    lineTo(4f, 13f)
                    close()
                }
            }
        }.build()
        val horizontal = bar(0f)
        val diagonal = bar(45f)
        for (rtl in listOf(false, true)) {
            for (preference in listOf(MorphRotationPreference.PreferClockwise, MorphRotationPreference.PreferCounterClockwise)) {
                val sign = if (preference == MorphRotationPreference.PreferClockwise) 1 else -1
                for ((from, to) in listOf(horizontal to diagonal, diagonal to horizontal)) {
                    val plan = buildMorphPlan(from, to, MorphOptions(rotationPreference = preference), rtl)
                    assertTrue(sign * plan.rotationRadians(0) > 0.1)
                    assertEquals(MorphCompatibility.FullPolar, plan.compatibilityReport.compatibility)
                }
            }
        }
    }

    @Test
    fun buildsPlanFromRelativeAndQuadraticNodes() {
        val source =
            vector("source") {
                path(fill = SolidColor(Color.Black)) {
                    moveTo(2.0f, 2.0f)
                    lineToRelative(16.0f, 0.0f)
                    lineToRelative(0.0f, 16.0f)
                    lineToRelative(-16.0f, 0.0f)
                    close()
                }
            }
        val target =
            vector("target") {
                path(fill = SolidColor(Color.Black)) {
                    moveTo(12.0f, 2.0f)
                    quadTo(22.0f, 12.0f, 12.0f, 22.0f)
                    quadTo(2.0f, 12.0f, 12.0f, 2.0f)
                    close()
                }
            }

        val plan = buildMorphPlan(source, target)

        assertEquals(1, plan.contourCount)
        assertEquals(64, plan.sampleCount)
        assertTrue(plan.residual(0).isFinite())
    }

    @Test
    fun preservesCompoundPathContourCount() {
        val compound =
            vector("compound") {
                path(fill = SolidColor(Color.Black)) {
                    moveTo(1.0f, 1.0f)
                    lineTo(23.0f, 1.0f)
                    lineTo(23.0f, 23.0f)
                    lineTo(1.0f, 23.0f)
                    close()
                    moveTo(8.0f, 8.0f)
                    lineTo(8.0f, 16.0f)
                    lineTo(16.0f, 16.0f)
                    lineTo(16.0f, 8.0f)
                    close()
                }
            }

        val plan = buildMorphPlan(compound, compound)
        assertEquals(2, plan.contourCount)
        assertEquals(MorphContourRole.Shape, plan.corePlan.role(0))
        assertEquals(MorphContourRole.Hole, plan.corePlan.role(1))
    }

    @Test
    fun classifiesOffCenterHoleInsideCurvedContour() {
        val compound =
            vector("curved compound") {
                path(fill = SolidColor(Color.Black)) {
                    moveTo(12.0f, 2.0f)
                    curveTo(17.52f, 2.0f, 22.0f, 6.48f, 22.0f, 12.0f)
                    curveTo(22.0f, 17.52f, 17.52f, 22.0f, 12.0f, 22.0f)
                    curveTo(6.48f, 22.0f, 2.0f, 17.52f, 2.0f, 12.0f)
                    curveTo(2.0f, 6.48f, 6.48f, 2.0f, 12.0f, 2.0f)
                    close()

                    moveTo(18.0f, 7.0f)
                    curveTo(18.0f, 7.33f, 18.27f, 7.6f, 18.6f, 7.6f)
                    curveTo(18.93f, 7.6f, 19.2f, 7.33f, 19.2f, 7.0f)
                    curveTo(19.2f, 6.67f, 18.93f, 6.4f, 18.6f, 6.4f)
                    curveTo(18.27f, 6.4f, 18.0f, 6.67f, 18.0f, 7.0f)
                    close()
                }
            }

        val plan = buildMorphPlan(compound, compound)
        val frame = plan.corePlan.createFrame()
        plan.corePlan.interpolate(0.5, frame)

        assertEquals(MorphContourRole.Shape, plan.corePlan.role(0))
        assertEquals(MorphContourRole.Hole, plan.corePlan.role(1))
        assertFalse(isFilledAt(frame, x = 18.6 / 24.0, y = 7.0 / 24.0))
    }

    @Test
    fun nestedIndependentPathsRemainIndependentShapes() {
        val vector =
            vector("independent") {
                path(fill = SolidColor(Color.Black)) {
                    moveTo(1.0f, 1.0f)
                    lineTo(23.0f, 1.0f)
                    lineTo(23.0f, 23.0f)
                    lineTo(1.0f, 23.0f)
                    close()
                }
                path(fill = SolidColor(Color.Black)) {
                    moveTo(8.0f, 8.0f)
                    lineTo(16.0f, 8.0f)
                    lineTo(16.0f, 16.0f)
                    lineTo(8.0f, 16.0f)
                    close()
                }
            }
        val plan = buildMorphPlan(vector, vector)

        assertEquals(MorphContourRole.Shape, plan.corePlan.role(0))
        assertEquals(MorphContourRole.Shape, plan.corePlan.role(1))
    }

    @Test
    fun rejectsStrokeOnlyVectorsClearly() {
        val stroke =
            vector("stroke") {
                path(
                    fill = null,
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = 2.0f,
                    strokeLineCap = StrokeCap.Round,
                ) {
                    moveTo(2.0f, 12.0f)
                    lineTo(22.0f, 12.0f)
                }
            }

        assertFailsWith<IllegalArgumentException> { buildMorphPlan(stroke, stroke) }
        val report = inspectMorphCompatibility(stroke, stroke)
        assertEquals(MorphCompatibility.Unsupported, report.compatibility)
        assertTrue(report.issues.single().contains("fill-only"))
        assertTrue(report.issues.single().contains("ImageVector 'stroke'"))
    }

    @Test
    fun appliesNestedGroupTransformsBeforePlanning() {
        val transformed =
            vector("transformed") {
                group(rotate = 90.0f, pivotX = 12.0f, pivotY = 12.0f) {
                    path(fill = SolidColor(Color.Black)) {
                        moveTo(10.0f, 8.0f)
                        lineTo(16.0f, 12.0f)
                        lineTo(10.0f, 16.0f)
                        close()
                    }
                }
            }
        val lowered =
            vector("lowered") {
                path(fill = SolidColor(Color.Black)) {
                    moveTo(16.0f, 10.0f)
                    lineTo(12.0f, 16.0f)
                    lineTo(8.0f, 10.0f)
                    close()
                }
            }

        val plan = buildMorphPlan(transformed, lowered)
        assertTrue(kotlin.math.abs(plan.rotationRadians(0)) < 1e-6)
        assertTrue(plan.residual(0) < 1e-6)
    }

    @Test
    fun filledContourTopologyChangeDoesNotExplodeDuringPolarInterpolation() {
        val close =
            vector("close") {
                path(fill = SolidColor(Color.Black)) {
                    moveTo(19.0f, 6.41f)
                    lineTo(17.59f, 5.0f)
                    lineTo(12.0f, 10.59f)
                    lineTo(6.41f, 5.0f)
                    lineTo(5.0f, 6.41f)
                    lineTo(10.59f, 12.0f)
                    lineTo(5.0f, 17.59f)
                    lineTo(6.41f, 19.0f)
                    lineTo(12.0f, 13.41f)
                    lineTo(17.59f, 19.0f)
                    lineTo(19.0f, 17.59f)
                    lineTo(13.41f, 12.0f)
                    close()
                }
            }
        val search =
            vector("search") {
                path(fill = SolidColor(Color.Black)) {
                    moveTo(15.5f, 14.0f)
                    horizontalLineToRelative(-0.79f)
                    lineToRelative(-0.28f, -0.27f)
                    curveTo(15.41f, 12.59f, 16.0f, 11.11f, 16.0f, 9.5f)
                    curveTo(16.0f, 5.91f, 13.09f, 3.0f, 9.5f, 3.0f)
                    reflectiveCurveTo(3.0f, 5.91f, 3.0f, 9.5f)
                    reflectiveCurveTo(5.91f, 16.0f, 9.5f, 16.0f)
                    curveToRelative(1.61f, 0.0f, 3.09f, -0.59f, 4.23f, -1.57f)
                    lineToRelative(0.27f, 0.28f)
                    verticalLineToRelative(0.79f)
                    lineToRelative(5.0f, 4.99f)
                    lineTo(20.49f, 19.0f)
                    lineToRelative(-4.99f, -5.0f)
                    close()
                    moveTo(9.5f, 14.0f)
                    curveTo(7.01f, 14.0f, 5.0f, 11.99f, 5.0f, 9.5f)
                    reflectiveCurveTo(7.01f, 5.0f, 9.5f, 5.0f)
                    reflectiveCurveTo(14.0f, 7.01f, 14.0f, 9.5f)
                    reflectiveCurveTo(11.99f, 14.0f, 9.5f, 14.0f)
                    close()
                }
            }

        assertSafeTopologyChange(
            buildMorphPlan(close, search),
            collapsedAtStart = true,
            fallbackReason = MorphFallbackReason.MissingSourceContour,
        )
        assertSafeTopologyChange(
            buildMorphPlan(search, close),
            collapsedAtStart = false,
            fallbackReason = MorphFallbackReason.MissingTargetContour,
        )
    }

    @Test
    fun unequalShapeCountsPreserveHoleWindingNearEndpoints() {
        val twoShapes =
            vector("two shapes") {
                path(fill = SolidColor(Color.Black)) {
                    moveTo(4.0f, 3.0f)
                    lineTo(10.0f, 3.0f)
                    lineTo(10.0f, 9.0f)
                    lineTo(4.0f, 9.0f)
                    close()
                    moveTo(5.0f, 15.0f)
                    lineTo(19.0f, 15.0f)
                    lineTo(19.0f, 21.0f)
                    lineTo(5.0f, 21.0f)
                    close()
                }
            }
        val shapeWithHole =
            vector("shape with hole") {
                path(fill = SolidColor(Color.Black)) {
                    moveTo(2.0f, 2.0f)
                    lineTo(22.0f, 2.0f)
                    lineTo(22.0f, 22.0f)
                    lineTo(2.0f, 22.0f)
                    close()
                    moveTo(8.0f, 8.0f)
                    lineTo(8.0f, 16.0f)
                    lineTo(16.0f, 16.0f)
                    lineTo(16.0f, 8.0f)
                    close()
                }
            }

        val forward = buildMorphPlan(twoShapes, shapeWithHole)
        val reverse = buildMorphPlan(shapeWithHole, twoShapes)

        assertPartialMatching(forward)
        assertPartialMatching(reverse)
        assertHoleNearEndpoint(forward, progress = 0.999)
        assertHoleNearEndpoint(reverse, progress = 0.001)
    }

    private fun assertSafeTopologyChange(
        plan: ImageVectorMorphPlan,
        collapsedAtStart: Boolean,
        fallbackReason: MorphFallbackReason,
    ) {
        val frame = plan.corePlan.createFrame()

        assertEquals(2, plan.contourCount)
        assertEquals(MorphContourRole.Shape, plan.corePlan.role(0))
        assertEquals(MorphContourRole.Hole, plan.corePlan.role(1))
        assertEquals(MorphCompatibility.Hybrid, plan.compatibilityReport.compatibility)
        assertEquals(MorphInterpolation.Polar, plan.compatibilityReport.contours[0].interpolation)
        assertEquals(MorphInterpolation.Linear, plan.compatibilityReport.contours[1].interpolation)
        assertEquals(fallbackReason, plan.compatibilityReport.contours[1].fallbackReason)

        plan.corePlan.interpolate(if (collapsedAtStart) 0.0 else 1.0, frame)
        assertTrue(contourDiameter(frame, 1) < 1e-9, "A missing hole must collapse cleanly")

        plan.corePlan.interpolate(0.5, frame)
        for (contour in 0 until frame.contourCount) {
            for (point in 0 until frame.sampleCount) {
                assertTrue(frame.x(contour, point) in -0.25..1.25)
                assertTrue(frame.y(contour, point) in -0.25..1.25)
            }
        }
    }

    private fun assertPartialMatching(plan: ImageVectorMorphPlan) {
        val contours = plan.compatibilityReport.contours
        assertEquals(3, contours.size)
        assertEquals(1, contours.count { it.interpolation == MorphInterpolation.Polar })
        assertEquals(
            setOf(
                MorphFallbackReason.MissingSourceContour,
                MorphFallbackReason.MissingTargetContour,
            ),
            contours.mapNotNull { it.fallbackReason }.toSet(),
        )
    }

    private fun assertHoleNearEndpoint(plan: ImageVectorMorphPlan, progress: Double) {
        val frame = plan.corePlan.createFrame()
        plan.corePlan.interpolate(progress, frame)

        assertTrue(isFilledAt(frame, x = 0.2, y = 0.5), "The outer shape must remain filled")
        assertFalse(isFilledAt(frame, x = 0.5, y = 0.5), "The center hole must remain visible")
    }
}

private fun isFilledAt(
    frame: li.songe.morph.compose.internal.MorphFrame,
    x: Double,
    y: Double,
): Boolean {
    var winding = 0
    for (contour in 0 until frame.contourCount) {
        if (!frame.isClosed(contour)) continue
        for (point in 0 until frame.sampleCount) {
            val next = (point + 1) % frame.sampleCount
            val x1 = frame.x(contour, point)
            val y1 = frame.y(contour, point)
            val x2 = frame.x(contour, next)
            val y2 = frame.y(contour, next)
            val side = (x2 - x1) * (y - y1) - (y2 - y1) * (x - x1)
            if (y1 <= y && y2 > y && side > 0.0) winding++
            if (y1 > y && y2 <= y && side < 0.0) winding--
        }
    }
    return winding != 0
}

private fun contourDiameter(
    frame: li.songe.morph.compose.internal.MorphFrame,
    contour: Int,
): Double {
    var diameter = 0.0
    for (first in 0 until frame.sampleCount) {
        for (second in first + 1 until frame.sampleCount) {
            diameter =
                maxOf(
                    diameter,
                    hypot(
                        frame.x(contour, first) - frame.x(contour, second),
                        frame.y(contour, first) - frame.y(contour, second),
                    ),
                )
        }
    }
    return diameter
}

private fun vector(
    name: String,
    content: ImageVector.Builder.() -> Unit,
): ImageVector =
    ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24.0f,
        viewportHeight = 24.0f,
    ).apply(content).build()
