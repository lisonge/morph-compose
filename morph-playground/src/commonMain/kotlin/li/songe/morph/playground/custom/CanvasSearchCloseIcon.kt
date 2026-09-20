package li.songe.morph.playground.custom

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material.IconButton
import androidx.compose.material.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt

// Original Canvas animation retained as a porting reference for other frameworks.
@Composable
fun CanvasSearchCloseIconButton(
    onClick: () -> Unit,
    isSearchOpen: Boolean,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current,
    contentDescription: String = if (isSearchOpen) "Close search" else "Open search",
) {
    IconButton(onClick = onClick) {
        CanvasSearchCloseIcon(
            isSearchOpen = isSearchOpen,
            modifier = modifier,
            tint = tint,
            contentDescription = contentDescription,
        )
    }
}

@Composable
fun CanvasSearchCloseIcon(
    isSearchOpen: Boolean,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current,
    contentDescription: String = if (isSearchOpen) "Close search" else "Open search",
) {
    // A shared timeline preserves the original staggered trims and translation in both directions.
    val playTime = remember { Animatable(if (isSearchOpen) 300f else 0f) }
    LaunchedEffect(isSearchOpen) {
        val target = if (isSearchOpen) 300f else 0f
        playTime.animateTo(
            targetValue = target,
            animationSpec = tween(
                durationMillis = abs(target - playTime.value).roundToInt(),
                easing = LinearEasing,
            ),
        )
    }
    val ovalMeasure = remember {
        PathMeasure().apply {
            setPath(Path().apply {
                moveTo(13.389f, 13.389f)
                cubicTo(15.537f, 11.241f, 15.537f, 7.759f, 13.389f, 5.611f)
                cubicTo(11.241f, 3.463f, 7.759f, 3.463f, 5.611f, 5.611f)
                cubicTo(3.463f, 7.759f, 3.463f, 11.241f, 5.611f, 13.389f)
                cubicTo(7.759f, 15.537f, 11.241f, 15.537f, 13.389f, 13.389f)
                close()
            }, false)
        }
    }
    val ovalSegment = remember { Path() }
    val stroke = remember { Stroke(width = 1.8f) }
    Canvas(
        modifier = modifier.size(24.dp).semantics {
            this.contentDescription = contentDescription
            role = Role.Image
        },
    ) {
        val time = playTime.value
        val stemProgress = FastOutSlowInEasing.transform(((time - 108f) / 179f).coerceIn(0f, 1f))
        val crossProgress = FastOutSlowInEasing.transform(((time - 187f) / 113f).coerceIn(0f, 1f))
        val ovalProgress = ((time - 48f) / 149f).coerceIn(0f, 1f)
        val ovalTrim = ((1.0 - cos(PI * ovalProgress)) / 2.0).toFloat()
        val scale = size.minDimension / 24f
        withTransform({
            translate(
                left = (size.width - size.minDimension) / 2f,
                top = (size.height - size.minDimension) / 2f,
            )
            scale(scaleX = scale, scaleY = scale, pivot = Offset.Zero)
        }) {
            if (ovalTrim < 1f) {
                ovalSegment.reset()
                ovalMeasure.getSegment(
                    startDistance = ovalMeasure.length * ovalTrim,
                    stopDistance = ovalMeasure.length,
                    destination = ovalSegment,
                )
                translate(left = -6.7f * stemProgress, top = -6.7f * stemProgress) {
                    drawPath(ovalSegment, color = tint, style = stroke)
                }
            }
            if (crossProgress > 0f) {
                drawLine(
                    color = tint,
                    start = Offset(6f + 12f * crossProgress, 18f - 12f * crossProgress),
                    end = Offset(6f, 18f),
                    strokeWidth = 1.8f,
                )
            }
            val stemStart = 6f + 14f * 0.48f * (1f - stemProgress)
            val stemEnd = 6f + 14f * (1f - 0.14f * stemProgress)
            drawLine(
                color = tint,
                start = Offset(stemStart, stemStart),
                end = Offset(stemEnd, stemEnd),
                strokeWidth = 1.8f,
            )
        }
    }
}
