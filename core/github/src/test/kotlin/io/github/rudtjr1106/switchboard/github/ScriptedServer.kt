package io.github.rudtjr1106.switchboard.github

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.Parameters
import io.ktor.http.Url
import io.ktor.http.content.OutgoingContent
import io.ktor.http.headers
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import java.net.URLDecoder

/** 테스트용 GitHub. 메서드+경로로 응답을 정하고 받은 요청을 순서대로 기록한다 */
class ScriptedServer {

    class RecordedRequest(val method: HttpMethod, val url: Url, val headers: Headers, val body: String) {
        val path: String get() = url.encodedPath
        val query: Parameters get() = url.parameters
        fun json(): JsonObject = Json.parseToJsonElement(body).jsonObject
        /** application/x-www-form-urlencoded 본문. Ktor 의 파서는 `+` 를 공백으로 안 풀어서 직접 푼다 */
        fun form(): Map<String, String> = body.split('&').filter { it.isNotEmpty() }.associate { pair ->
            val name = pair.substringBefore('=')
            val value = pair.substringAfter('=', "")
            URLDecoder.decode(name, Charsets.UTF_8) to URLDecoder.decode(value, Charsets.UTF_8)
        }
        override fun toString(): String = "${method.value} $path"
    }

    private class Route(val method: HttpMethod, val path: String, val handler: Handler)

    val requests = mutableListOf<RecordedRequest>()
    private val routes = mutableListOf<Route>()

    /** 같은 메서드+경로를 다시 등록하면 나중 것이 이긴다. 기본 시나리오 위에 실패 케이스를 덮어쓸 때 쓴다 */
    fun on(method: HttpMethod, path: String, handler: Handler) {
        routes += Route(method, path, handler)
    }

    fun on(method: HttpMethod, path: String, status: HttpStatusCode = HttpStatusCode.OK, body: String = "{}") {
        on(method, path) { jsonResponse(body, status) }
    }

    val engine: MockEngine = MockEngine { request ->
        val recorded = RecordedRequest(request.method, request.url, request.headers, request.bodyText())
        requests += recorded
        val route = routes.lastOrNull { it.method == request.method && it.path == recorded.path }
        route?.handler?.invoke(this, recorded)
            ?: jsonResponse("""{"message":"unexpected $recorded"}""", HttpStatusCode.InternalServerError)
    }

    val client: HttpClient = GitHubHttp.client(engine)

    fun requests(method: HttpMethod, path: String): List<RecordedRequest> =
        requests.filter { it.method == method && it.path == path }

    fun calls(): List<String> = requests.map { it.toString() }

    private fun HttpRequestData.bodyText(): String = when (val content = body) {
        is OutgoingContent.ByteArrayContent -> content.bytes().decodeToString()
        else -> ""
    }
}

typealias Handler = suspend MockRequestHandleScope.(ScriptedServer.RecordedRequest) -> HttpResponseData

fun MockRequestHandleScope.jsonResponse(
    body: String,
    status: HttpStatusCode = HttpStatusCode.OK,
    extraHeaders: Map<String, String> = emptyMap(),
): HttpResponseData = respond(
    body,
    status,
    headers {
        append(HttpHeaders.ContentType, "application/json; charset=utf-8")
        extraHeaders.forEach { (name, value) -> append(name, value) }
    },
)

object Fixtures {
    const val USER_JSON = """{"login":"octo","name":"Octo Cat","avatar_url":"https://a/octo.png","html_url":"https://github.com/octo"}"""
    val user = GitHubUser(login = "octo", name = "Octo Cat", avatarUrl = "https://a/octo.png", htmlUrl = "https://github.com/octo")

    fun repoJson(owner: String = "acme", name: String = "config", hasPages: Boolean = true, topics: List<String> = listOf("switchboard-config")): String =
        """{"name":"$name","owner":{"login":"$owner","type":"User"},"html_url":"https://github.com/$owner/$name","default_branch":"main","private":false,"description":"설정","topics":[${topics.joinToString(",") { "\"$it\"" }}],"has_pages":$hasPages,"permissions":{"push":true,"admin":true}}"""

    fun contentJson(sha: String, content: String = "{}"): String =
        """{"sha":"$sha","content":"${java.util.Base64.getEncoder().encodeToString(content.toByteArray())}","encoding":"base64"}"""

    fun putContentJson(fileSha: String, commitSha: String): String =
        """{"content":{"sha":"$fileSha"},"commit":{"sha":"$commitSha"}}"""

    fun checkRunsJson(status: String, conclusion: String?): String =
        """{"total_count":1,"check_runs":[{"name":"validate","status":"$status","conclusion":${conclusion?.let { "\"$it\"" } ?: "null"}}]}"""

    fun pagesBuildJson(status: String, commit: String, error: String? = null): String =
        """{"status":"$status","commit":"$commit","error":{"message":${error?.let { "\"$it\"" } ?: "null"}}}"""
}
