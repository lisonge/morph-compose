package li.songe.morph.playground

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Checkbox
import androidx.compose.material.CheckboxDefaults
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Slider
import androidx.compose.material.SliderDefaults
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.darkColors
import androidx.compose.material.lightColors
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import li.songe.morph.compose.MorphIcon
import li.songe.morph.compose.MorphCompatibility
import li.songe.morph.compose.MorphOptions
import li.songe.morph.compose.MorphRotationPreference
import li.songe.morph.compose.MorphInterpolation
import li.songe.morph.compose.inspectMorphCompatibility
import kotlin.math.roundToInt

@Composable
internal fun MorphApp(state: PlaygroundState) {
    PlaygroundTheme(state) {
        Surface(modifier = Modifier.fillMaxSize(), color = PlaygroundColors.Background) {
            PlaygroundTabs(state)
        }
    }
}

@Composable
internal fun MorphAppPreview(state: PlaygroundState) {
    PlaygroundTheme(state) {
        Surface(modifier = Modifier.fillMaxSize(), color = PlaygroundColors.Background) {
            if (state.canAnimate) {
                val from = iconEntries[state.fromIndex]
                val to = iconEntries[state.toIndex]
                AnimationStage(
                    from = from,
                    to = to,
                    progress = state.progress,
                    compareLinear = state.compareLinear,
                    options = state.morphOptions,
                    pairDescription = "${from.name} to ${to.name}",
                    onClick = {},
                )
            } else {
                AnimationUnavailableStage(
                    selectedIcon = state.activeIconIndex?.let(iconEntries::get),
                )
            }
        }
    }
}

@Composable
private fun PlaygroundTheme(
    state: PlaygroundState,
    content: @Composable () -> Unit,
) {
    val palette = if (state.isDarkTheme) DarkPalette else LightPalette
    CompositionLocalProvider(LocalPlaygroundPalette provides palette) {
        MaterialTheme(
            colors =
                if (state.isDarkTheme) {
                    darkColors(
                        background = palette.background,
                        surface = palette.panel,
                        primary = palette.amber,
                        onBackground = palette.ink,
                        onSurface = palette.ink,
                    )
                } else {
                    lightColors(
                        background = palette.background,
                        surface = palette.panel,
                        primary = palette.amber,
                        onBackground = palette.ink,
                        onSurface = palette.ink,
                    )
                },
        ) {
            content()
        }
    }
}

@Composable
internal fun PlaybackEffect(state: PlaygroundState) {
    val requestId = state.animationRequestId
    val shouldAnimate = state.isAnimating && (state.isPlaying || state.isSingleStep)
    LaunchedEffect(requestId, state.isPlaying, state.isSingleStep) {
        if (!shouldAnimate) return@LaunchedEffect
        animate(
            initialValue = state.progress,
            targetValue = 1.0f,
            animationSpec = spring(dampingRatio = 0.72f, stiffness = 220.0f),
        ) { value, _ ->
            state.updateAnimationProgress(requestId, value)
        }
        if (state.completeAnimation(requestId) && state.isPlaying) {
            delay(360)
            state.requestAutoNext()
        }
    }
}

@Composable
internal fun MorphAppContent(state: PlaygroundState) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val wideLayout = maxWidth >= 980.dp
        val horizontalPadding = if (maxWidth < 480.dp) 12.dp else 20.dp
        val iconGridHeight = if (maxWidth < 480.dp) 360.dp else 310.dp
        Column(
            modifier =
                Modifier
                    .align(Alignment.TopCenter)
                    .widthIn(max = 1120.dp)
                    .fillMaxWidth()
                    .fillMaxHeight()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = horizontalPadding, vertical = 18.dp),
        ) {
            Header()
            Spacer(Modifier.height(14.dp))
            if (wideLayout) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Column(modifier = Modifier.weight(1.22f)) {
                        MorphStage(state)
                        Spacer(Modifier.height(10.dp))
                        AdaptiveControlPanel(state)
                    }
                    Column(modifier = Modifier.weight(0.78f)) {
                        SelectedIconList(state)
                        Spacer(Modifier.height(12.dp))
                        IconLibrary(state, height = 570.dp)
                    }
                }
            } else {
                MorphStage(state)
                Spacer(Modifier.height(10.dp))
                AdaptiveControlPanel(state)
                Spacer(Modifier.height(14.dp))
                SelectedIconList(state)
                Spacer(Modifier.height(14.dp))
                IconLibrary(state, height = iconGridHeight)
            }
            Spacer(Modifier.height(12.dp))
            Text(
                text = "Select any icons · morphing activates with two or more selections",
                modifier = Modifier.fillMaxWidth(),
                color = PlaygroundColors.Muted,
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun MorphStage(state: PlaygroundState) {
    if (state.canAnimate) {
        val from = iconEntries[state.fromIndex]
        val to = iconEntries[state.toIndex]
        AnimationStage(
            from = from,
            to = to,
            progress = state.progress,
            compareLinear = state.compareLinear,
            options = state.morphOptions,
            pairDescription = "${from.name} to ${to.name}",
            onClick = state::requestNext,
        )
    } else {
        AnimationUnavailableStage(
            selectedIcon = state.activeIconIndex?.let(iconEntries::get),
        )
    }
}

@Composable
private fun AnimationUnavailableStage(selectedIcon: IconEntry?) {
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(284.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(
                    Brush.radialGradient(
                        colors = listOf(PlaygroundColors.PanelRaised, PlaygroundColors.Panel),
                    ),
                ).border(1.dp, PlaygroundColors.Line, RoundedCornerShape(22.dp))
                .padding(18.dp),
        contentAlignment = Alignment.Center,
    ) {
        StageGrid()
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (selectedIcon != null) {
                Icon(
                    imageVector = selectedIcon.imageVector,
                    contentDescription = selectedIcon.name,
                    modifier = Modifier.size(104.dp),
                    tint = PlaygroundColors.Ink,
                )
                Spacer(Modifier.height(14.dp))
            }
            Text(
                text = if (selectedIcon == null) "No icons selected" else selectedIcon.name,
                color = PlaygroundColors.Ink,
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Select at least two icons to enable morphing",
                color = PlaygroundColors.Muted,
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
            )
        }
    }
}

@Composable
private fun Header() {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val compact = maxWidth < 420.dp
        Column {
            Text(
                text = "MORPH COMPOSE · PLAYGROUND",
                color = PlaygroundColors.Muted,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.SemiBold,
                fontSize = 11.sp,
                letterSpacing = 1.8.sp,
            )
            Spacer(Modifier.height(7.dp))
            Text(
                text = "Universal icon morphing",
                color = PlaygroundColors.Ink,
                fontWeight = FontWeight.SemiBold,
                fontSize = if (compact) 20.sp else 24.sp,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Compose Multiplatform · Material Icons · polar interpolation",
                color = PlaygroundColors.Muted,
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                maxLines = if (compact) 2 else 1,
            )
        }
    }
}

@Composable
private fun AnimationStage(
    from: IconEntry,
    to: IconEntry,
    progress: Float,
    compareLinear: Boolean,
    options: MorphOptions,
    pairDescription: String,
    onClick: () -> Unit,
) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val stackComparisons = compareLinear && maxWidth < 460.dp
        val containerModifier =
            Modifier
                .fillMaxWidth()
                .height(if (stackComparisons) 430.dp else 284.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(
                    Brush.radialGradient(
                        colors = listOf(PlaygroundColors.PanelRaised, PlaygroundColors.Panel),
                    ),
                ).border(1.dp, PlaygroundColors.Line, RoundedCornerShape(22.dp))
                .clickable(onClick = onClick)
                .padding(if (maxWidth < 480.dp) 12.dp else 18.dp)
        if (stackComparisons) {
            Column(
                modifier = containerModifier,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                StagePreview(
                    label = "POLAR",
                    from = from,
                    to = to,
                    progress = progress,
                    options = options,
                    pairDescription = pairDescription,
                    modifier = Modifier.weight(1.0f),
                )
                StagePreview(
                    label = "LINEAR",
                    from = from,
                    to = to,
                    progress = progress,
                    options = options,
                    pairDescription = "$pairDescription linear comparison",
                    interpolation = MorphInterpolation.Linear,
                    modifier = Modifier.weight(1.0f),
                )
            }
        } else {
            Row(
                modifier = containerModifier,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                StagePreview(
                    label = "POLAR",
                    from = from,
                    to = to,
                    progress = progress,
                    options = options,
                    pairDescription = pairDescription,
                    modifier = Modifier.weight(1.0f),
                )
                if (compareLinear) {
                    StagePreview(
                        label = "LINEAR",
                        from = from,
                        to = to,
                        progress = progress,
                        options = options,
                        pairDescription = "$pairDescription linear comparison",
                        interpolation = MorphInterpolation.Linear,
                        modifier = Modifier.weight(1.0f),
                    )
                }
            }
        }
    }
}

@Composable
private fun StagePreview(
    label: String,
    from: IconEntry,
    to: IconEntry,
    progress: Float,
    pairDescription: String,
    options: MorphOptions,
    modifier: Modifier = Modifier,
    interpolation: MorphInterpolation = MorphInterpolation.Polar,
) {
    BoxWithConstraints(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val iconSize = minOf(176.dp, maxWidth - 20.dp, maxHeight - 34.dp).coerceAtLeast(48.dp)
        StageGrid()
        MorphIcon(
            from = from.imageVector,
            to = to.imageVector,
            progress = progress,
            options = options,
            modifier = Modifier.size(iconSize),
            tint = PlaygroundColors.Ink,
            interpolation = interpolation,
            contentDescription = pairDescription,
        )
        Text(
            text = label,
            modifier = Modifier.align(Alignment.BottomCenter),
            color = PlaygroundColors.Muted,
            fontFamily = FontFamily.Monospace,
            fontSize = 10.sp,
            letterSpacing = 1.4.sp,
        )
    }
}

@Composable
private fun StageGrid() {
    val lineColor = PlaygroundColors.Line
    Canvas(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        for (step in 1..5) {
            val alpha = if (step == 3) 1.0f else 0.62f
            val x = size.width * step / 6.0f
            val y = size.height * step / 6.0f
            drawLine(lineColor.copy(alpha = alpha), start = androidx.compose.ui.geometry.Offset(x, 0f), end = androidx.compose.ui.geometry.Offset(x, size.height))
            drawLine(lineColor.copy(alpha = alpha), start = androidx.compose.ui.geometry.Offset(0f, y), end = androidx.compose.ui.geometry.Offset(size.width, y))
        }
    }
}

@Composable
private fun AdaptiveControlPanel(state: PlaygroundState) {
    Panel {
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val horizontal = maxWidth >= 600.dp
            val compactTransport = maxWidth < 360.dp
            Column {
                if (horizontal) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(24.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        PairSummary(state, modifier = Modifier.weight(1.0f))
                        TransportControls(
                            state = state,
                            compact = false,
                            modifier = Modifier.widthIn(min = 280.dp, max = 360.dp),
                        )
                    }
                } else {
                    PairSummary(state)
                    Spacer(Modifier.height(12.dp))
                    TransportControls(
                        state = state,
                        compact = compactTransport,
                    )
                }
                Spacer(Modifier.height(10.dp))
                ScrubberContent(state)
                Spacer(Modifier.height(4.dp))
                RotationPreferenceControl(state)
                Spacer(Modifier.height(8.dp))
                MorphDoctorSummary(state)
            }
        }
    }
}

@Composable
private fun RotationPreferenceControl(state: PlaygroundState) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        SectionLabel("ROTATION PREFERENCE")
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            for (preference in MorphRotationPreference.entries) {
                val label = when (preference) {
                    MorphRotationPreference.Auto -> "Auto"
                    MorphRotationPreference.PreferClockwise -> "Clockwise"
                    MorphRotationPreference.PreferCounterClockwise -> "Counterclockwise"
                }
                ChoiceChip(label, state.rotationPreference == preference) {
                    state.changeRotationPreference(preference)
                }
            }
        }
    }
}

@Composable
private fun MorphDoctorSummary(state: PlaygroundState) {
    val report =
        if (state.canAnimate) {
            val from = iconEntries[state.fromIndex].imageVector
            val to = iconEntries[state.toIndex].imageVector
            val options = state.morphOptions
            remember(from, to, options) { inspectMorphCompatibility(from, to, options) }
        } else {
            null
        }
    val summary =
        when (report?.compatibility) {
            MorphCompatibility.FullPolar -> "FULL POLAR · ${report.contours.size} CONTOURS"
            MorphCompatibility.Hybrid -> {
                val polarCount =
                    report.contours.count { it.interpolation == MorphInterpolation.Polar }
                val linearCount = report.contours.size - polarCount
                "HYBRID · $polarCount POLAR · $linearCount LINEAR FALLBACK"
            }
            MorphCompatibility.Unsupported -> "UNSUPPORTED · ${report.issues.firstOrNull().orEmpty()}"
            null -> "SELECT AT LEAST TWO ICONS TO INSPECT"
        }
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(PlaygroundColors.PanelRaised)
                .border(1.dp, PlaygroundColors.Line, RoundedCornerShape(8.dp))
                .padding(horizontal = 10.dp, vertical = 7.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SectionLabel("MORPH DOCTOR")
        Text(
            text = summary,
            modifier = Modifier.weight(1.0f),
            color =
                if (report?.compatibility == MorphCompatibility.Hybrid) {
                    PlaygroundColors.Amber
                } else {
                    PlaygroundColors.Muted
                },
            fontFamily = FontFamily.Monospace,
            fontSize = 9.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.End,
        )
    }
}

@Composable
private fun PairSummary(
    state: PlaygroundState,
    modifier: Modifier = Modifier,
) {
    val title =
        if (state.canAnimate) {
            "${iconEntries[state.fromIndex].name}  →  ${iconEntries[state.toIndex].name}"
        } else {
            state.activeIconIndex?.let { "${iconEntries[it].name} selected" } ?: "No icons selected"
        }
    val detail =
        if (state.canAnimate) {
            "${state.selectedIndices.size} selected / $iconCount available"
        } else {
            "${state.selectedIndices.size} selected / $iconCount available · animation disabled"
        }
    Column(modifier = modifier) {
        SectionLabel(if (state.canAnimate) "PAIR · SELECTED SEQUENCE" else "SELECTION")
        Spacer(Modifier.height(6.dp))
        Text(
            text = title,
            color = PlaygroundColors.Ink,
            fontFamily = FontFamily.Monospace,
            fontSize = 14.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = detail,
            color = PlaygroundColors.Muted,
            fontFamily = FontFamily.Monospace,
            fontSize = 10.sp,
            maxLines = 2,
        )
    }
}

@Composable
private fun TransportControls(
    state: PlaygroundState,
    compact: Boolean,
    modifier: Modifier = Modifier,
) {
    val enabled = state.canAnimate
    Column(modifier = modifier) {
        SectionLabel("ANIMATION CONTROLLER")
        Spacer(Modifier.height(7.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TransportButton(
                label = "Last",
                icon = Icons.Filled.SkipPrevious,
                enabled = enabled,
                compact = compact,
                onClick = state::requestPrevious,
            )
            Spacer(Modifier.width(if (compact) 9.dp else 12.dp))
            Box(
                modifier =
                    Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(if (enabled) PlaygroundColors.Amber else PlaygroundColors.PanelRaised)
                        .border(1.dp, if (enabled) PlaygroundColors.Amber else PlaygroundColors.Line, CircleShape)
                        .clickable(enabled = enabled, onClick = state::togglePlayback),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (state.isPlaying) "Pause" else "Play",
                    modifier = Modifier.size(27.dp),
                    tint = if (enabled) PlaygroundColors.Background else PlaygroundColors.Muted,
                )
            }
            Spacer(Modifier.width(if (compact) 9.dp else 12.dp))
            TransportButton(
                label = "Next",
                icon = Icons.Filled.SkipNext,
                enabled = enabled,
                compact = compact,
                onClick = state::requestNext,
            )
        }
    }
}

@Composable
private fun TransportButton(
    label: String,
    icon: ImageVector,
    enabled: Boolean,
    compact: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .height(40.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(if (enabled) PlaygroundColors.PanelRaised else PlaygroundColors.Panel)
                .border(1.dp, PlaygroundColors.Line, RoundedCornerShape(10.dp))
                .clickable(enabled = enabled, onClick = onClick)
                .padding(horizontal = if (compact) 10.dp else 13.dp),
        horizontalArrangement = Arrangement.spacedBy(7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val contentColor = if (enabled) PlaygroundColors.Ink else PlaygroundColors.Muted
        Icon(icon, contentDescription = label, modifier = Modifier.size(19.dp), tint = contentColor)
        if (!compact) Text(label, color = contentColor, fontSize = 12.sp)
    }
}

@Composable
private fun ScrubberContent(state: PlaygroundState) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SectionLabel("SCRUB")
        Text(
            text = "t = ${formatProgress(state.progress)}",
            color = PlaygroundColors.Muted,
            fontFamily = FontFamily.Monospace,
            fontSize = 10.sp,
        )
        Spacer(Modifier.weight(1.0f))
        Checkbox(
            checked = state.compareLinear,
            onCheckedChange = { state.compareLinear = it },
            enabled = state.canAnimate,
            colors =
                CheckboxDefaults.colors(
                    checkedColor = PlaygroundColors.Amber,
                    checkmarkColor = PlaygroundColors.Background,
                    uncheckedColor = PlaygroundColors.Muted,
                ),
        )
        Text("LINEAR", color = PlaygroundColors.Muted, fontSize = 10.sp)
    }
    Slider(
        value = state.progress.coerceIn(0.0f, 1.0f),
        onValueChange = state::scrubTo,
        enabled = state.canAnimate,
        modifier = Modifier.fillMaxWidth(),
        colors =
            SliderDefaults.colors(
                thumbColor = PlaygroundColors.Amber,
                activeTrackColor = PlaygroundColors.Amber,
                inactiveTrackColor = PlaygroundColors.Line,
            ),
    )
}

@Composable
private fun SelectedIconList(state: PlaygroundState) {
    SectionHeader(
        title = "SELECTED",
        detail = "${state.selectedIndices.size} icons · tap to remove",
    )
    Spacer(Modifier.height(8.dp))
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        state.selectedIndices.forEachIndexed { position, iconIndex ->
            val icon = iconEntries[iconIndex]
            SelectedIcon(
                icon = icon,
                order = position + 1,
                isCurrent = position == state.activeSelectionPosition,
                isTarget = iconIndex == state.toIndex && state.isAnimating,
                onClick = { state.toggleIcon(iconIndex) },
            )
        }
    }
}

@Composable
private fun SelectedIcon(
    icon: IconEntry,
    order: Int,
    isCurrent: Boolean,
    isTarget: Boolean,
    onClick: () -> Unit,
) {
    val borderColor =
        when {
            isCurrent -> PlaygroundColors.Amber
            isTarget -> PlaygroundColors.Ink
            else -> PlaygroundColors.Line
        }
    Column(
        modifier =
            Modifier
                .width(70.dp)
                .clip(RoundedCornerShape(11.dp))
                .background(PlaygroundColors.Panel)
                .border(if (isCurrent || isTarget) 2.dp else 1.dp, borderColor, RoundedCornerShape(11.dp))
                .clickable(onClick = onClick)
                .padding(horizontal = 7.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(contentAlignment = Alignment.TopEnd) {
            Icon(
                imageVector = icon.imageVector,
                contentDescription = icon.name,
                modifier = Modifier.size(28.dp),
                tint = if (isCurrent) PlaygroundColors.Amber else PlaygroundColors.Ink,
            )
            Text(
                text = order.toString(),
                modifier = Modifier.align(Alignment.TopEnd),
                color = PlaygroundColors.Amber,
                fontFamily = FontFamily.Monospace,
                fontSize = 9.sp,
            )
        }
        Spacer(Modifier.height(5.dp))
        Text(
            text = icon.name,
            modifier = Modifier.fillMaxWidth(),
            color = PlaygroundColors.Muted,
            fontSize = 10.sp,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun IconLibrary(
    state: PlaygroundState,
    height: Dp,
) {
    SectionHeader(
        title = "MATERIAL ICONS",
        detail = "$iconCount ImageVectors · click to toggle selection",
    )
    Spacer(Modifier.height(8.dp))
    LazyVerticalGrid(
        columns = GridCells.Adaptive(48.dp),
        modifier =
            Modifier
                .fillMaxWidth()
                .height(height)
                .clip(RoundedCornerShape(14.dp))
                .background(PlaygroundColors.Panel)
                .border(1.dp, PlaygroundColors.Line, RoundedCornerShape(14.dp)),
        contentPadding = PaddingValues(10.dp),
        horizontalArrangement = Arrangement.spacedBy(7.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        items(iconEntries.size) { index ->
            IconCell(
                icon = iconEntries[index],
                selectionOrder = state.selectedOrder(index),
                onClick = { state.toggleIcon(index) },
            )
        }
    }
}

@Composable
private fun IconCell(
    icon: IconEntry,
    selectionOrder: Int?,
    onClick: () -> Unit,
) {
    val selected = selectionOrder != null
    Box(
        modifier =
            Modifier
                .size(46.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(PlaygroundColors.PanelRaised)
                .border(
                    width = if (selected) 2.dp else 1.dp,
                    color = if (selected) PlaygroundColors.Amber else PlaygroundColors.Line,
                    shape = RoundedCornerShape(10.dp),
                ).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon.imageVector,
            contentDescription = icon.name,
            modifier = Modifier.size(22.dp),
            tint = if (selected) PlaygroundColors.Ink else PlaygroundColors.Muted,
        )
        if (selectionOrder != null) {
            Text(
                text = selectionOrder.toString(),
                modifier = Modifier.align(Alignment.TopEnd).padding(top = 2.dp, end = 3.dp),
                color = PlaygroundColors.Amber,
                fontFamily = FontFamily.Monospace,
                fontSize = 8.sp,
            )
        }
    }
}

@Composable
private fun Panel(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(PlaygroundColors.Panel)
                .border(1.dp, PlaygroundColors.Line, RoundedCornerShape(14.dp))
                .padding(horizontal = 14.dp, vertical = 12.dp),
        content = content,
    )
}

@Composable
private fun SectionHeader(
    title: String,
    detail: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SectionLabel(title)
        Text(
            text = "· $detail",
            color = PlaygroundColors.Muted,
            fontFamily = FontFamily.Monospace,
            fontSize = 10.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        color = PlaygroundColors.Muted,
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.SemiBold,
        fontSize = 10.sp,
        letterSpacing = 1.6.sp,
    )
}

private fun formatProgress(progress: Float): String =
    ((progress * 1_000).roundToInt() / 1_000.0).toString().padEnd(5, '0')

private data class PlaygroundPalette(
    val background: Color,
    val panel: Color,
    val panelRaised: Color,
    val line: Color,
    val ink: Color,
    val muted: Color,
    val amber: Color,
)

private val LightPalette =
    PlaygroundPalette(
        background = Color(0xfff7f8fa),
        panel = Color(0xffffffff),
        panelRaised = Color(0xfff1f4f8),
        line = Color(0xffdce2ea),
        ink = Color(0xff172033),
        muted = Color(0xff6f7d90),
        amber = Color(0xffd5901d),
    )

private val DarkPalette =
    PlaygroundPalette(
        background = Color(0xff0c1017),
        panel = Color(0xff111722),
        panelRaised = Color(0xff151d2a),
        line = Color(0xff202a3a),
        ink = Color(0xffe8edf5),
        muted = Color(0xff6f7d90),
        amber = Color(0xffe6a83c),
    )

private val LocalPlaygroundPalette = staticCompositionLocalOf { LightPalette }

internal object PlaygroundColors {
    val Background: Color
        @Composable
        @ReadOnlyComposable
        get() = LocalPlaygroundPalette.current.background

    val Panel: Color
        @Composable
        @ReadOnlyComposable
        get() = LocalPlaygroundPalette.current.panel

    val PanelRaised: Color
        @Composable
        @ReadOnlyComposable
        get() = LocalPlaygroundPalette.current.panelRaised

    val Line: Color
        @Composable
        @ReadOnlyComposable
        get() = LocalPlaygroundPalette.current.line

    val Ink: Color
        @Composable
        @ReadOnlyComposable
        get() = LocalPlaygroundPalette.current.ink

    val Muted: Color
        @Composable
        @ReadOnlyComposable
        get() = LocalPlaygroundPalette.current.muted

    val Amber: Color
        @Composable
        @ReadOnlyComposable
        get() = LocalPlaygroundPalette.current.amber
}
