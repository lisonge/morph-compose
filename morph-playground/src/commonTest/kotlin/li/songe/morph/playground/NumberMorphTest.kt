package li.songe.morph.playground

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NumberMorphTest {
    private fun pair(from: String, to: String) = NumberPair(
        requireNotNull(IntegerGlyphs.parse(from)), requireNotNull(IntegerGlyphs.parse(to)),
    )

    @Test fun carryMatchesOnesAndTensAndAddsHundreds() {
        val slots = pair("99", "100").slots()
        assertEquals(listOf(NumberSlot(0, 9, 0), NumberSlot(1, 9, 0), NumberSlot(2, null, 1)), slots)
        assertEquals(0f, slots[2].visibility(0f))
        assertEquals(0.5f, slots[2].visibility(0.5f))
        assertEquals(1f, slots[2].visibility(1f))
    }

    @Test fun shorterTargetsRemoveOnlyTheHigherPlaces() {
        val slots = pair("1024", "7").slots()
        assertEquals(NumberSlot(0, 4, 7), slots[0])
        assertEquals(listOf(2, 0, 1), slots.drop(1).map { it.from })
        assertTrue(slots.drop(1).all { it.to == null && it.visibility(1f) == 0f })
        assertEquals(1f, slots[0].visibility(0.5f))
        assertEquals(NumberSlot(0, 0, 0), pair("0", "0").slots().single())
    }

    @Test fun signsAreSeparateFromPlaceValuesAndLargeIntegersRemainExact() {
        val transition = pair("-009", "100")
        assertTrue(transition.hasSign)
        assertEquals(3, transition.slots().size)
        assertEquals(listOf(9, 0, 0), transition.slots().map { it.from })
        assertEquals("-009", transition.from.text)
        val large = "1234567890123456789012345678901234567890"
        assertEquals(large, IntegerGlyphs.parse(large)?.text)
        assertEquals("001", IntegerGlyphs.parse(" +001 ")?.text)
        listOf("", "-", "--1", "-+1", "+-1", "1.5", "1e3", "NaN", "12 3", "１２").forEach {
            assertNull(IntegerGlyphs.parse(it), it)
        }
    }

    @Test fun invalidEditsKeepAppliedPairAndScrubbingStopsPlayback() {
        val state = NumberDemoState()
        assertTrue(state.play())
        state.seek(0.4f)
        assertFalse(state.playing)
        state.fromInput = "-"
        assertFalse(state.play())
        assertEquals("99", state.pair.from.text)
        assertEquals(0.4f, state.progress)
        assertNotNull(state.error)
        state.fromInput = "123"
        assertTrue(state.play())
        assertEquals("123", state.pair.from.text)
        assertNull(state.error)
        assertEquals(0f, state.progress)
        val replay = state.replay
        state.play()
        assertTrue(state.replay > replay)
        state.reverse()
        assertEquals("100", state.pair.from.text)
        assertEquals("123", state.pair.to.text)
    }
}
