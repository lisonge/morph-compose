package li.songe.morph.playground

import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Slider
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import li.songe.morph.compose.MorphContentScale
import li.songe.morph.compose.MorphGeometry
import li.songe.morph.compose.MorphOptions
import li.songe.morph.compose.rememberMorphGeometryState
import li.songe.morph.compose.rememberMorphPlan
import li.songe.morph.compose.rememberMorphShape
import kotlin.math.roundToInt

internal class FeatureDemoState {
    var fromIndex by mutableIntStateOf(0)
        private set
    var targetIndex by mutableIntStateOf(1)
        private set
    var progress by mutableFloatStateOf(0.5f)
        private set
    var manual by mutableStateOf(false)
    var playing by mutableStateOf(false)

    fun select(index: Int, count: Int) {
        require(index in 0 until count)
        if (index == targetIndex) return
        fromIndex = targetIndex
        targetIndex = index
    }

    fun seek(value: Float) {
        require(value.isFinite() && value in 0f..1f)
        playing = false
        manual = true
        progress = value
    }

    fun next(count: Int) { select((targetIndex + 1) % count, count) }
}

internal data class DemoGeometry(val name: String, val geometry: MorphGeometry)

@Composable
internal fun rememberDemoShape(
    state: FeatureDemoState,
    geometries: List<DemoGeometry>,
    scale: MorphContentScale = MorphContentScale.Fit,
): Shape {
    // A new tab composition starts at its selected target; later changes still animate from the visible frame.
    val animation = rememberMorphGeometryState(geometries[state.targetIndex].geometry, MorphOptions(sampleCount = 192))
    LaunchedEffect(geometries, state.targetIndex, state.fromIndex, state.manual, state.progress) {
        if (state.manual) animation.seekTo(geometries[state.fromIndex].geometry, geometries[state.targetIndex].geometry, state.progress)
        else animation.animateTo(
            geometries[state.targetIndex].geometry,
            animationSpec = spring(dampingRatio = 0.78f, stiffness = 140f),
        )
    }
    LaunchedEffect(state.playing, state.manual) {
        if (state.playing && !state.manual) {
            while (true) {
                delay(1500)
                state.next(geometries.size)
            }
        }
    }
    return if (state.manual) {
        val plan = rememberMorphPlan(geometries[state.fromIndex].geometry, geometries[state.targetIndex].geometry, MorphOptions(sampleCount = 192))
        rememberMorphShape(plan, state.progress, scale)
    } else rememberMorphShape(animation, scale)
}

@Composable
internal fun FeaturePage(
    number: String,
    title: String,
    description: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier.widthIn(max = 1040.dp).fillMaxWidth().verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Text("MORPH COMPOSE / $number", color = PlaygroundColors.Muted, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(title, fontSize = 30.sp, fontWeight = FontWeight.SemiBold, color = PlaygroundColors.Ink)
                Text(description, fontSize = 14.sp, lineHeight = 22.sp, color = PlaygroundColors.Muted)
            }
            content()
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
internal fun DemoStage(content: @Composable () -> Unit) {
    Box(
        Modifier.fillMaxWidth().height(320.dp)
            .background(PlaygroundColors.Panel, RoundedCornerShape(24.dp))
            .border(1.dp, PlaygroundColors.Line, RoundedCornerShape(24.dp)).padding(24.dp),
        contentAlignment = Alignment.Center,
    ) { content() }
}

@Composable
internal fun DemoControls(state: FeatureDemoState, geometries: List<DemoGeometry>) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            geometries.forEachIndexed { index, geometry ->
                ChoiceChip(geometry.name, state.targetIndex == index) { state.select(index, geometries.size) }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ChoiceChip(if (state.playing) "Pause" else "Cycle", state.playing) {
                state.manual = false
                state.playing = !state.playing
            }
            ChoiceChip("Scrub pair", state.manual) { state.playing = false; state.manual = !state.manual }
        }
        if (state.manual) {
            Text("${geometries[state.fromIndex].name} → ${geometries[state.targetIndex].name} · ${(state.progress * 100).roundToInt()}%",
                color = PlaygroundColors.Muted, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
            Slider(value = state.progress, onValueChange = state::seek)
        } else {
            Text("Choose another shape at any point during the animation.", color = PlaygroundColors.Muted, fontSize = 12.sp)
        }
    }
}
