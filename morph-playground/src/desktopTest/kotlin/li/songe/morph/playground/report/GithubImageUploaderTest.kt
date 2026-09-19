package li.songe.morph.playground.report

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import io.ktor.http.content.TextContent
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import java.nio.file.Files
import kotlin.test.*

class GithubImageUploaderTest {
    @Test fun uploadPublicVerifyAndCleanupUseSeparateCredentials() = runBlocking {
        val events = mutableListOf<String>()
        val href = "https://github.com/user-attachments/assets/test-image"
        val bytes = byteArrayOf(1, 2, 3)
        val github = HttpClient(MockEngine { request ->
            assertEquals("test-cookie", request.headers[HttpHeaders.Cookie])
            assertEquals("github.com", request.url.host)
            val response = when (request.url.encodedPath) {
                "/upload/policies/assets" -> {
                    events += "policy"
                    """{"upload_url":"https://storage.example/upload","asset_upload_url":"/upload/assets/1","asset_upload_authenticity_token":"test-token","asset":{"href":"$href"},"form":{"key":"test-key"}}"""
                }
                "/upload/assets/1" -> { events += "finalize"; "{}" }
                else -> {
                    val input = Json.parseToJsonElement((request.body as TextContent).text).jsonObject
                    val name = input.str("persistedQueryName")
                    events += name
                    when (name) {
                        "addCommentMutation" -> {
                            assertEquals(href, input.obj("variables").obj("input").str("body"))
                            """{"data":{"addComment":{"timelineEdge":{"node":{"id":"comment-1"}}}}}"""
                        }
                        "deleteIssueCommentMutation" -> {
                            assertEquals("comment-1", input.obj("variables").obj("input").str("id")); """{"data":{}}"""
                        }
                        else -> {
                            assertEquals("UNSUBSCRIBED", input.obj("variables").obj("input").str("state")); """{"data":{}}"""
                        }
                    }
                }
            }
            respond(response, headers = headersOf(HttpHeaders.ContentType, "application/json"))
        }) { followRedirects = false }
        val anonymous = HttpClient(MockEngine { request ->
            assertNull(request.headers[HttpHeaders.Cookie])
            assertNull(request.headers[HttpHeaders.Authorization])
            if (request.url.host == "storage.example") { events += "storage"; respond("") }
            else { events += "verify"; respond(bytes, headers = headersOf(HttpHeaders.ContentType, "image/png")) }
        })
        val file = Files.createTempFile("morph-image-", ".png").toFile().apply { writeBytes(bytes) }
        try {
            val uploader = GithubImageUploader(github, anonymous, "test-cookie", publicationDelay = { events += "delay" })
            assertEquals(href, uploader.uploadPrivate(file))
            uploader.publish(listOf(href), { events += "persist" }, { events += "clear" })
            uploader.verifyPublic(href, bytes)
            assertEquals(listOf("policy", "storage", "finalize", "addCommentMutation", "persist", "delay",
                "deleteIssueCommentMutation", "updateIssueSubscriptionMutation", "clear", "verify"), events)
        } finally { file.delete(); github.close(); anonymous.close() }
    }

    @Test fun cleanupAttemptsUnsubscribeWhenDeleteFails() = runBlocking {
        val operations = mutableListOf<String>()
        val client = HttpClient(MockEngine { request ->
            val name = Json.parseToJsonElement((request.body as TextContent).text).jsonObject.str("persistedQueryName")
            operations += name
            respond(if (name == "deleteIssueCommentMutation") """{"errors":[{"type":"FORBIDDEN"}]}"""
                else """{"data":{}}""", headers = headersOf(HttpHeaders.ContentType, "application/json"))
        })
        try {
            assertFailsWith<IllegalArgumentException> { GithubImageUploader(client, client, "test").cleanup("comment") }
            assertEquals(listOf("deleteIssueCommentMutation", "updateIssueSubscriptionMutation"), operations)
        } finally { client.close() }
    }

    @Test fun cleanupStillRunsIfPersistingCommentFails() = runBlocking {
        val operations = mutableListOf<String>()
        val client = HttpClient(MockEngine { request ->
            val name = Json.parseToJsonElement((request.body as TextContent).text).jsonObject.str("persistedQueryName")
            operations += name
            respond(if (name == "addCommentMutation") """{"data":{"addComment":{"timelineEdge":{"node":{"id":"comment"}}}}}"""
                else """{"data":{}}""", headers = headersOf(HttpHeaders.ContentType, "application/json"))
        })
        try {
            assertFailsWith<IllegalStateException> {
                GithubImageUploader(client, client, "test", {}).publish(
                    listOf("https://github.com/user-attachments/assets/test"), { error("磁盘写入失败") }, {})
            }
            assertEquals(listOf("addCommentMutation", "deleteIssueCommentMutation", "updateIssueSubscriptionMutation"), operations)
        } finally { client.close() }
    }
}
