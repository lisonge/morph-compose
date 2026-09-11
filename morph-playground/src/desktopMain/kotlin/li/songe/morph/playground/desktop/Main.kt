package li.songe.morph.playground.desktop

import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.awt.ComposeWindow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import java.awt.GraphicsEnvironment
import java.awt.Rectangle
import li.songe.morph.playground.MorphApp
import li.songe.morph.playground.PlaygroundState
import li.songe.morph.playground.iconCount

public fun main(): Unit = application {
    val state = remember { PlaygroundState() }
    val boundsStore = remember { WindowBoundsStore() }
    val restoredBounds = remember(boundsStore) { boundsStore.load() }
    val windowReference = remember { WindowReference() }
    val windowState =
        rememberWindowState(
            position =
                restoredBounds
                    ?.takeIf(StoredWindowBounds::isVisibleOnScreen)
                    ?.let { WindowPosition(it.x.dp, it.y.dp) }
                    ?: WindowPosition.PlatformDefault,
            width = (restoredBounds?.width ?: DefaultWindowWidth).dp,
            height = (restoredBounds?.height ?: DefaultWindowHeight).dp,
        )
    Window(
        onCloseRequest = {
            windowReference.value?.let { boundsStore.save(it.bounds.toStoredWindowBounds()) }
            exitApplication()
        },
        title = "morph-compose · playground",
        state = windowState,
    ) {
        DisposableEffect(window, state, boundsStore) {
            windowReference.value = window
            val debugServer = DesktopDebugServer.start(state, window, iconCount)
            onDispose {
                if (windowReference.value === window) {
                    boundsStore.save(window.bounds.toStoredWindowBounds())
                    windowReference.value = null
                }
                debugServer.close()
            }
        }
        MorphApp(state)
    }
}

private const val DefaultWindowWidth = 760
private const val DefaultWindowHeight = 920
private const val MinimumVisibleWidth = 96
private const val MinimumVisibleHeight = 48

private class WindowReference(
    var value: ComposeWindow? = null,
)

private fun Rectangle.toStoredWindowBounds(): StoredWindowBounds =
    StoredWindowBounds(
        x = x,
        y = y,
        width = width,
        height = height,
    )

private fun StoredWindowBounds.isVisibleOnScreen(): Boolean {
    if (GraphicsEnvironment.isHeadless()) return true
    val savedArea = Rectangle(x, y, width, height)
    return GraphicsEnvironment
        .getLocalGraphicsEnvironment()
        .screenDevices
        .flatMap { it.configurations.asIterable() }
        .any { configuration ->
            val visibleArea = savedArea.intersection(configuration.bounds)
            visibleArea.width >= MinimumVisibleWidth && visibleArea.height >= MinimumVisibleHeight
        }
}
