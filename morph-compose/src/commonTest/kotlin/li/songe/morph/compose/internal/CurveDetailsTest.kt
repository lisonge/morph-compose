package li.songe.morph.compose.internal

import li.songe.morph.compose.MorphInterpolation
import li.songe.morph.compose.MorphOptions
import li.songe.morph.compose.MorphRotationPreference
import kotlin.math.cos
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CurveDetailsTest {
    private val options = MorphOptions(sampleCount = 32)

    @Test fun coincidentSourceSamplesRetainEveryEdgeWhenInterruptedAndEnlarged() {
        val vertices = listOf(0.0 to 0.0, 6e-13 to 0.0, 6e-13 to 6e-13, 0.0 to 6e-13, 0.0 to 0.0)
        val points = mutableListOf(0.0, 0.0)
        for ((a, b) in vertices.zipWithNext()) {
            for (fraction in listOf(1.0 / 3, 2.0 / 3, 1.0)) {
                points += a.first + (b.first - a.first) * fraction
                points += a.second + (b.second - a.second) * fraction
            }
        }
        val tiny = cubicPathOf(points.toDoubleArray(), true)
        val original = buildMorphPlan(listOf(tiny), listOf(tiny), options)
        assertNotNull(original.items.single().stationaryCurve)
        for (mode in MorphInterpolation.entries) {
            val enlarged = buildMorphPlanFromSampledSource(original.snapshotContours(0.5, mode), listOf(oval()), options)
            val frame = enlarged.createFrame()
            enlarged.interpolate(1.0, frame, mode)
            val curves = assertNotNull(frame.curves.single())
            // At the target, the retained cubic chain must visit every sampled vertex, even
            // those that shared a source position before the contour grew.
            for (i in 0 until frame.sampleCount) {
                assertTrue((0 until curves.size step 6).any { j ->
                    kotlin.math.abs(curves[j] - frame.x(0, i)) < 1e-9 &&
                        kotlin.math.abs(curves[j + 1] - frame.y(0, i)) < 1e-9
                }, "Target sample $i was lost")
            }
        }
    }
    private fun oval(dx: Double = 0.0, role: MorphContourRole = MorphContourRole.Shape): CubicPath =
        cubicPathOf(doubleArrayOf(
            0.2, 0.5, 0.2, 0.1, 0.8, 0.1, 0.8, 0.5,
            0.8, 0.9, 0.2, 0.9, 0.2, 0.5,
        ).mapIndexed { i, v -> if (i % 2 == 0) v + dx else v }.toDoubleArray(), true, role)

    @Test fun ordinaryMotionAndPairingStayIdenticalToTheSampleOnlySolver() {
        val source = listOf(oval(), oval(1.0))
        val rotated = source.map { path ->
            val points = path.copyPackedPoints()
            for (i in points.indices step 2) {
                val x = points[i]
                val y = points[i + 1]
                points[i] = (x * cos(0.7) - y * sin(0.7)) * 1.3
                points[i + 1] = (x * sin(0.7) + y * cos(0.7)) * 1.3
            }
            cubicPathOf(points, true)
        }
        // No shared boundary: ordinary rotation, translation and count changes retain their solver.
        val targets = listOf(rotated, listOf(oval(0.1), oval(1.2)), listOf(oval(0.1)), listOf(oval(0.1), oval(1.2), oval(2.0)))
        for (preference in MorphRotationPreference.entries) for (target in targets) {
            val opts = options.copy(rotationPreference = preference)
            val sampledSource = resamplePaths(source, opts)
            val sampledTarget = resamplePaths(target, opts)
            val actual = buildSampledMorphPlan(sampledSource, sampledTarget, opts)
            val baseline = buildSampledMorphPlan(sampledSource.map { it.copy(curveSource = null) },
                sampledTarget.map { it.copy(curveSource = null) }, opts)
            for (i in actual.items.indices) {
                assertEquals(baseline.items[i].theta, actual.items[i].theta)
                assertEquals(baseline.items[i].logScale, actual.items[i].logScale)
                assertEquals(baseline.items[i].blockTransport, actual.items[i].blockTransport)
                assertTrue(baseline.items[i].targetOriented.contentEquals(actual.items[i].targetOriented))
            }
            for (mode in MorphInterpolation.entries) for (t in listOf(-0.1, 0.0, 0.4, 1.0, 1.1)) {
                val a = actual.createFrame()
                val b = baseline.createFrame()
                actual.interpolate(t, a, mode)
                baseline.interpolate(t, b, mode)
                for (i in a.points.indices) assertTrue(a.points[i].contentEquals(b.points[i]))
            }
        }
    }

    @Test fun translatedRotatedAndDifferentRoleContoursAreNotFrozen() {
        val source = oval()
        val translated = buildMorphPlan(listOf(source), listOf(oval(0.1)), options)
        assertNull(translated.items.single().stationaryCurve)
        assertNotNull(translated.items.single().targetCurveDetails)
        val changedRole = buildMorphPlan(listOf(source), listOf(oval(role = MorphContourRole.Hole)), options)
        assertTrue(changedRole.items.all { it.stationaryCurve == null })
        val points = source.copyPackedPoints()
        for (i in points.indices step 2) { val x = points[i]; points[i] = -points[i + 1]; points[i + 1] = x }
        val rotated = buildMorphPlan(listOf(source), listOf(cubicPathOf(points, true)), options)
        assertNull(rotated.items.single().stationaryCurve)
        assertNotNull(rotated.items.single().targetCurveDetails)
    }

    @Test fun repeatedInterruptionsPreserveCurvesAndEventuallyReachTheCurvedTarget() {
        for (mode in MorphInterpolation.entries) {
            var plan = buildMorphPlan(listOf(oval()), listOf(oval()), options)
            assertNotNull(plan.items.single().stationaryCurve)
            // First retain the same curve, then move it, then interrupt a moving curved contour.
            for (target in listOf(oval(), oval(0.15), oval(-0.2), oval(0.3))) {
                val before = plan.createFrame()
                plan.interpolate(0.37, before, mode)
                val next = buildMorphPlanFromSampledSource(plan.snapshotContours(0.37, mode), listOf(target), options)
                val after = next.createFrame()
                next.interpolate(0.0, after, mode)
                val expected = assertNotNull(before.curves.single())
                val actual = assertNotNull(after.curves.single())
                assertEquals(expected.size, actual.size)
                for (i in expected.indices) assertEquals(expected[i], actual[i], 1e-10)
                next.interpolate(1e-7, after, mode)
                for (i in expected.indices) assertEquals(expected[i], actual[i], 1e-5)
                plan = next
            }
            val end = plan.createFrame()
            plan.interpolate(1.0, end, mode)
            val detail = assertNotNull(plan.items.single().curveDetails)
            val rebased = detail.rebase(end.points.single(), assertNotNull(end.curves.single()))
            val targetDetails = assertNotNull(plan.items.single().targetCurveDetails)
            for (i in rebased.offsets.indices) assertEquals(targetDetails.offsets[i], rebased.offsets[i], 1e-10)
            assertTrue(rebased.offsets.any { kotlin.math.abs(it) > 1e-6 })
        }
    }

    @Test fun sharedBoundaryIsExcludedFromGlobalTransport() {
        // A tiny change in a second contour produces a near-rigid global fit.
        val plan = buildMorphPlan(listOf(oval(), oval(1.0)), listOf(oval(), oval(1.0001)), options)
        assertNull(plan.items.first().blockTransport)
        assertEquals(0.0, plan.items.first().logScale)
        assertNotNull(plan.items.first().stationaryCurve)
    }
}
