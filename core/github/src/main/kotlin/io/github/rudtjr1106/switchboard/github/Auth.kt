package io.github.rudtjr1106.switchboard.github

import kotlinx.coroutines.flow.Flow

enum class TokenSource { DEVICE_FLOW, GH_CLI, MANUAL }

data class GitHubToken(val value: String, val source: TokenSource)

data class DeviceCode(
    val deviceCode: String,
    val userCode: String,
    val verificationUri: String,
    val expiresInSeconds: Int,
    val intervalSeconds: Int,
)

sealed interface DeviceFlowEvent {
    /** 사용자에게 보여줄 코드가 나왔다. 브라우저에서 [DeviceCode.verificationUri] 를 열고 코드를 입력한다 */
    data class CodeIssued(val code: DeviceCode) : DeviceFlowEvent

    /** 아직 승인 전. 폴링 중 */
    data object Pending : DeviceFlowEvent

    data class Authorized(val token: GitHubToken, val user: GitHubUser) : DeviceFlowEvent

    data class Failed(val reason: String, val cause: Throwable? = null) : DeviceFlowEvent
}

/**
 * GitHub OAuth Device Flow (https://docs.github.com/apps/oauth-apps/building-oauth-apps/authorizing-oauth-apps#device-flow)
 *
 * OAuth App 에 "Enable Device Flow" 가 켜져 있어야 한다. Client ID 는 비밀이 아니라 앱에 넣어도 된다.
 */
interface DeviceFlowAuthenticator {

    /** [DeviceFlowEvent.CodeIssued] 를 먼저 내고, 승인될 때까지 [DeviceFlowEvent.Pending] 을 반복하다가 [DeviceFlowEvent.Authorized] 또는 [DeviceFlowEvent.Failed] 로 끝난다 */
    fun authenticate(clientId: String, scopes: List<String> = DEFAULT_SCOPES): Flow<DeviceFlowEvent>

    companion object {
        /**
         * - repo: 설정 저장소 읽기·쓰기·PR 머지·저장소 생성
         * - workflow: `.github/workflows/validate.yml` 을 API 로 올리려면 필요하다
         * - read:org: 조직 목록을 보여주고 조직 아래에 저장소를 만들 때 필요하다
         */
        val DEFAULT_SCOPES: List<String> = listOf("repo", "workflow", "read:org")
    }
}

/** `gh auth token` 으로 GitHub CLI 의 토큰을 빌려 쓴다. OAuth App 을 아직 등록하지 않았을 때의 폴백이다 */
interface GhCliTokenProvider {
    suspend fun isInstalled(): Boolean
    suspend fun readToken(): GitHubToken?
}

/** 토큰을 OS 자격 증명 저장소(macOS 키체인, Windows 자격 증명 관리자)에 보관한다 */
interface TokenStore {
    fun load(): GitHubToken?
    fun save(token: GitHubToken)
    fun clear()
}
