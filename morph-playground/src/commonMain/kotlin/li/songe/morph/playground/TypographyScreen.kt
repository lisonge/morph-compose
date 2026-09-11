package li.songe.morph.playground

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.DropdownMenu
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.OutlinedButton
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch

@Composable
internal fun TypographyScreen(state: FeatureDemoState, fonts: TypographyState) {
    FeaturePage("05 / TYPOGRAPHY", "Numbers with a continuous rhythm.", "Morph whole integers or explore individual digits, using the outlines of your selected font.") {
        SystemFontSelector(fonts)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ChoiceChip("Number N → M", fonts.numberMode) { fonts.numberMode = true }
            ChoiceChip("Single digit", !fonts.numberMode) { fonts.numberMode = false }
        }
        if (fonts.numberMode) {
            NumberDemo(fonts.numbers, fonts.digits)
        } else {
            val shape = rememberDemoShape(state, fonts.digits)
            DemoStage { Box(Modifier.size(260.dp).background(PlaygroundColors.Ink, shape)) }
            DemoControls(state, fonts.digits)
        }
        Text(if (fonts.selected == bundledFont) "Space Grotesk · SIL Open Font License 1.1" else fonts.selected.label,
            color = PlaygroundColors.Muted, fontSize = 12.sp)
        Text("Choose a font to change the actual digit outlines. Your digit selection and animation controls stay in place.",
            color = PlaygroundColors.Muted, fontSize = 13.sp)
    }
}

@Composable
private fun SystemFontSelector(fonts: TypographyState) {
    var query by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    LaunchedEffect(fonts) {
        if (fonts.supported && !fonts.needsPermission && !fonts.loadedList) fonts.refresh()
    }
    Column {
        Text("Typeface", color = PlaygroundColors.Muted, fontSize = 12.sp)
        Box {
            OutlinedButton(onClick = { fonts.menuExpanded = !fonts.menuExpanded }, modifier = Modifier.fillMaxWidth()) {
                Text("${fonts.selected.label}  ▾", maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            DropdownMenu(expanded = fonts.menuExpanded, onDismissRequest = { fonts.menuExpanded = false }, modifier = Modifier.width(320.dp)) {
                OutlinedTextField(value = query, onValueChange = { query = it }, label = { Text("Search fonts") },
                    singleLine = true, modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp))
                val matches = remember(fonts.fonts, query) { fonts.fonts.filter { it.label.contains(query, ignoreCase = true) } }
                LazyColumn(Modifier.height(280.dp).width(320.dp)) {
                    items(matches, key = { it.id }) { font ->
                        DropdownMenuItem(onClick = {
                            fonts.menuExpanded = false
                            scope.launch { fonts.select(font) }
                        }) {
                            Text(font.label, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                color = if (font == fonts.selected) PlaygroundColors.Amber else PlaygroundColors.Ink)
                        }
                    }
                    if (matches.isEmpty()) item { Text("No matching fonts", modifier = Modifier.padding(16.dp)) }
                }
            }
        }
        if (fonts.supported && !fonts.loadedList) {
            OutlinedButton(enabled = !fonts.listing, onClick = {
                scope.launch(start = CoroutineStart.UNDISPATCHED) { fonts.refresh() }
            }) { Text(if (fonts.listing) "Loading fonts…" else "Load system fonts") }
        } else if (!fonts.supported) {
            Text("This browser cannot access local fonts. Use Desktop or a browser with Local Font Access on HTTPS.",
                color = PlaygroundColors.Muted, fontSize = 12.sp)
        } else {
            Text("${fonts.fonts.size - 1} local fonts available", color = PlaygroundColors.Muted, fontSize = 12.sp)
        }
        if (fonts.loading) Text("Loading digit outlines…", color = PlaygroundColors.Muted, fontSize = 12.sp)
        fonts.error?.let { Text(it, color = PlaygroundColors.Amber, fontSize = 12.sp) }
    }
}
