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

    // ---- 조직에 만들기 ----

    private val orgRequest = request.copy(owner = GitHubOwner("acme-org", OwnerType.ORGANIZATION))

    /** 조직이 이 앱의 접근을 승인하지 않으면 GitHub 는 403 으로 답한다. 무엇을 하면 되는지 알려 줘야 한다 */
    @Test
    fun `조직 저장소 생성이 403 이면 승인과 권한을 안내한다`() = runTest {
        server.on(HttpMethod.Post, "/orgs/acme-org/repos", HttpStatusCode.Forbidden, """{"message":"Resource not accessible by integration"}""")
        val failure = assertFailsWith<GitHubException.Forbidden> { bootstrapper().bootstrap(orgRequest) { snapshots += it } }
        val message = failure.message.orEmpty()
        assertTrue("acme-org" in message, message)
        assertTrue("승인" in message, message)
        assertTrue("Member privileges" in message, message)
        assertTrue("Resource not accessible by integration" in message, "GitHub 가 준 이유도 남겨야 한다: $message")
    }

    /** 접근 권한이 없는 토큰에는 조직이 아예 없는 것처럼 404 로 보인다 */
    @Test
    fun `조직 저장소 생성이 404 여도 같은 안내를 한다`() = runTest {
        server.on(HttpMethod.Post, "/orgs/acme-org/repos", HttpStatusCode.NotFound, """{"message":"Not Found"}""")
        val failure = assertFailsWith<GitHubException.Forbidden> { bootstrapper().bootstrap(orgRequest) { snapshots += it } }
        assertTrue("acme-org" in failure.message.orEmpty(), failure.message.orEmpty())
    }

    /** 개인 계정의 403 은 그대로 둔다. 조직 안내를 붙이면 엉뚱해진다 */
    @Test
    fun `개인 계정의 403 은 조직 안내를 붙이지 않는다`() = runTest {
        server.on(HttpMethod.Post, "/user/repos", HttpStatusCode.Forbidden, """{"message":"Nope"}""")
        val failure = assertFailsWith<GitHubException.Forbidden> { bootstrapper().bootstrap(request) { snapshots += it } }
        assertFalse("Member privileges" in failure.message.orEmpty(), failure.message.orEmpty())
    }
}
