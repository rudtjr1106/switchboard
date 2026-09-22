package io.github.rudtjr1106.switchboard.github

import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class KtorGitHubApiTest {

    private val server = ScriptedServer()
    private val api = KtorGitHubApi("tok_secret", server.client)
    private val ref = RepoRef("acme", "config")

    @Test
    fun `every call carries the GitHub headers`() = runTest {
        server.on(HttpMethod.Get, "/user", body = Fixtures.USER_JSON)

        assertEquals(Fixtures.user, api.currentUser())

        val headers = server.requests.single().headers
        assertEquals("application/vnd.github+json", headers["Accept"])
        assertEquals("2022-11-28", headers["X-GitHub-Api-Version"])
        assertEquals("Bearer tok_secret", headers["Authorization"])
        assertEquals("Switchboard", headers["User-Agent"])
        assertEquals("no-cache", headers["Cache-Control"])
        assertEquals("https://api.github.com/user", server.requests.single().url.toString())
    }

    @Test
    fun `tokenScopes reads the X-OAuth-Scopes header`() = runTest {
        server.on(HttpMethod.Get, "/user") {
            jsonResponse(Fixtures.USER_JSON, extraHeaders = mapOf("X-OAuth-Scopes" to "repo, workflow, read:org"))
        }
        assertEquals(listOf("repo", "workflow", "read:org"), api.tokenScopes())
    }

    @Test
    fun `getFile decodes base64 that contains line breaks`() = runTest {
        val content = buildString { repeat(6) { append("{\"line\": $it, \"text\": \"안내 문구 가나다라\"}\n") } }
        val encoded = Base64.getMimeEncoder().encodeToString(content.toByteArray())
        assertTrue(encoded.contains("\r\n"), "fixture must span several lines")
        val body = buildJsonObject {
            put("sha", "abc123")
            put("content", encoded)
            put("encoding", "base64")
        }.toString()
        server.on(HttpMethod.Get, "/repos/acme/config/contents/app-config.json", body = body)

        val file = api.getFile(ref, "app-config.json", "main")

        assertEquals("abc123", file.sha)
        assertEquals(content, file.content)
        assertEquals("app-config.json", file.path)
        assertEquals("main", server.requests.single().query["ref"])
    }

    @Test
    fun `404 becomes NotFound with a description of the resource`() = runTest {
        server.on(HttpMethod.Get, "/repos/acme/config/contents/app-config.json", HttpStatusCode.NotFound, """{"message":"Not Found"}""")

        val error = assertFailsWith<GitHubException.NotFound> { api.getFile(ref, "app-config.json", "main") }
        assertEquals("파일 app-config.json", error.what)
    }

    @Test
    fun `401 becomes Unauthorized`() = runTest {
        server.on(HttpMethod.Get, "/user", HttpStatusCode.Unauthorized, """{"message":"Bad credentials"}""")
        assertFailsWith<GitHubException.Unauthorized> { api.currentUser() }
    }

    @Test
    fun `403 with exhausted rate limit becomes RateLimited`() = runTest {
        server.on(HttpMethod.Get, "/user") {
            jsonResponse(
                """{"message":"API rate limit exceeded"}""",
                HttpStatusCode.Forbidden,
                mapOf("X-RateLimit-Remaining" to "0", "X-RateLimit-Reset" to "1700000000"),
            )
        }
        val error = assertFailsWith<GitHubException.RateLimited> { api.currentUser() }
        assertEquals(1_700_000_000L, error.resetEpochSeconds)
    }

    @Test
    fun `403 without rate limit becomes Forbidden with the API message`() = runTest {
        server.on(HttpMethod.Get, "/user") {
            jsonResponse("""{"message":"Resource not accessible"}""", HttpStatusCode.Forbidden, mapOf("X-RateLimit-Remaining" to "42"))
        }
        val error = assertFailsWith<GitHubException.Forbidden> { api.currentUser() }
        assertEquals("Resource not accessible", error.apiMessage)
    }

    @Test
    fun `other failures become Http with message and error details`() = runTest {
        server.on(
            HttpMethod.Post, "/user/repos", HttpStatusCode.UnprocessableEntity,
            """{"message":"Repository creation failed.","errors":[{"resource":"Repository","code":"custom","field":"name","message":"name already exists on this account"}]}""",
        )
        val error = assertFailsWith<GitHubException.Http> {
            api.createRepository(GitHubOwner("octo", OwnerType.USER), "config", "d", isPrivate = false)
        }
        assertEquals(422, error.status)
        assertEquals("Repository creation failed. name already exists on this account", error.apiMessage)
    }

    @Test
    fun `putFile sends the file and maps 409 to Conflict`() = runTest {
        server.on(HttpMethod.Put, "/repos/acme/config/contents/app-config.json", HttpStatusCode.Conflict, """{"message":"app-config.json does not match"}""")

        assertFailsWith<GitHubException.Conflict> {
            api.putFile(ref, "app-config.json", "{\"version\":1}", "원격 설정: 변경", "config/20260922-101112", "old-sha")
        }

        val body = server.requests.single().json()
        assertEquals("원격 설정: 변경", body["message"]?.jsonPrimitive?.content)
        assertEquals("config/20260922-101112", body["branch"]?.jsonPrimitive?.content)
        assertEquals("old-sha", body["sha"]?.jsonPrimitive?.content)
        assertEquals("{\"version\":1}", Base64.getDecoder().decode(body["content"]!!.jsonPrimitive.content).decodeToString())
    }

    @Test
    fun `putFile omits sha for new files and returns both shas`() = runTest {
        server.on(HttpMethod.Put, "/repos/acme/config/contents/schema.json", HttpStatusCode.Created, Fixtures.putContentJson("f1", "c1"))

        val result = api.putFile(ref, "schema.json", "{}", "msg", "main", sha = null)

        assertEquals(CommitResult(commitSha = "c1", fileSha = "f1"), result)
        assertFalse(server.requests.single().json().containsKey("sha"))
    }

    @Test
    fun `latestPagesBuild returns null on 404`() = runTest {
        server.on(HttpMethod.Get, "/repos/acme/config/pages/builds/latest", HttpStatusCode.NotFound, """{"message":"Not Found"}""")
        assertNull(api.latestPagesBuild(ref))
    }

    @Test
    fun `latestPagesBuild maps status commit and error`() = runTest {
        server.on(HttpMethod.Get, "/repos/acme/config/pages/builds/latest", body = Fixtures.pagesBuildJson("errored", "m1", "Page build failed"))
        val build = api.latestPagesBuild(ref)
        assertEquals(PagesBuild(status = "errored", commitSha = "m1", errorMessage = "Page build failed"), build)
        assertTrue(build!!.isErrored)
    }

    @Test
    fun `createRepository posts to the organization endpoint for organizations`() = runTest {
        server.on(HttpMethod.Post, "/orgs/acme/repos", HttpStatusCode.Created, Fixtures.repoJson(owner = "acme", name = "config", hasPages = false, topics = emptyList()))

        val repo = api.createRepository(GitHubOwner("acme", OwnerType.ORGANIZATION), "config", "설정", isPrivate = false)

        assertEquals(RepoRef("acme", "config"), repo.ref)
        assertEquals("main", repo.defaultBranch)
        assertTrue(repo.permissions.admin)
        val body = server.requests.single().json()
        assertEquals("config", body["name"]?.jsonPrimitive?.content)
        assertEquals("설정", body["description"]?.jsonPrimitive?.content)
        assertEquals("false", body["private"]?.jsonPrimitive?.content)
        assertEquals("true", body["auto_init"]?.jsonPrimitive?.content)
    }

    @Test
    fun `createRepository posts to user repos for users`() = runTest {
        server.on(HttpMethod.Post, "/user/repos", HttpStatusCode.Created, Fixtures.repoJson(owner = "octo"))
        api.createRepository(GitHubOwner("octo", OwnerType.USER), "config", "설정", isPrivate = false)
        assertEquals(listOf("POST /user/repos"), server.calls())
    }

    @Test
    fun `enablePages treats 409 as already enabled`() = runTest {
        server.on(HttpMethod.Post, "/repos/acme/config/pages", HttpStatusCode.Conflict, """{"message":"already enabled"}""")
        api.enablePages(ref, "main", "/")
        val body = server.requests.single().json()
        assertEquals("legacy", body["build_type"]?.jsonPrimitive?.content)
        assertEquals("main", body["source"]?.jsonObject?.get("branch")?.jsonPrimitive?.content)
        assertEquals("/", body["source"]?.jsonObject?.get("path")?.jsonPrimitive?.content)
    }

    @Test
    fun `protectBranch sends the required checks`() = runTest {
        server.on(HttpMethod.Put, "/repos/acme/config/branches/main/protection")
        api.protectBranch(ref, "main", listOf("validate"))
        val body = server.requests.single().json()
        val checks = body["required_status_checks"]!!.jsonObject
        assertEquals("false", checks["strict"]?.jsonPrimitive?.content)
        assertEquals(listOf("validate"), checks["contexts"]!!.jsonArray.map { it.jsonPrimitive.content })
        assertEquals("false", body["enforce_admins"]?.jsonPrimitive?.content)
        assertTrue(body.containsKey("required_pull_request_reviews"))
        assertTrue(body.containsKey("restrictions"))
    }

    @Test
    fun `repositoriesWithTopic dedupes across owners and skips owners that 422`() = runTest {
        server.on(HttpMethod.Get, "/search/repositories") { request ->
            when (request.query["q"]) {
                "topic:switchboard-config user:acme" -> jsonResponse("""{"items":[${Fixtures.repoJson("acme", "config")}]}""")
                "topic:switchboard-config user:ghost" -> jsonResponse("""{"message":"Validation Failed"}""", HttpStatusCode.UnprocessableEntity)
                "topic:switchboard-config org:ghost" -> jsonResponse("""{"message":"Validation Failed"}""", HttpStatusCode.UnprocessableEntity)
                "topic:switchboard-config user:org1" -> jsonResponse("""{"items":[]}""")
                "topic:switchboard-config org:org1" -> jsonResponse("""{"items":[${Fixtures.repoJson("acme", "config")},${Fixtures.repoJson("org1", "cfg")}]}""")
                else -> jsonResponse("""{"message":"unexpected q=${request.query["q"]}"}""", HttpStatusCode.InternalServerError)
            }
        }

        val repos = api.repositoriesWithTopic("switchboard-config", listOf("acme", "ghost", "org1"))

        assertEquals(listOf("acme/config", "org1/cfg"), repos.map { it.ref.fullName })
    }

    @Test
    fun `malformed JSON becomes InvalidResponse`() = runTest {
        server.on(HttpMethod.Get, "/user", body = "<html>oops</html>")
        assertFailsWith<GitHubException.InvalidResponse> { api.currentUser() }
    }
}
