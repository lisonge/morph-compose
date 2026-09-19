package li.songe.morph.compose

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp
import java.awt.BasicStroke
import java.awt.geom.Area
import java.awt.geom.Path2D
import li.songe.morph.compose.internal.CubicPath
import li.songe.morph.compose.internal.MorphContourRole
import li.songe.morph.compose.internal.snapshotContours
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class StrokeOutlineTest {
    private fun cross(cap: StrokeCap = StrokeCap.Round, join: StrokeJoin = StrokeJoin.Round) =
        ImageVector.Builder("Cross", 24.dp, 24.dp, 24f, 24f).apply {
            path(fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 2f,
                strokeLineCap = cap, strokeLineJoin = join) {
                moveTo(6f, 6f); lineTo(18f, 18f)
                moveTo(18f, 6f); lineTo(6f, 18f)
            }
        }.build()
    private val play = ImageVector.Builder("Play", 24.dp, 24.dp, 24f, 24f).apply {
        path(fill = SolidColor(Color.Black)) { moveTo(6f, 4f); lineTo(20f, 12f); lineTo(6f, 20f); close() }
    }.build()

    @Test fun crossPlayUsesOneMergedShapeInBothDirections() {
        for ((from, to) in listOf(cross() to play, play to cross())) {
            val plan = buildMorphPlan(from, to)
            assertEquals(1, plan.contourCount)
            assertEquals(MorphCompatibility.FullPolar, plan.compatibilityReport.compatibility)
            assertEquals(MorphContourKind.Shape, plan.compatibilityReport.contours.single().kind)
            for (mode in MorphInterpolation.entries) {
                val a = plan.corePlan.snapshotContours(0.000001, mode).map { it.asCubicPath() }
                val b = plan.corePlan.snapshotContours(0.999999, mode).map { it.asCubicPath() }
                assertCoverage(from.toCubicPaths(false), a)
                assertCoverage(to.toCubicPaths(false), b)
                val mid = plan.corePlan.snapshotContours(0.5, mode).map { it.asCubicPath() }
                assertTrue(area(mid).contains(0.5, 0.5), "The shape must not disappear mid-flight")
            }
        }
    }

    @Test fun capsJoinsAndClosedRingMatchTheirStrokedInk() {
        for (cap in listOf(StrokeCap.Butt, StrokeCap.Round, StrokeCap.Square)) {
            for (join in listOf(StrokeJoin.Miter, StrokeJoin.Round, StrokeJoin.Bevel)) {
                val vector = ImageVector.Builder("Corner", 24.dp, 24.dp, 24f, 24f).apply {
                    path(fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 3f,
                        strokeLineCap = cap, strokeLineJoin = join) {
                        moveTo(4f, 18f); lineTo(12f, 4f); lineTo(20f, 18f)
                    }
                }.build()
                val source = vector.toCubicPaths(false)
                assertCoverage(source, source.expandStrokes(MorphOptions()))
            }
        }
        val ring = ImageVector.Builder("Ring", 24.dp, 24.dp, 24f, 24f).apply {
            path(fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 2f) {
                moveTo(4f, 4f); lineTo(20f, 4f); lineTo(20f, 20f); lineTo(4f, 20f); close()
            }
        }.build().toCubicPaths(false)
        val outline = ring.expandStrokes(MorphOptions())
        assertEquals(1, outline.count { it.role == MorphContourRole.Hole })
        assertCoverage(ring, outline)
    }

    @Test fun interruptedCenterlineConvertsToOutlineWithoutLosingItsVisibleShape() {
        val options = MorphOptions()
        val search = ImageVector.Builder("Search", 24.dp, 24.dp, 24f, 24f).apply {
            path(fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
                moveTo(15f, 9f)
                curveTo(15f, 1f, 3f, 1f, 3f, 9f)
                curveTo(3f, 17f, 15f, 17f, 15f, 9f)
                moveTo(13f, 13f); lineTo(20f, 20f)
            }
        }.build()
        assertCoverage(search.toCubicPaths(false), search.toCubicPaths(false).expandStrokes(options))
        val initial = buildMorphPlan(cross(), search).corePlan
        for (mode in MorphInterpolation.entries) {
            val before = initial.snapshotContours(0.37, mode)
            val next = buildInterruptedVectorPlan(before, play.toCubicPaths(false), options)
            assertCoverage(before.map { it.asCubicPath() }, next.snapshotContours(0.0, mode).map { it.asCubicPath() })
            val middle = next.snapshotContours(0.43, mode)
            val again = buildInterruptedVectorPlan(middle, cross().toCubicPaths(false), options)
            assertCoverage(middle.map { it.asCubicPath() }, again.snapshotContours(0.0, mode).map { it.asCubicPath() })
        }
    }

    @Test fun explicitModesAndStrokeCountPoliciesAreHonored() {
        assertEquals(MorphContourKind.Stroke, buildMorphPlan(cross(), cross()).compatibilityReport.contours.first().kind)
        assertEquals(1, buildMorphPlan(cross(), cross(), MorphOptions(transitionMode = MorphTransitionMode.Outline)).contourCount)
        assertFailsWith<IllegalArgumentException> {
            buildMorphPlan(cross(), play, MorphOptions(transitionMode = MorphTransitionMode.Centerline))
        }
        val line = ImageVector.Builder("Line", 24.dp, 24.dp, 24f, 24f).apply {
            path(fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 2f) { moveTo(4f, 12f); lineTo(20f, 12f) }
        }.build()
        assertEquals(MorphCompatibility.FullPolar, buildMorphPlan(cross(), line).compatibilityReport.compatibility)
        assertEquals(MorphCompatibility.Hybrid, buildMorphPlan(cross(), line,
            MorphOptions(strokeCountStrategy = MorphStrokeCountStrategy.Collapse)).compatibilityReport.compatibility)
    }

    private fun area(paths: List<CubicPath>): Area {
        val fill = Path2D.Double(Path2D.WIND_NON_ZERO)
        val ink = Area()
        for (contour in paths) {
            val path = Path2D.Double()
            val p = contour.points
            path.moveTo(p[0], p[1])
            for (i in 2 until p.size step 6) path.curveTo(p[i], p[i + 1], p[i + 2], p[i + 3], p[i + 4], p[i + 5])
            if (contour.closed) path.closePath()
            val stroke = contour.stroke
            if (stroke == null) fill.append(path, false) else {
                val cap = when (stroke.cap) { StrokeCap.Round -> BasicStroke.CAP_ROUND; StrokeCap.Square -> BasicStroke.CAP_SQUARE; else -> BasicStroke.CAP_BUTT }
                val join = when (stroke.join) { StrokeJoin.Round -> BasicStroke.JOIN_ROUND; StrokeJoin.Bevel -> BasicStroke.JOIN_BEVEL; else -> BasicStroke.JOIN_MITER }
                ink.add(Area(BasicStroke(stroke.width.toFloat(), cap, join, stroke.miter).createStrokedShape(path)))
            }
        }
        ink.add(Area(fill))
        return ink
    }

    private fun assertCoverage(expected: List<CubicPath>, actual: List<CubicPath>) {
        val a = area(expected); val b = area(actual)
        var difference = 0
        for (y in 0 until 256) for (x in 0 until 256) {
            val px = (x + 0.5) / 256; val py = (y + 0.5) / 256
            val inside = a.contains(px, py)
            if (inside != b.contains(px, py)) {
                // Expansion is tolerance-bounded, not pixel-identical at an arbitrarily aligned grid.
                val tolerance = 4 * MorphOptions().outlineTolerance
                val onBoundary = listOf(-tolerance, 0.0, tolerance).any { dx ->
                    listOf(-tolerance, 0.0, tolerance).any { dy -> a.contains(px + dx, py + dy) != inside }
                }
                if (!onBoundary) difference++
            }
        }
        assertTrue(difference <= 8, "Expanded/transitioned ink differs by $difference pixels")
    }
}
