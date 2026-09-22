package io.github.rudtjr1106.switchboard.app.session

import io.github.oshai.kotlinlogging.KotlinLogging
import io.github.rudtjr1106.switchboard.app.BuildInfo
import io.github.rudtjr1106.switchboard.app.settings.SettingsStore
import io.github.rudtjr1106.switchboard.github.DeviceFlowAuthenticator
import io.github.rudtjr1106.switchboard.github.DeviceFlowEvent
import io.github.rudtjr1106.switchboard.github.GhCliTokenProvider
import io.github.rudtjr1106.switchboard.github.GitHubApi
import io.github.rudtjr1106.switchboard.github.GitHubClientFactory
import io.github.rudtjr1106.switchboard.github.GitHubException
import io.github.rudtjr1106.switchboard.github.GitHubToken
import io.github.rudtjr1106.switchboard.github.GitHubUser
import io.github.rudtjr1106.switchboard.github.TokenSource
import io.github.rudtjr1106.switchboard.github.TokenStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.Instant

private val logger = KotlinLogging.logger {}

sealed interface SessionState {
    data object Restoring : SessionState

    /** @property notice 로그아웃·연결 해제 뒤 로그인 화면에 띄울 안내 (오류가 아님) */
    data class SignedOut(val error: String? = null, val ghCliAvailable: Boolean = false, val notice: String? = null) : SessionState

    /** Device Flow 진행 중. 사용자가 브라우저에서 [userCode] 를 입력하길 기다린다 */
    data class SigningIn(
        val userCode: String? = null,
        val verificationUri: String? = null,
        val expiresAt: Instant? = null,
    ) : SessionState

    data class SignedIn(
        val user: GitHubUser,
        val token: GitHubToken,
        val api: GitHubApi,
        val scopes: List<String>,
    ) : SessionState {
        val missingScopes: List<String>
            get() = DeviceFlowAuthenticator.DEFAULT_SCOPES.filterNot { it in scopes }
                // gh CLI 토큰은 스코프 헤더가 없을 수 있다. 그때는 경고하지 않는다
                .takeIf { scopes.isNotEmpty() }.orEmpty()
    }
}

/** 로그인 상태와 토큰 보관. 앱 전체에서 하나만 쓴다 */
class SessionManager(
    private val tokenStore: TokenStore,
    private val ghCli: GhCliTokenProvider,
    private val deviceFlow: DeviceFlowAuthenticator,
    private val clientFactory: GitHubClientFactory,
    private val settings: SettingsStore,
    private val scope: CoroutineScope,
    /** 빌드에 넣은 OAuth App Client ID. 테스트에서 바꿔 끼운다 */
    private val defaultClientId: String = BuildInfo.GITHUB_CLIENT_ID,
) {
    private val _state = MutableStateFlow<SessionState>(SessionState.Restoring)
    val state: StateFlow<SessionState> = _state.asStateFlow()

    private var signInJob: Job? = null

    /** 설정 > Client ID 가 우선, 없으면 빌드에 넣은 값. 둘 다 없으면 Device Flow 를 쓸 수 없다 */
    val clientId: String?
        get() = settings.current.githubClientId?.takeIf { it.isNotBlank() } ?: defaultClientId.takeIf { it.isNotBlank() }

    val signedIn: SessionState.SignedIn? get() = _state.value as? SessionState.SignedIn

    fun restore() {
        scope.launch {
            // 개발 편의: 저장된 토큰이 없어도 SWITCHBOARD_GH_AUTOLOGIN=1 이면 gh CLI 토큰으로 바로 들어간다
            val token = tokenStore.load()
                ?: ghCli.readToken().takeIf { System.getenv("SWITCHBOARD_GH_AUTOLOGIN") == "1" }
            if (token == null) {
                _state.value = SessionState.SignedOut(ghCliAvailable = ghCli.isInstalled())
                return@launch
            }
            try {
                _state.value = verify(token)
            } catch (e: GitHubException.Unauthorized) {
                tokenStore.clear()
                _state.value = SessionState.SignedOut("저장된 로그인이 만료됐어요. 다시 로그인해 주세요.", ghCli.isInstalled())
            } catch (e: GitHubException) {
                // 오프라인이면 토큰은 두고 로그인 화면에서 다시 시도하게 한다
                logger.warn(e) { "세션 복원 실패" }
                _state.value = SessionState.SignedOut(e.message, ghCli.isInstalled())
            }
        }
    }

    fun signInWithDeviceFlow() {
        val clientId = clientId ?: run {
            _state.value = SessionState.SignedOut("GitHub OAuth App 의 Client ID 가 없어요. 설정에서 넣어 주세요.", ghCliAvailableCached())
            return
        }
        signInJob?.cancel()
        _state.value = SessionState.SigningIn()
        signInJob = scope.launch {
            try {
                deviceFlow.authenticate(clientId).collect { event ->
                    when (event) {
                        is DeviceFlowEvent.CodeIssued -> _state.value = SessionState.SigningIn(
                            userCode = event.code.userCode,
                            verificationUri = event.code.verificationUri,
                            expiresAt = Instant.now().plusSeconds(event.code.expiresInSeconds.toLong()),
                        )
                        DeviceFlowEvent.Pending -> Unit
                        is DeviceFlowEvent.Authorized -> {
                            tokenStore.save(event.token)
                            _state.value = verify(event.token)
                        }
                        is DeviceFlowEvent.Failed -> _state.value = SessionState.SignedOut(event.reason, ghCliAvailableCached())
                    }
                }
            } catch (e: GitHubException) {
                _state.value = SessionState.SignedOut(e.message, ghCliAvailableCached())
            }
        }
    }

    fun signInWithGhCli() {
        _state.value = SessionState.SigningIn()
        scope.launch {
            val token = ghCli.readToken()
            if (token == null) {
                _state.value = SessionState.SignedOut(
                    "gh CLI 에서 토큰을 읽지 못했어요. 터미널에서 gh auth login 을 먼저 실행하세요.",
                    ghCli.isInstalled(),
                )
                return@launch
            }
            finishSignIn(token)
        }
    }

    fun signInWithToken(raw: String) {
        val value = raw.trim()
        if (value.isEmpty()) return
        _state.value = SessionState.SigningIn()
        scope.launch { finishSignIn(GitHubToken(value, TokenSource.MANUAL)) }
    }

    fun cancelSignIn() {
        signInJob?.cancel()
        signInJob = null
        _state.value = SessionState.SignedOut(ghCliAvailable = ghCliAvailableCached())
    }

    fun signOut(notice: String? = "로그아웃했어요.") {
        signInJob?.cancel()
        tokenStore.clear()
        settings.update { it.copy(lastRepository = null) }
        _state.value = SessionState.SignedOut(ghCliAvailable = ghCliAvailableCached(), notice = notice)
    }

    /**
     * 연결 해제. 스위치보드는 따로 회원 가입이 없어서 탈퇴 대신 이것을 쓴다
     *
     * 이 컴퓨터의 토큰과 스위치보드 설정을 지운다. GitHub 쪽 권한은 client secret 없이는 앱이 취소할 수 없어서,
     * 사용자가 직접 취소할 페이지 주소를 돌려준다 (gh CLI 토큰이면 null: gh 자체의 권한이라 건드리지 않는다).
     */
    fun disconnect(): String? {
        val url = revokeUrl(signedIn?.token?.source)
        signInJob?.cancel()
        tokenStore.clear()
        settings.reset()
        val notice = if (url != null) {
            "연결을 해제하고 이 컴퓨터의 데이터를 지웠어요. 열린 GitHub 페이지에서 Revoke 를 누르면 권한까지 없어져요."
        } else {
            "연결을 해제하고 이 컴퓨터의 데이터를 지웠어요."
        }
        _state.value = SessionState.SignedOut(ghCliAvailable = ghCliAvailableCached(), notice = notice)
        return url
    }

    /** 이 로그인의 권한을 GitHub 에서 취소하는 페이지 */
    fun revokeUrl(source: TokenSource?): String? = when (source) {
        TokenSource.DEVICE_FLOW -> clientId?.let { "https://github.com/settings/connections/applications/$it" }
        TokenSource.MANUAL -> "https://github.com/settings/tokens"
        TokenSource.GH_CLI, null -> null
    }

    private suspend fun finishSignIn(token: GitHubToken) {
        try {
            val signedIn = verify(token)
            tokenStore.save(token)
            _state.value = signedIn
        } catch (e: GitHubException) {
            _state.value = SessionState.SignedOut(e.message, ghCli.isInstalled())
        }
    }

    private suspend fun verify(token: GitHubToken): SessionState.SignedIn {
        val api = clientFactory.api(token)
        val user = api.currentUser()
        val scopes = runCatching { api.tokenScopes() }.getOrDefault(emptyList())
        return SessionState.SignedIn(user, token, api, scopes)
    }

    private var ghCliAvailable: Boolean? = null

    private fun ghCliAvailableCached(): Boolean = ghCliAvailable ?: false.also {
        scope.launch { ghCliAvailable = ghCli.isInstalled() }
    }
}
