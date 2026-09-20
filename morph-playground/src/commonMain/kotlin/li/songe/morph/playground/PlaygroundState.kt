package li.songe.morph.playground

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import li.songe.morph.compose.MorphOptions
import li.songe.morph.compose.MorphRotationPreference
import li.songe.morph.compose.MorphTransitionMode
import li.songe.morph.compose.MorphStrokeCountStrategy
import li.songe.morph.compose.MorphContourStrategy

internal data class PlaygroundSnapshot(
    val selectedIndices: List<Int>,
    val activeSelectionPosition: Int,
    val fromIndex: Int,
    val toIndex: Int,
    val progress: Float,
    val isPlaying: Boolean,
    val compareLinear: Boolean,
    val isDarkTheme: Boolean,
    val rotationPreference: MorphRotationPreference = MorphRotationPreference.Auto,
    val route: String = "icons",
    val demoTarget: Int = 1,
    val demoProgress: Float = 0.5f,
    val demoManual: Boolean = false,
    val demoPlaying: Boolean = false,
)

internal class PlaygroundState(
    initialSelection: List<Int> = listOf(0, 1, 2, 3),
) {
    var selectedTab by mutableStateOf(PlaygroundTab.Icons)
    var customBackOrClose by mutableStateOf(true)
    var customSearchOpen by mutableStateOf(false)
    val typography = TypographyState()
    private val demos = PlaygroundTab.entries.associateWith { FeatureDemoState() }
    fun demo(tab: PlaygroundTab): FeatureDemoState = demos.getValue(tab)

    private val mutableSelectedIndices = mutableStateListOf<Int>()

    val selectedIndices: List<Int>
        get() = mutableSelectedIndices

    var activeSelectionPosition by mutableIntStateOf(-1)
        private set
    var fromIndex by mutableIntStateOf(-1)
        private set
    var toIndex by mutableIntStateOf(-1)
        private set
    var progress by mutableFloatStateOf(0.0f)
        private set
    var isPlaying by mutableStateOf(false)
        private set
    var compareLinear by mutableStateOf(false)
    var iconQuery by mutableStateOf("")
    var showPlanDetails by mutableStateOf(false)
    var rotationPreference by mutableStateOf(MorphRotationPreference.Auto)
        private set
    val morphOptions: MorphOptions
        get() = MorphOptions(rotationPreference = rotationPreference, transitionMode = transitionMode,
            strokeCountStrategy = strokeCountStrategy, contourStrategy = contourStrategy)

    var contourStrategy by mutableStateOf(MorphContourStrategy.SharedBoundary)
        private set

    fun changeContourStrategy(strategy: MorphContourStrategy) {
        if (contourStrategy == strategy) return
        contourStrategy = strategy
        resetAnimation()
    }

    var transitionMode by mutableStateOf(MorphTransitionMode.Auto)
        private set
    var strokeCountStrategy by mutableStateOf(MorphStrokeCountStrategy.SplitMerge)
        private set

    fun changeTransitionMode(mode: MorphTransitionMode) {
        if (transitionMode == mode) return
        transitionMode = mode
        resetAnimation()
    }

    fun changeStrokeCountStrategy(strategy: MorphStrokeCountStrategy) {
        if (strokeCountStrategy == strategy) return
        strokeCountStrategy = strategy
        resetAnimation()
    }

    fun changeRotationPreference(preference: MorphRotationPreference) {
        if (preference == rotationPreference) return
        rotationPreference = preference
        activeSelectionPosition = mutableSelectedIndices.indexOf(fromIndex)
        resetAnimation()
    }
    var isDarkTheme by mutableStateOf(false)
    var animationRequestId by mutableIntStateOf(0)
        private set
    var isAnimating by mutableStateOf(false)
        private set
    var isSingleStep by mutableStateOf(false)
        private set

    private var transitionTargetPosition by mutableIntStateOf(-1)

    init {
        replaceSelection(initialSelection)
    }

    val canAnimate: Boolean
        get() = mutableSelectedIndices.size >= 2

    val activeIconIndex: Int?
        get() = mutableSelectedIndices.getOrNull(activeSelectionPosition)

    fun selectedOrder(index: Int): Int? =
        mutableSelectedIndices.indexOf(index).takeIf { it >= 0 }?.plus(1)

    fun toggleIcon(index: Int) {
        require(index >= 0) { "icon index must not be negative" }
        val selectedPosition = mutableSelectedIndices.indexOf(index)
        if (selectedPosition >= 0) {
            val activeIndex = activeIconIndex
            mutableSelectedIndices.removeAt(selectedPosition)
            val retainedPosition = activeIndex?.let(mutableSelectedIndices::indexOf) ?: -1
            settleAt(
                if (retainedPosition >= 0) {
                    retainedPosition
                } else {
                    selectedPosition.coerceAtMost(mutableSelectedIndices.lastIndex)
                },
            )
        } else {
            val activeIndex = activeIconIndex
            mutableSelectedIndices += index
            settleAt(activeIndex?.let(mutableSelectedIndices::indexOf) ?: 0)
        }
    }

    fun replaceSelection(indices: List<Int>) {
        require(indices.all { it >= 0 }) { "icon indices must not be negative" }
        require(indices.distinct().size == indices.size) { "selected icon indices must be distinct" }
        mutableSelectedIndices.clear()
        mutableSelectedIndices.addAll(indices)
        settleAt(0)
    }

    fun selectPair(
        from: Int,
        to: Int,
    ) {
        require(from >= 0 && to >= 0) { "icon indices must not be negative" }
        require(from != to) { "from and to must select different icons" }
        if (from !in mutableSelectedIndices) mutableSelectedIndices += from
        if (to !in mutableSelectedIndices) mutableSelectedIndices += to
        activeSelectionPosition = mutableSelectedIndices.indexOf(from)
        transitionTargetPosition = mutableSelectedIndices.indexOf(to)
        fromIndex = from
        toIndex = to
        resetAnimation()
    }

    fun requestPrevious() {
        if (!canAnimate) return
        isPlaying = false
        startStep(direction = -1, singleStep = true)
    }

    fun requestNext() {
        if (!canAnimate) return
        isPlaying = false
        startStep(direction = 1, singleStep = true)
    }

    fun setPlayback(playing: Boolean) {
        if (!canAnimate) {
            if (isPlaying || isAnimating || isSingleStep || progress != 0.0f) resetAnimation()
            return
        }
        if (isPlaying == playing) return
        isPlaying = playing
        isSingleStep = false
        if (playing) {
            if (isAnimating) {
                animationRequestId += 1
            } else {
                startStep(direction = 1, singleStep = false)
            }
        }
    }

    fun togglePlayback() {
        setPlayback(!isPlaying)
    }

    fun toggleTheme() {
        isDarkTheme = !isDarkTheme
    }

    fun requestAutoNext() {
        if (isPlaying && canAnimate) startStep(direction = 1, singleStep = false)
    }

    fun updateAnimationProgress(
        requestId: Int,
        value: Float,
    ) {
        if (requestId == animationRequestId) progress = value
    }

    fun completeAnimation(requestId: Int): Boolean {
        if (requestId != animationRequestId) return false
        activeSelectionPosition = transitionTargetPosition
        progress = 1.0f
        isAnimating = false
        isSingleStep = false
        return true
    }

    fun scrubTo(value: Float) {
        require(value.isFinite()) { "progress must be finite" }
        if (!canAnimate) {
            if (isPlaying || isAnimating || isSingleStep || progress != 0.0f) resetAnimation()
            return
        }
        isPlaying = false
        isSingleStep = false
        isAnimating = value < 1.0f
        animationRequestId += 1
        progress = value
    }

    fun snapshot(): PlaygroundSnapshot =
        PlaygroundSnapshot(
            selectedIndices = mutableSelectedIndices.toList(),
            activeSelectionPosition = activeSelectionPosition,
            fromIndex = fromIndex,
            toIndex = toIndex,
            progress = progress,
            isPlaying = isPlaying,
            compareLinear = compareLinear,
            isDarkTheme = isDarkTheme,
            rotationPreference = rotationPreference,
            route = selectedTab.id,
            demoTarget = demo(selectedTab).targetIndex,
            demoProgress = demo(selectedTab).progress,
            demoManual = demo(selectedTab).manual,
            demoPlaying = demo(selectedTab).playing,
        )

    private fun settleAt(position: Int) {
        if (mutableSelectedIndices.isEmpty()) {
            activeSelectionPosition = -1
            transitionTargetPosition = -1
            fromIndex = -1
            toIndex = -1
            resetAnimation()
            return
        }

        activeSelectionPosition = position.floorMod(mutableSelectedIndices.size)
        fromIndex = mutableSelectedIndices[activeSelectionPosition]
        if (canAnimate) {
            transitionTargetPosition = (activeSelectionPosition + 1).floorMod(mutableSelectedIndices.size)
            toIndex = mutableSelectedIndices[transitionTargetPosition]
        } else {
            transitionTargetPosition = activeSelectionPosition
            toIndex = fromIndex
        }
        resetAnimation()
    }

    private fun startStep(
        direction: Int,
        singleStep: Boolean,
    ) {
        if (!canAnimate) return
        fromIndex = checkNotNull(activeIconIndex)
        transitionTargetPosition = (activeSelectionPosition + direction).floorMod(mutableSelectedIndices.size)
        toIndex = mutableSelectedIndices[transitionTargetPosition]
        progress = 0.0f
        isAnimating = true
        isSingleStep = singleStep
        animationRequestId += 1
    }

    private fun resetAnimation() {
        isPlaying = false
        isAnimating = false
        isSingleStep = false
        progress = 0.0f
        animationRequestId += 1
    }
}

private fun Int.floorMod(size: Int): Int = ((this % size) + size) % size
