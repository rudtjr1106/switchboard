package io.github.rudtjr1106.switchboard.github

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

/** 이 모듈이 함께 쓰는 Ktor 클라이언트와 JSON 설정 */
object GitHubHttp {

    /** GitHub 응답에는 안 쓰는 필드가 많고 새 필드가 늘어나도 앱이 깨지면 안 된다 */
    val json: Json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        coerceInputValues = true
    }

    /** 앱 전체가 돌려쓰는 CIO 클라이언트. 만드는 비용이 커서 한 번만 만든다 */
    fun client(): HttpClient = HttpClient(CIO) { configure() }

    /** 테스트에서 MockEngine 을 꽂을 때 쓴다. 설정은 실제 클라이언트와 같다 */
    fun client(engine: HttpClientEngine): HttpClient = HttpClient(engine) { configure() }

    private fun HttpClientConfig<*>.configure() {
        // 상태 코드는 KtorGitHubApi 가 직접 GitHubException 으로 바꾸므로 Ktor 가 먼저 던지면 안 된다
        expectSuccess = false
        followRedirects = true
        install(ContentNegotiation) { json(GitHubHttp.json) }
        install(HttpTimeout) {
            connectTimeoutMillis = 15_000
            requestTimeoutMillis = 60_000
            socketTimeoutMillis = 60_000
        }
    }
}

/** 앱이 쓰는 기본 조립. 토큰 하나로 API·저장소·부트스트래퍼를 만든다 */
class DefaultGitHubClientFactory(
    private val client: HttpClient = GitHubHttp.client(),
) : GitHubClientFactory {

    override fun api(token: GitHubToken): GitHubApi = KtorGitHubApi(token.value, client)

    override fun configRepository(api: GitHubApi, ref: RepoRef, baseBranch: String): ConfigRepository =
        GitHubConfigRepository(api, ref, baseBranch)

    override fun bootstrapper(api: GitHubApi): RepoBootstrapper = GitHubRepoBootstrapper(api)

    /** Device Flow 로그인. 같은 클라이언트를 쓰므로 타임아웃·프록시 설정이 API 호출과 같다 */
    fun deviceFlowAuthenticator(): DeviceFlowAuthenticator =
        KtorDeviceFlowAuthenticator(client, apiFactory = { token -> KtorGitHubApi(token, client) })
}
