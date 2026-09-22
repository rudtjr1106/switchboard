package io.github.rudtjr1106.switchboard.github

import kotlinx.serialization.Serializable

@Serializable
data class GitHubUser(
    val login: String,
    val name: String? = null,
    val avatarUrl: String? = null,
    val htmlUrl: String,
)

enum class OwnerType { USER, ORGANIZATION }

@Serializable
data class GitHubOwner(
    val login: String,
    val type: OwnerType,
    val avatarUrl: String? = null,
)

/** 저장소 좌표. 설정 저장소를 가리키는 데 쓴다 */
@Serializable
data class RepoRef(val owner: String, val name: String) {
    val fullName: String get() = "$owner/$name"
    val htmlUrl: String get() = "https://github.com/$fullName"

    /** GitHub Pages 주소. 소유자 이름은 소문자로 바뀐다 */
    fun pagesUrl(path: String = ""): String = "https://${owner.lowercase()}.github.io/$name/$path"

    companion object {
        fun parse(text: String): RepoRef? {
            val cleaned = text.trim()
                .removePrefix("https://github.com/")
                .removePrefix("github.com/")
                .removeSuffix(".git")
                .trim('/')
            val parts = cleaned.split('/')
            if (parts.size != 2 || parts.any { it.isBlank() }) return null
            return RepoRef(parts[0], parts[1])
        }
    }
}

@Serializable
data class GitHubRepo(
    val ref: RepoRef,
    val htmlUrl: String,
    val defaultBranch: String = "main",
    val isPrivate: Boolean = false,
    val description: String? = null,
    val topics: List<String> = emptyList(),
    val hasPages: Boolean = false,
    val permissions: RepoPermissions = RepoPermissions(),
)

@Serializable
data class RepoPermissions(val push: Boolean = false, val admin: Boolean = false)

data class RepoFile(val path: String, val sha: String, val content: String)

data class CommitResult(val commitSha: String, val fileSha: String)

data class PullRequest(val number: Int, val htmlUrl: String)

data class CheckRun(val name: String, val status: String, val conclusion: String?) {
    val isCompleted: Boolean get() = status == "completed"
    val isSuccess: Boolean get() = isCompleted && conclusion == "success"
}

data class PagesBuild(val status: String, val commitSha: String?, val errorMessage: String?) {
    val isBuilt: Boolean get() = status == "built"
    val isErrored: Boolean get() = status == "errored"
}

data class PagesInfo(val url: String?, val status: String?, val sourceBranch: String?)

data class ReleaseAsset(val name: String, val downloadUrl: String, val sizeBytes: Long)

data class Release(
    val tagName: String,
    val name: String?,
    val htmlUrl: String,
    val body: String?,
    val assets: List<ReleaseAsset>,
)
