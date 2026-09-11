package li.songe.morph.playground

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import li.songe.morph.compose.MorphContentScale

@Composable
internal fun InteractionScreen(state: FeatureDemoState) {
    val options = demoGeometries.take(3)
    val shape = rememberDemoShape(state, options, MorphContentScale.FillBounds)
    val position by animateFloatAsState(state.targetIndex.toFloat())
    FeaturePage("03 / INTERACTION", "Let the selection take shape.", "Choose a destination. The highlight travels between tabs while its outline changes independently.") {
        DemoStage {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(32.dp)) {
                Box(Modifier.size(106.dp).background(Color(0xFF9CBBC4), shape), contentAlignment = Alignment.Center) {
                    Text(listOf("01", "02", "03")[state.targetIndex], color = Color(0xFF193E4A), fontSize = 26.sp, fontWeight = FontWeight.Bold)
                }
                BoxWithConstraints(Modifier.fillMaxWidth().background(PlaygroundColors.PanelRaised, RoundedCornerShape(20.dp)).padding(6.dp)) {
                    val tabWidth = maxWidth / 3
                    Box(Modifier.offset(x = tabWidth * position).size(tabWidth, 58.dp).background(PlaygroundColors.Amber, shape))
                    Row(Modifier.fillMaxWidth()) {
                        listOf("Explore", "Saved", "Profile").forEachIndexed { index, title ->
                            Box(Modifier.weight(1f).height(58.dp).clickable { state.manual = false; state.select(index, options.size) }, contentAlignment = Alignment.Center) {
                                Text(title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                                    color = if (state.targetIndex == index) Color(0xFF282017) else PlaygroundColors.Ink)
                            }
                        }
                    }
                }
            }
        }
        DemoControls(state, options)
        Text("Scrub the outline independently, or tap tabs rapidly to interrupt and redirect the animation.", color = PlaygroundColors.Muted, fontSize = 13.sp)
    }
}
