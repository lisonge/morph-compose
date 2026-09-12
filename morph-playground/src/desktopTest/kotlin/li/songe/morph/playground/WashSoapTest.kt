package li.songe.morph.playground

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Soap
import androidx.compose.material.icons.filled.Wash
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathSegment
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.VectorPath
import java.awt.geom.Path2D
import li.songe.morph.compose.MorphInterpolation
import li.songe.morph.compose.MorphOptions
import li.songe.morph.compose.MorphRotationPreference
import li.songe.morph.compose.buildMorphPlan
import li.songe.morph.compose.morphGeometryOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WashSoapTest {
    @Test fun fingersKeepTheirOriginalOutlineThroughoutTheTransition() {
        val size = Size(24f, 24f)
        fun geometry(vector: ImageVector) = morphGeometryOf((vector.root[0] as VectorPath).pathData, size)
        val wash = geometry(Icons.Filled.Wash)
        val soap = geometry(Icons.Filled.Soap)
        for ((from, to) in listOf(wash to soap, soap to wash)) {
            for (rotation in MorphRotationPreference.entries) {
                val writer = buildMorphPlan(from, to, MorphOptions(rotationPreference = rotation)).createPathWriter()
                for (mode in MorphInterpolation.entries) {
                    fun frame(t: Float) = Path().also { writer.writePath(it, t, size, interpolation = mode) }
                    val expected = outline(frame(0f))
                    for (t in listOf(-0.1f, 0.001f, 0.25f, 0.5f, 0.75f, 0.999f, 1.1f)) {
                        val path = frame(t)
                        assertTrue(path.iterator().asSequence().any { it.type == PathSegment.Type.Cubic })
                        val actual = outline(path)
                        // The upper-right drops/bubbles occupy y < 10; inspect the hand below them.
                        for (y in 100 until 240) for (x in 0 until 240) {
                            val px = (x + 0.5) / 10.0
                            val py = (y + 0.5) / 10.0
                            assertEquals(expected.contains(px, py), actual.contains(px, py),
                                "Finger changed at ($px,$py), progress=$t, $rotation, $mode")
                        }
                    }
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
                else -> error("Unexpected path segment ${segment.type}")
            }
        }
    }
}
