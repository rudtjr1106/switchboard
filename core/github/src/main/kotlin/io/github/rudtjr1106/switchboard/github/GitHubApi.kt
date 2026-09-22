package io.github.rudtjr1106.switchboard.github

/**
 * GitHub REST API (https://docs.github.com/rest) 중 이 앱이 쓰는 부분
 *
 * 응답은 max-age=60 으로 캐시되므로 구현은 캐시를 쓰지 않아야 충돌 확인·폴링이 옛 값을 보지 않는다.
 * 모든 실패는 [GitHubException] 으로 던진다.
 */
interface GitHubApi {

    suspend fun currentUser(): GitHubUser

    /** 토큰에 붙은 OAuth 스코프. Fine-grained 토큰은 빈 목록을 돌려준다 */
    suspend fun tokenScopes(): List<String>

    suspend fun organizations(): List<GitHubOwner>

    /** [owners] 각각의 저장소 중 [topic] 이 달린 것. 설정 저장소를 다시 찾을 때 쓴다 */
    suspend fun repositoriesWithTopic(topic: String, owners: List<String>): List<GitHubRepo>

    suspend fun repository(ref: RepoRef): GitHubRepo

    /** @throws GitHubException.NotFound 파일이 없을 때 */
    suspend fun getFile(ref: RepoRef, path: String, branch: String): RepoFile

    suspend fun branchSha(ref: RepoRef, branch: String): String

    suspend fun createBranch(ref: RepoRef, branch: String, fromSha: String)

    /** [sha] 가 null 이면 새 파일. 기존 파일이면 불러왔을 때의 sha 를 넘겨야 하고, 다르면 [GitHubException.Conflict] */
    suspend fun putFile(ref: RepoRef, path: String, content: String, message: String, branch: String, sha: String?): CommitResult

    suspend fun createPullRequest(ref: RepoRef, title: String, body: String, head: String, base: String): PullRequest

    suspend fun checkRuns(ref: RepoRef, commitSha: String, checkName: String): List<CheckRun>

    /** 스쿼시 머지. 머지 커밋 sha 를 돌려준다 */
    suspend fun mergePullRequest(ref: RepoRef, number: Int, commitTitle: String): String

    suspend fun closePullRequest(ref: RepoRef, number: Int)

    suspend fun deleteBranch(ref: RepoRef, branch: String)

    suspend fun latestPagesBuild(ref: RepoRef): PagesBuild?

    suspend fun pages(ref: RepoRef): PagesInfo?

    suspend fun createRepository(owner: GitHubOwner, name: String, description: String, isPrivate: Boolean): GitHubRepo

    suspend fun enablePages(ref: RepoRef, branch: String, path: String = "/")

    /** `main` 을 보호하고 [requiredChecks] 를 필수 검사로 건다 */
    suspend fun protectBranch(ref: RepoRef, branch: String, requiredChecks: List<String>)

    suspend fun replaceTopics(ref: RepoRef, topics: List<String>)

    suspend fun latestRelease(ref: RepoRef): Release?
}

/** 로그인한 토큰으로 [GitHubApi] 등을 만든다. 구현은 Ktor 기반 */
interface GitHubClientFactory {
    fun api(token: GitHubToken): GitHubApi
    fun configRepository(api: GitHubApi, ref: RepoRef, baseBranch: String = "main"): ConfigRepository
    fun bootstrapper(api: GitHubApi): RepoBootstrapper
}
