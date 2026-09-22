package io.github.rudtjr1106.switchboard.github

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.request.url
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.content.TextContent
import io.ktor.http.encodeURLPathPart
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import java.io.IOException
import java.nio.channels.UnresolvedAddressException
import java.util.Base64

private val logger = KotlinLogging.logger {}

/**
 * [GitHubApi] 의 Ktor 구현
 *
 * 헤더·오류 변환·JSON 해석은 [send] 한 곳에서 하고 각 메서드는 경로와 본문만 만든다.
 * GitHub 응답은 max-age=60 이라 매 요청에 Cache-Control: no-cache 를 붙여 충돌 확인·폴링이 옛 값을 보지 않게 한다.
 */
class KtorGitHubApi(
    private val token: String,
    private val client: HttpClient,
    private val baseUrl: String = DEFAULT_BASE_URL,
) : GitHubApi {

    private val json = GitHubHttp.json

    override suspend fun currentUser(): GitHubUser =
        send(HttpMethod.Get, "/user", notFound = "사용자 정보").parse<UserDto>().toUser()

    override suspend fun tokenScopes(): List<String> {
        val response = send(HttpMethod.Get, "/user", notFound = "사용자 정보")
        // Fine-grained 토큰에는 이 헤더가 없다
        return response.headers["X-OAuth-Scopes"].orEmpty()
            .split(',')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
    }

    override suspend fun organizations(): List<GitHubOwner> =
        send(HttpMethod.Get, "/user/orgs", query = mapOf("per_page" to "100"), notFound = "조직 목록")
            .parse<List<OrganizationDto>>()
            .map { GitHubOwner(login = it.login, type = OwnerType.ORGANIZATION, avatarUrl = it.avatarUrl) }

    override suspend fun repositoriesWithTopic(topic: String, owners: List<String>): List<GitHubRepo> {
        val found = LinkedHashMap<String, GitHubRepo>()
        for (owner in owners.distinct()) {
            // user: 는 개인 계정 기준이라 조직은 비어 있을 수 있다. 그때만 org: 로 한 번 더 찾는다
            val repos = searchByTopic(topic, "user:$owner").ifEmpty { searchByTopic(topic, "org:$owner") }
            repos.forEach { found.putIfAbsent(it.ref.fullName, it) }
        }
        return found.values.toList()
    }

    private suspend fun searchByTopic(topic: String, ownerQualifier: String): List<GitHubRepo> = try {
        send(
            HttpMethod.Get,
            "/search/repositories",
            query = mapOf("q" to "topic:$topic $ownerQualifier", "per_page" to "100"),
            notFound = "저장소 검색 결과",
        ).parse<SearchDto>().items.map { it.toRepo() }
    } catch (e: GitHubException.Http) {
        // 없는 계정이나 볼 수 없는 조직은 422 로 온다. 한 소유자 때문에 나머지 목록까지 못 보여주면 안 된다
        if (e.status == 422) {
            logger.info { "저장소 검색 건너뜀 ($ownerQualifier): ${e.apiMessage}" }
            emptyList()
        } else {
            throw e
        }
    }

    override suspend fun repository(ref: RepoRef): GitHubRepo =
        send(HttpMethod.Get, repoPath(ref), notFound = "저장소 ${ref.fullName}").parse<RepoDto>().toRepo()

    override suspend fun getFile(ref: RepoRef, path: String, branch: String): RepoFile {
        val dto = send(
            HttpMethod.Get,
            "${repoPath(ref)}/contents/${encodePath(path)}",
            query = mapOf("ref" to branch),
            notFound = "파일 $path",
        ).parse<ContentDto>()
        if (dto.encoding != null && dto.encoding != "base64") {
            // 1MB 를 넘는 파일은 content 가 비고 encoding 이 none 이다. 설정 파일이 그럴 일은 없지만 조용히 빈 파일로 보이면 안 된다
            throw GitHubException.InvalidResponse("파일 $path 의 인코딩(${dto.encoding})을 읽을 수 없어요.")
        }
        return RepoFile(path = path, sha = dto.sha, content = decodeBase64(dto.content.orEmpty()))
    }

    override suspend fun branchSha(ref: RepoRef, branch: String): String =
        send(HttpMethod.Get, "${repoPath(ref)}/git/ref/heads/${encodePath(branch)}", notFound = "브랜치 $branch")
            .parse<ReferenceDto>().target.sha

    override suspend fun createBranch(ref: RepoRef, branch: String, fromSha: String) {
        val body = buildJsonObject {
            put("ref", "refs/heads/$branch")
            put("sha", fromSha)
        }
        send(HttpMethod.Post, "${repoPath(ref)}/git/refs", body, notFound = "저장소 ${ref.fullName}")
    }

    override suspend fun putFile(
        ref: RepoRef,
        path: String,
        content: String,
        message: String,
        branch: String,
        sha: String?,
    ): CommitResult {
        val body = buildJsonObject {
            put("message", message)
            put("content", encodeBase64(content))
            put("branch", branch)
            if (sha != null) put("sha", sha)
        }
        val dto = try {
            send(HttpMethod.Put, "${repoPath(ref)}/contents/${encodePath(path)}", body, notFound = "파일 $path")
                .parse<PutContentDto>()
        } catch (e: GitHubException.Http) {
            // sha 가 어긋나면 409, 새 파일인데 이미 있으면 422. 둘 다 "누가 먼저 바꿨다" 는 뜻이다
            if (e.status == 409 || e.status == 422) throw GitHubException.Conflict() else throw e
        }
        return CommitResult(commitSha = dto.commit.sha, fileSha = dto.content.sha)
    }

    override suspend fun createPullRequest(ref: RepoRef, title: String, body: String, head: String, base: String): PullRequest {
        val payload = buildJsonObject {
            put("title", title)
            put("body", body)
            put("head", head)
            put("base", base)
        }
        val dto = send(HttpMethod.Post, "${repoPath(ref)}/pulls", payload, notFound = "저장소 ${ref.fullName}")
            .parse<PullRequestDto>()
        return PullRequest(number = dto.number, htmlUrl = dto.htmlUrl)
    }

    override suspend fun checkRuns(ref: RepoRef, commitSha: String, checkName: String): List<CheckRun> =
        send(
            HttpMethod.Get,
            "${repoPath(ref)}/commits/${encodePath(commitSha)}/check-runs",
            query = mapOf("check_name" to checkName),
            notFound = "커밋 $commitSha",
        ).parse<CheckRunsDto>().checkRuns.map { CheckRun(name = it.name, status = it.status, conclusion = it.conclusion) }

    override suspend fun mergePullRequest(ref: RepoRef, number: Int, commitTitle: String): String {
        val body = buildJsonObject {
            put("merge_method", "squash")
            put("commit_title", commitTitle)
        }
        return send(HttpMethod.Put, "${repoPath(ref)}/pulls/$number/merge", body, notFound = "PR #$number")
            .parse<ShaDto>().sha
    }

    override suspend fun closePullRequest(ref: RepoRef, number: Int) {
        val body = buildJsonObject { put("state", "closed") }
        send(HttpMethod.Patch, "${repoPath(ref)}/pulls/$number", body, notFound = "PR #$number")
    }

    override suspend fun deleteBranch(ref: RepoRef, branch: String) {
        send(HttpMethod.Delete, "${repoPath(ref)}/git/refs/heads/${encodePath(branch)}", notFound = "브랜치 $branch")
    }

    override suspend fun latestPagesBuild(ref: RepoRef): PagesBuild? {
        val dto = sendOrNull(HttpMethod.Get, "${repoPath(ref)}/pages/builds/latest")?.parse<PagesBuildDto>() ?: return null
        return PagesBuild(status = dto.status ?: "unknown", commitSha = dto.commit, errorMessage = dto.error?.message)
    }

    override suspend fun pages(ref: RepoRef): PagesInfo? {
        val dto = sendOrNull(HttpMethod.Get, "${repoPath(ref)}/pages")?.parse<PagesDto>() ?: return null
        return PagesInfo(url = dto.htmlUrl, status = dto.status, sourceBranch = dto.source?.branch)
    }

    override suspend fun createRepository(owner: GitHubOwner, name: String, description: String, isPrivate: Boolean): GitHubRepo {
        val path = when (owner.type) {
            OwnerType.USER -> "/user/repos"
            OwnerType.ORGANIZATION -> "/orgs/${owner.login.encodeURLPathPart()}/repos"
        }
        val body = buildJsonObject {
            put("name", name)
            put("description", description)
            put("private", isPrivate)
            // 첫 커밋이 있어야 기본 브랜치가 생겨 바로 파일을 올릴 수 있다
            put("auto_init", true)
        }
        return send(HttpMethod.Post, path, body, notFound = "소유자 ${owner.login}").parse<RepoDto>().toRepo()
    }

    override suspend fun enablePages(ref: RepoRef, branch: String, path: String) {
        val body = buildJsonObject {
            put("build_type", "legacy")
            putJsonObject("source") {
                put("branch", branch)
                put("path", path)
            }
        }
        try {
            send(HttpMethod.Post, "${repoPath(ref)}/pages", body, notFound = "저장소 ${ref.fullName}")
        } catch (e: GitHubException.Http) {
            // 이미 켜져 있으면 409. 원하는 상태이므로 성공으로 본다
            if (e.status == 409) logger.info { "${ref.fullName} 은 GitHub Pages 가 이미 켜져 있어요" } else throw e
        }
    }

    override suspend fun protectBranch(ref: RepoRef, branch: String, requiredChecks: List<String>) {
        val body = buildJsonObject {
            putJsonObject("required_status_checks") {
                put("strict", false)
                putJsonArray("contexts") { requiredChecks.forEach { add(it) } }
            }
            put("enforce_admins", false)
            put("required_pull_request_reviews", JsonNull)
            put("restrictions", JsonNull)
        }
        send(HttpMethod.Put, "${repoPath(ref)}/branches/${encodePath(branch)}/protection", body, notFound = "브랜치 $branch")
    }

    override suspend fun replaceTopics(ref: RepoRef, topics: List<String>) {
        val body = buildJsonObject { putJsonArray("names") { topics.forEach { add(it) } } }
        send(HttpMethod.Put, "${repoPath(ref)}/topics", body, notFound = "저장소 ${ref.fullName}")
    }

    override suspend fun latestRelease(ref: RepoRef): Release? {
        val dto = sendOrNull(HttpMethod.Get, "${repoPath(ref)}/releases/latest")?.parse<ReleaseDto>() ?: return null
        return Release(
            tagName = dto.tagName,
            name = dto.name,
            htmlUrl = dto.htmlUrl,
            body = dto.body,
            assets = dto.assets.map { ReleaseAsset(name = it.name, downloadUrl = it.downloadUrl, sizeBytes = it.size) },
        )
    }

    // ---- 공통 ----

    private class ApiResponse(val status: Int, val headers: Headers, val body: String)

    /** 성공(2xx) 응답만 돌려주고 나머지는 [GitHubException] 으로 던진다. [notFound] 는 404 일 때 사용자에게 보여줄 대상 이름 */
    private suspend fun send(
        method: HttpMethod,
        path: String,
        body: JsonObject? = null,
        query: Map<String, String> = emptyMap(),
        notFound: String? = null,
    ): ApiResponse {
        val response = try {
            client.request {
                this.method = method
                url("$baseUrl$path")
                query.forEach { (name, value) -> parameter(name, value) }
                header(HttpHeaders.Accept, "application/vnd.github+json")
                header("X-GitHub-Api-Version", "2022-11-28")
                header(HttpHeaders.Authorization, "Bearer $token")
                header(HttpHeaders.UserAgent, "Switchboard")
                header(HttpHeaders.CacheControl, "no-cache")
                if (body != null) {
                    setBody(TextContent(json.encodeToString(JsonObject.serializer(), body), ContentType.Application.Json))
                }
            }
        } catch (e: IOException) {
            throw GitHubException.Network(e)
        } catch (e: UnresolvedAddressException) {
            throw GitHubException.Network(e)
        }
        val text = try {
            response.bodyAsText()
        } catch (e: IOException) {
            throw GitHubException.Network(e)
        }
        val status = response.status.value
        if (status in 200..299) return ApiResponse(status, response.headers, text)
        throw toException(status, response.headers, text, notFound)
    }

    /** 404 를 null 로 돌려준다. "아직 없음" 이 정상인 조회(Pages 빌드, 최신 릴리즈)에 쓴다 */
    private suspend fun sendOrNull(method: HttpMethod, path: String): ApiResponse? = try {
        send(method, path)
    } catch (e: GitHubException.NotFound) {
        null
    }

    private fun toException(status: Int, headers: Headers, body: String, notFound: String?): GitHubException {
        val message = errorMessage(body)
        return when (status) {
            401 -> GitHubException.Unauthorized()
            403 -> if (headers["X-RateLimit-Remaining"] == "0") {
                GitHubException.RateLimited(headers["X-RateLimit-Reset"]?.toLongOrNull())
            } else {
                GitHubException.Forbidden(message)
            }
            404 -> GitHubException.NotFound(notFound ?: "요청한 항목")
            429 -> GitHubException.RateLimited(headers["X-RateLimit-Reset"]?.toLongOrNull())
            else -> GitHubException.Http(status, message)
        }
    }

    private fun errorMessage(body: String): String? {
        if (body.isBlank()) return null
        return try {
            json.decodeFromString(ErrorDto.serializer(), body).describe()
        } catch (e: SerializationException) {
            null
        } catch (e: IllegalArgumentException) {
            null
        }
    }

    private inline fun <reified T> ApiResponse.parse(): T = try {
        json.decodeFromString<T>(body)
    } catch (e: SerializationException) {
        logger.warn(e) { "GitHub 응답 해석 실패 (${T::class.simpleName})" }
        throw GitHubException.InvalidResponse()
    } catch (e: IllegalArgumentException) {
        logger.warn(e) { "GitHub 응답 해석 실패 (${T::class.simpleName})" }
        throw GitHubException.InvalidResponse()
    }

    private fun repoPath(ref: RepoRef): String =
        "/repos/${ref.owner.encodeURLPathPart()}/${ref.name.encodeURLPathPart()}"

    /** 파일 경로·브랜치 이름의 `/` 는 구분자로 남기고 각 조각만 인코딩한다 */
    private fun encodePath(path: String): String =
        path.split('/').joinToString("/") { it.encodeURLPathPart() }

    private fun decodeBase64(content: String): String = try {
        // GitHub 는 base64 를 60자마다 줄바꿈해서 준다. MIME 디코더는 줄바꿈을 무시한다
        String(Base64.getMimeDecoder().decode(content), Charsets.UTF_8)
    } catch (e: IllegalArgumentException) {
        throw GitHubException.InvalidResponse("파일 내용의 base64 가 잘못됐어요.")
    }

    private fun encodeBase64(text: String): String =
        Base64.getEncoder().encodeToString(text.toByteArray(Charsets.UTF_8))

    companion object {
        const val DEFAULT_BASE_URL = "https://api.github.com"
    }
}
