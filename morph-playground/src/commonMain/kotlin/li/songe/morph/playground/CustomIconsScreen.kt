package li.songe.morph.playground

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import li.songe.morph.playground.custom.CanvasBackCloseIcon
import li.songe.morph.playground.custom.CanvasSearchCloseIcon

@Composable
internal fun CustomIconsScreen(state: PlaygroundState) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Handwritten icons", color = PlaygroundColors.Ink, fontSize = 24.sp)
        Text("Original Canvas animations from GKD · click each card to toggle",
            color = PlaygroundColors.Muted)
        CustomIconCard("Back / Close", { state.customBackOrClose = !state.customBackOrClose }) {
            CanvasBackCloseIcon(state.customBackOrClose, Modifier.size(96.dp), tint = PlaygroundColors.Ink)
        }
        CustomIconCard("Search / Close", { state.customSearchOpen = !state.customSearchOpen }) {
            CanvasSearchCloseIcon(state.customSearchOpen, Modifier.size(96.dp), tint = PlaygroundColors.Ink)
        }
    }
}

@Composable
private fun CustomIconCard(title: String, onClick: () -> Unit, icon: @Composable () -> Unit) {
    Column(
        Modifier.widthIn(max = 720.dp).fillMaxWidth().clip(RoundedCornerShape(20.dp))
            .background(PlaygroundColors.Panel).clickable(onClick = onClick).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Text(title, color = PlaygroundColors.Ink, fontSize = 18.sp)
        Box(Modifier.size(144.dp), contentAlignment = Alignment.Center) { icon() }
        Text("Click to animate", color = PlaygroundColors.Muted, fontSize = 12.sp)
    }
}
