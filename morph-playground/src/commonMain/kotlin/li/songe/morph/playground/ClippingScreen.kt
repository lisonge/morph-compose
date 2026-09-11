package li.songe.morph.playground

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun ClippingScreen(state: FeatureDemoState) {
    val shape = rememberDemoShape(state, demoGeometries)
    val image = remember { alpineImage() }
    FeaturePage("02 / IMAGE CLIPS", "A new frame for the same view.", "Reveal an image through a moving boundary. The landscape stays still as the mask changes.") {
        DemoStage {
            Image(image, contentDescription = "Illustration of an alpine lake at sunset", contentScale = ContentScale.Crop,
                modifier = Modifier.size(270.dp).clip(shape))
        }
        DemoControls(state, demoGeometries)
        Text("Alpine study · an original landscape illustration", color = PlaygroundColors.Muted, fontSize = 13.sp)
    }
}

private fun alpineImage(): ImageBitmap = ImageBitmap(640, 640).also { bitmap ->
    CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(bitmap), Size(640f, 640f)) {
        drawRect(Brush.verticalGradient(listOf(Color(0xFFE59C88), Color(0xFFF6DBA7), Color(0xFFBDD6C5))))
        drawCircle(Color(0xFFFFE8B4), 62f, Offset(430f, 170f))
        fun ridge(points: List<Offset>, color: Color) {
            drawPath(Path().apply {
                moveTo(0f, 640f)
                points.forEach { lineTo(it.x, it.y) }
                lineTo(640f, 640f); close()
            }, color)
        }
        ridge(listOf(Offset(0f, 340f), Offset(150f, 170f), Offset(280f, 340f), Offset(410f, 230f), Offset(640f, 330f)), Color(0xFF778E92))
        ridge(listOf(Offset(0f, 450f), Offset(130f, 310f), Offset(290f, 430f), Offset(490f, 290f), Offset(640f, 395f)), Color(0xFF3A646B))
        drawRect(Brush.verticalGradient(listOf(Color(0xFF72ADB1), Color(0xFF183F53)), startY = 430f, endY = 640f), topLeft = Offset(0f, 430f), size = Size(640f, 210f))
        for (i in 0..10) {
            val y = 456f + i * 15f
            drawLine(Color(0x557AE0D6), Offset(250f - i * 8f, y), Offset(445f + i * 6f, y), 2f)
        }
        ridge(listOf(Offset(0f, 470f), Offset(70f, 490f), Offset(180f, 640f)), Color(0xFF173F3B))
    }
}
