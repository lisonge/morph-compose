package li.songe.morph.playground.report

import io.ktor.client.HttpClient
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import java.io.File

internal const val ISSUE_ID = "I_kwDOJ3SWBc6viUWN"
private const val REFERER = "https://github.com/gkd-kit/inspect/issues/46"
private const val BACK = "client:$ISSUE_ID:__Issue__backTimelineItems_connection(visibleEventsOnly:true)"
private const val FRONT = "client:$ISSUE_ID:__Issue__frontTimelineItems_connection(visibleEventsOnly:true)"

internal fun JsonObject.obj(key: String) = this[key]?.jsonObject ?: error("响应缺少对象：$key")
internal fun JsonObject.str(key: String) = this[key]?.jsonPrimitive?.content ?: error("响应缺少字段：$key")
internal fun jsonObject(vararg entries: Pair<String, String>) = buildJsonObject { entries.forEach { (k, v) -> put(k, v) } }

/** Cookies are attached only to fixed github.com API requests, never to storage or anonymous verification. */
internal class GithubImageUploader(
    private val github: HttpClient,
    private val anonymous: HttpClient,
    private val cookie: String,
    private val publicationDelay: suspend () -> Unit = { delay(1_000) },
) {
    private fun HttpRequestBuilder.githubHeaders() {
        header(HttpHeaders.Cookie, cookie)
        header(HttpHeaders.Referrer, REFERER)
        header(HttpHeaders.Origin, "https://github.com")
        header(HttpHeaders.UserAgent, "Mozilla/5.0")
        header(HttpHeaders.Accept, "application/json")
    }

    suspend fun uploadPrivate(file: File): String {
        require(file.extension == "png" && file.length() > 0) { "只能上传非空报告图片。" }
        val policy = github.post("https://github.com/upload/policies/assets") {
            githubHeaders()
            header("GitHub-Verified-Fetch", "true")
            header("X-Requested-With", "XMLHttpRequest")
            setBody(multipart(jsonObject("repository_id" to "661952005", "name" to file.name,
                "size" to file.length().toString(), "content_type" to "image/png")))
        }.objectResponse("申请图片上传")
        val uploadUrl = Url(policy.str("upload_url"))
        require(uploadUrl.protocol == URLProtocol.HTTPS) { "存储地址必须使用加密连接。" }
        anonymous.post(uploadUrl) {
            setBody(MultiPartFormDataContent(formData {
                policy.obj("form").forEach { (key, value) -> append(key, value.jsonPrimitive.content) }
                append("file", file.readBytes(), Headers.build {
                    append(HttpHeaders.ContentType, "image/png")
                    append(HttpHeaders.ContentDisposition, "filename=\"${file.name}\"")
                })
            }))
        }.requireSuccess("上传图片数据")
        val finalizeUrl = Url(URLBuilder("https://github.com").takeFrom(policy.str("asset_upload_url")).buildString())
        require(finalizeUrl.protocol == URLProtocol.HTTPS && finalizeUrl.host == "github.com" && finalizeUrl.port == 443) {
            "上传确认地址不属于目标服务。"
        }
        github.put(finalizeUrl) {
            githubHeaders()
            setBody(multipart(jsonObject("authenticity_token" to policy.str("asset_upload_authenticity_token"))))
        }.requireSuccess("确认图片上传")
        return policy.obj("asset").str("href").also(::requireImageUrl)
    }

    /** Callbacks persist the comment id immediately and clear it only after both cleanup operations succeed. */
    suspend fun publish(urls: List<String>, onCreated: (String) -> Unit, onCleaned: () -> Unit) {
        require(urls.isNotEmpty())
        urls.forEach(::requireImageUrl)
        val response = graphql("addCommentMutation", "edafa18ab5734f05c9893cbc92d0dfb1", buildJsonObject {
            put("connections", JsonArray(listOf(JsonPrimitive(BACK))))
            put("input", jsonObject("body" to urls.joinToString("\n"), "subjectId" to ISSUE_ID))
        })
        val id = response.obj("data").obj("addComment").obj("timelineEdge").obj("node").str("id")
        var failure: Throwable? = null
        try {
            onCreated(id)
            publicationDelay()
        } catch (error: Throwable) { failure = error; throw error }
        finally {
            withContext(NonCancellable) {
                try { cleanup(id); onCleaned() }
                catch (error: Throwable) { if (failure != null) failure.addSuppressed(error) else throw error }
            }
        }
    }

    suspend fun cleanup(commentId: String) {
        // Unsubscribe is attempted even when deleting the comment fails.
        var failure: Throwable? = null
        try {
            graphql("deleteIssueCommentMutation", "b0f125991160e607a64d9407db9c01b3", buildJsonObject {
                put("connections", JsonArray(listOf(JsonPrimitive(FRONT), JsonPrimitive(BACK))))
                put("input", jsonObject("id" to commentId))
            }, allowNotFound = true)
        } catch (error: Throwable) { failure = error }
        try {
            graphql("updateIssueSubscriptionMutation", "d0752b2e49295017f67c84f21bfe41a3", buildJsonObject {
                put("input", jsonObject("state" to "UNSUBSCRIBED", "subscribableId" to ISSUE_ID))
            })
        } catch (error: Throwable) { if (failure != null) failure.addSuppressed(error) else failure = error }
        failure?.let { throw it }
    }

    suspend fun verifyPublic(url: String, expected: ByteArray) {
        requireImageUrl(url)
        val response = anonymous.get(url)
        response.requireSuccess("匿名验证公开图片")
        require(response.bodyAsBytes().contentEquals(expected)) { "公开图片内容与本地图片不一致。" }
    }

    private suspend fun graphql(name: String, hash: String, variables: JsonObject, allowNotFound: Boolean = false): JsonObject {
        val result = github.post("https://github.com/_graphql") {
            githubHeaders()
            header("GitHub-Verified-Fetch", "true")
            contentType(ContentType.Text.Plain.withCharset(Charsets.UTF_8))
            setBody(buildJsonObject { put("persistedQueryName", name); put("query", hash); put("variables", variables) }.toString())
        }.objectResponse("执行评论与订阅操作")
        val errors = result["errors"] as? JsonArray
        // Only a typed missing-node result makes a repeated delete idempotent; other errors stay visible.
        require(errors.isNullOrEmpty() || (allowNotFound && errors.all {
            it.jsonObject["type"]?.jsonPrimitive?.content == "NOT_FOUND"
        })) { "评论或订阅操作失败，请检查登录状态与目标议题权限。" }
        require(result["data"] != null || (allowNotFound && !errors.isNullOrEmpty())) { "评论接口响应缺少结果。" }
        return result
    }

    private fun multipart(fields: JsonObject) = MultiPartFormDataContent(formData {
        fields.forEach { (key, value) -> append(key, value.jsonPrimitive.content) }
    })
}

internal fun requireImageUrl(value: String) {
    val url = Url(value)
    require(url.protocol == URLProtocol.HTTPS && url.host == "github.com" && url.encodedPath.startsWith("/user-attachments/assets/")) {
        "响应不是有效的图片附件链接。"
    }
}

private fun HttpResponse.requireSuccess(operation: String) {
    // Never include response bodies, signed upload URLs or cookies in errors.
    require(status.value in 200..299) { "$operation 失败（状态码 ${status.value}）。" }
}
private suspend fun HttpResponse.objectResponse(operation: String): JsonObject {
    requireSuccess(operation)
    return try { Json.parseToJsonElement(bodyAsText()).jsonObject }
    catch (_: Exception) { error("$operation 未返回有效的结构化响应。") }
}
