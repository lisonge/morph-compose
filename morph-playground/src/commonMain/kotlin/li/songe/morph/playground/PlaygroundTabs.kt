package li.songe.morph.playground

import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.IconButton
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import li.songe.morph.compose.AnimatedMorphIcon

internal enum class PlaygroundTab(val id: String, val title: String) {
    Icons("icons", "Icons"),
    CustomIcons("custom-icons", "Custom icons"),
    Shapes("shapes", "Shapes"),
    Clipping("clipping", "Image clips"),
    Interaction("interaction", "Interaction"),
    Loading("loading", "Loading"),
    Typography("typography", "Typography"),
}

@Composable
internal fun PlaygroundTabs(state: PlaygroundState) {
    val selectedTab = state.selectedTab
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().background(PlaygroundColors.Panel)
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                onClick = state::toggleTheme,
                modifier = Modifier.size(42.dp).clip(CircleShape)
                    .border(1.dp, PlaygroundColors.Line, CircleShape),
            ) {
                AnimatedMorphIcon(
                    from = Icons.Filled.DarkMode,
                    to = Icons.Filled.LightMode,
                    targetState = state.isDarkTheme,
                    contentDescription = if (state.isDarkTheme) "Use light theme" else "Use dark theme",
                    modifier = Modifier.size(21.dp),
                    tint = PlaygroundColors.Ink,
                    animationSpec = spring(dampingRatio = 0.78f, stiffness = 180f),
                )
            }
            Row(
                Modifier.weight(1f).horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                for (tab in PlaygroundTab.entries) {
                    ChoiceChip(tab.title, selectedTab == tab) { state.selectedTab = tab }
                }
            }
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (selectedTab) {
                PlaygroundTab.Icons -> {
                    PlaybackEffect(state)
                    MorphAppContent(state)
                }
                PlaygroundTab.Shapes -> ShapeScreen(state.demo(selectedTab))
                PlaygroundTab.CustomIcons -> CustomIconsScreen(state)
                PlaygroundTab.Clipping -> ClippingScreen(state.demo(selectedTab))
                PlaygroundTab.Interaction -> InteractionScreen(state.demo(selectedTab))
                PlaygroundTab.Loading -> LoadingScreen(state.demo(selectedTab))
                PlaygroundTab.Typography -> TypographyScreen(state.demo(selectedTab), state.typography)
            }
        }
    }
}

@Composable
internal fun ChoiceChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        label,
        modifier = Modifier.clip(RoundedCornerShape(12.dp))
            .background(if (selected) PlaygroundColors.Amber else PlaygroundColors.PanelRaised)
            .clickable(onClick = onClick).padding(horizontal = 13.dp, vertical = 10.dp),
        color = if (selected) androidx.compose.ui.graphics.Color(0xFF282017) else PlaygroundColors.Ink,
        fontSize = 12.sp,
        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
    )
}
