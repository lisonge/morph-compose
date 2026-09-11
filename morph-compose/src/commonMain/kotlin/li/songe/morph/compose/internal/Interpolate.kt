package li.songe.morph.compose.internal

import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin

internal fun interpolatePolar(
    plan: MorphPlan,
    progress: Double,
    output: Array<DoubleArray>,
) {
    for (contourIndex in plan.items.indices) {
        val item = plan.items[contourIndex]
        val points = output[contourIndex]
        if (!item.polarInterpolation) {
            for (pointIndex in 0 until plan.sampleCount) {
                val arrayIndex = pointIndex * 2
                points[arrayIndex] =
                    item.source[arrayIndex] +
                        (item.targetOriented[arrayIndex] - item.source[arrayIndex]) * progress
                points[arrayIndex + 1] =
                    item.source[arrayIndex + 1] +
                        (item.targetOriented[arrayIndex + 1] - item.source[arrayIndex + 1]) * progress
            }
            continue
        }
        val scale = exp(item.logScale * progress)
        val angle = item.theta * progress
        val cosine = cos(angle) * scale
        val sine = sin(angle) * scale
        val centerX: Double
        val centerY: Double
        val block = item.blockTransport
        if (block != null) {
            centerX =
                item.sourceCenterX + block.driftX * progress +
                    (block.offsetX * cosine - block.offsetY * sine - block.offsetX)
            centerY =
                item.sourceCenterY + block.driftY * progress +
                    (block.offsetX * sine + block.offsetY * cosine - block.offsetY)
        } else {
            centerX =
                item.sourceCenterX + (item.targetCenterX - item.sourceCenterX) * progress
            centerY =
                item.sourceCenterY + (item.targetCenterY - item.sourceCenterY) * progress
        }
        for (pointIndex in 0 until plan.sampleCount) {
            val arrayIndex = pointIndex * 2
            val localX =
                item.sourceCentered[arrayIndex] +
                    (item.targetLocal[arrayIndex] - item.sourceCentered[arrayIndex]) * progress
            val localY =
                item.sourceCentered[arrayIndex + 1] +
                    (item.targetLocal[arrayIndex + 1] - item.sourceCentered[arrayIndex + 1]) * progress
            points[arrayIndex] = centerX + localX * cosine - localY * sine
            points[arrayIndex + 1] = centerY + localX * sine + localY * cosine
        }
    }
}

internal fun interpolateLinear(
    plan: MorphPlan,
    progress: Double,
    output: Array<DoubleArray>,
) {
    for (contourIndex in plan.items.indices) {
        val item = plan.items[contourIndex]
        val points = output[contourIndex]
        for (pointIndex in 0 until plan.sampleCount) {
            val arrayIndex = pointIndex * 2
            points[arrayIndex] =
                item.source[arrayIndex] +
                    (item.targetOriented[arrayIndex] - item.source[arrayIndex]) * progress
            points[arrayIndex + 1] =
                item.source[arrayIndex + 1] +
                    (item.targetOriented[arrayIndex + 1] - item.source[arrayIndex + 1]) * progress
        }
    }
}
