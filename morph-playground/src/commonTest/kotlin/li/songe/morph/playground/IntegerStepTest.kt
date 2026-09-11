package li.songe.morph.playground

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class IntegerStepTest {
    private fun number(text: String) = requireNotNull(IntegerGlyphs.parse(text))

    @Test fun signedIncrementAndDecrementMatchIntegerArithmetic() {
        for (from in -110..110) for (to in -110..110) {
            val expected = from + (to - from).compareTo(0)
            assertEquals(expected.toString(), number(from.toString()).stepToward(number(to.toString())).text,
                "$from → $to")
        }
    }

    @Test fun largeIntegersAndPaddingStayExact() {
        val nines = "9".repeat(40)
        val power = "1" + "0".repeat(40)
        assertEquals(power, number(nines).stepToward(number(power)).text)
        assertEquals(nines, number(power).stepToward(number("0")).text)
        assertEquals("-$power", number("-$nines").stepToward(number("-$power")).text)
        assertEquals("009", number("008").stepToward(number("12")).text)
        assertEquals("010", number("009").stepToward(number("12")).text)
        assertEquals("0", number("-001").stepToward(number("0")).text)
        assertEquals("-000", number("0").stepToward(number("-000")).text)
        assertEquals("1", number("001").stepToward(number("1")).text)
    }

    @Test fun playbackVisitsEveryAdjacentPairAndStopsAtExactTarget() {
        for ((from, to) in listOf(1 to 100, 100 to 1, -3 to 3, 3 to -3, 5 to 5)) {
            val state = NumberDemoState()
            state.changeStep(true)
            state.fromInput = from.toString()
            state.toInput = to.toString()
            assertTrue(state.play())
            var current = from
            var transitions = 0
            while (state.playing && transitions < 110) {
                val next = current + (to - current).compareTo(0)
                assertEquals(current.toString(), state.pair.from.text)
                assertEquals(next.toString(), state.pair.to.text)
                state.completeTransition()
                current = next
                transitions++
            }
            assertFalse(state.playing)
            assertEquals(to.toString(), state.pair.to.text)
            assertEquals(1f, state.progress)
            assertEquals(maxOf(1, kotlin.math.abs(to - from)), transitions)
        }
    }

    @Test fun modeChangesScrubbingAndReplayCancelThePreviousSequence() {
        val state = NumberDemoState()
        state.fromInput = "1"
        state.toInput = "100"
        state.changeStep(true)
        state.play()
        state.completeTransition()
        assertEquals("2", state.pair.from.text)
        state.seek(0.5f)
        state.completeTransition()
        assertFalse(state.playing)
        assertEquals("2", state.pair.from.text)
        assertEquals(0.5f, state.progress)
        state.play()
        assertEquals("1", state.pair.from.text)
        state.changeStep(false)
        assertFalse(state.playing)
        assertEquals("100", state.pair.to.text)
        state.play()
        state.completeTransition()
        assertFalse(state.playing)
        state.changeStep(true)
        state.reverse()
        assertEquals("100", state.pair.from.text)
        assertEquals("99", state.pair.to.text)
    }
}
