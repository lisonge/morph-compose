@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package li.songe.morph.playground

import kotlin.js.JsAny
import kotlin.js.JsArray
import kotlin.js.Promise
import kotlin.js.get
import kotlinx.coroutines.await
import org.jetbrains.skia.Data
import org.jetbrains.skia.FontMgr

internal actual fun createSystemFontSource(): SystemFontSource = BrowserSystemFonts()

private class BrowserSystemFonts : SystemFontSource {
    override val supported: Boolean get() = localFontsSupported()
    override val needsPermission = true
    private var available = emptyMap<String, BrowserFont>()

    override suspend fun listFonts(): List<SystemFontOption> {
        // This call must run before the first suspension, inside the user's click activation.
        val result: JsArray<BrowserFont> = queryFonts().await()
        val fonts = List(result.length) { result[it]!! }
        available = fonts.associateBy { it.postscriptName }
        return fonts.map { SystemFontOption(it.postscriptName, it.fullName) }
    }

    override suspend fun loadDigits(font: SystemFontOption): List<DemoGeometry> {
        val source = requireNotNull(available[font.id]) { "Font access must be granted again" }
        val raw: FontBytes = readFontBytes(source).await()
        val bytes = ByteArray(raw.length) { raw.at(it).toByte() }
        val data = Data.makeFromBytes(bytes)
        try {
            val face = requireNotNull(FontMgr.default.makeFromData(data)) { "Unsupported font data" }
            try {
                return systemDigitOutlines(face)
            } finally {
                face.close()
            }
        } finally {
            data.close()
        }
    }
}

private external interface BrowserFont : JsAny {
    val postscriptName: String
    val fullName: String
}

private external interface FontBytes : JsAny {
    val length: Int
    fun at(index: Int): Int
}

private fun localFontsSupported(): Boolean = js("window.isSecureContext && typeof window.queryLocalFonts === 'function'")
private fun queryFonts(): Promise<JsArray<BrowserFont>> = js("window.queryLocalFonts()")
private fun readFontBytes(font: BrowserFont): Promise<FontBytes> =
    js("font.blob().then(blob => blob.arrayBuffer()).then(buffer => new Uint8Array(buffer))")
