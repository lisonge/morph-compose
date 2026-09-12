package li.songe.morph.compose.internal

import li.songe.morph.compose.MorphInterpolation
import li.songe.morph.compose.MorphOptions
import li.songe.morph.compose.MorphRotationPreference
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class MorphPlanTest {
    @Test
    fun rotatedLineHasExactEndpoints() {
        val plan =
            buildMorphPlan(
                from = listOf(line(0.0, 0.0, 1.0, 0.0)),
                to = listOf(line(0.0, 0.0, 0.0, 1.0)),
                options = MorphOptions(sampleCount = 16),
            )
        assertTrue(abs(abs(plan.rotationRadians(0)) - PI / 2.0) < 1e-9)
        assertTrue(plan.residual(0) < 1e-9)

        val frame = plan.createFrame()
        plan.interpolate(0.0, frame)
        for (point in 0 until frame.sampleCount) {
            val expected = point.toDouble() / (frame.sampleCount - 1)
            assertTrue(abs(frame.x(0, point) - expected) < 1e-9)
            assertTrue(abs(frame.y(0, point)) < 1e-9)
        }
        plan.interpolate(1.0, frame)
        for (point in 0 until frame.sampleCount) {
            val expected = point.toDouble() / (frame.sampleCount - 1)
            assertTrue(abs(frame.x(0, point)) < 1e-9)
            assertTrue(abs(frame.y(0, point) - expected) < 1e-9)
        }
    }

    @Test
    fun closedLoopFindsEquivalentStartingPoint() {
        val square = polygon(0.0 to 0.0, 1.0 to 0.0, 1.0 to 1.0, 0.0 to 1.0)
        val shifted = polygon(1.0 to 0.0, 1.0 to 1.0, 0.0 to 1.0, 0.0 to 0.0)
        val plan = buildMorphPlan(listOf(square), listOf(shifted), MorphOptions(sampleCount = 64))

        assertTrue(plan.residual(0) < 1e-9)
        assertTrue(abs(plan.rotationRadians(0)) < 1e-9)
        assertTrue(plan.createFrame().isClosed(0))
    }

    @Test
    fun resamplingRetainsSharpCornerFeatures() {
        val square = polygon(0.0 to 0.0, 1.0 to 0.0, 1.0 to 1.0, 0.0 to 1.0)
        val sampled = resamplePaths(listOf(square), MorphOptions(sampleCount = 32)).single()
        val corners = sampled.featureWeights.filter { it > 0.0 }

        assertEquals(4, corners.size)
        assertTrue(corners.all { abs(it - 0.5) < 1e-9 })
    }

    @Test
    fun cornerFeaturesDisambiguateSymmetricClosedLoopAlignment() {
        val sampleCount = 16
        val points =
            DoubleArray(sampleCount * 2) { coordinate ->
                val angle = 2.0 * PI * (coordinate / 2) / sampleCount
                if (coordinate % 2 == 0) cos(angle) else sin(angle)
            }
        val sourceFeatures = DoubleArray(sampleCount).also { it[0] = 1.0 }
        val targetFeatures = DoubleArray(sampleCount).also { it[sampleCount / 4] = 1.0 }
        val source =
            SampledContour(points, closed = true, MorphContourRole.Shape, sourceFeatures)
        val target =
            SampledContour(points.copyOf(), closed = true, MorphContourRole.Shape, targetFeatures)

        val plan =
            buildSampledMorphPlan(
                source = listOf(source),
                target = listOf(target),
                options = MorphOptions(sampleCount = sampleCount),
            )
        val item = plan.items.single()

        assertTrue(abs(abs(item.theta) - PI / 2.0) < 1e-9)
        assertEquals(1.0, item.sourceFeatureWeights[0])
        assertEquals(1.0, item.targetFeatureWeights[0])
    }

    @Test
    fun cornerFeaturesDoNotOverrideAVisiblyBetterGeometricFit() {
        val sampleCount = 16
        val points =
            DoubleArray(sampleCount * 2) { coordinate ->
                val angle = 2.0 * PI * (coordinate / 2) / sampleCount
                if (coordinate % 2 == 0) 2.0 * cos(angle) else sin(angle)
            }
        val sourceFeatures = DoubleArray(sampleCount).also { it[0] = 1.0 }
        val targetFeatures = DoubleArray(sampleCount).also { it[sampleCount / 4] = 1.0 }
        val plan =
            buildSampledMorphPlan(
                source =
                    listOf(
                        SampledContour(
                            points,
                            closed = true,
                            MorphContourRole.Shape,
                            sourceFeatures,
                        ),
                    ),
                target =
                    listOf(
                        SampledContour(
                            points.copyOf(),
                            closed = true,
                            MorphContourRole.Shape,
                            targetFeatures,
                        ),
                    ),
                options = MorphOptions(sampleCount = sampleCount),
            )
        val item = plan.items.single()

        assertTrue(abs(item.theta) < 1e-9)
        assertEquals(1.0, item.targetFeatureWeights[sampleCount / 4])
    }

    @Test
    fun arrowBackAndCloseDoNotFoldTheirBoundary() {
        val (arrowBack, close) = arrowBackClosePair()
        for ((from, to) in listOf(arrowBack to close, close to arrowBack)) {
            val plan = buildMorphPlan(listOf(from), listOf(to), MorphOptions(sampleCount = 64))
            for (interpolation in MorphInterpolation.entries) {
                val frame = plan.createFrame()
                for (progress in listOf(0.25, 0.5, 0.75)) {
                    plan.interpolate(progress, frame, interpolation)
                    assertEquals(
                        expected = 0,
                        actual = properIntersectionCount(frame.points.single(), closed = true),
                        message =
                            "$interpolation boundary folded at progress=$progress for " +
                                "${if (from === arrowBack) "Arrow back -> Close" else "Close -> Arrow back"}",
                    )
                }
            }
        }
    }

    @Test
    fun preferredRotationChangesBackCloseGeometryInBothDirections() {
        val (back, close) = arrowBackClosePair()
        for (preference in listOf(MorphRotationPreference.PreferClockwise, MorphRotationPreference.PreferCounterClockwise)) {
            val options = MorphOptions(rotationPreference = preference)
            val forward = buildMorphPlan(listOf(back), listOf(close), options)
            val backward = buildMorphPlan(listOf(close), listOf(back), options)
            val sign = if (preference == MorphRotationPreference.PreferClockwise) 1 else -1
            for (plan in listOf(forward, backward)) {
                assertTrue(sign * plan.rotationRadians(0) > 0.01, "$preference angle=${plan.rotationRadians(0)} residual=${plan.residual(0)}")
                val frame = plan.createFrame()
                for (interpolation in MorphInterpolation.entries) {
                    for (progress in listOf(0.25, 0.5, 0.75)) {
                        plan.interpolate(progress, frame, interpolation)
                        assertEquals(0, properIntersectionCount(frame.points.single(), closed = true), "$preference $interpolation at $progress")
                    }
                }
            }
            val a = forward.createFrame()
            val b = backward.createFrame()
            forward.interpolate(0.5, a)
            backward.interpolate(0.5, b)
            // Compare the point sets, ignoring contour starting indices and traversal orientation.
            val maxNearestDistance = (0 until a.sampleCount).maxOf { i ->
                (0 until b.sampleCount).minOf { j ->
                    kotlin.math.hypot(a.x(0, i) - b.x(0, j), a.y(0, i) - b.y(0, j))
                }
            }
            assertTrue(maxNearestDistance > 0.02, "The two paths still trace the same geometry: $maxNearestDistance")
            for ((plan, from, to) in listOf(Triple(forward, back, close), Triple(backward, close, back))) {
                val auto = buildMorphPlan(listOf(from), listOf(to))
                assertTrue(plan.residual(0) <= auto.residual(0) + 0.001)
                for (progress in listOf(0.0, 1.0)) {
                    val expected = auto.createFrame()
                    auto.interpolate(progress, expected)
                    plan.interpolate(progress, a)
                    assertSamePointSets(expected, a)
                }
            }
        }
    }

    @Test
    fun directionPreferenceDoesNotSpinAnUnchangedOrTranslatedShape() {
        val shape = polygon(0.0 to 0.0, 2.0 to 0.0, 2.0 to 2.0, 0.0 to 2.0)
        val translated = polygon(1.0 to 1.0, 3.0 to 1.0, 3.0 to 3.0, 1.0 to 3.0)
        for (preference in MorphRotationPreference.entries) {
            for (target in listOf(shape, translated)) {
                val plan = buildMorphPlan(listOf(shape), listOf(target), MorphOptions(rotationPreference = preference))
                assertTrue(abs(plan.rotationRadians(0)) < 1e-6)
                val auto = buildMorphPlan(listOf(shape), listOf(target))
                val expected = auto.createFrame()
                auto.interpolate(0.5, expected)
                val actual = plan.createFrame()
                plan.interpolate(0.5, actual)
                assertSamePointSets(expected, actual)
            }
        }
    }

    @Test
    fun directionPreferenceFallsBackForAnAsymmetricShape() {
        val shape = polygon(0.1 to 0.2, 0.9 to 0.3, 0.4 to 0.8)
        val rotated = rotatePath(shape, -0.2)
        val plan = buildMorphPlan(
            listOf(shape), listOf(rotated),
            MorphOptions(rotationPreference = MorphRotationPreference.PreferClockwise),
        )
        assertTrue(abs(plan.rotationRadians(0) + 0.2) < 1e-6)
        assertTrue(plan.residual(0) < 1e-6)
    }

    @Test
    fun globalAlignmentDoesNotOverrideTheSelectedDirection() {
        // Tiny lines on widely separated centers make the global fit nearly rigid even when
        // the local endpoint correspondences select the opposite half turn.
        val source = listOf(line(0.2999, 0.5, 0.3001, 0.5), line(0.6999, 0.5, 0.7001, 0.5))
        val target = source.map { rotatePath(it, -0.2) }
        val auto = buildMorphPlan(source, target)
        assertTrue(auto.items.all { it.blockTransport != null && it.theta < 0.0 })
        val directed = buildMorphPlan(
            source, target, MorphOptions(rotationPreference = MorphRotationPreference.PreferClockwise),
        )
        assertTrue(directed.items.all { it.theta > 0.0 && it.blockTransport == null })
        for (progress in listOf(0.0, 1.0)) {
            val expected = auto.createFrame()
            val actual = directed.createFrame()
            auto.interpolate(progress, expected)
            directed.interpolate(progress, actual)
            assertSamePointSets(expected, actual)
        }
    }

    @Test
    fun directionPreferenceRetainsGeometryOnInterruption() {
        val (back, close) = arrowBackClosePair()
        for (preference in MorphRotationPreference.entries) {
            val options = MorphOptions(rotationPreference = preference)
            val plan = buildMorphPlan(listOf(back), listOf(close), options)
            for (progress in listOf(0.1, 0.37, 0.9)) {
                val before = plan.createFrame()
                plan.interpolate(progress, before)
                val redirected = buildMorphPlanFromSampledSource(plan.snapshotContours(progress, MorphInterpolation.Polar), listOf(back), options)
                val after = redirected.createFrame()
                redirected.interpolate(0.0, after)
                assertSamePointSets(before, after)
            }
        }
    }

    @Test
    fun unequalContourCountsUseEveryTarget() {
        val source =
            listOf(
                line(0.0, 0.0, 1.0, 0.0),
                line(0.0, 0.5, 1.0, 0.5),
                line(0.0, 1.0, 1.0, 1.0),
            )
        val target = listOf(line(0.0, 0.0, 1.0, 1.0), line(0.0, 1.0, 1.0, 0.0))
        val plan = buildMorphPlan(source, target, MorphOptions(sampleCount = 24))
        val frame = plan.createFrame()

        assertEquals(3, plan.contourCount)
        plan.interpolate(0.5, frame)
        for (contour in 0 until frame.contourCount) {
            for (point in 0 until frame.sampleCount) {
                assertTrue(frame.x(contour, point).isFinite())
                assertTrue(frame.y(contour, point).isFinite())
            }
        }
    }

    @Test
    fun interruptedPlanStartsAtThePreviouslyRenderedGeometry() {
        val options = MorphOptions(sampleCount = 24)
        val original =
            buildMorphPlan(
                from = listOf(line(0.1, 0.2, 0.9, 0.2)),
                to = listOf(line(0.2, 0.1, 0.2, 0.9)),
                options = options,
            )
        val progress = 0.37
        val expected = original.createFrame()
        original.interpolate(progress, expected, MorphInterpolation.Polar)

        val interrupted =
            buildMorphPlanFromSampledSource(
                from = original.snapshotContours(progress, MorphInterpolation.Polar),
                to = listOf(line(0.1, 0.9, 0.9, 0.1)),
                options = options,
            )
        val actual = interrupted.createFrame()
        interrupted.interpolate(0.0, actual, MorphInterpolation.Polar)

        val forwardMatches = framesMatch(expected, actual, reversed = false)
        val reversedMatches = framesMatch(expected, actual, reversed = true)
        assertTrue(forwardMatches || reversedMatches)
    }

    @Test
    fun rejectsInvalidInputs() {
        assertFailsWith<IllegalArgumentException> { MorphOptions(sampleCount = 1) }
        assertFailsWith<IllegalArgumentException> {
            cubicPathOf(doubleArrayOf(0.0, 0.0, 1.0), closed = false)
        }
        assertFailsWith<IllegalArgumentException> {
            buildMorphPlan(emptyList(), listOf(line(0.0, 0.0, 1.0, 1.0)))
        }
    }
}

private fun framesMatch(
    expected: MorphFrame,
    actual: MorphFrame,
    reversed: Boolean,
): Boolean {
    if (expected.contourCount != actual.contourCount || expected.sampleCount != actual.sampleCount) {
        return false
    }
    for (point in 0 until expected.sampleCount) {
        val actualPoint = if (reversed) expected.sampleCount - 1 - point else point
        if (abs(expected.x(0, point) - actual.x(0, actualPoint)) >= 1e-9) return false
        if (abs(expected.y(0, point) - actual.y(0, actualPoint)) >= 1e-9) return false
    }
    return true
}

private fun properIntersectionCount(points: DoubleArray, closed: Boolean): Int {
    val pointCount = points.size / 2
    val edgeCount = if (closed) pointCount else pointCount - 1
    var intersections = 0
    for (first in 0 until edgeCount) {
        val firstNext = (first + 1) % pointCount
        for (second in first + 1 until edgeCount) {
            val secondNext = (second + 1) % pointCount
            if (first == secondNext || firstNext == second) continue
            if (first == 0 && secondNext == 0) continue
            if (
                segmentsProperlyIntersect(
                    points[first * 2],
                    points[first * 2 + 1],
                    points[firstNext * 2],
                    points[firstNext * 2 + 1],
                    points[second * 2],
                    points[second * 2 + 1],
                    points[secondNext * 2],
                    points[secondNext * 2 + 1],
                )
            ) {
                intersections++
            }
        }
    }
    return intersections
}

private fun segmentsProperlyIntersect(
    ax: Double,
    ay: Double,
    bx: Double,
    by: Double,
    cx: Double,
    cy: Double,
    dx: Double,
    dy: Double,
): Boolean {
    fun side(
        px: Double,
        py: Double,
        qx: Double,
        qy: Double,
        rx: Double,
        ry: Double,
    ): Double = (qx - px) * (ry - py) - (qy - py) * (rx - px)

    val firstC = side(ax, ay, bx, by, cx, cy)
    val firstD = side(ax, ay, bx, by, dx, dy)
    val secondA = side(cx, cy, dx, dy, ax, ay)
    val secondB = side(cx, cy, dx, dy, bx, by)
    return firstC * firstD < -1e-12 && secondA * secondB < -1e-12
}

private fun line(x0: Double, y0: Double, x1: Double, y1: Double): CubicPath =
    cubicPathOf(
        doubleArrayOf(
            x0,
            y0,
            x0 + (x1 - x0) / 3.0,
            y0 + (y1 - y0) / 3.0,
            x0 + 2.0 * (x1 - x0) / 3.0,
            y0 + 2.0 * (y1 - y0) / 3.0,
            x1,
            y1,
        ),
        closed = false,
    )

private fun rotatePath(path: CubicPath, angle: Double): CubicPath {
    val points = path.copyPackedPoints()
    for (i in points.indices step 2) {
        val x = points[i] - 0.5
        val y = points[i + 1] - 0.5
        points[i] = 0.5 + x * cos(angle) - y * sin(angle)
        points[i + 1] = 0.5 + x * sin(angle) + y * cos(angle)
    }
    return cubicPathOf(points, path.closed, path.role)
}

private fun polygon(vararg vertices: Pair<Double, Double>): CubicPath {
    require(vertices.size >= 3)
    val points = mutableListOf(vertices.first().first, vertices.first().second)
    for (index in vertices.indices) {
        val from = vertices[index]
        val to = vertices[(index + 1) % vertices.size]
        points += from.first + (to.first - from.first) / 3.0
        points += from.second + (to.second - from.second) / 3.0
        points += from.first + 2.0 * (to.first - from.first) / 3.0
        points += from.second + 2.0 * (to.second - from.second) / 3.0
        points += to.first
        points += to.second
    }
    return cubicPathOf(points.toDoubleArray(), closed = true)
}

private fun arrowBackClosePair(): Pair<CubicPath, CubicPath> {
    val arrowBack =
        polygon(
            20.0 to 11.0,
            7.83 to 11.0,
            13.42 to 5.41,
            12.0 to 4.0,
            4.0 to 12.0,
            12.0 to 20.0,
            13.41 to 18.59,
            7.83 to 13.0,
            20.0 to 13.0,
        )
    val close =
        polygon(
            19.0 to 6.41,
            17.59 to 5.0,
            12.0 to 10.59,
            6.41 to 5.0,
            5.0 to 6.41,
            10.59 to 12.0,
            5.0 to 17.59,
            6.41 to 19.0,
            12.0 to 13.41,
            17.59 to 19.0,
            19.0 to 17.59,
            13.41 to 12.0,
        )
    return cubicPathOf(arrowBack.points.map { it / 24.0 }.toDoubleArray(), true) to
        cubicPathOf(close.points.map { it / 24.0 }.toDoubleArray(), true)
}

private fun assertSamePointSets(expected: MorphFrame, actual: MorphFrame) {
    assertEquals(expected.contourCount, actual.contourCount)
    for (contour in 0 until expected.contourCount) {
        for (i in 0 until expected.sampleCount) {
            val distance = (0 until actual.sampleCount).minOf { j ->
                kotlin.math.hypot(expected.x(contour, i) - actual.x(contour, j), expected.y(contour, i) - actual.y(contour, j))
            }
            assertTrue(distance < 1e-8, "Point moved by $distance")
        }
    }
}
