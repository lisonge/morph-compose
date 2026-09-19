package li.songe.morph.compose.internal

import kotlin.math.hypot

internal data class HoleOpeningCandidate(val path: CubicPath, val x: Double, val y: Double)

/** Open a disappearing hole through a zero-width seam without changing the source fill. */
internal fun holeOpeningCandidates(outer: SampledContour, hole: SampledContour): List<HoleOpeningCandidate> {
    val boundary = outer.curveSource?.path ?: return emptyList()
    val opening = hole.curveSource?.path ?: return emptyList()
    if (boundary.role != MorphContourRole.Shape || opening.role != MorphContourRole.Hole) return emptyList()
    fun contains(x: Double, y: Double): Boolean {
        var inside = false
        val p = outer.points
        var previous = p.size / 2 - 1
        for (i in 0 until p.size / 2) {
            val ax = p[i * 2]
            val ay = p[i * 2 + 1]
            val bx = p[previous * 2]
            val by = p[previous * 2 + 1]
            if ((ay > y) != (by > y) && x < (bx - ax) * (y - ay) / (by - ay) + ax) inside = !inside
            previous = i
        }
        return inside
    }
    if ((hole.points.indices step 2).any { !contains(hole.points[it], hole.points[it + 1]) }) return emptyList()
    var bestDistance = Double.POSITIVE_INFINITY
    val bridges = mutableListOf<Triple<Int, Int, Double>>()
    for (i in 0 until boundary.segmentCount) for (j in 0 until opening.segmentCount) {
        val ax = boundary.points[i * 6]
        val ay = boundary.points[i * 6 + 1]
        val bx = opening.points[j * 6]
        val by = opening.points[j * 6 + 1]
        val distance = hypot(bx - ax, by - ay)
        if (distance > bestDistance + 1e-7 || distance < 1e-9) continue
        if ((1..7).any { !contains(ax + (bx - ax) * it / 8, ay + (by - ay) * it / 8) }) continue
        bestDistance = minOf(bestDistance, distance)
        bridges += Triple(i, j, distance)
    }
    return bridges.filter { it.third <= bestDistance + 1e-7 }.map { (outerIndex, holeIndex, _) ->
        val result = mutableListOf(boundary.points[0], boundary.points[1])
        fun line(x: Double, y: Double) {
            val ax = result[result.lastIndex - 1]
            val ay = result.last()
            for (k in 1..3) {
                result += ax + (x - ax) * k / 3
                result += ay + (y - ay) * k / 3
            }
        }
        for (i in 0 until boundary.segmentCount) {
            if (i == outerIndex) {
                line(opening.points[holeIndex * 6], opening.points[holeIndex * 6 + 1])
                for (k in 0 until opening.segmentCount) {
                    val segment = (holeIndex + k) % opening.segmentCount
                    for (offset in 2..7) result += opening.points[segment * 6 + offset]
                }
                line(boundary.points[i * 6], boundary.points[i * 6 + 1])
            }
            for (offset in 2..7) result += boundary.points[i * 6 + offset]
        }
        HoleOpeningCandidate(cubicPathOf(result.toDoubleArray(), true, MorphContourRole.Shape),
            boundary.points[outerIndex * 6], boundary.points[outerIndex * 6 + 1])
    }
}
