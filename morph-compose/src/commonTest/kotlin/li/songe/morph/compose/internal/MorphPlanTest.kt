package li.songe.morph.compose.internal

import li.songe.morph.compose.MorphInterpolation
import li.songe.morph.compose.MorphOptions
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
