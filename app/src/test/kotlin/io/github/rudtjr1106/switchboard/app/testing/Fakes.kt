package io.github.rudtjr1106.switchboard.app.testing

import io.github.rudtjr1106.switchboard.github.ApplyProgress
import io.github.rudtjr1106.switchboard.github.ApplyRequest
import io.github.rudtjr1106.switchboard.github.ApplyResult
import io.github.rudtjr1106.switchboard.github.ApplyStep
import io.github.rudtjr1106.switchboard.github.CheckRun
import io.github.rudtjr1106.switchboard.github.CommitResult
import io.github.rudtjr1106.switchboard.github.ConfigRepository
import io.github.rudtjr1106.switchboard.github.DeviceFlowAuthenticator
import io.github.rudtjr1106.switchboard.github.DeviceFlowEvent
import io.github.rudtjr1106.switchboard.github.GhCliTokenProvider
import io.github.rudtjr1106.switchboard.github.GitHubApi
import io.github.rudtjr1106.switchboard.github.GitHubClientFactory
import io.github.rudtjr1106.switchboard.github.GitHubException
import io.github.rudtjr1106.switchboard.github.GitHubOwner
import io.github.rudtjr1106.switchboard.github.GitHubRepo
import io.github.rudtjr1106.switchboard.github.GitHubToken
import io.github.rudtjr1106.switchboard.github.GitHubUser
import io.github.rudtjr1106.switchboard.github.LoadedConfig
import io.github.rudtjr1106.switchboard.github.PagesBuild
import io.github.rudtjr1106.switchboard.github.PagesInfo
import io.github.rudtjr1106.switchboard.github.PullRequest
import io.github.rudtjr1106.switchboard.github.Release
import io.github.rudtjr1106.switchboard.github.RepoBootstrapper
import io.github.rudtjr1106.switchboard.github.RepoFile
import io.github.rudtjr1106.switchboard.github.RepoRef
import io.github.rudtjr1106.switchboard.github.StepState
import io.github.rudtjr1106.switchboard.github.TokenStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.time.Instant

open class FakeConfigRepository(
    override val ref: RepoRef = RepoRef("acme", "app-config"),
    var configText: String,
    var schemaText: String,
    var configSha: String = "sha-config-1",
    var schemaSha: String = "sha-schema-1",
    var failApplyWith: Exception? = null,
) : ConfigRepository {
    override val baseBranch: String = "main"
    val applied = mutableListOf<ApplyRequest>()
    var loads = 0

    open override suspend fun load(): LoadedConfig {
        loads++
        return LoadedConfig(
            config = RepoFile("app-config.json", configSha, configText),
            schema = RepoFile("schema.json", schemaSha, schemaText),
            branch = baseBranch,
            loadedAt = Instant.parse("2026-09-22T00:00:00Z"),
        )
    }

    override suspend fun apply(request: ApplyRequest, onProgress: (ApplyProgress) -> Unit): ApplyResult {
        applied += request
        var progress = ApplyProgress()
        for (step in ApplyStep.entries) {
            progress = progress.copy(steps = progress.steps + (step to StepState.Running))
            onProgress(progress)
            if (step == ApplyStep.PULL_REQUEST) progress = progress.copy(pullRequestUrl = "https://github.com/acme/app-config/pull/7")
            failApplyWith?.let { error ->
                if (step == ApplyStep.VALIDATION) {
                    onProgress(progress.copy(steps = progress.steps + (step to StepState.Failed(error.message ?: "실패"))))
                    throw error
                }
            }
            progress = progress.copy(steps = progress.steps + (step to StepState.Done))
            onProgress(progress)
        }
        val shas = request.files.associate { it.path to "sha-after-${it.path}" }
        request.files.forEach { change ->
            if (change.path == "app-config.json") {
                configText = change.content
                configSha = shas.getValue(change.path)
            }
        }
        return ApplyResult(pullRequestUrl = progress.pullRequestUrl!!, mergeSha = "merge-1", fileShas = shas, deployed = true)
    }
}

class FakeTokenStore(var stored: GitHubToken? = null) : TokenStore {
    override fun load(): GitHubToken? = stored
    override fun save(token: GitHubToken) { stored = token }
    override fun clear() { stored = null }
}

class FakeGhCli(private val installed: Boolean, private val token: GitHubToken? = null) : GhCliTokenProvider {
    override suspend fun isInstalled(): Boolean = installed
    override suspend fun readToken(): GitHubToken? = token
}

class FakeDeviceFlow(private val events: List<DeviceFlowEvent>) : DeviceFlowAuthenticator {
    var requestedClientId: String? = null
    override fun authenticate(clientId: String, scopes: List<String>): Flow<DeviceFlowEvent> = flow {
        requestedClientId = clientId
        events.forEach { emit(it) }
    }
}

/** 필요한 것만 구현하고 나머지는 호출되면 실패한다 */
open class FakeGitHubApi(
    private val user: GitHubUser = GitHubUser("octocat", "Octo Cat", null, "https://github.com/octocat"),
    private val scopes: List<String> = listOf("repo", "workflow", "read:org"),
    private val repos: Map<RepoRef, GitHubRepo> = emptyMap(),
    private val files: Map<Pair<RepoRef, String>, RepoFile> = emptyMap(),
    var unauthorized: Boolean = false,
) : GitHubApi {
    override suspend fun currentUser(): GitHubUser = if (unauthorized) throw GitHubException.Unauthorized() else user
    override suspend fun tokenScopes(): List<String> = scopes
    override suspend fun organizations(): List<GitHubOwner> = emptyList()
    override suspend fun repositoriesWithTopic(topic: String, owners: List<String>): List<GitHubRepo> = repos.values.filter { topic in it.topics }
    override suspend fun repository(ref: RepoRef): GitHubRepo = repos[ref] ?: throw GitHubException.NotFound("저장소 ${ref.fullName}")
    override suspend fun getFile(ref: RepoRef, path: String, branch: String): RepoFile = files[ref to path] ?: throw GitHubException.NotFound("파일 $path")
    override suspend fun branchSha(ref: RepoRef, branch: String): String = unsupported()
    override suspend fun createBranch(ref: RepoRef, branch: String, fromSha: String) = unsupported()
    override suspend fun putFile(ref: RepoRef, path: String, content: String, message: String, branch: String, sha: String?): CommitResult = unsupported()
    override suspend fun createPullRequest(ref: RepoRef, title: String, body: String, head: String, base: String): PullRequest = unsupported()
    override suspend fun checkRuns(ref: RepoRef, commitSha: String, checkName: String): List<CheckRun> = unsupported()
    override suspend fun mergePullRequest(ref: RepoRef, number: Int, commitTitle: String): String = unsupported()
    override suspend fun closePullRequest(ref: RepoRef, number: Int) = unsupported()
    override suspend fun deleteBranch(ref: RepoRef, branch: String) = unsupported()
    override suspend fun latestPagesBuild(ref: RepoRef): PagesBuild? = unsupported()
    override suspend fun pages(ref: RepoRef): PagesInfo? = unsupported()
    override suspend fun createRepository(owner: GitHubOwner, name: String, description: String, isPrivate: Boolean): GitHubRepo = unsupported()
    override suspend fun enablePages(ref: RepoRef, branch: String, path: String) = unsupported()
    override suspend fun protectBranch(ref: RepoRef, branch: String, requiredChecks: List<String>) = unsupported()
    override suspend fun replaceTopics(ref: RepoRef, topics: List<String>) = unsupported()
    override suspend fun latestRelease(ref: RepoRef): Release? = null

    private fun unsupported(): Nothing = error("이 테스트에서는 쓰지 않는 호출")
}

class FakeClientFactory(
    private val api: GitHubApi,
    private val repositories: (RepoRef) -> ConfigRepository,
) : GitHubClientFactory {
    val tokensSeen = mutableListOf<GitHubToken>()
    override fun api(token: GitHubToken): GitHubApi { tokensSeen += token; return api }
    override fun configRepository(api: GitHubApi, ref: RepoRef, baseBranch: String): ConfigRepository = repositories(ref)
    override fun bootstrapper(api: GitHubApi): RepoBootstrapper = error("이 테스트에서는 쓰지 않는 호출")
}
