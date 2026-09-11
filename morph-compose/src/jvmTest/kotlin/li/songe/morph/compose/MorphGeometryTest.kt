package li.songe.morph.compose

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathSegment
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.animation.core.tween
import androidx.compose.runtime.MonotonicFrameClock
import kotlinx.coroutines.runBlocking
import kotlin.math.hypot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MorphGeometryTest {
    private val viewport = Size(100f, 100f)
    private fun rectangle() = Path().apply { addRect(Rect(0f, 0f, 100f, 100f)) }
    private fun circle() = Path().apply { addOval(Rect(0f, 0f, 100f, 100f)) }

    @Test fun copiesMutableInputAndPreservesRestingBounds() {
        val path = rectangle()
        val geometry = morphGeometryOf(path, viewport)
        path.reset()
        val output = Path()
        buildMorphPlan(geometry, geometry).createPathWriter().writePath(output, 0f, viewport)
        assertEquals(Rect(0f, 0f, 100f, 100f), output.getBounds())
    }

    @Test fun fitCentersAndFillBoundsUsesBothAxes() {
        val square = morphGeometryOf(rectangle(), viewport)
        val plan = buildMorphPlan(square, square)
        val path = Path()
        plan.createPathWriter().writePath(path, 1f, Size(200f, 100f))
        assertEquals(Rect(50f, 0f, 150f, 100f), path.getBounds())
        plan.createPathWriter().writePath(path, 1f, Size(200f, 100f), MorphContentScale.FillBounds)
        assertEquals(Rect(0f, 0f, 200f, 100f), path.getBounds())
    }

    @Test fun outlinesDoNotAliasWhenTheSameShapeIsMeasuredTwice() {
        val square = morphGeometryOf(rectangle(), viewport)
        val shape = MorphShape(buildMorphPlan(square, square), 0.5f)
        val first = shape.createOutline(viewport, LayoutDirection.Ltr, Density(1f)) as Outline.Generic
        val firstBounds = first.path.getBounds()
        val second = shape.createOutline(Size(240f, 320f), LayoutDirection.Ltr, Density(1f)) as Outline.Generic
        assertEquals(firstBounds, first.path.getBounds())
        assertFalse(first.path === second.path)
        assertTrue(second.path.getBounds().width > firstBounds.width)
    }

    @Test fun endpointKeepsCurvesAndIntermediateFrameIsFinite() {
        val plan = buildMorphPlan(morphGeometryOf(rectangle(), viewport), morphGeometryOf(circle(), viewport))
        val path = Path()
        plan.createPathWriter().writePath(path, 1f, viewport)
        assertTrue(path.iterator().asSequence().any { it.type == PathSegment.Type.Cubic })
        for (progress in listOf(-0.1f, 0.25f, 0.5f, 0.75f, 1.1f)) {
            plan.createPathWriter().writePath(path, progress, viewport)
            assertTrue(path.iterator().asSequence().all { segment -> segment.points.all { it.isFinite() } })
        }
    }

    @Test fun holesCanDisappearAndEmptyInputIsRejected() {
        val ring = circle().apply { addOval(Rect(30f, 30f, 70f, 70f)) }
        val plan = buildMorphPlan(morphGeometryOf(ring, viewport), morphGeometryOf(circle(), viewport))
        assertEquals(2, plan.contourCount)
        assertEquals(MorphCompatibility.Hybrid, plan.compatibilityReport.compatibility)
        assertFailsWith<IllegalArgumentException> { morphGeometryOf(Path(), viewport) }
    }

    @Test fun validatesInputsAndClearsZeroSizedOutput() {
        assertFailsWith<IllegalArgumentException> { morphGeometryOf(rectangle(), Size(Float.NaN, 100f)) }
        assertFailsWith<IllegalArgumentException> { morphGeometryOf(circle(), viewport, conicTolerance = 0f) }
        val square = morphGeometryOf(rectangle(), viewport)
        val plan = buildMorphPlan(square, square)
        assertFailsWith<IllegalArgumentException> { plan.createPathWriter().writePath(Path(), Float.NaN, viewport) }
        assertFailsWith<IllegalArgumentException> { plan.createPathWriter().writePath(Path(), 0.5f, Size(-1f, 10f)) }
        val output = rectangle()
        plan.createPathWriter().writePath(output, 0.5f, Size.Zero)
        assertTrue(output.isEmpty)
    }

    @Test fun conicCircleRetainsItsRadiusWithinRequestedTolerance() {
        val geometry = morphGeometryOf(circle(), viewport, conicTolerance = 0.01f)
        for (contour in geometry.vector.toCubicPaths(false)) {
            for (segment in 0 until contour.segmentCount) {
                val p = contour.points
                val i = segment * 6
                for (step in 0..8) {
                    val t = step / 8.0
                    val u = 1 - t
                    val x = u*u*u*p[i] + 3*u*u*t*p[i+2] + 3*u*t*t*p[i+4] + t*t*t*p[i+6]
                    val y = u*u*u*p[i+1] + 3*u*u*t*p[i+3] + 3*u*t*t*p[i+5] + t*t*t*p[i+7]
                    assertEquals(0.5, hypot(x - 0.5, y - 0.5), 0.0001)
                }
            }
        }
    }

    @Test fun animationContinuesFromTheScrubbedFrame() {
        val square = morphGeometryOf(rectangle(), viewport)
        val round = morphGeometryOf(circle(), viewport)
        val state = MorphGeometryState(MorphIconState(square.vector, MorphOptions(), false, MorphInterpolation.Polar))
        runBlocking { state.seekTo(square, round, 0.42f) }
        val expected = state.delegate.plan.corePlan.createFrame()
        state.delegate.plan.corePlan.interpolate(state.progress.toDouble(), expected)
        var checkedStart = false
        var time = 0L
        val clock = object : MonotonicFrameClock {
            override suspend fun <R> withFrameNanos(onFrame: (Long) -> R): R {
                if (!checkedStart) {
                    val actual = state.delegate.plan.corePlan.createFrame()
                    state.delegate.plan.corePlan.interpolate(state.progress.toDouble(), actual)
                    for (i in 0 until expected.sampleCount) {
                        assertEquals(expected.x(0, i), actual.x(0, i), 1e-8)
                        assertEquals(expected.y(0, i), actual.y(0, i), 1e-8)
                    }
                    checkedStart = true
                }
                time += 16_000_000
                return onFrame(time)
            }
        }
        runBlocking(clock) { state.animateTo(square, tween(64)) }
        assertTrue(checkedStart)
        assertEquals(1f, state.progress)
        assertFalse(state.isRunning)
    }
}
