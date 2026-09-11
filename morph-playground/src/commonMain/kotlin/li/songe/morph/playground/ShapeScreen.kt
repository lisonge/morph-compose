package li.songe.morph.playground

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import li.songe.morph.compose.MorphContentScale

@Composable
internal fun ShapeScreen(state: FeatureDemoState) {
    val shape = rememberDemoShape(state, demoGeometries, MorphContentScale.FillBounds)
    FeaturePage("01 / SHAPES", "Give components a new outline.", "The same content, a changing silhouette. Tap the card or choose a shape below.") {
        DemoStage {
            Box(
                Modifier.widthIn(max = 330.dp).fillMaxWidth().height(220.dp)
                    .background(Brush.linearGradient(listOf(Color(0xFFF1B85B), Color(0xFFE78864))), shape)
                    .border(2.dp, Color(0xFFC4753F), shape)
                    .clickable { state.next(demoGeometries.size) },
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Make room", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Color(0xFF392919))
                    Text("for a different shape", color = Color(0xFF584029), fontSize = 13.sp)
                }
            }
        }
        DemoControls(state, demoGeometries)
        Text("The outline changes while the content keeps its position and size.", color = PlaygroundColors.Muted, fontSize = 13.sp)
    }
}
