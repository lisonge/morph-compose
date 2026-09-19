package li.songe.morph.playground.report

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsBytes
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import java.io.Closeable
import java.io.File

/** Versioned metadata lives in Git; verified image bytes live only in the local cache. */
internal class RemoteVisualBaseline(
    baseline: File,
    cache: File,
    private val client: HttpClient = reportHttpClient(true),
) : Closeable {
    private val imageCache = ImageCache(cache)
    private val entries: JsonObject
    init {
        try {
            val manifest = File(baseline, "manifest.txt")
            val links = File(baseline, "images.json")
            if (!manifest.isFile && !links.isFile) entries = JsonObject(emptyMap())
            else {
                require(manifest.isFile && links.isFile) { "基线清单或图片链接清单缺失。" }
                val data = Json.parseToJsonElement(links.readText()).jsonObject
                require(data["version"]?.jsonPrimitive?.int == 1) { "不支持的基线链接格式。" }
                require(data.str("manifestSha256") == sha256(manifest.readText().replace("\r\n", "\n").toByteArray())) { "案例清单摘要不匹配，基线更新可能未完成。" }
                entries = data.obj("images")
                val ids = manifest.readLines().drop(1).map { it.substringBefore('\t') }
                require(ids.isNotEmpty() && ids.distinct().size == ids.size && ids.toSet() == entries.keys) {
                    "基线案例清单与图片链接清单不一致。"
                }
                entries.forEach { (id, value) ->
                    require(Regex("[A-Za-z0-9_]+").matches(id)) { "基线案例编号无效。" }
                    val entry = value.jsonObject
                    requireImageUrl(entry.str("url"))
                    require(Regex("[0-9a-f]{64}").matches(entry.str("sha256"))) { "基线图片摘要无效。" }
                }
            }
        } catch (error: Exception) { client.close(); throw error }
    }

    fun image(id: String): File? {
        val entry = entries[id]?.jsonObject ?: return null
        val hash = entry.str("sha256")
        imageCache.verified(entry.str("url"), hash)?.let { return it }
        val bytes = try {
            runBlocking {
                val response = client.get(entry.str("url"))
                require(response.status.value in 200..299) { "基线图片下载失败（状态码 ${response.status.value}）。" }
                response.bodyAsBytes()
            }
        } catch (error: Exception) {
            throw IllegalStateException("无法下载基线图片：$id。请检查网络或公开链接；此次验证失败。", error)
        }
        require(sha256(bytes) == hash) { "基线图片摘要不匹配：$id。此次验证失败。" }
        return imageCache.store(entry.str("url"), hash, bytes)
    }

    override fun close() = client.close()
}
