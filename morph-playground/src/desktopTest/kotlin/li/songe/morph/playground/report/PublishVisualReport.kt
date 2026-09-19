package li.songe.morph.playground.report

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import java.io.File
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.URI
import java.nio.channels.FileChannel
import java.nio.file.StandardOpenOption
import java.security.MessageDigest
import kotlin.system.exitProcess

internal fun sha256(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
private fun File.writeAtomic(value: String) {
    val temp = File(parentFile, "$name.tmp")
    temp.writeText(value)
    java.nio.file.Files.move(temp.toPath(), toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING)
}

internal fun reportHttpClient(redirects: Boolean) = HttpClient(OkHttp) {
    followRedirects = redirects
    expectSuccess = false
    install(HttpTimeout) { connectTimeoutMillis = 30_000; requestTimeoutMillis = 120_000; socketTimeoutMillis = 120_000 }
    engine {
        config { followRedirects(redirects); followSslRedirects(redirects) }
        val proxyUrl = System.getenv("HTTPS_PROXY") ?: System.getenv("HTTP_PROXY")
        if (!proxyUrl.isNullOrBlank()) {
            val uri = URI(proxyUrl)
            proxy = Proxy(Proxy.Type.HTTP, InetSocketAddress(uri.host, if (uri.port >= 0) uri.port else 80))
        }
    }
}

fun main() {
    try { runBlocking { publishReport() } }
    catch (error: Exception) {
        // Ktor exceptions can contain signed URLs; do not dump their messages or stack traces.
        System.err.println("图片发布未完成（${error.javaClass.simpleName}）。本地报告保留；重新运行会复用上传缓存并优先清理待删除评论。")
        if (error is IllegalArgumentException || error is IllegalStateException) {
            // Only our fixed Chinese validation messages are safe to display.
            val message = error.message.orEmpty()
            if (!message.contains("http", ignoreCase = true) && !message.contains(System.getenv("GITHUB_COOKIE").orEmpty().ifEmpty { "\u0000" })) {
                System.err.println(message.take(240))
            }
        }
        exitProcess(1)
    }
}

private suspend fun publishReport() {
    val root = File(checkNotNull(System.getProperty("morph.visual.root")))
    val suite = System.getProperty("morph.visual.suite", "quick")
    require(suite in listOf("quick", "wide")) { "未知报告类型。" }
    val report = File(root, "morph-playground/build/reports/morph-visual/$suite")
    val summary = Json.parseToJsonElement(File(report, "summary.json").readText()).jsonObject
    require(summary.str("state") == "complete") { "请先完成报告生成。" }
    val source = File(report, "index.html").readText()
    val sourceHash = sha256(source.toByteArray())
    val imagePattern = Regex("(\\b(?:src|data-animation)=)([\"'])([^\"']+)([\"'])")
    val paths = imagePattern.findAll(source).map { it.groupValues[3] }.distinct().toList()
    require(paths.isNotEmpty()) { "报告不包含图片。" }
    val files = paths.associateWith { relative ->
        require(Regex("(candidate|before|diff|animation)/[A-Za-z0-9_]+\\.png").matches(relative)) { "报告包含未知图片路径。" }
        File(report, relative).also { require(it.isFile) { "报告缺少图片，请重新生成。" } }
    }
    val hashes = files.mapValues { sha256(it.value.readBytes()) }
    val unique = files.entries.associate { hashes.getValue(it.key) to it.value }
    val imageCache = ImageCache(File(root, ".cache/image"))
    val cookie = System.getenv("GITHUB_COOKIE")?.trim().orEmpty()
    require(cookie.isNotEmpty()) { "缺少 GITHUB_COOKIE 环境变量。" }
    val cacheDir = File(root, ".local/github-report-images").apply { mkdirs() }
    FileChannel.open(File(cacheDir, "publish.lock").toPath(), StandardOpenOption.CREATE, StandardOpenOption.WRITE).use { channel ->
        val lock = channel.tryLock() ?: error("已有图片发布任务正在运行。")
        lock.use {
            val cacheFile = File(cacheDir, "images.json")
            val pendingFile = File(cacheDir, "pending-comment.txt")
            val cache = if (cacheFile.isFile) Json.parseToJsonElement(cacheFile.readText()).jsonObject.toMutableMap() else mutableMapOf()
            fun save() = cacheFile.writeAtomic(JsonObject(cache).toString())
            reportHttpClient(false).use { authenticated -> reportHttpClient(true).use { anonymous ->
                val uploader = GithubImageUploader(authenticated, anonymous, cookie)
                if (pendingFile.isFile) {
                    println("清理上次未完成的临时评论与订阅。")
                    uploader.cleanup(pendingFile.readText().trim())
                    check(pendingFile.delete()) { "未能清除待处理评论记录。" }
                }
                for ((index, entry) in unique.entries.withIndex()) {
                    val (hash, file) = entry
                    if (cache[hash] == null) {
                        val href = uploader.uploadPrivate(file)
                        cache[hash] = buildJsonObject { put("href", href); put("public", false) }
                        save()
                        println("图片上传进度：${index + 1}/${unique.size}")
                    }
                }
                val privateHashes = unique.keys.filter { cache.getValue(it).jsonObject["public"]?.jsonPrimitive?.boolean != true }
                for (batch in privateHashes.chunked(30)) {
                    val urls = batch.map { cache.getValue(it).jsonObject.str("href") }
                    uploader.publish(urls, onCreated = { pendingFile.writeAtomic(it) }, onCleaned = {
                        check(pendingFile.delete()) { "未能清除待处理评论记录。" }
                    })
                    for (hash in batch) {
                        val href = cache.getValue(hash).jsonObject.str("href")
                        verify(uploader, href, unique.getValue(hash), imageCache, force = true)
                        cache[hash] = buildJsonObject { put("href", href); put("public", true) }
                        save()
                    }
                    println("已公开并匿名验证 ${batch.size} 张图片，临时评论已删除，目标议题已取消订阅。")
                }
                // Previously public images reuse verified local bytes; a new publication always checks anonymous access.
                for (hash in unique.keys - privateHashes.toSet()) {
                    try { verify(uploader, cache.getValue(hash).jsonObject.str("href"), unique.getValue(hash), imageCache) }
                    catch (error: Exception) { cache.remove(hash); save(); throw error }
                }
            } }
            require(sha256(File(report, "index.html").readBytes()) == sourceHash &&
                files.all { (path, file) -> sha256(file.readBytes()) == hashes.getValue(path) } &&
                Json.parseToJsonElement(File(report, "summary.json").readText()).jsonObject == summary) {
                "报告在上传期间发生变化，请重新运行发布命令。"
            }
            val links = hashes.mapValues { cache.getValue(it.value).jsonObject.str("href") }
            val html = imagePattern.replace(source) { match ->
                "${match.groupValues[1]}\"${links.getValue(match.groupValues[3])}\""
            }
            File(report, "public-index.html").writeAtomic(html)
            File(report, "public-images.json").writeAtomic(buildJsonObject {
                put("sourceSha256", sourceHash)
                put("generatedAt", summary.str("generatedAt"))
                put("images", JsonObject(links.mapValues { JsonPrimitive(it.value) }))
                put("sha256", JsonObject(hashes.mapValues { JsonPrimitive(it.value) }))
            }.toString())
            println("公开链接报告已生成：${File(report, "public-index.html").absolutePath}")
            println("${paths.size} 个图片引用，${unique.size} 张不同图片；图片链接清单已保存。")
        }
    }
}

private suspend fun verify(uploader: GithubImageUploader, href: String, file: File, cache: ImageCache, force: Boolean = false) {
    val bytes = file.readBytes()
    val hash = sha256(bytes)
    if (!force && cache.verified(href, hash) != null) return
    repeat(3) { attempt ->
        try { uploader.verifyPublic(href, bytes); cache.store(href, hash, bytes); return }
        catch (error: Exception) { if (attempt == 2) throw error; delay(1_000L * (attempt + 1)) }
    }
}
