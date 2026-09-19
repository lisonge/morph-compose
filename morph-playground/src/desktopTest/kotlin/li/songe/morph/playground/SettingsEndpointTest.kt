package li.songe.morph.playground

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
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
import kotlin.test.assertTrue

class SettingsEndpointTest {
    @Test fun homeSettingsCurvesConvergeAtBothEndpointsIncludingOvershoot() {
        val size = Size(24f, 24f)
        fun geometry(vector: ImageVector) = morphGeometryOf((vector.root[0] as VectorPath).pathData, size)
        val home = geometry(Icons.Filled.Home)
        val settings = geometry(Icons.Filled.Settings)
        for ((from, to) in listOf(home to settings, settings to home)) {
            for (rotation in MorphRotationPreference.entries) {
                val writer = buildMorphPlan(from, to, MorphOptions(rotationPreference = rotation)).createPathWriter()
                for (mode in MorphInterpolation.entries) {
                    fun frame(t: Float) = outline(Path().also { writer.writePath(it, t, size, interpolation = mode) })
                    for (endpoint in listOf(0f, 1f)) {
                        val exact = frame(endpoint)
                        for (delta in listOf(-0.000001f, 0.000001f)) {
                            val near = frame(endpoint + delta)
                            var different = 0
                            for (y in 0 until 384) for (x in 0 until 384) {
                                val px = (x + 0.5) / 16
                                val py = (y + 0.5) / 16
                                if (exact.contains(px, py) != near.contains(px, py)) different++
                            }
                            assertTrue(different <= 4, "Endpoint $endpoint delta=$delta $rotation $mode: $different changed pixels")
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
