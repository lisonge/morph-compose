package li.songe.morph.playground.report

import io.ktor.http.Url
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/** Shared by anonymous downloads and verified uploads; attachment IDs locate files and content hashes validate their bytes. */
internal class ImageCache(private val directory: File) {
    private fun target(url: String): File {
        requireImageUrl(url)
        val id = Url(url).encodedPath.removePrefix("/user-attachments/assets/")
        require(Regex("[A-Za-z0-9-]+").matches(id)) { "无效的图片附件编号。" }
        return File(directory, "$id.png")
    }

    fun verified(url: String, hash: String): File? {
        require(Regex("[0-9a-f]{64}").matches(hash))
        val file = target(url)
        if (!file.isFile) return null
        if (sha256(file.readBytes()) == hash) return file
        check(file.delete()) { "无法移除损坏的图片缓存。" }
        return null
    }

    fun store(url: String, hash: String, bytes: ByteArray): File {
        require(sha256(bytes) == hash) { "图片摘要不匹配，拒绝写入缓存。" }
        directory.mkdirs()
        val target = target(url)
        val temp = Files.createTempFile(directory.toPath(), "download-", ".tmp")
        try {
            Files.write(temp, bytes)
            Files.move(temp, target.toPath(), StandardCopyOption.REPLACE_EXISTING)
        } finally { Files.deleteIfExists(temp) }
        return target
    }
}
