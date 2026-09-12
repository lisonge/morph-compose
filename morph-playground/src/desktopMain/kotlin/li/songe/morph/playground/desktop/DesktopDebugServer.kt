@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package li.songe.morph.playground.desktop

import androidx.compose.ui.awt.ComposeWindow
import androidx.compose.ui.renderComposeScene
import androidx.compose.ui.unit.Density
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.FutureTask
import javax.swing.SwingUtilities
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import li.songe.morph.playground.MorphApp
import li.songe.morph.playground.MorphAppPreview
import li.songe.morph.compose.MorphRotationPreference
import li.songe.morph.playground.PlaygroundSnapshot
import li.songe.morph.playground.PlaygroundState
import li.songe.morph.playground.PlaygroundTab
import kotlin.math.roundToInt
import org.jetbrains.skia.EncodedImageFormat

internal const val DebugServerPort = 17_321
private const val DebugPreviewHeightDp = 284
private const val DebugStackedPreviewHeightDp = 430
private val EdtDispatcher = object : CoroutineDispatcher() {
    override fun isDispatchNeeded(context: CoroutineContext): Boolean = !SwingUtilities.isEventDispatchThread()
    override fun dispatch(context: CoroutineContext, block: Runnable) = SwingUtilities.invokeLater(block)
}

internal class DesktopDebugServer private constructor(
    private val stopServer: () -> Unit,
) : AutoCloseable {
    override fun close() {
        stopServer()
    }

    companion object {
        fun start(
            state: PlaygroundState,
            window: ComposeWindow,
            iconCount: Int,
            port: Int = DebugServerPort,
        ): DesktopDebugServer {
            val server =
                embeddedServer(
                    factory = CIO,
                    host = "127.0.0.1",
                    port = port,
                ) {
                    routing {
                        get("/health") {
                            call.respondJson(onEdt { state.snapshot() }.toJson())
                        }
                        get("/numbers") {
                            try {
                                val result = onEdt {
                                    val parameters = call.request.queryParameters
                                    val numbers = state.typography.numbers
                                    if (parameters.names().isNotEmpty()) {
                                        state.selectedTab = PlaygroundTab.Typography
                                        state.typography.numberMode = true
                                    }
                                    parameters["from"]?.let { numbers.fromInput = it }
                                    parameters["to"]?.let { numbers.toInput = it }
                                    parameters["step"]?.parseBoolean("step")?.let(numbers::changeStep)
                                    if (parameters["from"] != null || parameters["to"] != null || parameters["play"]?.parseBoolean("play") == true) {
                                        numbers.play()
                                    }
                                    parameters["progress"]?.toFloat()?.let(numbers::seek)
                                    "{\"from\":${numbers.pair.from.text.jsonString()},\"to\":${numbers.pair.to.text.jsonString()}," +
                                        "\"progress\":${numbers.progress},\"playing\":${numbers.playing}," +
                                        "\"step\":${numbers.stepEnabled},\"rangeFrom\":${numbers.range.from.text.jsonString()},\"rangeTo\":${numbers.range.to.text.jsonString()}," +
                                        "\"error\":${numbers.error?.jsonString() ?: "null"}}"
                                }
                                call.respondJson(result)
                            } catch (error: Throwable) {
                                call.respondError(error)
                            }
                        }
                        get("/fonts") {
                            try {
                                val result = withContext(EdtDispatcher) {
                                    val fonts = state.typography
                                    if (!fonts.loadedList) fonts.refresh()
                                    call.request.queryParameters["id"]?.let { id ->
                                        val font = requireNotNull(fonts.fonts.find { it.id == id }) { "Unknown font: $id" }
                                        fonts.select(font)
                                    }
                                    call.request.queryParameters["menu"]?.parseBoolean("menu")?.let {
                                        fonts.menuExpanded = it
                                    }
                                    "{\"selected\":${fonts.selected.id.jsonString()},\"error\":${fonts.error?.jsonString() ?: "null"}," +
                                        "\"fonts\":[${fonts.fonts.joinToString { "{\"id\":${it.id.jsonString()},\"label\":${it.label.jsonString()}}" }}]}"
                                }
                                call.respondJson(result)
                            } catch (error: Throwable) {
                                call.respondError(error)
                            }
                        }
                        get("/control") {
                            try {
                                val parameters = call.request.queryParameters
                                val snapshot =
                                    onEdt {
                                        parameters["module"]?.let { id ->
                                            val tab = requireNotNull(PlaygroundTab.entries.find { it.id == id }) { "Unknown module: $id" }
                                            state.selectedTab = tab
                                        }
                                        val demo = state.demo(state.selectedTab)
                                        if (state.selectedTab == PlaygroundTab.Typography &&
                                            listOf("demoTarget", "demoProgress", "demoPlaying").any { parameters[it] != null }) {
                                            state.typography.numberMode = false
                                        }
                                        parameters["demoTarget"]?.toInt()?.let { index ->
                                            val count = when (state.selectedTab) {
                                                PlaygroundTab.Interaction -> 3
                                                PlaygroundTab.Loading -> 5
                                                PlaygroundTab.Typography -> 10
                                                else -> 6
                                            }
                                            demo.select(index, count)
                                        }
                                        parameters["demoProgress"]?.toFloat()?.let(demo::seek)
                                        parameters["demoPlaying"]?.parseBoolean("demoPlaying")?.let {
                                            demo.manual = false
                                            demo.playing = it
                                        }
                                        parameters["selected"]?.parseIndices()?.let { indices ->
                                            require(indices.all { it in 0 until iconCount }) {
                                                "selected indices must be in 0..${iconCount - 1}"
                                            }
                                            state.replaceSelection(indices)
                                        }
                                        parameters["pair"]?.toInt()?.let {
                                            val pairCount = iconCount / 2
                                            require(it in 0 until pairCount) {
                                                "pair must be in 0..${pairCount - 1}"
                                            }
                                            state.selectPair(it * 2, it * 2 + 1)
                                        }
                                        if (parameters["from"] != null || parameters["to"] != null) {
                                            val from = parameters["from"]?.toInt() ?: state.fromIndex
                                            val to = parameters["to"]?.toInt() ?: state.toIndex
                                            require(from in 0 until iconCount) {
                                                "from must be in 0..${iconCount - 1}"
                                            }
                                            require(to in 0 until iconCount) {
                                                "to must be in 0..${iconCount - 1}"
                                            }
                                            state.selectPair(from, to)
                                        }
                                        parameters["rotation"]?.let { value ->
                                            state.changeRotationPreference(
                                                when (value) {
                                                    "auto" -> MorphRotationPreference.Auto
                                                    "cw" -> MorphRotationPreference.PreferClockwise
                                                    "ccw" -> MorphRotationPreference.PreferCounterClockwise
                                                    else -> throw IllegalArgumentException("rotation must be auto, cw, or ccw")
                                                },
                                            )
                                        }
                                        parameters["progress"]?.toFloat()?.let {
                                            state.scrubTo(it)
                                        }
                                        val playing =
                                            parameters["playing"]?.parseBoolean("playing")
                                                ?: parameters["target"]?.parseBoolean("target")
                                        playing?.let {
                                            state.setPlayback(it)
                                        }
                                        parameters["linear"]?.parseBoolean("linear")?.let {
                                            state.compareLinear = it
                                        }
                                        parameters["dark"]?.parseBoolean("dark")?.let {
                                            state.isDarkTheme = it
                                        }
                                        if (parameters["width"] != null || parameters["height"] != null) {
                                            val width = parameters["width"]?.toInt() ?: window.width
                                            val height = parameters["height"]?.toInt() ?: window.height
                                            require(width in 320..2_400) { "width must be in 320..2400" }
                                            require(height in 480..2_400) { "height must be in 480..2400" }
                                            window.setSize(width, height)
                                        }
                                        parameters["action"]?.let { action ->
                                            when (action.lowercase()) {
                                                "last", "previous", "prev" -> state.requestPrevious()
                                                "next" -> state.requestNext()
                                                "play" -> state.setPlayback(true)
                                                "pause" -> state.setPlayback(false)
                                                "toggle" -> state.togglePlayback()
                                                else -> throw IllegalArgumentException(
                                                    "action must be last, previous, next, play, pause, or toggle",
                                                )
                                            }
                                        }
                                        state.snapshot()
                                    }
                                call.respondJson(snapshot.toJson())
                            } catch (error: Throwable) {
                                call.respondError(error)
                            }
                        }
                        get("/screenshot") {
                            try {
                                val area = call.request.queryParameters["area"] ?: "preview"
                                require(area == "preview" || area == "window") {
                                    "area must be preview or window"
                                }
                                val requestedPath = call.request.queryParameters["path"]
                                val output =
                                    if (requestedPath == null) {
                                        Path.of("build", "reports", "morph-playground-debug.png").toAbsolutePath()
                                    } else {
                                        Path.of(requestedPath).also {
                                            require(it.isAbsolute) { "screenshot path must be absolute" }
                                        }
                                    }.normalize()
                                output.parent?.let(Files::createDirectories)
                                val image =
                                    when (area) {
                                        "preview" -> capturePreview(window, state)
                                        else -> captureWindow(window, state)
                                    }
                                Files.write(output, image)
                                call.respondJson("{\"path\":${output.toString().jsonString()}}")
                            } catch (error: Throwable) {
                                call.respondError(error)
                            }
                        }
                    }
                }.start(wait = false)
            println("Morph desktop debug API: http://127.0.0.1:$port")
            return DesktopDebugServer {
                server.stop(gracePeriodMillis = 0, timeoutMillis = 1_000)
            }
        }
    }
}

private fun capturePreview(
    window: ComposeWindow,
    state: PlaygroundState,
): ByteArray {
    return onEdt {
        val content = window.contentPane
        val transform = window.graphicsConfiguration.defaultTransform
        val previewHeight =
            if (state.compareLinear && content.width < 460) {
                DebugStackedPreviewHeightDp
            } else {
                DebugPreviewHeightDp
            }
        val image =
            renderComposeScene(
                width = (content.width * transform.scaleX).roundToInt(),
                height = (previewHeight * transform.scaleY).roundToInt(),
                density = Density(transform.scaleX.toFloat()),
            ) {
                MorphAppPreview(state)
            }
        try {
            val data = checkNotNull(image.encodeToData(EncodedImageFormat.PNG)) {
                "Unable to encode screenshot as PNG"
            }
            try {
                data.bytes
            } finally {
                data.close()
            }
        } finally {
            image.close()
        }
    }
}

private fun captureWindow(
    window: ComposeWindow,
    state: PlaygroundState,
): ByteArray {
    return onEdt {
        val content = window.contentPane
        val transform = window.graphicsConfiguration.defaultTransform
        val image =
            renderComposeScene(
                width = (content.width * transform.scaleX).roundToInt(),
                height = (content.height * transform.scaleY).roundToInt(),
                density = Density(transform.scaleX.toFloat()),
            ) {
                MorphApp(state)
            }
        try {
            val data = checkNotNull(image.encodeToData(EncodedImageFormat.PNG)) {
                "Unable to encode screenshot as PNG"
            }
            try {
                data.bytes
            } finally {
                data.close()
            }
        } finally {
            image.close()
        }
    }
}

private fun PlaygroundSnapshot.toJson(): String =
    "{\"selected\":[${selectedIndices.joinToString()}],\"active\":$activeSelectionPosition," +
        "\"pair\":${legacyPairIndex()},\"from\":$fromIndex,\"to\":$toIndex,\"progress\":$progress," +
        "\"rotationPreference\":${rotationPreference.name.jsonString()}," +
        "\"playing\":$isPlaying,\"target\":$isPlaying,\"linear\":$compareLinear,\"dark\":$isDarkTheme," +
        "\"module\":${route.jsonString()},\"demoTarget\":$demoTarget,\"demoProgress\":$demoProgress," +
        "\"demoManual\":$demoManual,\"demoPlaying\":$demoPlaying}"

private fun PlaygroundSnapshot.legacyPairIndex(): Int =
    if (fromIndex % 2 == 0 && toIndex == fromIndex + 1) fromIndex / 2 else -1

private fun String.parseBoolean(name: String): Boolean =
    when (lowercase()) {
        "true", "1", "yes" -> true
        "false", "0", "no" -> false
        else -> throw IllegalArgumentException("$name must be true or false")
    }

private fun String.parseIndices(): List<Int> {
    if (isBlank()) return emptyList()
    return split(',')
        .map(String::trim)
        .also { require(it.none(String::isEmpty)) { "selected must be a comma-separated index list" } }
        .map(String::toInt)
}

private suspend fun ApplicationCall.respondJson(
    body: String,
    status: HttpStatusCode = HttpStatusCode.OK,
) {
    respondText(body, ContentType.Application.Json, status)
}

private suspend fun ApplicationCall.respondError(error: Throwable) {
    val message = error.message ?: error::class.simpleName.orEmpty()
    respondJson("{\"error\":${message.jsonString()}}", status = HttpStatusCode.BadRequest)
}

private fun String.jsonString(): String =
    buildString(length + 2) {
        append('"')
        for (character in this@jsonString) {
            when (character) {
                '"' -> append("\\\"")
                '\\' -> append("\\\\")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> append(character)
            }
        }
        append('"')
    }

private fun <T> onEdt(block: () -> T): T {
    if (SwingUtilities.isEventDispatchThread()) return block()
    val task = FutureTask(block)
    SwingUtilities.invokeLater(task)
    return task.get()
}
