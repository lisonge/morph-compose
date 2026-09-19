package li.songe.morph.compose

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.group
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp
import li.songe.morph.compose.internal.buildMorphPlanFromSampledSource
import li.songe.morph.compose.internal.snapshotContours
import li.songe.morph.compose.internal.strokeAt
import kotlin.math.hypot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class StrokeMorphTest {
    @Test fun closeBackDirectionUsesShortRotationRegardlessOfStrokeOrder() {
        val reversedClose = stroke {
            moveTo(18f, 6f); lineTo(6f, 18f)
            moveTo(6f, 6f); lineTo(18f, 18f)
        }
        for (x in listOf(close, reversedClose)) {
            for ((from, to) in listOf(x to back, back to x)) {
                for (preference in MorphRotationPreference.entries) {
                    val plan = buildMorphPlan(from, to, MorphOptions(rotationPreference = preference)).corePlan
                    plan.items.forEach { item ->
                        val degrees = item.theta * 180 / kotlin.math.PI
                        assertTrue(kotlin.math.abs(degrees) in 40.0..50.0, "$preference: $degrees")
                        if (preference == MorphRotationPreference.PreferClockwise) assertTrue(degrees > 0)
                        if (preference == MorphRotationPreference.PreferCounterClockwise) assertTrue(degrees < 0)
                    }
                }
            }
        }
    }
    private val close = stroke {
        moveTo(6f, 6f); lineTo(18f, 18f)
        moveTo(18f, 6f); lineTo(6f, 18f)
    }
    private val back = stroke {
        moveTo(19f, 12f); lineTo(5f, 12f)
        moveTo(12f, 5f); lineTo(5f, 12f); lineTo(12f, 19f)
    }
    private val menu = stroke {
        moveTo(4f, 6f); lineTo(20f, 6f)
        moveTo(4f, 12f); lineTo(20f, 12f)
        moveTo(4f, 18f); lineTo(20f, 18f)
    }

    @Test fun closeBackKeepsSeparateConstantWidthStrokesThroughout() {
        for ((from, to) in listOf(close to back, back to close)) {
            for (preference in MorphRotationPreference.entries) {
                val plan = buildMorphPlan(from, to, MorphOptions(rotationPreference = preference)).corePlan
                assertEquals(2, plan.contourCount)
                val frame = plan.createFrame()
                for (step in 0..100) {
                    val t = step / 100.0
                    plan.interpolate(t, frame)
                    plan.items.forEachIndexed { i, item ->
                        assertTrue(!frame.isClosed(i))
                        assertTrue(item.polarInterpolation)
                        assertEquals(2.0 / 24, item.strokeAt(t)!!.width, 1e-9)
                        val length = (1 until frame.sampleCount).sumOf { j ->
                            hypot(frame.x(i, j) - frame.x(i, j - 1), frame.y(i, j) - frame.y(i, j - 1))
                        }
                        assertTrue(length > 0.4, "Stroke collapsed at $t: $length ($preference)")
                    }
                }
            }
        }
    }

    @Test fun unequalStrokeCountsSplitAndMergeWithoutPointCollapse() {
        for ((from, to) in listOf(close to menu, menu to close)) {
            val plan = buildMorphPlan(from, to).corePlan
            assertEquals(3, plan.contourCount)
            assertTrue(plan.items.all { it.polarInterpolation && it.fallbackReason == null })
            for (t in listOf(0.0, 0.5, 1.0)) {
                val frame = plan.createFrame()
                plan.interpolate(t, frame)
                for (i in 0 until frame.contourCount) {
                    assertTrue(hypot(frame.x(i, 0) - frame.x(i, frame.sampleCount - 1),
                        frame.y(i, 0) - frame.y(i, frame.sampleCount - 1)) > 0.5)
                }
            }
        }
    }

    @Test fun interruptedStrokeKeepsItsWidthAndExactCenterline() {
        val wide = stroke(4f) { moveTo(4f, 12f); lineTo(20f, 12f) }
        val plan = buildMorphPlan(close, wide).corePlan
        val snapshot = plan.snapshotContours(0.37, MorphInterpolation.Polar)
        val next = buildMorphPlanFromSampledSource(snapshot, back.toCubicPaths(false), MorphOptions())
        val frame = next.createFrame()
        next.interpolate(0.0, frame)
        next.items.forEachIndexed { i, item ->
            val original = snapshot.first { it.points.contentEquals(item.source) }
            assertEquals(original.stroke, item.strokeAt(0.0))
            for (j in original.points.indices) assertEquals(original.points[j], frame.points[i][j], 1e-9)
            assertEquals((2 + 2 * 0.37) / 24, original.stroke!!.width, 1e-9)
        }
    }

    @Test fun closedStrokeRemainsAStrokeAndMirrorsInRtl() {
        val icon = ImageVector.Builder("loop", 24.dp, 24.dp, 24f, 24f, autoMirror = true).apply {
            path(fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 2f) {
                moveTo(2f, 2f); lineTo(8f, 2f); lineTo(8f, 8f); close()
            }
        }.build()
        val ltr = icon.toCubicPaths(false).single()
        val rtl = icon.toCubicPaths(true).single()
        assertTrue(ltr.closed && rtl.closed)
        assertEquals(ltr.stroke, rtl.stroke)
        for (i in ltr.points.indices step 2) assertEquals(1.0 - ltr.points[i], rtl.points[i], 1e-9)
    }

    @Test fun nonUniformStrokeTransformIsRejectedInsteadOfChangingWidth() {
        val icon = ImageVector.Builder("stretched", 24.dp, 24.dp, 24f, 24f).apply {
            group(scaleX = 2f, scaleY = 1f) {
                path(fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 2f) {
                    moveTo(2f, 2f); lineTo(8f, 8f)
                }
            }
        }.build()
        assertFailsWith<IllegalArgumentException> { buildMorphPlan(icon, icon) }
    }

    private fun stroke(width: Float = 2f, commands: PathBuilder.() -> Unit): ImageVector =
        ImageVector.Builder("stroke", 24.dp, 24.dp, 24f, 24f).apply {
            path(fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = width,
                strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round, pathBuilder = commands)
        }.build()
}
