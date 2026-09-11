package li.songe.morph.playground

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.skia.FontMgr
import org.jetbrains.skia.FontStyle

internal actual fun createSystemFontSource(): SystemFontSource = DesktopSystemFonts()

private class DesktopSystemFonts : SystemFontSource {
    override val supported = true
    override val needsPermission = false

    override suspend fun listFonts(): List<SystemFontOption> = withContext(Dispatchers.Default) {
        val manager = FontMgr.default
        List(manager.familiesCount) { index ->
            val family = manager.getFamilyName(index)
            SystemFontOption(family, family)
        }
    }

    override suspend fun loadDigits(font: SystemFontOption): List<DemoGeometry> = withContext(Dispatchers.Default) {
        val face = requireNotNull(FontMgr.default.matchFamilyStyle(font.id, FontStyle.NORMAL)) { "Font is unavailable" }
        try {
            systemDigitOutlines(face)
        } finally {
            face.close()
        }
    }
}
