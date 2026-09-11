package li.songe.morph.playground

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun LoadingScreen(state: FeatureDemoState) {
    val shapes = demoGeometries.take(5)
    val shape = rememberDemoShape(state, shapes)
    FeaturePage("04 / LOADING", "A little motion while you wait.", "Cycle through soft shapes for a loading state, or use the same outline as a decorative accent.") {
        DemoStage {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(22.dp)) {
                Box(Modifier.size(175.dp).background(Brush.linearGradient(listOf(Color(0xFFBB94BF), Color(0xFFEDBBA3))), shape), contentAlignment = Alignment.Center) {
                    Box(Modifier.size(64.dp).background(PlaygroundColors.Panel, shape))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(22.dp).background(PlaygroundColors.Amber, shape))
                    Text(if (state.playing) "Finding something good…" else "Ready when you are", color = PlaygroundColors.Ink, fontSize = 14.sp)
                }
            }
        }
        DemoControls(state, shapes)
        Text("Select Cycle to start. Leaving this page stops its animation loop.", color = PlaygroundColors.Muted, fontSize = 13.sp)
    }
}
