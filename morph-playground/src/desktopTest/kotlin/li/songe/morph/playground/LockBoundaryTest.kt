package li.songe.morph.playground

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathSegment
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.VectorPath
import java.awt.geom.Path2D
import li.songe.morph.compose.MorphInterpolation
import li.songe.morph.compose.MorphOptions
import li.songe.morph.compose.MorphContourStrategy
import li.songe.morph.compose.MorphRotationPreference
import li.songe.morph.compose.buildMorphPlan
import li.songe.morph.compose.morphGeometryOf
import kotlin.test.Test
import kotlin.test.assertTrue

class LockBoundaryTest {
    @Test fun holeOpeningRequiresExplicitOptInAndReportsOriginalContourOwnership() {
        for (policy in MorphContourStrategy.entries) {
            val plan = buildMorphPlan(Icons.Outlined.Lock, LockOpenRight, MorphOptions(contourStrategy = policy))
            val reports = plan.compatibilityReport.contours
            assertTrue(reports.all { it.decision.isNotBlank() })
            assertTrue(reports.flatMap { it.sourceContours }.sorted() == listOf(0, 1, 2, 3))
            assertTrue(reports.flatMap { it.targetContours }.sorted() == listOf(0, 1, 2))
            assertTrue(reports.sumOf { it.holeOpeningCount } == if (policy == MorphContourStrategy.ExperimentalHoleOpening) 1 else 0)
        }
    }

    @Test fun closedShackleOpensIntoTheOuterBoundaryInsteadOfCollapsingAHole() {
        for (open in listOf(Icons.Filled.LockOpen, LockOpenRight)) {
            for ((a, b) in listOf(Icons.Outlined.Lock to open, open to Icons.Outlined.Lock)) {
                val plan = buildMorphPlan(a, b, MorphOptions(contourStrategy = MorphContourStrategy.ExperimentalHoleOpening))
                assertTrue(plan.compatibilityReport.contours.all { it.fallbackReason == null },
                    "${a.name} -> ${b.name}: the shackle hole must join the changing outer boundary")
                assertTrue(plan.contourCount == 3, "Expected body, body opening and keyhole dot")
            }
        }
    }

    @Test fun sharedBodyStaysWithinItsEndpointOutlines() {
        fun geometry(vector: ImageVector) = morphGeometryOf(
            (vector.root[0] as VectorPath).pathData, Size(vector.viewportWidth, vector.viewportHeight))
        val pairs = listOf(Icons.Outlined.Lock to Icons.Filled.LockOpen,
            Icons.Filled.LockOpen to LockOpenRight, Icons.Outlined.Lock to LockOpenRight)
        for ((a, b) in pairs.flatMap { listOf(it, it.second to it.first) }) {
            for (rotation in MorphRotationPreference.entries) {
                val writer = buildMorphPlan(geometry(a), geometry(b), MorphOptions(rotationPreference = rotation)).createPathWriter()
                for (mode in MorphInterpolation.entries) {
                    fun frame(t: Float) = outline(Path().also { writer.writePath(it, t, Size(24f, 24f), interpolation = mode) })
                    val start = frame(0f)
                    val end = frame(1f)
                    for (endpoint in listOf(0f, 1f)) {
                        val exact = frame(endpoint)
                        for (delta in listOf(-0.000001f, 0.000001f)) {
                            val near = frame(endpoint + delta)
                            var changed = 0
                            for (y in 0 until 384 step 2) for (x in 0 until 384 step 2) {
                                val px = (x + 0.5) / 16
                                val py = (y + 0.5) / 16
                                if (exact.contains(px, py) != near.contains(px, py)) changed++
                            }
                            assertTrue(changed <= 4, "${a.name} -> ${b.name}, $rotation $mode endpoint=$endpoint delta=$delta: $changed pixels flashed")
                        }
                    }
                    for (t in listOf(0.000001f, 0.1f, 0.25f, 0.5f, 0.75f, 0.9f, 0.999999f)) {
                        val actual = frame(t)
                        var unexpected = 0
                        // Only allow the tiny genuine cubic/quadratic differences at the endpoints.
                        for (y in 128 until 384) for (x in 0 until 384) {
                            val px = (x + 0.5) / 16
                            val py = (y + 0.5) / 16
                            val s = start.contains(px, py)
                            val e = end.contains(px, py)
                            if (s == e && actual.contains(px, py) != s) unexpected++
                        }
                        assertTrue(unexpected <= 4, "${a.name} -> ${b.name}, $rotation $mode t=$t: $unexpected body pixels moved")
                    }
                    val middle = frame(0.5f)
                    assertTrue((0 until 128).any { y -> (0 until 384).any { x ->
                        start.contains((x + 0.5) / 16, (y + 0.5) / 16) != middle.contains((x + 0.5) / 16, (y + 0.5) / 16)
                    } }, "The shackle must still animate")
                }
            }
        }
    }

    private fun outline(path: Path) = Path2D.Float(Path2D.WIND_NON_ZERO).apply {
        for (segment in path.iterator().asSequence()) {
            val p = segment.points
            when (segment.type) {
                PathSegment.Type.Move -> moveTo(p[0].toDouble(), p[1].toDouble())
                PathSegment.Type.Line -> lineTo(p[2].toDouble(), p[3].toDouble())
                PathSegment.Type.Cubic -> curveTo(p[2].toDouble(), p[3].toDouble(), p[4].toDouble(), p[5].toDouble(), p[6].toDouble(), p[7].toDouble())
                PathSegment.Type.Close -> closePath()
                PathSegment.Type.Done -> Unit
                else -> error("Unexpected segment ${segment.type}")
            }
        }
    }
}
