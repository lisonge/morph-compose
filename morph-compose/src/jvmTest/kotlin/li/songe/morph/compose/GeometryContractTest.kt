package li.songe.morph.compose

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathSegment
import java.awt.geom.Path2D
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Input/strategy contracts expressed without any icon-pack fixtures. */
class GeometryContractTest {
    private val size = Size(24f, 24f)
    private fun rectangle(start: Int = 0, split: Boolean = false): MorphGeometry {
        val vertices = if (split) listOf(4f to 5f, 12f to 5f, 20f to 5f, 20f to 19f, 4f to 19f)
            else listOf(4f to 5f, 20f to 5f, 20f to 19f, 4f to 19f)
        return morphGeometryOf(Path().apply {
            val first = vertices[start]
            moveTo(first.first, first.second)
            for (i in 1 until vertices.size) {
                val p = vertices[(start + i) % vertices.size]
                lineTo(p.first, p.second)
            }
            close()
        }, size)
    }

    @Test fun identityCyclicStartAndEquivalentSubdivisionKeepTheSameInk() {
        val source = rectangle()
        for (policy in MorphContourStrategy.entries) for (rotation in MorphRotationPreference.entries) {
            for (target in listOf(source, rectangle(2), rectangle(split = true))) {
                val plan = buildMorphPlan(source, target, MorphOptions(contourStrategy = policy, rotationPreference = rotation))
                val writer = plan.createPathWriter()
                for (mode in MorphInterpolation.entries) {
                    fun frame(t: Float) = coverage(Path().also { writer.writePath(it, t, size, interpolation = mode) })
                    val expected = frame(0f)
                    for (t in listOf(-0.1f, 0.001f, 0.25f, 0.5f, 0.999f, 1.1f)) {
                        assertTrue(expected.contentEquals(frame(t)), "$policy $rotation $mode t=$t changed equivalent geometry")
                    }
                }
            }
        }
    }

    @Test fun ordinaryPolicyDoesNotEnterSharedOrExperimentalStrategies() {
        val a = rectangle().vector
        for (policy in MorphContourStrategy.entries) {
            val plan = buildMorphPlan(a, a, MorphOptions(contourStrategy = policy))
            val report = plan.compatibilityReport.contours.single()
            assertEquals(listOf(0), report.sourceContours)
            assertEquals(listOf(0), report.targetContours)
            assertEquals(0, report.holeOpeningCount)
            if (policy == MorphContourStrategy.Standard) {
                assertEquals(MorphAppliedStrategy.Outline, report.strategy)
                assertEquals(0, report.sharedAnchorCount)
                assertTrue(plan.corePlan.items.all { it.boundaryMotions.isEmpty() })
            } else {
                assertEquals(MorphAppliedStrategy.SharedBoundary, report.strategy)
                assertTrue(report.sharedAnchorCount >= 2)
            }
        }
        assertEquals(MorphContourStrategy.SharedBoundary, MorphOptions().contourStrategy)
    }

    @Test fun identityDoesNotRepairAnExistingSelfIntersection() {
        val geometry = morphGeometryOf(Path().apply {
            moveTo(4f, 4f); lineTo(20f, 20f); lineTo(4f, 20f); lineTo(20f, 4f); close()
        }, size)
        val plan = buildMorphPlan(geometry, geometry, MorphOptions(contourStrategy = MorphContourStrategy.Standard))
        val writer = plan.createPathWriter()
        fun frame(t: Float) = coverage(Path().also { writer.writePath(it, t, size) })
        val expected = frame(0f)
        for (t in listOf(0.1f, 0.5f, 0.9f)) assertTrue(expected.contentEquals(frame(t)))
    }

    private fun coverage(path: Path): BooleanArray {
        val shape = Path2D.Float(Path2D.WIND_NON_ZERO)
        for (segment in path.iterator().asSequence()) {
            val p = segment.points
            assertTrue(p.all { it.isFinite() })
            when (segment.type) {
                PathSegment.Type.Move -> shape.moveTo(p[0].toDouble(), p[1].toDouble())
                PathSegment.Type.Line -> shape.lineTo(p[2].toDouble(), p[3].toDouble())
                PathSegment.Type.Cubic -> shape.curveTo(p[2].toDouble(), p[3].toDouble(), p[4].toDouble(), p[5].toDouble(), p[6].toDouble(), p[7].toDouble())
                PathSegment.Type.Close -> shape.closePath()
                PathSegment.Type.Done -> Unit
                else -> error("Unexpected segment ${segment.type}")
            }
        }
        // Avoid querying exactly on x=y edges: subdivision roundoff can choose either side
        // of an infinitesimal boundary without changing the filled region.
        return BooleanArray(96 * 96) { i -> shape.contains((i % 96 + 0.37) / 4, (i / 96 + 0.61) / 4) }
    }
}
