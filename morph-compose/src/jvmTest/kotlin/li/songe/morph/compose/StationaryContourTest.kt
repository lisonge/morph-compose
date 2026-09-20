package li.songe.morph.compose

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathSegment
import li.songe.morph.compose.internal.buildMorphPlanFromSampledSource
import li.songe.morph.compose.internal.snapshotContours
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import java.awt.geom.Path2D
import java.awt.image.BufferedImage

class StationaryContourTest {
    private val size = Size(100f, 100f)
    private val options = MorphOptions(sampleCount = 32)
    private fun geometry(right: Float) = morphGeometryOf(Path().apply {
        addOval(Rect(5f, 35f, 75f, 95f))
        addRect(Rect(80f, 5f, right, 20f))
    }, size)

    private fun render(plan: ImageVectorMorphPlan, progress: Float, mode: MorphInterpolation) =
        Path().also { MorphPathRenderer(plan).writePath(it, progress, size, MorphContentScale.Fit, mode) }

    @Test fun sharedCurvedContourStaysCurvedInBothDirectionsAndModes() {
        val a = geometry(90f).vector
        val b = geometry(98f).vector
        for ((from, to) in listOf(a to b, b to a)) {
            for (mode in MorphInterpolation.entries) {
                val plan = buildMorphPlan(from, to, options)
                val expected = mask(render(plan, 0f, mode))
                for (progress in listOf(-0.1f, 0.001f, 0.5f, 0.999f, 1.1f)) {
                    val path = render(plan, progress, mode)
                    assertTrue(path.iterator().asSequence().any { it.type == PathSegment.Type.Cubic },
                        "The shared oval lost its curves at $progress ($mode)")
                    val actual = mask(path)
                    for (y in 120 until 400) for (x in 0 until 400) {
                        assertEquals(expected.getRGB(x, y), actual.getRGB(x, y), "Outline changed at $x,$y")
                    }
                }
            }
        }
    }

    @Test fun interruptedSharedCurveStartsFromTheRenderedCurve() {
        val a = geometry(90f).vector
        val b = geometry(98f).vector
        val target = morphGeometryOf(Path().apply { addRect(Rect(10f, 10f, 90f, 90f)) }, size).vector
        for (mode in MorphInterpolation.entries) {
            val original = buildMorphPlan(a, b, options)
            val expected = render(original, 0.4f, mode)
            assertTrue(expected.iterator().asSequence().any { it.type == PathSegment.Type.Cubic })
            val core = buildMorphPlanFromSampledSource(
                original.corePlan.snapshotContours(0.4f.toDouble(), mode), target.toCubicPaths(false), options,
            )
            val interrupted = ImageVectorMorphPlan(core, null, target, original.defaultWidth, original.defaultHeight,
                sourcePaths = null, targetPaths = target.toCubicPaths(false))
            val actual = render(interrupted, 0f, mode)
            val before = expected.iterator().asSequence().toList()
            val after = actual.iterator().asSequence().toList()
            assertEquals(before.size, after.size)
            for (i in before.indices) {
                assertEquals(before[i].type, after[i].type)
                before[i].points.indices.forEach { j -> assertEquals(before[i].points[j], after[i].points[j], 0.0001f) }
            }
        }
    }

    @Test fun retainedHoleKeepsItsWindingAndOpening() {
        fun ring(right: Float) = morphGeometryOf(Path().apply {
            addOval(Rect(5f, 35f, 75f, 95f))
            addOval(Rect(25f, 50f, 55f, 80f))
            addRect(Rect(80f, 5f, right, 20f))
        }, size).vector
        val plan = buildMorphPlan(ring(90f), ring(98f), options)
        val expected = mask(render(plan, 0f, MorphInterpolation.Polar))
        val actual = mask(render(plan, 0.5f, MorphInterpolation.Polar))
        assertEquals(0, actual.getRGB(160, 260))
        assertTrue(actual.getRGB(160, 150) != 0)
        for (y in 120 until 400) for (x in 0 until 400) assertEquals(expected.getRGB(x, y), actual.getRGB(x, y))
    }

    @Test fun interruptedMovingCurvesKeepTheirOutlineWhenTargetSegmentationChanges() {
        val oval = morphGeometryOf(Path().apply { addOval(Rect(8f, 18f, 88f, 92f)) }, size).vector
        val rounded = morphGeometryOf(Path().apply {
            moveTo(10f, 15f)
            cubicTo(75f, 0f, 100f, 35f, 85f, 75f)
            cubicTo(65f, 100f, 20f, 70f, 10f, 15f)
            close()
        }, size).vector
        val triangle = morphGeometryOf(Path().apply {
            moveTo(10f, 80f); lineTo(50f, 10f); lineTo(90f, 80f); close()
        }, size).vector
        for (mode in MorphInterpolation.entries) {
            var plan = buildMorphPlan(triangle, oval, options)
            for (target in listOf(rounded, triangle, oval)) {
                val before = mask(render(plan, 0.43f, mode))
                val core = buildMorphPlanFromSampledSource(
                    plan.corePlan.snapshotContours(0.43f.toDouble(), mode), target.toCubicPaths(false), options,
                )
                plan = ImageVectorMorphPlan(core, null, target, plan.defaultWidth, plan.defaultHeight,
                    sourcePaths = null, targetPaths = target.toCubicPaths(false))
                val after = mask(render(plan, 0f, mode))
                for (y in 0 until 400) for (x in 0 until 400) {
                    assertEquals(before.getRGB(x, y), after.getRGB(x, y), "Interrupted outline changed at $x,$y ($mode)")
                }
            }
        }
    }

    @Test fun interruptedLocalBoundaryMotionStartsAtTheRenderedFrame() {
        fun shape(left: Float) = morphGeometryOf(Path().apply {
            moveTo(10f, 40f)
            lineTo(left, 40f)
            lineTo(left, 10f)
            lineTo(left + 20f, 10f)
            lineTo(left + 20f, 40f)
            lineTo(90f, 40f)
            lineTo(90f, 90f)
            lineTo(10f, 90f)
            close()
        }, size).vector
        val a = shape(25f)
        val b = shape(55f)
        for (mode in MorphInterpolation.entries) {
            val original = buildMorphPlan(a, b, options)
            assertTrue(original.corePlan.items.any { it.boundaryMotions.isNotEmpty() })
            val expected = mask(render(original, 0.43f, mode))
            val core = buildMorphPlanFromSampledSource(
                original.corePlan.snapshotContours(0.43f.toDouble(), mode), a.toCubicPaths(false), options)
            val interrupted = ImageVectorMorphPlan(core, null, a, original.defaultWidth, original.defaultHeight,
                sourcePaths = null, targetPaths = a.toCubicPaths(false))
            val actual = mask(render(interrupted, 0f, mode))
            for (y in 0 until 400) for (x in 0 until 400) {
                assertEquals(expected.getRGB(x, y), actual.getRGB(x, y), "Local motion jumped at $x,$y ($mode)")
            }
        }
    }

    private fun mask(path: Path): BufferedImage {
        val shape = Path2D.Float(Path2D.WIND_NON_ZERO)
        for (segment in path.iterator().asSequence()) {
            val p = segment.points
            when (segment.type) {
                PathSegment.Type.Move -> shape.moveTo(p[0].toDouble(), p[1].toDouble())
                PathSegment.Type.Line -> shape.lineTo(p[2].toDouble(), p[3].toDouble())
                PathSegment.Type.Cubic -> shape.curveTo(p[2].toDouble(), p[3].toDouble(), p[4].toDouble(), p[5].toDouble(), p[6].toDouble(), p[7].toDouble())
                PathSegment.Type.Close -> shape.closePath()
                PathSegment.Type.Done -> Unit
                else -> error("Expected normalized cubics, got ${segment.type}")
            }
        }
        return BufferedImage(400, 400, BufferedImage.TYPE_INT_ARGB).also { image ->
            // Compare geometric coverage: Java2D's rasterizer rounds identical subdivided curves
            // differently at edge pixels, independently of the path's actual boundary.
            for (y in 0 until 400) for (x in 0 until 400) {
                if (shape.contains((x + 0.5) / 4.0, (y + 0.5) / 4.0)) image.setRGB(x, y, -1)
            }
        }
    }
}
