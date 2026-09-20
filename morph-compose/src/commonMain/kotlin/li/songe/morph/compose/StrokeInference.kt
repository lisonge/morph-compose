package li.songe.morph.compose

import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import li.songe.morph.compose.internal.CubicPath
import li.songe.morph.compose.internal.MorphContourRole
import li.songe.morph.compose.internal.MorphPlan
import li.songe.morph.compose.internal.StrokeStyle
import li.songe.morph.compose.internal.cubicPathOf
import kotlin.math.abs
import kotlin.math.roundToLong
import kotlin.math.sqrt
import kotlin.math.hypot

/** Deliberately bounded recognizer, not a general medial-axis or semantic icon decomposition. */
internal object StrokeInference {
    private data class P(val x: Double, val y: Double) {
        operator fun plus(p: P) = P(x + p.x, y + p.y)
        operator fun minus(p: P) = P(x - p.x, y - p.y)
        operator fun times(s: Double) = P(x * s, y * s)
        fun dot(p: P) = x * p.x + y * p.y
        fun cross(p: P) = x * p.y - y * p.x
        fun length() = hypot(x, y)
        fun unit() = this * (1.0 / length())
    }
    private data class Cap(val center: P, val inward: P, val width: Double)
    private data class Line(val points: List<P>, val width: Double)

    internal data class Candidate(
        val paths: List<CubicPath>,
        val difference: InkDifference,
        val boundaryError: Double,
        val width: Double,
        val bends: Int,
        val key: String,
    )

    internal fun candidates(paths: List<CubicPath>, options: MorphOptions): List<Candidate> {
        // Disconnected shapes, holes, curved boundaries and mixed paint stay on the outline solver.
        val polygon = polygon(paths.singleOrNull() ?: return emptyList()) ?: return emptyList()
        if (polygon.size !in 4..32) return emptyList()
        val caps = polygon.indices.mapNotNull { i ->
            val a = polygon[i]; val b = polygon[(i + 1) % polygon.size]
            val before = polygon[(i + polygon.size - 1) % polygon.size] - a
            val after = polygon[(i + 2) % polygon.size] - b
            val edge = b - a
            val width = edge.length()
            if (before.length() < width * 2 || after.length() < width * 2) return@mapNotNull null
            val u = before.unit(); val v = after.unit()
            if (u.dot(v) < 0.9999 || abs(edge.unit().dot(u)) > 0.01 || abs(edge.unit().dot(v)) > 0.01) return@mapNotNull null
            Cap((a + b) * 0.5, (u + v).unit(), width)
        }
        if (caps.size !in 2..6) return emptyList()
        val width = caps.map { it.width }.average()
        if (caps.any { abs(it.width - width) > width * 0.01 }) return emptyList()
        val tolerance = width * 0.006
        val candidates = mutableListOf<List<Line>>()
        fun pair(a: Cap, b: Cap): Line? {
            val delta = b.center - a.center
            val cross = a.inward.cross(b.inward)
            if (abs(cross) < 0.00001) {
                if (a.inward.dot(b.inward) > -0.9999 || delta.dot(a.inward) <= width ||
                    abs(delta.cross(a.inward)) > tolerance) return null
                return Line(listOf(a.center, b.center), (a.width + b.width) / 2)
            }
            val t = delta.cross(b.inward) / cross
            val s = delta.cross(a.inward) / cross
            if (t < width || s < width) return null
            val joint = a.center + a.inward * t
            if (!inside(joint, polygon)) return null
            return Line(listOf(a.center, joint, b.center), (a.width + b.width) / 2)
        }
        fun enumerate(remaining: List<Int>, lines: List<Line>, single: Int?) {
            if (remaining.isEmpty()) {
                if (single == null) { candidates += lines; return }
                val cap = caps[single]
                // An odd terminal may attach to an existing joint, but not to an arbitrary point.
                val joints = lines.flatMap { it.points.drop(1).dropLast(1) }.toMutableList()
                val segments = lines.flatMap { it.points.zipWithNext() }
                for (i in segments.indices) for (j in 0 until i) {
                    val (a, b) = segments[i]; val (c, d) = segments[j]
                    val u = b - a; val v = d - c; val cross = u.cross(v)
                    if (abs(cross) < 1e-12) continue
                    val t = (c - a).cross(v) / cross; val s = (c - a).cross(u) / cross
                    if (t in 0.0..1.0 && s in 0.0..1.0) joints += a + u * t
                }
                for (joint in joints.distinct()) {
                    val delta = joint - cap.center
                    if (delta.dot(cap.inward) <= width || abs(delta.cross(cap.inward)) > tolerance) continue
                    candidates += lines + Line(listOf(cap.center, cap.center + cap.inward * delta.dot(cap.inward)), cap.width)
                }
                return
            }
            val first = remaining.first()
            for (other in remaining.drop(1)) {
                val line = pair(caps[first], caps[other]) ?: continue
                enumerate(remaining.filter { it != first && it != other }, lines + line, single)
            }
        }
        if (caps.size % 2 == 0) enumerate(caps.indices.toList(), emptyList(), null)
        else for (single in caps.indices) enumerate(caps.indices.filter { it != single }, emptyList(), single)

        val accepted = mutableListOf<Candidate>()
        for (lines in candidates) {
            val canonical = lines.map { line ->
                val reverse = line.copy(points = line.points.reversed())
                if (lineKey(line) <= lineKey(reverse)) line else reverse
            }.sortedBy(::lineKey)
            val key = canonical.joinToString("|") { lineKey(it) }
            if (accepted.any { it.key == key }) continue
            val recovered = canonical.map(::cubic)
            val expanded = recovered.expandStrokes(options)
            val rebuilt = polygon(expanded.singleOrNull() ?: continue) ?: continue
            // Compare lost and gained regions independently: equal areas do not imply equal ink.
            val difference = compareFilledInk(paths, expanded)
            if (!difference.symmetricDifferenceRatio.isFinite() || difference.symmetricDifferenceRatio > 0.003) continue
            if (!boundaryWithin(polygon, rebuilt, tolerance) || !boundaryWithin(rebuilt, polygon, tolerance)) continue
            // Certified upper bound for polygonal boundaries, not a sample-point maximum.
            var low = 0.0; var high = tolerance
            repeat(12) {
                val middle = (low + high) / 2
                if (boundaryWithin(polygon, rebuilt, middle) && boundaryWithin(rebuilt, polygon, middle)) high = middle
                else low = middle
            }
            accepted += Candidate(recovered, difference, high, width, canonical.sumOf { it.points.size - 2 }, key)
        }
        // Keep a bounded set, including the old straight-first baseline, for joint pair planning.
        return accepted.sortedWith(compareBy<Candidate> { it.bends }.thenBy { it.key }).take(4)
    }

    private fun lineKey(line: Line): String = (line.points.flatMap { listOf(it.x, it.y) } + line.width)
        .joinToString(",") { (it * 1e8).roundToLong().toString() }
    private fun cubic(line: Line): CubicPath {
        val p = mutableListOf(line.points.first().x, line.points.first().y)
        for ((a, b) in line.points.zipWithNext()) for (k in 1..3) {
            val q = a + (b - a) * (k / 3.0)
            p += q.x; p += q.y
        }
        return cubicPathOf(p.toDoubleArray(), false, MorphContourRole.Stroke,
            StrokeStyle(line.width, StrokeCap.Butt, StrokeJoin.Miter, 4f))
    }

    private fun polygon(path: CubicPath): List<P>? {
        if (!path.closed || path.role != MorphContourRole.Shape || path.stroke != null) return null
        val data = path.points
        fun point(i: Int) = P(data[i], data[i + 1])
        val result = mutableListOf(point(0))
        for (i in 0 until data.size - 2 step 6) {
            val a = point(i); val b = point(i + 6); val delta = b - a
            for (j in listOf(i + 2, i + 4)) {
                val control = point(j) - a
                if (abs(delta.cross(control)) > 1e-8 * maxOf(delta.length(), 1e-8) ||
                    control.dot(delta) < -1e-10 || control.dot(delta) > delta.dot(delta) + 1e-10) return null
            }
            if ((b - result.last()).length() > 1e-8) result += b
        }
        if ((result.last() - result.first()).length() < 1e-8) result.removeAt(result.lastIndex)
        var changed = true
        while (changed && result.size > 3) {
            changed = false
            for (i in result.indices) {
                val a = result[(i + result.size - 1) % result.size]
                val b = result[i]; val c = result[(i + 1) % result.size]
                if (distance(b, a, c) < 1e-8) { result.removeAt(i); changed = true; break }
            }
        }
        return result.takeIf { it.size >= 3 && area(it) > 1e-10 }
    }

    private fun area(p: List<P>) = abs(p.indices.sumOf { p[it].cross(p[(it + 1) % p.size]) }) / 2
    private fun inside(p: P, polygon: List<P>): Boolean {
        var result = false
        for (i in polygon.indices) {
            val a = polygon[i]; val b = polygon[(i + 1) % polygon.size]
            if ((a.y > p.y) != (b.y > p.y) && p.x < (b.x - a.x) * (p.y - a.y) / (b.y - a.y) + a.x) result = !result
        }
        return result
    }
    private fun distance(p: P, a: P, b: P): Double {
        val delta = b - a
        val t = ((p - a).dot(delta) / delta.dot(delta)).coerceIn(0.0, 1.0)
        return (p - (a + delta * t)).length()
    }
    /** Cover each entire edge by the union of capsules around the other polygon's edges. */
    private fun boundaryWithin(a: List<P>, b: List<P>, tolerance: Double): Boolean {
        for (i in a.indices) {
            val start = a[i]; val delta = a[(i + 1) % a.size] - start
            val intervals = mutableListOf<Pair<Double, Double>>()
            fun circle(center: P) {
                val w = start - center
                val aa = delta.dot(delta); val bb = w.dot(delta)
                val discriminant = bb * bb - aa * (w.dot(w) - tolerance * tolerance)
                if (discriminant < 0) return
                val root = sqrt(discriminant)
                val lo = maxOf(0.0, (-bb - root) / aa); val hi = minOf(1.0, (-bb + root) / aa)
                if (lo <= hi) intervals += lo to hi
            }
            for (j in b.indices) {
                val u = b[j]; val v = b[(j + 1) % b.size]
                val edge = v - u; val length = edge.length(); val direction = edge * (1 / length)
                var lo = 0.0; var hi = 1.0
                fun clip(value: Double, slope: Double, min: Double, max: Double) {
                    if (abs(slope) < 1e-15) { if (value < min || value > max) hi = -1.0; return }
                    val x = (min - value) / slope; val y = (max - value) / slope
                    lo = maxOf(lo, minOf(x, y)); hi = minOf(hi, maxOf(x, y))
                }
                clip((start - u).dot(direction), delta.dot(direction), 0.0, length)
                clip((start - u).cross(direction), delta.cross(direction), -tolerance, tolerance)
                if (lo <= hi) intervals += lo to hi
                circle(u); circle(v)
            }
            var covered = 0.0
            for ((lo, hi) in intervals.sortedBy { it.first }) {
                if (lo > covered + 1e-12) break
                covered = maxOf(covered, hi)
            }
            if (covered < 1.0 - 1e-12) return false
        }
        return true
    }
}

internal fun MorphPlan.withInferenceDecision(inferred: Boolean, sourceFilled: Boolean, targetFilled: Boolean, detail: String = ""): MorphPlan =
    MorphPlan(items.map { item ->
        item.copy(decision = item.decision.copy(
            strategy = if (inferred) MorphAppliedStrategy.InferredCenterline else item.decision.strategy,
            sourceContours = if (inferred && sourceFilled) listOf(0) else item.decision.sourceContours,
            targetContours = if (inferred && targetFilled) listOf(0) else item.decision.targetContours,
            reason = if (inferred) "Experimental straight-stroke inference: flat caps, near-uniform width; polygon boundary error <= 0.6% of width, symmetric ink difference <= 0.3%. " + detail + item.decision.reason
                else "Stroke inference ineligible or identical inputs; retained outline/explicit-stroke representation. " + item.decision.reason,
        ))
    }, sampleCount)
