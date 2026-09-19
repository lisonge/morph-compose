package li.songe.morph.playground

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue
import li.songe.morph.compose.MorphContourStrategy
import li.songe.morph.compose.MorphOptions
import li.songe.morph.compose.buildMorphPlan

/** Whole-catalog structural gate, complementary to sampled visual comparisons. */
class CatalogContractTest {
    @Test fun everyRegisteredIconHasAFiniteStationarySelfPlanUnderEveryPolicy() {
        val failures = mutableListOf<String>()
        for (entry in iconEntries) for (policy in MorphContourStrategy.entries) {
            runCatching {
                val plan = buildMorphPlan(entry.imageVector, entry.imageVector, MorphOptions(contourStrategy = policy))
                require(plan.compatibilityReport.isSupported && plan.contourCount > 0)
                for (i in 0 until plan.contourCount) {
                    // Similarity residual takes a square root of an energy difference; exact
                    // identity can therefore carry O(sqrt(machine epsilon)) rounding noise.
                    require(plan.residual(i).isFinite() && plan.residual(i) < 1e-7) { "Identity residual=${plan.residual(i)}" }
                    require(plan.rotationRadians(i).isFinite() && abs(plan.rotationRadians(i)) < 1e-8) { "Identity rotation" }
                }
                require(plan.compatibilityReport.contours.all { it.holeOpeningCount == 0 && it.fallbackReason == null })
            }.onFailure { failures += "${entry.name} / $policy: ${it.message}" }
        }
        assertTrue(failures.isEmpty(), failures.joinToString("\n"))
    }
}
