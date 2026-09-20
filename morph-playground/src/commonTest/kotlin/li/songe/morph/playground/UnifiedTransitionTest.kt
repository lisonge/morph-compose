package li.songe.morph.playground

import li.songe.morph.compose.MorphCompatibility
import li.songe.morph.compose.MorphContourKind
import li.songe.morph.compose.MorphOptions
import li.songe.morph.compose.MorphRotationPreference
import li.songe.morph.compose.MorphTransitionMode
import li.songe.morph.compose.buildMorphPlan
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class UnifiedTransitionTest {
    @Test fun iconPairsUseAConsistentRepresentationInBothDirections() {
        fun icon(name: String) = iconEntries.first { it.name == name }.imageVector
        for ((a, b) in listOf("Close" to "Play", "Search" to "Close", "Close" to "Arrow back", "Home" to "Settings")) {
            for ((from, to) in listOf(a to b, b to a)) {
                for (mode in listOf(MorphTransitionMode.Auto, MorphTransitionMode.Outline)) {
                    for (rotation in MorphRotationPreference.entries) {
                        val plan = buildMorphPlan(icon(from), icon(to), MorphOptions(transitionMode = mode, rotationPreference = rotation))
                        val report = plan.compatibilityReport
                        assertTrue(report.compatibility != MorphCompatibility.Unsupported)
                        assertTrue(report.contours.all { it.residual.isFinite() })
                        if (a == "Close" && b == "Play") {
                            assertEquals(MorphCompatibility.FullPolar, report.compatibility)
                            assertEquals(1, plan.contourCount)
                        }
                        assertEquals(false, report.contours.any { it.kind == MorphContourKind.Stroke }, "$from -> $to $mode")
                    }
                }
            }
        }
    }
}
