@file:OptIn(
    androidx.compose.ui.ExperimentalComposeUiApi::class,
    kotlin.js.ExperimentalJsExport::class,
    kotlin.js.ExperimentalWasmJsInterop::class,
)

package li.songe.morph.playground.browser

import androidx.compose.ui.window.ComposeViewport
import kotlinx.browser.document
import li.songe.morph.playground.MorphApp
import li.songe.morph.playground.PlaygroundState
import kotlin.js.JsAny
import org.w3c.dom.Element

@JsExport
public fun renderMorphPlayground(container: JsAny) {
    val state = PlaygroundState()
    ComposeViewport(viewportContainer = asElement(container)) {
        MorphApp(state)
    }
}

private fun asElement(container: JsAny): Element = js("container")

public fun main() {
    val container = document.querySelector("[data-morph-playground-auto-render]") ?: return
    renderMorphPlayground(container)
}
