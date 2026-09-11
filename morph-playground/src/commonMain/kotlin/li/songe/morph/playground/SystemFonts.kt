package li.songe.morph.playground

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CancellationException

internal data class SystemFontOption(val id: String, val label: String)
internal val bundledFont = SystemFontOption("builtin:space-grotesk", "Space Grotesk (bundled)")

internal interface SystemFontSource {
    val supported: Boolean
    val needsPermission: Boolean
    suspend fun listFonts(): List<SystemFontOption>
    suspend fun loadDigits(font: SystemFontOption): List<DemoGeometry>
}

internal expect fun createSystemFontSource(): SystemFontSource

/** Lives above the tab composition; only copied path data is cached, never open native font handles. */
internal class TypographyState(private val source: SystemFontSource = createSystemFontSource()) {
    val numbers = NumberDemoState()
    var numberMode by mutableStateOf(true)
    var menuExpanded by mutableStateOf(false)
    val supported: Boolean get() = source.supported
    val needsPermission: Boolean get() = source.needsPermission
    var fonts by mutableStateOf(listOf(bundledFont))
        private set
    var selected by mutableStateOf(bundledFont)
        private set
    var digits by mutableStateOf(digitGeometries)
        private set
    var listing by mutableStateOf(false)
        private set
    var loading by mutableStateOf(false)
        private set
    var loadedList by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    private var request = 0
    private val cache = linkedMapOf<String, List<DemoGeometry>>()

    suspend fun refresh() {
        if (!supported || listing) return
        listing = true
        error = null
        try {
            fonts = listOf(bundledFont) + source.listFonts().distinctBy { it.id }.sortedBy { it.label.lowercase() }
            loadedList = true
            if (fonts.size == 1) error = "No local fonts were made available. You can still use the bundled font."
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            error = "Local fonts could not be accessed. Allow font access and try again, or use the bundled font."
        } finally {
            listing = false
        }
    }

    suspend fun select(font: SystemFontOption) {
        require(font in fonts) { "Unknown font: ${font.label}" }
        val currentRequest = ++request
        loading = true
        error = null
        try {
            val outlines = if (font == bundledFont) digitGeometries else cache[font.id] ?: source.loadDigits(font)
            require(outlines.size == 10) { "Font must provide all ten digits" }
            if (currentRequest != request) return
            if (font != bundledFont) {
                cache.remove(font.id)
                cache[font.id] = outlines
                if (cache.size > 8) cache.remove(cache.keys.first())
            }
            digits = outlines
            selected = font
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            if (currentRequest == request) error = "${font.label} has no usable digit outlines. The previous font is still selected."
        } finally {
            if (currentRequest == request) loading = false
        }
    }
}
