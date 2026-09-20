@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package li.songe.morph.playground

import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.renderComposeScene
import androidx.compose.ui.unit.dp
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.File
import javax.imageio.ImageIO
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import li.songe.morph.compose.*
import org.jetbrains.skia.EncodedImageFormat

class InferredStrokeIconTest {
    private val options = MorphOptions(transitionMode = MorphTransitionMode.ExperimentalStrokeInference)
    private fun icon(name: String) = iconEntries.first { it.name == name }.imageVector
    private fun render(plan: ImageVectorMorphPlan, t: Float, interpolation: MorphInterpolation, tint: Color = Color.Black, pixels: Int = 96): BufferedImage {
        val image = renderComposeScene(pixels, pixels) {
            MorphIcon(plan, t, Modifier.size(pixels.dp), tint = tint, interpolation = interpolation)
        }
        try {
            val data = checkNotNull(image.encodeToData(EncodedImageFormat.PNG))
            try { return ImageIO.read(ByteArrayInputStream(data.bytes)) } finally { data.close() }
        } finally { image.close() }
    }
    private fun coverage(image: BufferedImage) = image.getRGB(0, 0, 96, 96, null, 0, 96).map { it ushr 24 }

    @Test fun largeScaleEndpointReconstructionIsMeasuredAndReviewable() {
        val output = File("build/reports/stroke-inference").apply { mkdirs() }
        val measurements = mutableListOf("from\tto\tinterpolation\tendpoint\tchangedPixels\ttotalAlphaErrorRatio")
        for ((a, b) in listOf("Close" to "Arrow back", "Close" to "Check", "Remove" to "Close")) {
            val plan = buildMorphPlan(icon(a), icon(b), options)
            for (interpolation in MorphInterpolation.entries) for (t in listOf(0f, 1f)) {
                val exact = render(plan, t, interpolation, pixels = 768)
                val near = render(plan, if (t == 0f) 0.000001f else 0.999999f, interpolation, pixels = 768)
                val original = exact.getRGB(0, 0, 768, 768, null, 0, 768).map { it ushr 24 }
                val rebuilt = near.getRGB(0, 0, 768, 768, null, 0, 768).map { it ushr 24 }
                val ratio = original.indices.sumOf { abs(original[it] - rebuilt[it]).toLong() }.toDouble() / original.sum()
                measurements += "$a\t$b\t$interpolation\t$t\t${original.indices.count { abs(original[it] - rebuilt[it]) > 128 }}\t$ratio"
                // Diagnostic only: geometric area tolerance cannot be reused as a raster alpha
                // threshold without calibration across resolutions and antialiasing behavior.
                assertTrue(ratio.isFinite(), "$a -> $b $interpolation endpoint=$t invalid measurement")
                if (interpolation == MorphInterpolation.Polar && t == 1f) {
                    val sheet = BufferedImage(768 * 3, 768, BufferedImage.TYPE_INT_RGB)
                    val g = sheet.createGraphics()
                    try {
                        g.color = java.awt.Color.WHITE; g.fillRect(0, 0, sheet.width, sheet.height)
                        g.drawImage(exact, 0, 0, null); g.drawImage(near, 768, 0, null)
                        g.drawImage(render(plan, 0.5f, interpolation, pixels = 768), 1536, 0, null)
                    } finally { g.dispose() }
                    ImageIO.write(sheet, "png", File(output, "large-${a.replace(' ', '-')}-${b.replace(' ', '-')}.png"))
                }
            }
        }
        File(output, "large-endpoints.tsv").writeText(measurements.joinToString("\n"))
    }

    @Test fun originalFilledCrossAndArrowsRecoverWithoutEndpointFlashes() {
        val output = File("build/reports/stroke-inference").apply { mkdirs() }
        val decisions = mutableListOf("from\tto\trotation\tplanningMs\tdecision")
        for (name in listOf("Arrow back", "Arrow forward", "Arrow up", "Arrow down")) {
            for ((a, b) in listOf("Close" to name, name to "Close")) {
                for (rotation in MorphRotationPreference.entries) {
                    val before = System.nanoTime()
                    val plan = buildMorphPlan(icon(a), icon(b), options.copy(rotationPreference = rotation))
                    val elapsed = (System.nanoTime() - before) / 1_000_000.0
                    decisions += "$a\t$b\t$rotation\t$elapsed\t${plan.compatibilityReport.contours.first().decision}"
                    assertEquals(2, plan.contourCount, "$a -> $b")
                    assertTrue(plan.compatibilityReport.contours.all { it.strategy == MorphAppliedStrategy.InferredCenterline },
                        "$a -> $b: ${plan.compatibilityReport}")
                    for (interpolation in MorphInterpolation.entries) {
                        val endpointDifference = listOf(0f, 1f).sumOf { t ->
                            val exact = coverage(render(plan, t, interpolation))
                            val near = coverage(render(plan, if (t == 0f) 0.000001f else 0.999999f, interpolation))
                            exact.indices.count { abs(exact[it] - near[it]) > 128 }
                        }
                        assertTrue(endpointDifference <= 8, "$a -> $b $rotation $interpolation endpoint pixels=$endpointDifference")
                    }
                }
            }
        }
        File(output, "inference-plans.tsv").writeText(decisions.joinToString("\n"))
        // Reviewable before/after frames, using the same original inputs in both rows.
        val from = icon("Close"); val to = icon("Arrow back")
        val sheet = BufferedImage(96 * 9, 96 * 2, BufferedImage.TYPE_INT_RGB)
        val g = sheet.createGraphics()
        try {
            g.color = java.awt.Color.WHITE; g.fillRect(0, 0, sheet.width, sheet.height)
            for ((row, mode) in listOf(MorphTransitionMode.Auto, MorphTransitionMode.ExperimentalStrokeInference).withIndex()) {
                val plan = buildMorphPlan(from, to, options.copy(transitionMode = mode))
                for (i in 0..8) g.drawImage(render(plan, i / 8f, MorphInterpolation.Polar), i * 96, row * 96, null)
            }
        } finally { g.dispose() }
        ImageIO.write(sheet, "png", File(output, "close-arrow-comparison.png"))
    }

    @Test fun unsupportedOriginalCurvesRetainOutlinesAndExplainFallback() {
        val plan = buildMorphPlan(icon("Search"), icon("Close"), options)
        assertTrue(plan.compatibilityReport.contours.none { it.strategy == MorphAppliedStrategy.InferredCenterline })
        assertTrue(plan.compatibilityReport.contours.all { it.decision.contains("ineligible") })
    }

    @Test fun unequalRecoveredStrokeCountsPreserveOriginalEndpointInk() {
        val decisions = mutableListOf("from\tto\tdecision")
        for ((a, b) in listOf("Close" to "Check", "Add" to "Check", "Remove" to "Close").flatMap { listOf(it, it.second to it.first) }) {
            val plan = buildMorphPlan(icon(a), icon(b), options)
            assertTrue(plan.compatibilityReport.contours.all { it.strategy == MorphAppliedStrategy.InferredCenterline }, "$a -> $b")
            for (t in listOf(0f, 1f)) {
                val exact = coverage(render(plan, t, MorphInterpolation.Polar))
                val near = coverage(render(plan, if (t == 0f) 0.000001f else 0.999999f, MorphInterpolation.Polar))
                assertTrue(exact.indices.count { abs(exact[it] - near[it]) > 128 } <= 4, "$a -> $b endpoint $t")
            }
            decisions += "$a\t$b\t${plan.compatibilityReport.contours.first().decision}"
        }
        File("build/reports/stroke-inference").apply { mkdirs() }
            .resolve("unequal-stroke-plans.tsv").writeText(decisions.joinToString("\n"))
    }

    @Test fun translucentFilledInkDoesNotDarkenAtRecoveredCrossings() {
        val plan = buildMorphPlan(icon("Close"), icon("Arrow back"), options)
        for (t in listOf(0.000001f, 0.25f, 0.5f, 0.75f, 0.999999f)) {
            val alpha = coverage(render(plan, t, MorphInterpolation.Polar, Color.Black.copy(alpha = 0.5f)))
            assertTrue(alpha.max() in 127..129, "Overlapping recovered strokes changed fill opacity at $t")
        }
    }
}
