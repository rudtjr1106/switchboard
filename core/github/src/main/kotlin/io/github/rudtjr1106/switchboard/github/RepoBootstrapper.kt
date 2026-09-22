package io.github.rudtjr1106.switchboard.github

enum class BootstrapStep(val title: String) {
    CREATE_REPO("저장소 만들기"),
    COMMIT_FILES("설정 파일 올리기"),
    ENABLE_PAGES("GitHub Pages 켜기"),
    PROTECT_BRANCH("main 보호 규칙"),
    TOPICS("주제(topic) 달기"),
    FIRST_DEPLOY("첫 배포 확인"),
}

data class BootstrapProgress(val steps: Map<BootstrapStep, StepState> = emptyMap()) {
    fun state(step: BootstrapStep): StepState = steps[step] ?: StepState.Waiting
}

data class BootstrapRequest(
    val owner: GitHubOwner,
    val name: String,
    val description: String,
    /** 경로 → 내용. RepoTemplates.files() 로 만든다 */
    val files: Map<String, String>,
    val topics: List<String>,
    val requiredCheck: String,
)

/**
 * 설정 저장소를 새로 만들고 바로 쓸 수 있게 세팅한다 (CodeRabbit 식 온보딩)
 *
 * GitHub Pages 는 공개 저장소에서만 무료라 저장소는 공개로 만든다.
 * 브랜치 보호는 무료 플랜의 공개 저장소에서만 되므로 실패해도 전체를 실패로 보지 않고 [StepState.Failed] 로만 표시한다.
 */
interface RepoBootstrapper {
    suspend fun bootstrap(request: BootstrapRequest, onProgress: (BootstrapProgress) -> Unit): GitHubRepo
}
