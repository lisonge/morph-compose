package li.songe.morph.compose

import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import li.songe.morph.compose.internal.CubicPath
import li.songe.morph.compose.internal.MorphContourRole
import li.songe.morph.compose.internal.StrokeStyle
import li.songe.morph.compose.internal.cubicPathOf
import li.songe.morph.compose.internal.snapshotContours
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StrokeInferenceTest {
    private fun recover(paths: List<li.songe.morph.compose.internal.CubicPath>, options: MorphOptions) =
        StrokeInference.candidates(paths, options).firstOrNull()?.paths

    private val options = MorphOptions(transitionMode = MorphTransitionMode.ExperimentalStrokeInference)
    private fun line(vararg p: Double, cap: StrokeCap = StrokeCap.Butt): CubicPath {
        val packed = mutableListOf(p[0], p[1])
        for (i in 0 until p.size - 2 step 2) for (k in 1..3) for (axis in 0..1)
            packed += p[i + axis] + (p[i + 2 + axis] - p[i + axis]) * k / 3
        return cubicPathOf(packed.toDoubleArray(), false, MorphContourRole.Stroke, StrokeStyle(0.08, cap, StrokeJoin.Miter, 4f))
    }
    private fun crossed() = listOf(line(0.25, 0.25, 0.75, 0.75), line(0.25, 0.75, 0.75, 0.25)).expandStrokes(options)
    private fun branched() = listOf(line(0.5, 0.2, 0.2, 0.5, 0.5, 0.8), line(0.2, 0.5, 0.85, 0.5)).expandStrokes(options)

    @Test fun polygonalInkRecoversIndependentStraightAndBentStrokes() {
        val a = assertNotNull(recover(crossed(), options))
        val b = assertNotNull(recover(branched(), options))
        assertEquals(2, a.size)
        assertEquals(listOf(1, 1), a.map { it.segmentCount })
        assertEquals(listOf(1, 2), b.map { it.segmentCount }.sorted())
        assertTrue((a + b).all { it.stroke?.cap == StrokeCap.Butt && it.stroke.join == StrokeJoin.Miter })
        for ((from, to) in listOf(crossed() to branched(), branched() to crossed())) {
            val plan = buildVectorCorePlan(from, to, options)
            assertEquals(2, plan.contourCount)
            assertTrue(plan.items.all { it.decision.strategy == MorphAppliedStrategy.InferredCenterline })
            assertTrue(plan.items.any { abs(it.theta) > 0.1 })
            for (t in listOf(-0.1, 0.0, 0.000001, 0.25, 0.5, 0.75, 0.999999, 1.0, 1.1)) {
                val frame = plan.snapshotContours(t, MorphInterpolation.Polar)
                assertTrue(frame.all { it.points.all(Double::isFinite) })
                assertTrue(frame.all { abs(checkNotNull(it.stroke).width - 0.08) < 1e-6 })
            }
        }
    }

    @Test fun recoveryIsIndependentOfRotationScaleReflectionAndPathStart() {
        for (angle in listOf(0.0, 0.37, 1.8)) for (scale in listOf(0.5, 1.0, 1.7)) for (mirror in listOf(-1, 1)) {
            val transformed = crossed().map { path ->
                val p = path.copyPackedPoints()
                for (i in p.indices step 2) {
                    val x = p[i] * mirror; val y = p[i + 1]
                    p[i] = 0.2 + scale * (x * cos(angle) - y * sin(angle))
                    p[i + 1] = -0.1 + scale * (x * sin(angle) + y * cos(angle))
                }
                cubicPathOf(p, true)
            }
            assertEquals(2, assertNotNull(recover(transformed, options)).size)
            // Shift the cubic chain's start without changing its outline.
            val shifted = transformed.map { path ->
                val p = path.points
                cubicPathOf(p.copyOfRange(6, p.size) + p.copyOfRange(2, 8), true)
            }
            assertEquals(2, assertNotNull(recover(shifted, options)).size)
        }
    }

    @Test fun curvedDisconnectedAndUnexplainedInkFallBack() {
        val rounded = listOf(line(0.2, 0.5, 0.8, 0.5, cap = StrokeCap.Round)).expandStrokes(options)
        assertNull(recover(rounded, options))
        assertNull(recover(crossed() + crossed(), options))
        val triangle = cubicPathOf(doubleArrayOf(0.1, 0.1, 0.3, 0.1, 0.6, 0.1, 0.9, 0.1,
            0.7, 0.4, 0.6, 0.6, 0.5, 0.9, 0.4, 0.6, 0.2, 0.3, 0.1, 0.1), true)
        assertNull(recover(listOf(triangle), options))
        val fallback = buildVectorCorePlan(crossed(), rounded, options)
        assertTrue(fallback.items.none { it.sourceStroke != null })
        assertTrue(fallback.items.all { it.decision.reason.contains("ineligible") })
    }

    @Test fun recognizableTerminalsCannotDiscardExtraInkAtTheJunction() {
        val square = androidx.compose.ui.graphics.Path().apply {
            addRect(androidx.compose.ui.geometry.Rect(0.34f, 0.34f, 0.66f, 0.66f))
        }
        val extra = morphGeometryOf(square, androidx.compose.ui.geometry.Size(1f, 1f)).vector.toCubicPaths(false)
        val merged = (crossed() + extra + line(0.25, 0.25, 0.75, 0.75)).expandStrokes(options)
        assertEquals(1, merged.size)
        assertNull(recover(merged, options), "Terminal detection alone must not replace unexplained interior ink")
    }

    @Test fun defaultAndIdenticalInputsKeepTheirOriginalRepresentation() {
        assertTrue(buildVectorCorePlan(crossed(), branched(), MorphOptions()).items.none { it.sourceStroke != null })
        assertTrue(buildVectorCorePlan(crossed(), crossed(), options).items.none { it.sourceStroke != null })
    }

    @Test fun crossingRetainsAlternativeDecompositionsAndTargetChoosesMotion() {
        val alternatives = StrokeInference.candidates(crossed(), options)
        assertTrue(alternatives.size > 1, "A crossing has both straight and bent interpretations")
        assertTrue(alternatives.size <= 4)
        assertTrue(alternatives.all { it.difference.symmetricDifferenceRatio <= 0.003 && it.boundaryError / it.width <= 0.006 })
        for (rotation in MorphRotationPreference.entries) {
            val selected = assertNotNull(selectInferredPlan(crossed(), branched(), options.copy(rotationPreference = rotation)))
            assertTrue(selected.consideredPairs > 1 && selected.consideredPairs <= 16)
            assertTrue(selected.quality.extraIntersections <= selected.baselineQuality.extraIntersections)
            if (selected.quality.extraIntersections == selected.baselineQuality.extraIntersections)
                assertTrue(selected.quality.score <= selected.baselineQuality.score + 1e-3)
            assertTrue(selected.plan.items.all { it.decision.reason.contains("Candidates=") && it.decision.reason.contains("missing=") })
        }
        val translated = crossed().map { path ->
            cubicPathOf(path.points.mapIndexed { i, value -> value + if (i % 2 == 0) 0.1 else 0.05 }.toDoubleArray(), true)
        }
        val translation = assertNotNull(selectInferredPlan(crossed(), translated, options))
        // An explicit bent-stroke target supplies a different structural interpretation of the same ink.
        val bentTarget = alternatives.first { it.bends > 0 }.paths.map { path ->
            cubicPathOf(path.points.mapIndexed { i, value -> value + if (i % 2 == 0) 0.1 else 0.05 }.toDoubleArray(), false,
                MorphContourRole.Stroke, path.stroke)
        }
        val branching = assertNotNull(selectInferredPlan(crossed(), bentTarget, options))
        assertTrue(translation.plan.items.all { abs(it.theta) < 1e-7 && it.residual < 1e-7 })
        assertTrue(translation.sourceCandidate != branching.sourceCandidate,
            "The target must influence the decomposition, not just select the first recovery: $translation / $branching")
    }

    @Test fun cyclicPathStartDoesNotChangeTheSelectedMotion() {
        val source = crossed()
        val shifted = source.map { path -> cubicPathOf(path.points.copyOfRange(6, path.points.size) + path.points.copyOfRange(2, 8), true) }
        val a = assertNotNull(selectInferredPlan(source, branched(), options))
        val b = assertNotNull(selectInferredPlan(shifted, branched(), options))
        assertEquals(a.consideredPairs, b.consideredPairs)
        assertEquals(a.quality.score, b.quality.score, 1e-7)
        val pa = a.plan.snapshotContours(0.37, MorphInterpolation.Polar)
        val pb = b.plan.snapshotContours(0.37, MorphInterpolation.Polar)
        assertTrue(pa.indices.all { i -> pa[i].points.indices.all { j -> abs(pa[i].points[j] - pb[i].points[j]) < 1e-7 } })
    }

    @Test fun tinyAreaNotchCannotPassTheWholeBoundaryCheck() {
        val path = androidx.compose.ui.graphics.Path().apply {
            moveTo(0f, 0f); lineTo(1f, 0f); lineTo(1f, 0.1f)
            lineTo(0.5001f, 0.1f); lineTo(0.5001f, 0.09f); lineTo(0.5f, 0.09f)
            lineTo(0.5f, 0.1f); lineTo(0f, 0.1f); close()
        }
        val notched = morphGeometryOf(path, androidx.compose.ui.geometry.Size(1f, 1f)).vector.toCubicPaths(false)
        assertNull(recover(notched, options))
    }

    @Test fun fillOnlyGeometryRejectsInferredStrokesRatherThanDrawingOpenPaths() {
        val path = androidx.compose.ui.graphics.Path().apply { addRect(androidx.compose.ui.geometry.Rect(0f, 0f, 24f, 2f)) }
        val shape = morphGeometryOf(path, androidx.compose.ui.geometry.Size(24f, 24f))
        assertFailsWith<IllegalArgumentException> { buildMorphPlan(shape, shape, options) }
    }

    @Test fun repeatedInterruptionsPreserveStrokeSnapshotsAcrossDirectionChanges() {
        for (interpolation in MorphInterpolation.entries) {
            var plan = buildVectorCorePlan(crossed(), branched(), options)
            repeat(24) { iteration ->
                val snapshot = plan.snapshotContours(0.17 + (iteration % 5) * 0.13, interpolation)
                val target = if (iteration % 2 == 0) crossed() else branched()
                plan = buildInterruptedVectorPlan(snapshot, target, options.copy(
                    rotationPreference = MorphRotationPreference.entries[iteration % MorphRotationPreference.entries.size]))
                val start = plan.snapshotContours(0.0, interpolation)
                fun coordinates(paths: List<li.songe.morph.compose.internal.SampledContour>) =
                    paths.flatMap { it.points.toList().chunked(2) }.sortedWith(compareBy({ it[0] }, { it[1] }))
                val expected = coordinates(snapshot)
                val actual = coordinates(start)
                assertEquals(expected.size, actual.size)
                assertTrue(expected.indices.all { i -> (0..1).all { abs(expected[i][it] - actual[i][it]) < 1e-8 } },
                    "Interruption $iteration / $interpolation changed its source")
                assertTrue(plan.snapshotContours(0.5, interpolation).all { it.points.all(Double::isFinite) })
            }
        }
    }

    @Test fun interruptedStrokesKeepTheExactSnapshotAndCanReturnToOutlines() {
        val initial = buildVectorCorePlan(crossed(), branched(), options)
        val snapshot = initial.snapshotContours(0.37, MorphInterpolation.Polar)
        val next = buildInterruptedVectorPlan(snapshot, crossed(), options)
        val start = next.snapshotContours(0.0, MorphInterpolation.Polar)
        assertEquals(snapshot.size, start.size)
        // Correspondence may reverse a centerline; compare its point set.
        fun coordinates(paths: List<li.songe.morph.compose.internal.SampledContour>) =
            paths.flatMap { it.points.toList().chunked(2) }.sortedWith(compareBy({ it[0] }, { it[1] }))
        val expected = coordinates(snapshot); val actual = coordinates(start)
        assertTrue(expected.indices.all { i -> (0..1).all { abs(expected[i][it] - actual[i][it]) < 1e-8 } })
        val filled = crossed()
        val fillSnapshot = buildVectorCorePlan(filled, branched(), MorphOptions()).snapshotContours(0.4, MorphInterpolation.Polar)
        val fallback = buildInterruptedVectorPlan(fillSnapshot, crossed(), options)
        assertTrue(fallback.items.none { it.sourceStroke != null })
        assertTrue(fallback.items.all { it.decision.reason.contains("Stroke inference ineligible") })
        val automatic = buildInterruptedVectorPlan(fillSnapshot, crossed(), MorphOptions())
        for (mode in MorphInterpolation.entries) for (t in listOf(0.0, 0.37, 1.0)) {
            val expectedFrame = automatic.snapshotContours(t, mode)
            val actualFrame = fallback.snapshotContours(t, mode)
            assertEquals(expectedFrame.size, actualFrame.size)
            assertTrue(expectedFrame.indices.all { expectedFrame[it].points.contentEquals(actualFrame[it].points) },
                "Fallback diagnostics must not change the rendered geometry")
        }
    }
}
