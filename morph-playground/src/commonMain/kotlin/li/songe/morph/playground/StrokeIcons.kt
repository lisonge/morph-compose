package li.songe.morph.playground

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/** Centerlines, rather than fused filled silhouettes, preserve the individual moving strokes. */
internal object StrokeIcons {
    val Lock = lockIcon(unlocked = false)
    val Unlock = lockIcon(unlocked = true)

    private fun lockIcon(unlocked: Boolean) = icon(if (unlocked) "Unlock outline" else "Lock outline") {
        // Identical body geometry lets the lock body stay still while the shackle opens.
        moveTo(7f, 11f)
        lineTo(17f, 11f)
        curveTo(18.105f, 11f, 19f, 11.895f, 19f, 13f)
        lineTo(19f, 19f)
        curveTo(19f, 20.105f, 18.105f, 21f, 17f, 21f)
        lineTo(7f, 21f)
        curveTo(5.895f, 21f, 5f, 20.105f, 5f, 19f)
        lineTo(5f, 13f)
        curveTo(5f, 11.895f, 5.895f, 11f, 7f, 11f)
        close()
        moveTo(8f, 11f)
        lineTo(8f, 7f)
        curveTo(8f, 4.791f, 9.791f, 3f, 12f, 3f)
        curveTo(14.209f, 3f, 16f, 4.791f, 16f, 7f)
        if (!unlocked) lineTo(16f, 11f)
    }

    val Search = icon("Search") {
        // Match the ring/handle construction in GkCanvasSearchCloseIcon. Keep the full ring
        // as an open stroke with coincident endpoints so it can unfold into a line without
        // dropping the last sampled edge when transitioning to an open Close stroke.
        moveTo(13.389f, 13.389f)
        curveTo(15.537f, 11.241f, 15.537f, 7.759f, 13.389f, 5.611f)
        curveTo(11.241f, 3.463f, 7.759f, 3.463f, 5.611f, 5.611f)
        curveTo(3.463f, 7.759f, 3.463f, 11.241f, 5.611f, 13.389f)
        curveTo(7.759f, 15.537f, 11.241f, 15.537f, 13.389f, 13.389f)
        // The shared icon style uses round caps, so start on the centerline of the ring.
        moveTo(13.389f, 13.389f); lineTo(20f, 20f)
    }
    val Close = icon("Close") {
        moveTo(6f, 6f); lineTo(18f, 18f)
        moveTo(18f, 6f); lineTo(6f, 18f)
    }
    val Menu = icon("Menu") {
        moveTo(4f, 6f); lineTo(20f, 6f)
        moveTo(4f, 12f); lineTo(20f, 12f)
        moveTo(4f, 18f); lineTo(20f, 18f)
    }
    val Add = icon("Add") {
        moveTo(12f, 5f); lineTo(12f, 19f)
        moveTo(5f, 12f); lineTo(19f, 12f)
    }
    val Remove = icon("Remove") { moveTo(5f, 12f); lineTo(19f, 12f) }
    val ArrowBack = icon("Arrow back", autoMirror = true) {
        moveTo(19f, 12f); lineTo(5f, 12f)
        moveTo(12f, 5f); lineTo(5f, 12f); lineTo(12f, 19f)
    }
    val ArrowForward = icon("Arrow forward", autoMirror = true) {
        moveTo(5f, 12f); lineTo(19f, 12f)
        moveTo(12f, 5f); lineTo(19f, 12f); lineTo(12f, 19f)
    }
    val ArrowUp = icon("Arrow up") {
        moveTo(12f, 19f); lineTo(12f, 5f)
        moveTo(5f, 12f); lineTo(12f, 5f); lineTo(19f, 12f)
    }
    val ArrowDown = icon("Arrow down") {
        moveTo(12f, 5f); lineTo(12f, 19f)
        moveTo(5f, 12f); lineTo(12f, 19f); lineTo(19f, 12f)
    }
    val Check = icon("Check") {
        moveTo(5f, 12f); lineTo(9f, 16f); lineTo(19f, 6f)
    }

    private fun icon(name: String, autoMirror: Boolean = false, path: PathBuilder.() -> Unit): ImageVector =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f, autoMirror = autoMirror).apply {
            path(fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round, pathBuilder = path)
        }.build()
}
