package io.github.rudtjr1106.switchboard.github

import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class GitHubRepoBootstrapperTest {

    private val server = ScriptedServer()
    private val api = KtorGitHubApi("tok", server.client)
    private fun TestScope.bootstrapper() = GitHubRepoBootstrapper(api, timeSource = testScheduler.timeSource)
    private val snapshots = mutableListOf<BootstrapProgress>()
    private val base = "/repos/acme/config"

    private val request = BootstrapRequest(
        owner = GitHubOwner("acme", OwnerType.USER),
        name = "config",
        description = "앱 원격 설정",
        files = linkedMapOf(
            "schema.json" to "{\"type\":\"object\"}",
            "app-config.json" to "{\"version\":1}",
            ".github/workflows/validate.yml" to "name: config 검증",
            "README.md" to "# config",
        ),
        topics = listOf("switchboard-config"),
        requiredCheck = "validate",
    )

    private fun scriptHappyPath() {
        server.on(HttpMethod.Post, "/user/repos", HttpStatusCode.Created, Fixtures.repoJson(hasPages = false, topics = emptyList()))
        server.on(HttpMethod.Get, "$base/contents/README.md", body = Fixtures.contentJson("readme-sha", "# config\n"))
        request.files.keys.forEach { path ->
            server.on(HttpMethod.Put, "$base/contents/$path", HttpStatusCode.Created, Fixtures.putContentJson("sha-$path", "commit-$path"))
        }
        server.on(HttpMethod.Post, "$base/pages", HttpStatusCode.Created, """{"url":"https://api.github.com$base/pages","status":null}""")
        server.on(HttpMethod.Put, "$base/branches/main/protection", body = """{"url":"https://api.github.com$base/branches/main/protection"}""")
        server.on(HttpMethod.Put, "$base/topics", body = """{"names":["switchboard-config"]}""")
        var buildCalls = 0
        server.on(HttpMethod.Get, "$base/pages/builds/latest") {
            if (buildCalls++ == 0) jsonResponse("""{"message":"Not Found"}""", HttpStatusCode.NotFound) else jsonResponse(Fixtures.pagesBuildJson("built", "first-sha"))
        }
        server.on(HttpMethod.Get, base, body = Fixtures.repoJson(hasPages = true, topics = listOf("switchboard-config")))
    }

    private fun lastProgress(): BootstrapProgress = snapshots.last()

    @Test
    fun `happy path creates, commits, enables pages, protects, tags and waits for the first build`() = runTest {
        scriptHappyPath()

        val repo = bootstrapper().bootstrap(request) { snapshots += it }

        assertEquals(RepoRef("acme", "config"), repo.ref)
        assertTrue(repo.hasPages)
        assertEquals(listOf("switchboard-config"), repo.topics)
        assertTrue(BootstrapStep.entries.all { lastProgress().state(it) == StepState.Done }, lastProgress().toString())

        assertEquals(
            listOf(
                "POST /user/repos",
                "GET $base/contents/README.md",
                "PUT $base/contents/schema.json",
                "PUT $base/contents/app-config.json",
                "PUT $base/contents/.github/workflows/validate.yml",
                "PUT $base/contents/README.md",
                "POST $base/pages",
                "PUT $base/branches/main/protection",
                "PUT $base/topics",
                "GET $base/pages/builds/latest",
                "GET $base/pages/builds/latest",
                "GET $base",
            ),
            server.calls(),
        )

        val create = server.requests(HttpMethod.Post, "/user/repos").single().json()
        assertEquals("config", create["name"]?.jsonPrimitive?.content)
        assertEquals("false", create["private"]?.jsonPrimitive?.content)
        assertEquals("true", create["auto_init"]?.jsonPrimitive?.content)

        val readmePut = server.requests(HttpMethod.Put, "$base/contents/README.md").single().json()
        assertEquals("readme-sha", readmePut["sha"]?.jsonPrimitive?.content)
        assertEquals(GitHubRepoBootstrapper.COMMIT_MESSAGE, readmePut["message"]?.jsonPrimitive?.content)
        assertEquals("main", readmePut["branch"]?.jsonPrimitive?.content)
        val schemaPut = server.requests(HttpMethod.Put, "$base/contents/schema.json").single().json()
        assertFalse(schemaPut.containsKey("sha"))

        val pages = server.requests(HttpMethod.Post, "$base/pages").single().json()
        assertEquals("legacy", pages["build_type"]?.jsonPrimitive?.content)
        assertEquals("main", pages["source"]?.jsonObject?.get("branch")?.jsonPrimitive?.content)
        assertEquals("/", pages["source"]?.jsonObject?.get("path")?.jsonPrimitive?.content)

        val protection = server.requests(HttpMethod.Put, "$base/branches/main/protection").single().json()
        assertEquals(listOf("validate"), protection["required_status_checks"]!!.jsonObject["contexts"]!!.jsonArray.map { it.jsonPrimitive.content })

        val topics = server.requests(HttpMethod.Put, "$base/topics").single().json()
        assertEquals(listOf("switchboard-config"), topics["names"]!!.jsonArray.map { it.jsonPrimitive.content })
    }

    @Test
    fun `branch protection failure is tolerated`() = runTest {
        scriptHappyPath()
        server.on(
            HttpMethod.Put, "$base/branches/main/protection", HttpStatusCode.Forbidden,
            """{"message":"Upgrade to GitHub Pro or make this repository public to enable this feature."}""",
        )

        val repo = bootstrapper().bootstrap(request) { snapshots += it }

        assertTrue(repo.hasPages)
        val failed = assertIs<StepState.Failed>(lastProgress().state(BootstrapStep.PROTECT_BRANCH))
        assertTrue(failed.message.contains("권한이 없어요"), failed.message)
        assertEquals(StepState.Done, lastProgress().state(BootstrapStep.TOPICS))
        assertEquals(StepState.Done, lastProgress().state(BootstrapStep.FIRST_DEPLOY))
        assertTrue(server.calls().contains("PUT $base/topics"))
    }

    @Test
    fun `first deploy timeout is tolerated`() = runTest {
        scriptHappyPath()
        server.on(HttpMethod.Get, "$base/pages/builds/latest", HttpStatusCode.NotFound, """{"message":"Not Found"}""")

        val repo = bootstrapper().bootstrap(request) { snapshots += it }

        assertEquals(RepoRef("acme", "config"), repo.ref)
        val failed = assertIs<StepState.Failed>(lastProgress().state(BootstrapStep.FIRST_DEPLOY))
        assertTrue(failed.message.contains("5분"), failed.message)
    }

    @Test
    fun `existing repository name is reported clearly`() = runTest {
        server.on(
            HttpMethod.Post, "/user/repos", HttpStatusCode.UnprocessableEntity,
            """{"message":"Repository creation failed.","errors":[{"resource":"Repository","code":"custom","field":"name","message":"name already exists on this account"}]}""",
        )

        val error = assertFailsWith<GitHubException.Http> { bootstrapper().bootstrap(request) { snapshots += it } }

        assertEquals(422, error.status)
        assertTrue(error.message!!.contains("이미 있어요"), error.message)
        assertIs<StepState.Failed>(lastProgress().state(BootstrapStep.CREATE_REPO))
        assertEquals(listOf("POST /user/repos"), server.calls())
    }

    @Test
    fun `workflow file rejected for missing scope explains the scope`() = runTest {
        scriptHappyPath()
        server.on(HttpMethod.Put, "$base/contents/.github/workflows/validate.yml", HttpStatusCode.NotFound, """{"message":"Not Found"}""")

        val error = assertFailsWith<GitHubException.Forbidden> { bootstrapper().bootstrap(request) { snapshots += it } }

        assertTrue(error.message!!.contains("workflow"), error.message)
        assertIs<StepState.Failed>(lastProgress().state(BootstrapStep.COMMIT_FILES))
        assertTrue(server.requests(HttpMethod.Post, "$base/pages").isEmpty())
    }

    @Test
    fun `organization owner creates the repository under the org`() = runTest {
        scriptHappyPath()
        server.on(HttpMethod.Post, "/orgs/acme/repos", HttpStatusCode.Created, Fixtures.repoJson(hasPages = false, topics = emptyList()))

        bootstrapper().bootstrap(request.copy(owner = GitHubOwner("acme", OwnerType.ORGANIZATION))) { snapshots += it }

        assertEquals("POST /orgs/acme/repos", server.calls().first())
    }
}
