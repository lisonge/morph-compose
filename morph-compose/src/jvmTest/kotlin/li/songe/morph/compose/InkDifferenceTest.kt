package li.songe.morph.compose

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class InkDifferenceTest {
    private fun rectangle(left: Float, top: Float, right: Float, bottom: Float) =
        morphGeometryOf(Path().apply { addRect(Rect(left, top, right, bottom)) }, Size(1f, 1f)).vector.toCubicPaths(false)

    @Test fun equalAreasCannotCancelLostAndAddedInk() {
        val source = rectangle(0.2f, 0.45f, 0.8f, 0.55f)
        val shifted = rectangle(0.2f, 0.4502f, 0.8f, 0.5502f)
        val result = compareFilledInk(source, shifted)
        assertEquals(filledArea(source), filledArea(shifted), 1e-7)
        assertEquals(0.00012, result.addedArea, 1e-7)
        assertEquals(0.00012, result.missingArea, 1e-7)
        assertTrue(result.added.isNotEmpty() && result.missing.isNotEmpty())
        assertTrue(result.symmetricDifferenceRatio > 0.003, "Net area would accept this incorrect reconstruction")
        val reverse = compareFilledInk(shifted, source)
        assertEquals(result.addedArea, reverse.missingArea, 1e-9)
        assertEquals(result.missingArea, reverse.addedArea, 1e-9)
    }

    @Test fun differencesIncludeHolesAndRetainTheirRegionGeometry() {
        val source = rectangle(0f, 0f, 1f, 1f)
        val ring = morphGeometryOf(Path().apply {
            fillType = PathFillType.EvenOdd
            addRect(Rect(0f, 0f, 1f, 1f)); addRect(Rect(0.25f, 0.25f, 0.75f, 0.75f))
        }, Size(1f, 1f)).vector.toCubicPaths(false)
        val difference = compareFilledInk(source, ring)
        assertEquals(0.0, difference.addedArea, 1e-9)
        assertEquals(0.25, difference.missingArea, 1e-9)
        assertEquals(0.75, filledArea(ring), 1e-9)
        assertEquals(0.0, compareFilledInk(ring, ring).symmetricDifferenceRatio, 1e-9)
    }
}
