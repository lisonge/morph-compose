package li.songe.morph.playground.desktop

import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption

internal data class StoredWindowBounds(
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int,
) {
    fun isValid(): Boolean = width in 240..20_000 && height in 240..20_000
}

internal class WindowBoundsStore(
    val path: Path = defaultWindowBoundsPath(),
) {
    fun load(): StoredWindowBounds? {
        if (!Files.isRegularFile(path)) return null
        return runCatching {
            val content = Files.readString(path)
            StoredWindowBounds(
                x = content.readInt("x") ?: return null,
                y = content.readInt("y") ?: return null,
                width = content.readInt("width") ?: return null,
                height = content.readInt("height") ?: return null,
            ).takeIf(StoredWindowBounds::isValid)
        }.getOrNull()
    }

    fun save(bounds: StoredWindowBounds): Boolean {
        if (!bounds.isValid()) return false
        return runCatching {
            path.parent?.let(Files::createDirectories)
            val temporaryPath = path.resolveSibling("${path.fileName}.tmp")
            Files.writeString(
                temporaryPath,
                bounds.toJson(),
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE,
            )
            try {
                Files.move(
                    temporaryPath,
                    path,
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING,
                )
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(temporaryPath, path, StandardCopyOption.REPLACE_EXISTING)
            }
            true
        }.getOrElse { false }
    }
}

internal fun defaultWindowBoundsPath(
    startDirectory: Path = Path.of("").toAbsolutePath().normalize(),
): Path {
    var directory: Path? = startDirectory
    while (directory != null) {
        if (
            Files.isRegularFile(directory.resolve("settings.gradle.kts")) &&
            Files.isDirectory(directory.resolve("morph-playground"))
        ) {
            return directory.resolve(".local/morph-playground/window-state.json")
        }
        directory = directory.parent
    }
    return startDirectory.resolve(".local/morph-playground/window-state.json")
}

private fun String.readInt(name: String): Int? =
    Regex("\"${Regex.escape(name)}\"\\s*:\\s*(-?\\d+)")
        .find(this)
        ?.groupValues
        ?.get(1)
        ?.toIntOrNull()

private fun StoredWindowBounds.toJson(): String =
    """{
  "x": $x,
  "y": $y,
  "width": $width,
  "height": $height
}
"""
