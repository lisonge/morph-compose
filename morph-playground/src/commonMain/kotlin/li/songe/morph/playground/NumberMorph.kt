package li.songe.morph.playground

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.Checkbox
import androidx.compose.material.CheckboxDefaults
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Slider
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import li.songe.morph.compose.MorphOptions
import li.songe.morph.compose.MorphPathWriter
import li.songe.morph.compose.buildMorphPlan
import kotlin.math.roundToInt

internal data class IntegerGlyphs(val digits: String, val negative: Boolean) {
    val text: String get() = (if (negative) "-" else "") + digits

    companion object {
        // Keep strings to preserve leading zeros and integers beyond Long's range.
        fun parse(input: String): IntegerGlyphs? {
            val text = input.trim()
            val digits = text.removePrefix("-").removePrefix("+")
            if (digits.isEmpty() || digits.any { it !in '0'..'9' } || text.startsWith("-+")) return null
            return IntegerGlyphs(digits, text.startsWith('-'))
        }
    }
}

internal data class NumberPair(val from: IntegerGlyphs, val to: IntegerGlyphs) {
    val digitCount: Int get() = maxOf(from.digits.length, to.digits.length)
    val hasSign: Boolean get() = from.negative || to.negative
    fun slots(): List<NumberSlot> = List(digitCount) { place ->
        NumberSlot(place, from.digits.getOrNull(from.digits.lastIndex - place)?.digitToInt(),
            to.digits.getOrNull(to.digits.lastIndex - place)?.digitToInt())
    }
}

internal data class NumberSlot(val place: Int, val from: Int?, val to: Int?) {
    fun visibility(progress: Float): Float = when {
        from == null -> progress
        to == null -> 1f - progress
        else -> 1f
    }
}

internal class NumberDemoState {
    var fromInput by mutableStateOf("99")
    var toInput by mutableStateOf("100")
    var pair by mutableStateOf(NumberPair(IntegerGlyphs("99", false), IntegerGlyphs("100", false)))
        private set
    var range by mutableStateOf(pair)
        private set
    var stepEnabled by mutableStateOf(false)
        private set
    var progress by mutableFloatStateOf(0f)
        private set
    var playing by mutableStateOf(false)
        private set
    var replay by mutableIntStateOf(0)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    fun play(): Boolean {
        val from = IntegerGlyphs.parse(fromInput)
        val to = IntegerGlyphs.parse(toInput)
        if (from == null || to == null) {
            error = "Enter two integers (digits 0–9, with an optional minus sign)."
            return false
        }
        range = NumberPair(from, to)
        pair = firstPair()
        error = null
        progress = 0f
        replay++
        playing = true
        return true
    }

    fun reverse() {
        val previous = fromInput
        fromInput = toInput
        toInput = previous
        play()
    }

    fun seek(value: Float) {
        require(value.isFinite() && value in 0f..1f)
        playing = false
        progress = value
    }

    fun frame(value: Float) { progress = value }

    fun changeStep(enabled: Boolean) {
        if (stepEnabled == enabled) return
        stepEnabled = enabled
        playing = false
        replay++
        pair = firstPair()
        progress = 0f
    }

    private fun firstPair(): NumberPair = if (stepEnabled) NumberPair(range.from, range.from.stepToward(range.to)) else range

    fun completeTransition() {
        if (!playing) return
        progress = 1f
        if (stepEnabled && pair.to != range.to) {
            pair = NumberPair(pair.to, pair.to.stepToward(range.to))
            progress = 0f
        } else {
            playing = false
        }
    }
}

@Composable
internal fun NumberDemo(state: NumberDemoState, digits: List<DemoGeometry>) {
    LaunchedEffect(state.replay, state.playing) {
        while (state.playing) {
            Animatable(state.progress).animateTo(1f, tween(if (state.stepEnabled) 350 else 1000)) { state.frame(value) }
            // Give every endpoint a visible frame, also yielding when system animations are disabled.
            if (state.stepEnabled && state.pair.to != state.range.to) delay(70)
            state.completeTransition()
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(state.fromInput, { state.fromInput = it }, label = { Text("From · N") },
                singleLine = true, modifier = Modifier.weight(1f))
            OutlinedTextField(state.toInput, { state.toInput = it }, label = { Text("To · M") },
                singleLine = true, modifier = Modifier.weight(1f))
        }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ChoiceChip("Play N → M", state.playing) { state.play() }
            ChoiceChip("Swap & play", false, state::reverse)
            ChoiceChip("99 → 100", false) { state.fromInput = "99"; state.toInput = "100"; state.play() }
            ChoiceChip("1024 → 7", false) { state.fromInput = "1024"; state.toInput = "7"; state.play() }
        }
        Row(Modifier.toggleable(state.stepEnabled, onValueChange = state::changeStep).heightIn(min = 40.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Checkbox(checked = state.stepEnabled, onCheckedChange = null,
                colors = CheckboxDefaults.colors(checkedColor = PlaygroundColors.Amber, uncheckedColor = PlaygroundColors.Muted))
            Text("Step · count by ±1", color = PlaygroundColors.Ink, fontSize = 13.sp)
        }
        state.error?.let { Text(it, color = PlaygroundColors.Amber, fontSize = 12.sp) }
    }
    DemoStage { NumberMorph(state.pair, digits, state.progress, state.range) }
    Column {
        if (state.stepEnabled) Text("Step range: ${state.range.from.text} → ${state.range.to.text}",
            color = PlaygroundColors.Muted, fontSize = 12.sp)
        Text("${state.pair.from.text} → ${state.pair.to.text} · ${(state.progress * 100).roundToInt()}%",
            color = PlaygroundColors.Muted, fontSize = 13.sp)
        Slider(state.progress, state::seek)
        Text("Digits align by place value. Added and removed places fade and scale. Long numbers scroll horizontally.",
            color = PlaygroundColors.Muted, fontSize = 12.sp)
    }
}

private class NumberGlyphDrawing(val writer: MorphPathWriter, val path: Path = Path())

@Composable
private fun NumberMorph(pair: NumberPair, digits: List<DemoGeometry>, progress: Float, range: NumberPair) {
    val slots = remember(pair) { pair.slots() }
    val cache = remember(digits) { mutableMapOf<Pair<Int, Int>, NumberGlyphDrawing>() }
    val drawings = remember(pair, digits) {
        slots.map { slot ->
            val from = slot.from ?: requireNotNull(slot.to)
            val to = slot.to ?: from
            cache.getOrPut(from to to) {
                NumberGlyphDrawing(buildMorphPlan(digits[from].geometry, digits[to].geometry,
                    MorphOptions(sampleCount = 192)).createPathWriter())
            }
        }
    }
    val ink = PlaygroundColors.Ink
    BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        val digitCount = maxOf(pair.digitCount, range.digitCount)
        val hasSign = range.hasSign
        val columns = digitCount + if (hasSign) 1 else 0
        val widthFactor = columns * 0.82f + 0.18f
        val cell = (maxWidth / widthFactor).coerceIn(36.dp, 240.dp)
        Box(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState(), reverseScrolling = true), contentAlignment = Alignment.Center) {
            Canvas(Modifier.width(cell * widthFactor).height(cell)) {
                val side = size.height
                val advance = side * 0.82f
                val signOffset = if (hasSign) 1 else 0
                slots.forEachIndexed { index, slot ->
                    val visible = slot.visibility(progress)
                    if (visible > 0f) {
                        val drawing = drawings[index]
                        val shapeProgress = if (slot.from == null || slot.to == null || slot.from == slot.to) 1f else progress
                        drawing.writer.writePath(drawing.path, shapeProgress, Size(side, side))
                        translate((signOffset + digitCount - 1 - slot.place) * advance, 0f) {
                            scale(visible, pivot = Offset(side / 2, side / 2)) {
                                drawPath(drawing.path, ink, alpha = visible)
                            }
                        }
                    }
                }
                if (pair.hasSign) {
                    val alpha = (if (pair.from.negative) 1f - progress else 0f) + (if (pair.to.negative) progress else 0f)
                    val length = pair.from.digits.length * (1f - progress) + pair.to.digits.length * progress
                    val x = (digitCount - length) * advance + side * 0.3f
                    drawRect(ink, Offset(x, side * 0.47f), Size(side * 0.4f, side * 0.065f), alpha = alpha)
                }
            }
        }
    }
}
