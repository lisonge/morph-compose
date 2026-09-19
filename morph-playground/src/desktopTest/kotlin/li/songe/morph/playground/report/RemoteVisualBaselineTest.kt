package li.songe.morph.playground.report

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import kotlinx.serialization.json.*
import java.io.File
import java.nio.file.Files
import kotlin.test.*

class RemoteVisualBaselineTest {
    private fun fixture(test: (File, File, File, ByteArray) -> Unit) {
        val root = Files.createTempDirectory("morph-baseline-test-").toFile()
        val baseline = File(root, "baseline").apply { mkdirs() }
        val cache = File(root, "cache")
        val bytes = byteArrayOf(1, 3, 5, 7)
        val manifest = File(baseline, "manifest.txt").apply { writeText("test\ncase\t案例\n") }
        File(baseline, "images.json").writeText(buildJsonObject {
            put("version", 1)
            put("manifestSha256", sha256(manifest.readBytes()))
            put("images", buildJsonObject { put("case", jsonObject(
                "url" to "https://github.com/user-attachments/assets/test", "sha256" to sha256(bytes))) })
        }.toString())
        try { test(root, baseline, cache, bytes) } finally { root.deleteRecursively() }
    }

    @Test fun downloadsAnonymouslyAndReusesVerifiedCache() = fixture { _, baseline, cache, bytes ->
        var requests = 0
        val client = HttpClient(MockEngine { request ->
            requests++
            assertNull(request.headers[HttpHeaders.Cookie])
            assertNull(request.headers[HttpHeaders.Authorization])
            respond(bytes)
        })
        RemoteVisualBaseline(baseline, cache, client).use { references ->
            assertContentEquals(bytes, references.image("case")!!.readBytes())
            assertContentEquals(bytes, references.image("case")!!.readBytes())
            assertNull(references.image("new_case"))
            assertEquals(1, requests)
            assertEquals(listOf("test.png"), cache.listFiles()!!.map { it.name })
        }
    }

    @Test fun corruptCacheIsRedownloaded() = fixture { _, baseline, cache, bytes ->
        cache.mkdirs()
        File(cache, "test.png").writeText("corrupt")
        var requests = 0
        RemoteVisualBaseline(baseline, cache, HttpClient(MockEngine { requests++; respond(bytes) })).use {
            assertContentEquals(bytes, it.image("case")!!.readBytes())
            assertEquals(1, requests)
            assertEquals(listOf("test.png"), cache.listFiles()!!.map { it.name })
        }
    }

    @Test fun wrongRemoteBytesAndHttpFailureCannotBecomeBaselines() = fixture { _, baseline, cache, _ ->
        RemoteVisualBaseline(baseline, cache, HttpClient(MockEngine { respond(byteArrayOf(9)) })).use {
            assertFailsWith<IllegalArgumentException> { it.image("case") }
        }
        RemoteVisualBaseline(baseline, cache, HttpClient(MockEngine { respond("missing", HttpStatusCode.NotFound) })).use {
            assertFailsWith<IllegalStateException> { it.image("case") }
        }
        assertTrue(cache.listFiles().isNullOrEmpty())
    }

    @Test fun checkoutLineEndingsDoNotChangeManifestIdentity() = fixture { _, baseline, cache, _ ->
        val manifest = File(baseline, "manifest.txt")
        manifest.writeText(manifest.readText().replace("\n", "\r\n"))
        RemoteVisualBaseline(baseline, cache, HttpClient(MockEngine { error("不应访问网络") })).use {
            assertNull(it.image("new_case"))
        }
    }

    @Test fun inconsistentManifestIsRejectedWithoutNetworkAccess() = fixture { _, baseline, cache, _ ->
        File(baseline, "manifest.txt").appendText("other\t另一案例\n")
        val client = HttpClient(MockEngine { error("不应访问网络") })
        assertFailsWith<IllegalArgumentException> { RemoteVisualBaseline(baseline, cache, client) }
    }
}
