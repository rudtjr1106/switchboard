package io.github.rudtjr1106.switchboard.github

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/*
 * GitHub REST 응답 중 이 앱이 읽는 필드만 담은 DTO.
 * 공개 모델([GitHubRepo] 등)과 분리해 두어 GitHub 가 필드를 바꿔도 UI 쪽 타입은 그대로다.
 */

@Serializable
internal data class UserDto(
    val login: String,
    val name: String? = null,
    @SerialName("avatar_url") val avatarUrl: String? = null,
    @SerialName("html_url") val htmlUrl: String,
) {
    fun toUser() = GitHubUser(login = login, name = name, avatarUrl = avatarUrl, htmlUrl = htmlUrl)
}

@Serializable
internal data class OrganizationDto(
    val login: String,
    @SerialName("avatar_url") val avatarUrl: String? = null,
)

@Serializable
internal data class OwnerDto(
    val login: String,
    val type: String? = null,
    @SerialName("avatar_url") val avatarUrl: String? = null,
)

@Serializable
internal data class PermissionsDto(val push: Boolean = false, val admin: Boolean = false)

@Serializable
internal data class RepoDto(
    val name: String,
    val owner: OwnerDto,
    @SerialName("html_url") val htmlUrl: String,
    @SerialName("default_branch") val defaultBranch: String = "main",
    @SerialName("private") val isPrivate: Boolean = false,
    val description: String? = null,
    val topics: List<String> = emptyList(),
    @SerialName("has_pages") val hasPages: Boolean = false,
    val permissions: PermissionsDto? = null,
) {
    fun toRepo() = GitHubRepo(
        ref = RepoRef(owner.login, name),
        htmlUrl = htmlUrl,
        defaultBranch = defaultBranch,
        isPrivate = isPrivate,
        description = description,
        topics = topics,
        hasPages = hasPages,
        permissions = permissions?.let { RepoPermissions(push = it.push, admin = it.admin) } ?: RepoPermissions(),
    )
}

@Serializable
internal data class SearchDto(val items: List<RepoDto> = emptyList())

@Serializable
internal data class ContentDto(
    val sha: String,
    val content: String? = null,
    val encoding: String? = null,
)

@Serializable
internal data class ShaDto(val sha: String)

@Serializable
internal data class ReferenceDto(@SerialName("object") val target: ShaDto)

@Serializable
internal data class PutContentDto(val content: ShaDto, val commit: ShaDto)

@Serializable
internal data class PullRequestDto(
    val number: Int,
    @SerialName("html_url") val htmlUrl: String,
)

@Serializable
internal data class CheckRunsDto(@SerialName("check_runs") val checkRuns: List<CheckRunDto> = emptyList())

@Serializable
internal data class CheckRunDto(
    val name: String = "",
    val status: String = "",
    val conclusion: String? = null,
)

@Serializable
internal data class PagesBuildDto(
    val status: String? = null,
    val commit: String? = null,
    val error: PagesBuildErrorDto? = null,
)

@Serializable
internal data class PagesBuildErrorDto(val message: String? = null)

@Serializable
internal data class PagesDto(
    @SerialName("html_url") val htmlUrl: String? = null,
    val status: String? = null,
    val source: PagesSourceDto? = null,
)

@Serializable
internal data class PagesSourceDto(val branch: String? = null, val path: String? = null)

@Serializable
internal data class ReleaseDto(
    @SerialName("tag_name") val tagName: String,
    val name: String? = null,
    @SerialName("html_url") val htmlUrl: String,
    val body: String? = null,
    val assets: List<ReleaseAssetDto> = emptyList(),
)

@Serializable
internal data class ReleaseAssetDto(
    val name: String,
    @SerialName("browser_download_url") val downloadUrl: String,
    val size: Long = 0,
)

/** 실패 응답. `errors` 는 문자열일 때도, `{ "message": ... }` 객체일 때도 있다 */
@Serializable
internal data class ErrorDto(
    val message: String? = null,
    val errors: List<JsonElement> = emptyList(),
) {
    /** 화면에 보여줄 한 줄. "Repository creation failed. name already exists on this account" 처럼 세부 사유까지 붙인다 */
    fun describe(): String? {
        val details = errors.mapNotNull { element ->
            when (element) {
                is JsonPrimitive -> element.content
                is JsonObject -> (element["message"] as? JsonPrimitive)?.content
                else -> null
            }
        }
        val parts = listOfNotNull(message?.takeIf { it.isNotBlank() }) + details.filter { it.isNotBlank() }
        return parts.joinToString(" ").takeIf { it.isNotBlank() }
    }
}
