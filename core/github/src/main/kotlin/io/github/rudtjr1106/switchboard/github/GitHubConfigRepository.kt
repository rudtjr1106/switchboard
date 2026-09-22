package io.github.rudtjr1106.switchboard.github

import io.github.oshai.kotlinlogging.KotlinLogging
import io.github.rudtjr1106.switchboard.config.templates.RepoTemplates
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import java.time.Clock
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

private val logger = KotlinLogging.logger {}

/**
 * [ConfigRepository] 의 GitHub 구현. iOS 편집 앱의 apply() 와 같은 순서로 진행한다
 *
 * - 머지 전에 실패하면(취소 포함) PR 을 닫고 브랜치를 지운 뒤 예외를 다시 던진다. 재시도마다 브랜치·PR 이 쌓이지 않게 하려는 것이다.
 * - 머지 뒤 배포 대기에서 실패하면 예외를 던지지만 main 은 이미 바뀐 상태다. 호출 쪽은 다시 불러와서 맞추면 된다.
 * - [clock] 은 브랜치 이름(`<prefix>/yyyyMMdd-HHmmss`)과 [LoadedConfig.loadedAt] 에 쓰고, 폴링 마감은 [timeSource] 로 잰다.
 */
class GitHubConfigRepository(
    private val api: GitHubApi,
    override val ref: RepoRef,
    override val baseBranch: String = "main",
    private val validationPollInterval: Duration = 3.seconds,
    private val deployPollInterval: Duration = 5.seconds,
    private val pollTimeout: Duration = 5.minutes,
    private val clock: Clock = Clock.systemDefaultZone(),
    private val timeSource: TimeSource = TimeSource.Monotonic,
) : ConfigRepository {

    override suspend fun load(): LoadedConfig {
        val config = api.getFile(ref, RepoTemplates.CONFIG_PATH, baseBranch)
        val schema = api.getFile(ref, RepoTemplates.SCHEMA_PATH, baseBranch)
        return LoadedConfig(config = config, schema = schema, branch = baseBranch, loadedAt = clock.instant())
    }

    override suspend fun apply(request: ApplyRequest, onProgress: (ApplyProgress) -> Unit): ApplyResult {
        require(request.files.isNotEmpty()) { "바꿀 파일이 없어요." }

        var pullRequestUrl: String? = null
        val tracker = StepTracker<ApplyStep> { steps -> onProgress(ApplyProgress(steps, pullRequestUrl)) }
        var createdBranch: String? = null
        var openedPullRequest: PullRequest? = null
        var merged = false

        try {
            tracker.runStep(ApplyStep.CONFLICT_CHECK) { checkConflicts(request.files) }

            val branch = branchName(request.branchPrefix)
            tracker.runStep(ApplyStep.BRANCH) {
                val baseSha = api.branchSha(ref, baseBranch)
                api.createBranch(ref, branch, baseSha)
                createdBranch = branch
            }

            val commits = tracker.runStep(ApplyStep.COMMIT) { commit(request, branch) }

            val pullRequest = tracker.runStep(ApplyStep.PULL_REQUEST) {
                api.createPullRequest(ref, request.commitTitle, request.pullRequestBody, head = branch, base = baseBranch).also {
                    openedPullRequest = it
                    pullRequestUrl = it.htmlUrl
                }
            }

            tracker.runStep(ApplyStep.VALIDATION) { waitForValidation(commits.lastCommitSha) }

            val mergeSha = tracker.runStep(ApplyStep.MERGE) {
                api.mergePullRequest(ref, pullRequest.number, "${request.commitTitle} (#${pullRequest.number})")
            }
            merged = true

            // 머지됐으니 브랜치는 필요 없다. 못 지워도 결과에는 영향이 없어 실패는 기록만 한다
            withContext(NonCancellable) {
                try {
                    api.deleteBranch(ref, branch)
                } catch (e: GitHubException) {
                    logger.warn(e) { "머지된 브랜치 $branch 를 지우지 못했어요" }
                }
            }

            var deployed = false
            if (request.waitForDeploy) {
                tracker.runStep(ApplyStep.DEPLOY) { waitForPages(mergeSha) }
                deployed = true
            }
            return ApplyResult(
                pullRequestUrl = pullRequest.htmlUrl,
                mergeSha = mergeSha,
                fileShas = commits.fileShas,
                deployed = deployed,
            )
        } catch (e: Throwable) {
            if (!merged) cleanup(openedPullRequest, createdBranch)
            throw e
        }
    }

    /** 불러왔을 때의 sha 와 지금 main 의 sha 가 다르면 누가 먼저 바꾼 것이다. 새 파일은 아직 없어야 한다 */
    private suspend fun checkConflicts(files: List<FileChange>) {
        for (change in files) {
            val currentSha = try {
                api.getFile(ref, change.path, baseBranch).sha
            } catch (e: GitHubException.NotFound) {
                null
            }
            if (currentSha != change.expectedSha) throw GitHubException.Conflict()
        }
    }

    private class Commits(val fileShas: Map<String, String>, val lastCommitSha: String)

    private suspend fun commit(request: ApplyRequest, branch: String): Commits {
        val fileShas = LinkedHashMap<String, String>()
        var lastCommitSha: String? = null
        for (change in request.files) {
            val result = api.putFile(ref, change.path, change.content, request.commitTitle, branch, change.expectedSha)
            fileShas[change.path] = result.fileSha
            lastCommitSha = result.commitSha
        }
        return Commits(fileShas, checkNotNull(lastCommitSha))
    }

    private suspend fun waitForValidation(commitSha: String) {
        pollUntil(validationPollInterval, pollTimeout, "${RepoTemplates.CHECK_NAME} 검사", timeSource) {
            val run = api.checkRuns(ref, commitSha, RepoTemplates.CHECK_NAME).firstOrNull()
            when {
                run == null || !run.isCompleted -> false
                run.isSuccess -> true
                else -> throw GitHubException.ValidationFailed(run.conclusion ?: run.status)
            }
        }
    }

    private suspend fun waitForPages(mergeSha: String) {
        pollUntil(deployPollInterval, pollTimeout, "GitHub Pages 배포", timeSource) {
            val build = api.latestPagesBuild(ref)
            when {
                // 이전 커밋의 빌드일 수 있다. 머지 커밋의 빌드가 올라올 때까지 기다린다
                build == null || build.commitSha != mergeSha -> false
                build.isErrored -> throw GitHubException.PagesFailed(build.errorMessage)
                else -> build.isBuilt
            }
        }
    }

    /** 실패·취소 뒤 정리. 취소 중에도 끝까지 돌아야 하므로 NonCancellable 이다. 닫힌 PR 에서도 검사 로그는 볼 수 있다 */
    private suspend fun cleanup(pullRequest: PullRequest?, branch: String?) = withContext(NonCancellable) {
        if (pullRequest != null) {
            try {
                api.closePullRequest(ref, pullRequest.number)
            } catch (e: GitHubException) {
                logger.warn(e) { "PR #${pullRequest.number} 을 닫지 못했어요" }
            }
        }
        if (branch != null) {
            try {
                api.deleteBranch(ref, branch)
            } catch (e: GitHubException) {
                logger.warn(e) { "브랜치 $branch 를 지우지 못했어요" }
            }
        }
    }

    private fun branchName(prefix: String): String =
        "$prefix/${BRANCH_TIMESTAMP.withZone(clock.zone).format(clock.instant())}"

    companion object {
        private val BRANCH_TIMESTAMP: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss", Locale.ROOT)
    }
}
