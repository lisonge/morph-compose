@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package li.songe.morph.playground

import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.renderComposeScene
import androidx.compose.ui.unit.dp
import java.io.ByteArrayInputStream
import javax.imageio.ImageIO
import li.songe.morph.compose.MorphCompatibility
import li.songe.morph.compose.MorphContourKind
import li.songe.morph.compose.MorphIcon
import li.songe.morph.compose.MorphOptions
import li.songe.morph.compose.MorphRotationPreference
import li.songe.morph.compose.buildMorphPlan
import org.jetbrains.skia.EncodedImageFormat
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SearchCloseTest {
    @Test fun registeredSearchAndCloseMorphAsTwoStrokesWithoutBirthOrDeathFallback() {
        val search = iconEntries.first { it.name == "Search" }.imageVector
        val close = iconEntries.first { it.name == "Close" }.imageVector
        for ((from, to) in listOf(search to close, close to search)) {
            for (rotation in MorphRotationPreference.entries) {
                val plan = buildMorphPlan(from, to, MorphOptions(rotationPreference = rotation))
                assertEquals(MorphCompatibility.FullPolar, plan.compatibilityReport.compatibility)
                assertEquals(2, plan.contourCount)
                assertTrue(plan.compatibilityReport.contours.all {
                    it.kind == MorphContourKind.Stroke && it.fallbackReason == null
                })
                fun render(progress: Float): IntArray {
                    val image = renderComposeScene(240, 240) {
                        MorphIcon(plan, progress, Modifier.size(240.dp), tint = Color.Black)
                    }
                    try {
                        val data = checkNotNull(image.encodeToData(EncodedImageFormat.PNG))
                        try {
                            val bitmap = ImageIO.read(ByteArrayInputStream(data.bytes))
                            return bitmap.getRGB(0, 0, 240, 240, null, 0, 240)
                        } finally { data.close() }
                    } finally { image.close() }
                }
                // Even the closed-looking ring must not drop a segment at the first frame.
                for (endpoint in listOf(0f, 1f)) {
                    val exact = render(endpoint)
                    val near = render(if (endpoint == 0f) 0.000001f else 0.999999f)
                    // Subdividing a stroked cubic changes Skia's antialiasing at edge pixels.
                    // Compare coverage rather than summing those subpixel alpha differences.
                    val difference = exact.indices.count {
                        kotlin.math.abs((exact[it] ushr 24) - (near[it] ushr 24)) > 128
                    }
                    assertTrue(difference <= 4, "Endpoint flash: $difference, $endpoint, $rotation")
                }
                for (t in listOf(0.25f, 0.5f, 0.75f)) {
                    val pixels = render(t)
                    val ink = pixels.count { it ushr 24 > 128 }
                    assertTrue(ink > 2000, "Strokes vanished at $t: $ink pixels")
                }
            }
        }
    }
}
