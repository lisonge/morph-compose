package li.songe.morph.playground

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.vector.PathNode
import li.songe.morph.compose.morphGeometryOf
import org.jetbrains.skia.Font
import org.jetbrains.skia.PathVerb
import org.jetbrains.skia.Typeface

/** Same glyph extraction and baseline on Desktop and Wasm. The caller owns the typeface. */
internal fun systemDigitOutlines(typeface: Typeface): List<DemoGeometry> {
    val font = Font(typeface, 100f)
    try {
        val glyphs = font.getStringGlyphs("0123456789")
        require(glyphs.size == 10 && glyphs.all { it.toInt() != 0 }) { "The font must contain digits 0–9" }
        val bounds = font.getBounds(glyphs)
        val top = bounds.minOf { it.top }
        val bottom = bounds.maxOf { it.bottom }
        val height = bottom - top
        val width = bounds.maxOf { it.width }
        require(height > 0f && width > 0f && height.isFinite() && width.isFinite()) { "Empty glyph bounds" }
        val scale = minOf(78f / height, 80f / width)
        val baseline = 50f - (top + bottom) / 2 * scale
        return glyphs.mapIndexed { digit, glyph ->
            val outline = requireNotNull(font.getPath(glyph)) { "No vector outline for $digit" }
            try {
                val xOffset = 50f - (bounds[digit].left + bounds[digit].right) / 2 * scale
                fun x(value: Float) = value * scale + xOffset
                fun y(value: Float) = value * scale + baseline
                val nodes = mutableListOf<PathNode>()
                for (segment in outline) {
                    if (segment == null) continue
                    when (segment.verb) {
                        PathVerb.MOVE -> nodes += PathNode.MoveTo(x(segment.p0!!.x), y(segment.p0!!.y))
                        PathVerb.LINE -> nodes += PathNode.LineTo(x(segment.p1!!.x), y(segment.p1!!.y))
                        PathVerb.QUAD -> nodes += PathNode.QuadTo(x(segment.p1!!.x), y(segment.p1!!.y), x(segment.p2!!.x), y(segment.p2!!.y))
                        PathVerb.CUBIC -> nodes += PathNode.CurveTo(x(segment.p1!!.x), y(segment.p1!!.y), x(segment.p2!!.x), y(segment.p2!!.y), x(segment.p3!!.x), y(segment.p3!!.y))
                        PathVerb.CLOSE -> nodes += PathNode.Close
                        PathVerb.DONE -> Unit
                        PathVerb.CONIC -> error("Unexpected conic in a font outline")
                    }
                }
                DemoGeometry(digit.toString(), morphGeometryOf(nodes, Size(100f, 100f)))
            } finally {
                outline.close()
            }
        }
    } finally {
        font.close()
    }
}
