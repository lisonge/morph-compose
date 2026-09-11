package li.songe.morph.playground

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class PlaygroundTabsTest {
    @Test fun moduleControlsSurviveTabSwitchesAndDoNotLeakBetweenModules() {
        val state = PlaygroundState()
        state.selectedTab = PlaygroundTab.Typography
        state.demo(PlaygroundTab.Typography).select(8, 10)
        state.demo(PlaygroundTab.Typography).seek(0.7f)
        state.selectedTab = PlaygroundTab.Clipping
        assertEquals(1, state.demo(PlaygroundTab.Clipping).targetIndex)
        state.selectedTab = PlaygroundTab.Typography
        assertEquals(8, state.demo(PlaygroundTab.Typography).targetIndex)
        assertEquals(0.7f, state.demo(PlaygroundTab.Typography).progress)
        assertTrue(state.demo(PlaygroundTab.Typography).manual)
    }

    @Test fun demoRejectsInvalidTargetsAndProgress() {
        val state = FeatureDemoState()
        assertFailsWith<IllegalArgumentException> { state.select(3, 3) }
        assertFailsWith<IllegalArgumentException> { state.seek(Float.NaN) }
        assertFailsWith<IllegalArgumentException> { state.seek(1.1f) }
    }
}
