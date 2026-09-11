package li.songe.morph.playground

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PlaygroundStateTest {
    @Test
    fun snapshotReflectsSelectionAndPlaybackControls() {
        val state = PlaygroundState(listOf(2, 7, 9))

        state.selectPair(2, 7)
        state.scrubTo(0.41f)
        state.compareLinear = true
        state.setPlayback(true)

        assertEquals(
            PlaygroundSnapshot(
                selectedIndices = listOf(2, 7, 9),
                activeSelectionPosition = 0,
                fromIndex = 2,
                toIndex = 7,
                progress = 0.41f,
                isPlaying = true,
                compareLinear = true,
                isDarkTheme = false,
            ),
            state.snapshot(),
        )

        state.toggleTheme()
        assertTrue(state.snapshot().isDarkTheme)
    }

    @Test
    fun iconSelectionCanBecomeEmptyAndDisablesAnimation() {
        val state = PlaygroundState(listOf(0, 1))

        state.toggleIcon(9)
        assertEquals(listOf(0, 1, 9), state.selectedIndices)
        assertEquals(3, state.selectedOrder(9))

        state.toggleIcon(1)
        assertEquals(listOf(0, 9), state.selectedIndices)

        state.setPlayback(true)
        assertTrue(state.isPlaying)
        state.toggleIcon(0)
        assertEquals(listOf(9), state.selectedIndices)
        assertFalse(state.isPlaying)
        assertFalse(state.isAnimating)
        assertEquals(9, state.activeIconIndex)
        assertEquals(9, state.fromIndex)
        assertEquals(9, state.toIndex)
        assertEquals(0.0f, state.progress)

        state.requestNext()
        state.setPlayback(true)
        state.scrubTo(0.5f)
        assertFalse(state.isPlaying)
        assertFalse(state.isAnimating)
        assertEquals(0.0f, state.progress)

        state.toggleIcon(9)
        assertTrue(state.selectedIndices.isEmpty())
        assertNull(state.activeIconIndex)
        assertEquals(-1, state.activeSelectionPosition)
        assertEquals(-1, state.fromIndex)
        assertEquals(-1, state.toIndex)

        state.toggleIcon(7)
        state.toggleIcon(8)
        assertEquals(listOf(7, 8), state.selectedIndices)
        assertEquals(7, state.fromIndex)
        assertEquals(8, state.toIndex)
    }

    @Test
    fun replaceSelectionAcceptsZeroOrOneIcon() {
        val state = PlaygroundState(listOf(0, 1))

        state.replaceSelection(emptyList())
        assertTrue(state.selectedIndices.isEmpty())

        state.replaceSelection(listOf(12))
        assertEquals(listOf(12), state.selectedIndices)
        assertEquals(12, state.activeIconIndex)
    }

    @Test
    fun nextAndPreviousAnimateAroundTheSelectedSequence() {
        val state = PlaygroundState(listOf(2, 5, 9))

        state.requestNext()
        val firstRequest = state.animationRequestId
        assertEquals(2, state.fromIndex)
        assertEquals(5, state.toIndex)
        assertTrue(state.isSingleStep)
        assertTrue(state.completeAnimation(firstRequest))
        assertEquals(5, state.activeIconIndex)

        state.requestPrevious()
        val secondRequest = state.animationRequestId
        assertEquals(5, state.fromIndex)
        assertEquals(2, state.toIndex)
        assertTrue(state.completeAnimation(secondRequest))
        assertEquals(2, state.activeIconIndex)
    }

    @Test
    fun playbackCanPauseAndResumeTheCurrentTransition() {
        val state = PlaygroundState(listOf(0, 1, 2))

        state.setPlayback(true)
        val initialRequest = state.animationRequestId
        state.updateAnimationProgress(initialRequest, 0.37f)
        state.setPlayback(false)

        assertFalse(state.isPlaying)
        assertTrue(state.isAnimating)
        assertEquals(0.37f, state.progress)

        state.setPlayback(true)
        assertTrue(state.isPlaying)
        assertTrue(state.animationRequestId > initialRequest)
        assertEquals(0.37f, state.progress)
    }
}
