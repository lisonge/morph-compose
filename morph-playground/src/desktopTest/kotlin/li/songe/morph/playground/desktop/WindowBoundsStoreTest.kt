package li.songe.morph.playground.desktop

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WindowBoundsStoreTest {
    @Test
    fun savesAndLoadsWindowBounds() =
        withTempDirectory { directory ->
            val path = directory.resolve("window-state.json")
            val store = WindowBoundsStore(path)
            val expected = StoredWindowBounds(x = -720, y = 84, width = 1_180, height = 820)

            assertTrue(store.save(expected))
            assertEquals(expected, store.load())
            assertTrue(Files.readString(path).contains("\"width\": 1180"))
        }

    @Test
    fun ignoresMalformedOrInvalidState() =
        withTempDirectory { directory ->
            val path = directory.resolve("window-state.json")
            val store = WindowBoundsStore(path)

            Files.writeString(path, "not json")
            assertNull(store.load())

            Files.writeString(path, """{"x":0,"y":0,"width":0,"height":920}""")
            assertNull(store.load())
            assertFalse(store.save(StoredWindowBounds(x = 0, y = 0, width = 0, height = 920)))
        }

    @Test
    fun resolvesStateBelowProjectRoot() =
        withTempDirectory { directory ->
            Files.writeString(directory.resolve("settings.gradle.kts"), "")
            Files.createDirectory(directory.resolve("morph-playground"))
            val nestedDirectory = Files.createDirectories(directory.resolve("morph-playground/build/run"))

            assertEquals(
                directory.resolve(".local/morph-playground/window-state.json"),
                defaultWindowBoundsPath(nestedDirectory),
            )
        }
}

private inline fun <T> withTempDirectory(block: (Path) -> T): T {
    val directory = Files.createTempDirectory("morph-window-state-")
    return try {
        block(directory)
    } finally {
        directory.toFile().deleteRecursively()
    }
}
