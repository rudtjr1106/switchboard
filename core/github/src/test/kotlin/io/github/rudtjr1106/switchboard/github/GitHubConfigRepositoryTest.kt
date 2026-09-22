package io.github.rudtjr1106.switchboard.github

import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.jsonPrimitive
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class GitHubConfigRepositoryTest {

    private val server = ScriptedServer()
    private val api = KtorGitHubApi("tok", server.client)
    private val ref = RepoRef("acme", "config")
    private val clock = Clock.fixed(Instant.parse("2026-09-22T10:11:12Z"), ZoneOffset.UTC)
    private val branch = "config/20260922-101112"
    /** 폴링 마감이 runTest 의 가상 시간을 따르도록 testScheduler.timeSource 를 넘긴다 */
    private fun TestScope.repository() = GitHubConfigRepository(api, ref, "main", clock = clock, timeSource = testScheduler.timeSource)
    private val snapshots = mutableListOf<ApplyProgress>()

    private val request = ApplyRequest(
        files = listOf(FileChange("app-config.json", "{\"version\":1,\"notices\":[]}", expectedSha = "sha-old")),
        commitTitle = "원격 설정: 점검 안내 켜짐",
        pullRequestBody = "### 요청 사유\n\n점검",
    )

    private val base = "/repos/acme/config"

    private fun scriptHappyPath() {
        server.on(HttpMethod.Get, "$base/contents/app-config.json", body = Fixtures.contentJson("sha-old"))
        server.on(HttpMethod.Get, "$base/git/ref/heads/main", body = """{"ref":"refs/heads/main","object":{"sha":"base-sha","type":"commit"}}""")
        server.on(HttpMethod.Post, "$base/git/refs", HttpStatusCode.Created, """{"ref":"refs/heads/$branch","object":{"sha":"base-sha"}}""")
        server.on(HttpMethod.Put, "$base/contents/app-config.json", body = Fixtures.putContentJson("file-sha-1", "commit-1"))
        server.on(HttpMethod.Post, "$base/pulls", HttpStatusCode.Created, """{"number":7,"html_url":"https://github.com/acme/config/pull/7"}""")
        var checkCalls = 0
        server.on(HttpMethod.Get, "$base/commits/commit-1/check-runs") {
            jsonResponse(if (checkCalls++ == 0) Fixtures.checkRunsJson("in_progress", null) else Fixtures.checkRunsJson("completed", "success"))
        }
        server.on(HttpMethod.Put, "$base/pulls/7/merge", body = """{"sha":"merge-sha","merged":true,"message":"Pull Request successfully merged"}""")
        server.on(HttpMethod.Delete, "$base/git/refs/heads/$branch", HttpStatusCode.NoContent, "")
        var buildCalls = 0
        server.on(HttpMethod.Get, "$base/pages/builds/latest") {
            jsonResponse(if (buildCalls++ == 0) Fixtures.pagesBuildJson("building", "merge-sha") else Fixtures.pagesBuildJson("built", "merge-sha"))
        }
        server.on(HttpMethod.Patch, "$base/pulls/7", body = """{"number":7,"state":"closed"}""")
    }

    private fun lastProgress(): ApplyProgress = snapshots.last()

    @Test
    fun `load fetches config and schema from the base branch`() = runTest {
        server.on(HttpMethod.Get, "$base/contents/app-config.json", body = Fixtures.contentJson("c1", "{\"version\":1}"))
        server.on(HttpMethod.Get, "$base/contents/schema.json", body = Fixtures.contentJson("s1", "{\"type\":\"object\"}"))

        val loaded = repository().load()

        assertEquals("c1", loaded.config.sha)
        assertEquals("{\"version\":1}", loaded.config.content)
        assertEquals("s1", loaded.schema.sha)
        assertEquals("main", loaded.branch)
        assertEquals(clock.instant(), loaded.loadedAt)
        assertTrue(server.requests.all { it.query["ref"] == "main" })
    }

    @Test
    fun `happy path runs every step in order and returns merge and file shas`() = runTest {
        scriptHappyPath()

        val result = repository().apply(request) { snapshots += it }

        assertEquals(
            ApplyResult(
                pullRequestUrl = "https://github.com/acme/config/pull/7",
                mergeSha = "merge-sha",
                fileShas = mapOf("app-config.json" to "file-sha-1"),
                deployed = true,
            ),
            result,
        )

        // 모든 단계가 순서대로 Running 이 됐다가 마지막에는 전부 Done 이다
        val firstRunning = snapshots.flatMap { snapshot -> snapshot.steps.filterValues { it == StepState.Running }.keys }.distinct()
        assertEquals(ApplyStep.entries, firstRunning)
        assertTrue(ApplyStep.entries.all { lastProgress().state(it) == StepState.Done }, lastProgress().toString())
        assertEquals("https://github.com/acme/config/pull/7", lastProgress().pullRequestUrl)

        val createBranch = server.requests(HttpMethod.Post, "$base/git/refs").single().json()
        assertEquals("refs/heads/$branch", createBranch["ref"]?.jsonPrimitive?.content)
        assertEquals("base-sha", createBranch["sha"]?.jsonPrimitive?.content)

        val put = server.requests(HttpMethod.Put, "$base/contents/app-config.json").single().json()
        assertEquals(request.commitTitle, put["message"]?.jsonPrimitive?.content)
        assertEquals(branch, put["branch"]?.jsonPrimitive?.content)
        assertEquals("sha-old", put["sha"]?.jsonPrimitive?.content)
        assertEquals(request.files[0].content, Base64.getDecoder().decode(put["content"]!!.jsonPrimitive.content).decodeToString())

        val pull = server.requests(HttpMethod.Post, "$base/pulls").single().json()
        assertEquals(request.commitTitle, pull["title"]?.jsonPrimitive?.content)
        assertEquals(request.pullRequestBody, pull["body"]?.jsonPrimitive?.content)
        assertEquals(branch, pull["head"]?.jsonPrimitive?.content)
        assertEquals("main", pull["base"]?.jsonPrimitive?.content)

        val checks = server.requests(HttpMethod.Get, "$base/commits/commit-1/check-runs")
        assertEquals(2, checks.size)
        assertEquals("validate", checks[0].query["check_name"])

        val merge = server.requests(HttpMethod.Put, "$base/pulls/7/merge").single().json()
        assertEquals("squash", merge["merge_method"]?.jsonPrimitive?.content)
        assertEquals("원격 설정: 점검 안내 켜짐 (#7)", merge["commit_title"]?.jsonPrimitive?.content)

        assertEquals(1, server.requests(HttpMethod.Delete, "$base/git/refs/heads/$branch").size)
        assertEquals(2, server.requests(HttpMethod.Get, "$base/pages/builds/latest").size)
        assertTrue(server.requests(HttpMethod.Patch, "$base/pulls/7").isEmpty(), "merged PR must not be closed")
    }

    @Test
    fun `sha mismatch stops at conflict check and creates nothing`() = runTest {
        scriptHappyPath()
        server.on(HttpMethod.Get, "$base/contents/app-config.json", body = Fixtures.contentJson("sha-someone-else"))

        assertFailsWith<GitHubException.Conflict> { repository().apply(request) { snapshots += it } }

        assertIs<StepState.Failed>(lastProgress().state(ApplyStep.CONFLICT_CHECK))
        assertEquals(StepState.Waiting, lastProgress().state(ApplyStep.BRANCH))
        assertEquals(listOf("GET $base/contents/app-config.json"), server.calls())
    }

    @Test
    fun `a new file that already exists on main is a conflict`() = runTest {
        scriptHappyPath()
        val newFile = request.copy(files = listOf(FileChange("schema.json", "{}", expectedSha = null)))
        server.on(HttpMethod.Get, "$base/contents/schema.json", body = Fixtures.contentJson("exists"))

        assertFailsWith<GitHubException.Conflict> { repository().apply(newFile) { snapshots += it } }
        assertEquals(listOf("GET $base/contents/schema.json"), server.calls())
    }

    @Test
    fun `a new file is committed without sha when it is absent on main`() = runTest {
        scriptHappyPath()
        val newFile = request.copy(files = listOf(FileChange("schema.json", "{}", expectedSha = null)), waitForDeploy = false)
        server.on(HttpMethod.Get, "$base/contents/schema.json", HttpStatusCode.NotFound, """{"message":"Not Found"}""")
        server.on(HttpMethod.Put, "$base/contents/schema.json", HttpStatusCode.Created, Fixtures.putContentJson("schema-sha", "commit-1"))

        val result = repository().apply(newFile) { snapshots += it }

        assertFalse(server.requests(HttpMethod.Put, "$base/contents/schema.json").single().json().containsKey("sha"))
        assertEquals(mapOf("schema.json" to "schema-sha"), result.fileShas)
        assertFalse(result.deployed)
        assertEquals(StepState.Waiting, lastProgress().state(ApplyStep.DEPLOY))
        assertTrue(server.requests(HttpMethod.Get, "$base/pages/builds/latest").isEmpty())
    }

    @Test
    fun `validation failure closes the PR and deletes the branch`() = runTest {
        scriptHappyPath()
        server.on(HttpMethod.Get, "$base/commits/commit-1/check-runs", body = Fixtures.checkRunsJson("completed", "failure"))

        val error = assertFailsWith<GitHubException.ValidationFailed> { repository().apply(request) { snapshots += it } }
        assertEquals("failure", error.conclusion)

        val progress = lastProgress()
        assertEquals(StepState.Done, progress.state(ApplyStep.PULL_REQUEST))
        assertEquals(StepState.Failed(error.message!!), progress.state(ApplyStep.VALIDATION))
        assertEquals(StepState.Waiting, progress.state(ApplyStep.MERGE))
        assertEquals("https://github.com/acme/config/pull/7", progress.pullRequestUrl)

        val close = server.requests(HttpMethod.Patch, "$base/pulls/7").single().json()
        assertEquals("closed", close["state"]?.jsonPrimitive?.content)
        assertEquals(1, server.requests(HttpMethod.Delete, "$base/git/refs/heads/$branch").size)
        assertTrue(server.requests(HttpMethod.Put, "$base/pulls/7/merge").isEmpty())
        // 정리는 실패 지점 뒤에 온다
        assertEquals(listOf("PATCH $base/pulls/7", "DELETE $base/git/refs/heads/$branch"), server.calls().takeLast(2))
    }

    @Test
    fun `failure before the PR exists only deletes the branch`() = runTest {
        scriptHappyPath()
        server.on(HttpMethod.Post, "$base/pulls", HttpStatusCode.UnprocessableEntity, """{"message":"Validation Failed","errors":["No commits between main and $branch"]}""")

        val error = assertFailsWith<GitHubException.Http> { repository().apply(request) { snapshots += it } }
        assertEquals(422, error.status)

        assertTrue(server.requests(HttpMethod.Patch, "$base/pulls/7").isEmpty())
        assertEquals(1, server.requests(HttpMethod.Delete, "$base/git/refs/heads/$branch").size)
        assertIs<StepState.Failed>(lastProgress().state(ApplyStep.PULL_REQUEST))
    }

    @Test
    fun `deploy timeout throws after the merge and keeps the merged state`() = runTest {
        scriptHappyPath()
        // 머지 커밋의 빌드가 끝내 올라오지 않는다
        server.on(HttpMethod.Get, "$base/pages/builds/latest", body = Fixtures.pagesBuildJson("built", "previous-sha"))

        val error = assertFailsWith<GitHubException.TimedOut> { repository().apply(request) { snapshots += it } }
        assertEquals("GitHub Pages 배포", error.step)

        val progress = lastProgress()
        assertEquals(StepState.Done, progress.state(ApplyStep.MERGE))
        assertEquals(StepState.Failed(error.message!!), progress.state(ApplyStep.DEPLOY))
        // 이미 머지됐으므로 PR 을 닫지 않고, 브랜치는 머지 직후 한 번만 지운다
        assertTrue(server.requests(HttpMethod.Patch, "$base/pulls/7").isEmpty())
        assertEquals(1, server.requests(HttpMethod.Delete, "$base/git/refs/heads/$branch").size)
        // 5분 / 5초 = 60번 남짓 폴링했다
        assertTrue(server.requests(HttpMethod.Get, "$base/pages/builds/latest").size >= 60)
    }

    @Test
    fun `errored pages build fails the deploy step`() = runTest {
        scriptHappyPath()
        server.on(HttpMethod.Get, "$base/pages/builds/latest", body = Fixtures.pagesBuildJson("errored", "merge-sha", "Jekyll failed"))

        val error = assertFailsWith<GitHubException.PagesFailed> { repository().apply(request) { snapshots += it } }
        assertTrue(error.message!!.contains("Jekyll failed"), error.message)
        assertEquals(StepState.Done, lastProgress().state(ApplyStep.MERGE))
    }

    @Test
    fun `validation timeout closes the PR`() = runTest {
        scriptHappyPath()
        server.on(HttpMethod.Get, "$base/commits/commit-1/check-runs", body = """{"total_count":0,"check_runs":[]}""")

        val error = assertFailsWith<GitHubException.TimedOut> { repository().apply(request) { snapshots += it } }
        assertEquals("validate 검사", error.step)
        assertEquals(1, server.requests(HttpMethod.Patch, "$base/pulls/7").size)
        assertEquals(1, server.requests(HttpMethod.Delete, "$base/git/refs/heads/$branch").size)
    }
}
