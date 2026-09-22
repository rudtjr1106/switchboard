package io.github.rudtjr1106.switchboard.github

import java.time.Instant

data class LoadedConfig(
    val config: RepoFile,
    val schema: RepoFile,
    val branch: String,
    val loadedAt: Instant,
)

/** 커밋할 파일 하나. [expectedSha] 는 불러왔을 때의 sha. null 이면 새 파일 */
data class FileChange(val path: String, val content: String, val expectedSha: String?)

data class ApplyRequest(
    val files: List<FileChange>,
    val commitTitle: String,
    val pullRequestBody: String,
    val branchPrefix: String = "config",
    /** 머지 후 GitHub Pages 배포가 끝날 때까지 기다릴지 */
    val waitForDeploy: Boolean = true,
)

enum class ApplyStep(val title: String) {
    CONFLICT_CHECK("충돌 확인"),
    BRANCH("브랜치 만들기"),
    COMMIT("커밋"),
    PULL_REQUEST("PR 열기"),
    VALIDATION("검사 (validate)"),
    MERGE("머지"),
    DEPLOY("배포 (GitHub Pages)"),
}

sealed interface StepState {
    data object Waiting : StepState
    data object Running : StepState
    data object Done : StepState
    data class Failed(val message: String) : StepState
}

data class ApplyProgress(
    val steps: Map<ApplyStep, StepState> = emptyMap(),
    val pullRequestUrl: String? = null,
) {
    fun state(step: ApplyStep): StepState = steps[step] ?: StepState.Waiting
}

data class ApplyResult(
    val pullRequestUrl: String,
    val mergeSha: String,
    /** 경로 → 머지된 파일의 sha. 다음 적용의 충돌 확인에 쓴다 */
    val fileShas: Map<String, String>,
    /** 배포 대기를 건너뛰었거나 배포에서 실패했으면 false. 머지는 이미 끝난 상태다 */
    val deployed: Boolean,
)

/**
 * 설정 저장소 하나에 대한 불러오기·적용
 *
 * 적용은 충돌 확인 → 브랜치 → 커밋 → PR → validate 대기 → 스쿼시 머지 → 브랜치 삭제 → Pages 배포 대기 순서다.
 * 머지 전에 실패하면 PR 을 닫고 브랜치를 지운다. 머지 후 배포에서 실패하면 [GitHubException.PagesFailed] 를 던지되 main 은 이미 바뀐 상태다.
 */
interface ConfigRepository {
    val ref: RepoRef
    val baseBranch: String

    suspend fun load(): LoadedConfig

    suspend fun apply(request: ApplyRequest, onProgress: (ApplyProgress) -> Unit): ApplyResult
}
