package io.github.rudtjr1106.switchboard.github

import io.github.oshai.kotlinlogging.KotlinLogging
import io.github.rudtjr1106.switchboard.config.templates.RepoTemplates
import kotlinx.coroutines.delay
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

private val logger = KotlinLogging.logger {}

/**
 * [RepoBootstrapper] 의 GitHub 구현
 *
 * 저장소 생성·파일 커밋·Pages 켜기·topic 은 실패하면 멈춘다(이미 만든 저장소는 그대로 남으므로 사용자가 이어서 손볼 수 있다).
 * main 보호와 첫 배포 확인은 실패해도 [StepState.Failed] 로만 남기고 저장소를 돌려준다.
 */
class GitHubRepoBootstrapper(
    private val api: GitHubApi,
    private val deployPollInterval: Duration = 5.seconds,
    private val deployTimeout: Duration = 5.minutes,
    private val readyRetryDelay: Duration = 1.seconds,
    private val timeSource: TimeSource = TimeSource.Monotonic,
) : RepoBootstrapper {

    override suspend fun bootstrap(request: BootstrapRequest, onProgress: (BootstrapProgress) -> Unit): GitHubRepo {
        val tracker = StepTracker<BootstrapStep> { steps -> onProgress(BootstrapProgress(steps)) }

        val created = tracker.runStep(BootstrapStep.CREATE_REPO) { createRepository(request) }
        val ref = created.ref
        val branch = created.defaultBranch

        tracker.runStep(BootstrapStep.COMMIT_FILES) { commitFiles(ref, branch, request.files) }
        tracker.runStep(BootstrapStep.ENABLE_PAGES) { api.enablePages(ref, branch, "/") }
        tracker.tolerate(BootstrapStep.PROTECT_BRANCH) { api.protectBranch(ref, branch, listOf(request.requiredCheck)) }
        tracker.runStep(BootstrapStep.TOPICS) { api.replaceTopics(ref, request.topics) }
        tracker.tolerate(BootstrapStep.FIRST_DEPLOY) { waitForFirstBuild(ref) }

        // hasPages·topics 가 반영된 값을 돌려준다. 다시 못 읽으면 만들 때 받은 값이라도 준다
        return try {
            api.repository(ref)
        } catch (e: GitHubException) {
            logger.warn(e) { "${ref.fullName} 을 다시 읽지 못해 생성 시점의 정보를 돌려줘요" }
            created
        }
    }

    private suspend fun createRepository(request: BootstrapRequest): GitHubRepo = try {
        // GitHub Pages 는 공개 저장소에서만 무료다
        api.createRepository(request.owner, request.name, request.description, isPrivate = false)
    } catch (e: GitHubException.Http) {
        if (e.status == 422 && e.apiMessage?.contains("already exists", ignoreCase = true) == true) {
            throw GitHubException.Http(
                422,
                "${request.owner.login}/${request.name} 저장소가 이미 있어요. 다른 이름을 쓰거나 기존 저장소를 여세요.",
            )
        }
        throw e
    }

    private suspend fun commitFiles(ref: RepoRef, branch: String, files: Map<String, String>) {
        val readmeSha = existingReadmeSha(ref, branch)
        for ((path, content) in files) {
            // auto_init 이 README 를 이미 만들어 두었으므로 그 sha 를 넘겨야 덮어쓸 수 있다
            val sha = if (path == RepoTemplates.README_PATH) readmeSha else null
            try {
                api.putFile(ref, path, content, COMMIT_MESSAGE, branch, sha)
            } catch (e: GitHubException) {
                if (path.startsWith(WORKFLOW_DIR) && (e is GitHubException.NotFound || e is GitHubException.Forbidden)) {
                    // workflow 스코프가 없는 토큰은 워크플로 파일을 못 올리고 GitHub 는 404 나 403 으로 답한다
                    throw GitHubException.Forbidden(
                        "토큰에 workflow 스코프가 없어서 $path 을(를) 올리지 못했어요. GitHub 에 다시 로그인하면서 workflow 권한을 허용해 주세요.",
                    )
                }
                throw e
            }
        }
    }

    /**
     * auto_init 이 만든 README 의 sha. 저장소를 만든 직후에는 첫 커밋이 잠깐 안 보일 수 있어(404·409) 몇 번 더 물어본다
     *
     * 끝까지 없으면 null 을 주고 README 는 새 파일로 올린다.
     */
    private suspend fun existingReadmeSha(ref: RepoRef, branch: String): String? {
        repeat(READY_ATTEMPTS) { attempt ->
            try {
                return api.getFile(ref, RepoTemplates.README_PATH, branch).sha
            } catch (e: GitHubException.NotFound) {
                logger.debug { "README 가 아직 안 보여요 (${attempt + 1}/$READY_ATTEMPTS)" }
            } catch (e: GitHubException.Http) {
                if (e.status != 409) throw e
                logger.debug { "저장소가 아직 비어 있어요 (${attempt + 1}/$READY_ATTEMPTS)" }
            }
            if (attempt < READY_ATTEMPTS - 1) delay(readyRetryDelay)
        }
        return null
    }

    private suspend fun waitForFirstBuild(ref: RepoRef) {
        pollUntil(deployPollInterval, deployTimeout, "GitHub Pages 첫 배포", timeSource) {
            val build = api.latestPagesBuild(ref)
            when {
                // Pages 를 막 켜면 빌드가 아직 없어 404 가 온다
                build == null -> false
                build.isErrored -> throw GitHubException.PagesFailed(build.errorMessage)
                else -> build.isBuilt
            }
        }
    }

    /** 실패해도 진행을 멈추지 않는 단계. 상태는 [StepState.Failed] 로 남는다 */
    private suspend fun StepTracker<BootstrapStep>.tolerate(step: BootstrapStep, block: suspend () -> Unit) {
        try {
            runStep(step, block)
        } catch (e: GitHubException) {
            logger.warn(e) { "${step.title} 실패, 계속 진행해요" }
        }
    }

    companion object {
        const val COMMIT_MESSAGE = "chore: 스위치보드 초기 설정"
        private const val WORKFLOW_DIR = ".github/workflows/"
        private const val READY_ATTEMPTS = 5
    }
}
