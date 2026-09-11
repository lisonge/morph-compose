package li.songe.morph.playground

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.toSvg
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import li.songe.morph.compose.buildMorphPlan
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class SystemFontsTest {
    @Test
    fun installedFontsProduceDifferentOutlinesAndUsableTransitions() = runBlocking {
        val source = createSystemFontSource()
        val fonts = source.listFonts()
        assertTrue(fonts.isNotEmpty())
        val preferred = listOf("Arial", "Times New Roman", "DejaVu Sans", "DejaVu Serif", "Helvetica", "Times")
        val candidates = fonts.sortedBy { preferred.indexOf(it.id).let { rank -> if (rank < 0) Int.MAX_VALUE else rank } }
        val digits = candidates.asSequence().mapNotNull { font ->
            // Symbol and bitmap-only fonts may not have usable numeral outlines.
            runCatching { runBlocking { source.loadDigits(font) } }.getOrNull()
        }.take(2).toList()
        assertEquals(2, digits.size)
        fun svg(set: List<DemoGeometry>, digit: Int): String {
            val geometry = set[digit].geometry
            val path = Path()
            buildMorphPlan(geometry, geometry).createPathWriter().writePath(path, 1f, Size(100f, 100f))
            return path.toSvg()
        }
        assertNotEquals(svg(digits[0], 2), svg(digits[1], 2))
        digits.forEach { set ->
            assertEquals(10, set.size)
            for (digit in 0..9) {
                val plan = buildMorphPlan(set[digit].geometry, set[(digit + 1) % 10].geometry)
                val path = Path()
                plan.createPathWriter().writePath(path, 0.5f, Size(100f, 100f))
                assertFalse(path.isEmpty)
                assertTrue(path.getBounds().width.isFinite())
            }
            assertTrue(buildMorphPlan(set[8].geometry, set[8].geometry).contourCount >= 3)
        }
    }

    @Test
    fun failureKeepsPreviousFontAndCachedFontsAvoidReloading() = runBlocking {
        var loads = 0
        val good = SystemFontOption("good", "Good")
        val bad = SystemFontOption("bad", "Bad")
        val source = object : SystemFontSource {
            override val supported = true
            override val needsPermission = false
            override suspend fun listFonts() = listOf(good, bad)
            override suspend fun loadDigits(font: SystemFontOption): List<DemoGeometry> {
                loads++
                check(font != bad)
                return digitGeometries.toList()
            }
        }
        val state = TypographyState(source)
        state.refresh()
        state.select(good)
        val previous = state.digits
        state.select(bad)
        assertEquals(good, state.selected)
        assertSame(previous, state.digits)
        assertNotNull(state.error)
        state.select(bundledFont)
        state.select(good)
        assertSame(previous, state.digits)
        assertEquals(2, loads)
        assertFalse(state.loading)
    }

    @Test
    fun slowOldRequestCannotOverwriteLatestSelection() = runBlocking {
        val slow = SystemFontOption("slow", "Slow")
        val result = CompletableDeferred<List<DemoGeometry>>()
        val source = object : SystemFontSource {
            override val supported = true
            override val needsPermission = false
            override suspend fun listFonts() = listOf(slow)
            override suspend fun loadDigits(font: SystemFontOption) = result.await()
        }
        val state = TypographyState(source)
        state.refresh()
        val pending = launch(start = CoroutineStart.UNDISPATCHED) { state.select(slow) }
        assertTrue(state.loading)
        state.select(bundledFont)
        result.complete(digitGeometries.toList())
        pending.join()
        assertEquals(bundledFont, state.selected)
        assertFalse(state.loading)
    }

    @Test
    fun deniedPermissionCanBeRetriedWithoutLosingBundledFont() = runBlocking {
        var attempts = 0
        val local = SystemFontOption("local", "Local")
        val source = object : SystemFontSource {
            override val supported = true
            override val needsPermission = true
            override suspend fun listFonts(): List<SystemFontOption> {
                check(++attempts > 1)
                return listOf(local)
            }
            override suspend fun loadDigits(font: SystemFontOption) = digitGeometries
        }
        val state = TypographyState(source)
        state.refresh()
        assertFalse(state.loadedList)
        assertEquals(bundledFont, state.selected)
        assertNotNull(state.error)
        state.refresh()
        assertTrue(state.loadedList)
        assertTrue(local in state.fonts)
    }
}
