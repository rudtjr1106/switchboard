package io.github.rudtjr1106.switchboard.github

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.HttpClient
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
import io.ktor.http.parameters
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import java.io.IOException
import java.nio.channels.UnresolvedAddressException

private val logger = KotlinLogging.logger {}

/**
 * GitHub OAuth Device Flow 의 Ktor 구현
 *
 * 코드를 받은 뒤 GitHub 가 알려준 간격으로 토큰을 물어본다. `slow_down` 이 오면 간격을 5초 늘린다.
 * [delay] 를 바꿔 끼울 수 있어 테스트에서 실제로 기다리지 않는다.
 */
class KtorDeviceFlowAuthenticator(
    private val client: HttpClient,
    private val apiFactory: (token: String) -> GitHubApi,
    private val delay: suspend (millis: Long) -> Unit = { kotlinx.coroutines.delay(it) },
) : DeviceFlowAuthenticator {

    private val json = GitHubHttp.json

    override fun authenticate(clientId: String, scopes: List<String>): Flow<DeviceFlowEvent> = flow {
        // DeviceFlowFailure 만 잡는다. 수집 쪽(emit 아래)에서 난 예외는 그대로 흘려보내야 Flow 규칙에 맞다
        try {
            val code = requestCode(clientId, scopes)
            emit(DeviceFlowEvent.CodeIssued(code))

            var intervalSeconds = code.intervalSeconds.coerceAtLeast(1)
            var elapsedSeconds = 0
            while (true) {
                delay(intervalSeconds * 1000L)
                elapsedSeconds += intervalSeconds
                if (elapsedSeconds > code.expiresInSeconds) throw DeviceFlowFailure(EXPIRED)

                val poll = pollToken(clientId, code.deviceCode)
                when {
                    poll.accessToken != null -> {
                        val token = GitHubToken(poll.accessToken, TokenSource.DEVICE_FLOW)
                        val user = guarded { apiFactory(token.value).currentUser() }
                        emit(DeviceFlowEvent.Authorized(token, user))
                        return@flow
                    }
                    poll.error == "authorization_pending" -> emit(DeviceFlowEvent.Pending)
                    poll.error == "slow_down" -> {
                        // GitHub 가 새 간격을 주기도 한다. 어느 쪽이든 최소 5초는 늘린다
                        intervalSeconds = maxOf(poll.interval ?: 0, intervalSeconds + 5)
                        emit(DeviceFlowEvent.Pending)
                    }
                    else -> throw DeviceFlowFailure(reasonFor(poll.error, poll.errorDescription))
                }
            }
        } catch (e: DeviceFlowFailure) {
            logger.info { "Device Flow 실패: ${e.reason}" }
            emit(DeviceFlowEvent.Failed(e.reason, e.cause))
        }
    }

    private suspend fun requestCode(clientId: String, scopes: List<String>): DeviceCode {
        val dto = guarded {
            val response = client.submitForm(
                url = DEVICE_CODE_URL,
                formParameters = parameters {
                    append("client_id", clientId)
                    append("scope", scopes.joinToString(" "))
                },
            ) {
                header(HttpHeaders.Accept, "application/json")
                header(HttpHeaders.UserAgent, "Switchboard")
            }
            val text = response.bodyAsText()
            if (!response.status.isSuccess()) {
                throw DeviceFlowFailure("GitHub 요청이 실패했어요 (${response.status.value}).")
            }
            json.decodeFromString(DeviceCodeResponse.serializer(), text)
        }
        if (dto.error != null) throw DeviceFlowFailure(reasonFor(dto.error, dto.errorDescription))
        return DeviceCode(
            deviceCode = dto.deviceCode ?: throw DeviceFlowFailure(INVALID),
            userCode = dto.userCode ?: throw DeviceFlowFailure(INVALID),
            verificationUri = dto.verificationUri ?: throw DeviceFlowFailure(INVALID),
            expiresInSeconds = dto.expiresIn ?: DEFAULT_EXPIRES_IN,
            intervalSeconds = dto.interval ?: DEFAULT_INTERVAL,
        )
    }

    private suspend fun pollToken(clientId: String, deviceCode: String): AccessTokenResponse = guarded {
        val response = client.submitForm(
            url = ACCESS_TOKEN_URL,
            formParameters = parameters {
                append("client_id", clientId)
                append("device_code", deviceCode)
                append("grant_type", GRANT_TYPE)
            },
        ) {
            header(HttpHeaders.Accept, "application/json")
            header(HttpHeaders.UserAgent, "Switchboard")
        }
        val text = response.bodyAsText()
        // 오류도 200 으로 오고 본문의 error 로 구분한다. 5xx 같은 진짜 실패만 여기서 끊는다
        if (!response.status.isSuccess()) {
            throw DeviceFlowFailure("GitHub 요청이 실패했어요 (${response.status.value}).")
        }
        json.decodeFromString(AccessTokenResponse.serializer(), text)
    }

    /** 네트워크·해석 실패를 사용자 문장이 붙은 [DeviceFlowFailure] 로 바꾼다 */
    private suspend fun <T> guarded(block: suspend () -> T): T = try {
        block()
    } catch (e: GitHubException) {
        throw DeviceFlowFailure(e.message.orEmpty(), e)
    } catch (e: IOException) {
        throw DeviceFlowFailure(GitHubException.Network(e).message.orEmpty(), e)
    } catch (e: UnresolvedAddressException) {
        throw DeviceFlowFailure(GitHubException.Network(e).message.orEmpty(), e)
    } catch (e: SerializationException) {
        throw DeviceFlowFailure(GitHubException.InvalidResponse().message.orEmpty(), e)
    }

    private fun reasonFor(error: String?, description: String?): String = when (error) {
        "expired_token" -> EXPIRED
        "access_denied" -> "GitHub 에서 승인을 거절했어요."
        "incorrect_device_code" -> "기기 코드가 맞지 않아요. 다시 시도해 주세요."
        "incorrect_client_credentials" -> "OAuth App 의 Client ID 가 잘못됐어요. 설정을 확인해 주세요."
        "device_flow_disabled" -> "OAuth App 에 Device Flow 가 꺼져 있어요. GitHub 에서 켜 주세요."
        "unsupported_grant_type" -> "GitHub 가 이 인증 방식을 받지 않았어요."
        else -> "GitHub 인증에 실패했어요" + (error?.let { " ($it${description?.let { d -> ": $d" }.orEmpty()})" }.orEmpty()) + "."
    }

    private class DeviceFlowFailure(val reason: String, cause: Throwable? = null) : Exception(reason, cause)

    @Serializable
    private data class DeviceCodeResponse(
        @SerialName("device_code") val deviceCode: String? = null,
        @SerialName("user_code") val userCode: String? = null,
        @SerialName("verification_uri") val verificationUri: String? = null,
        @SerialName("expires_in") val expiresIn: Int? = null,
        val interval: Int? = null,
        val error: String? = null,
        @SerialName("error_description") val errorDescription: String? = null,
    )

    @Serializable
    private data class AccessTokenResponse(
        @SerialName("access_token") val accessToken: String? = null,
        val interval: Int? = null,
        val error: String? = null,
        @SerialName("error_description") val errorDescription: String? = null,
    )

    companion object {
        const val DEVICE_CODE_URL = "https://github.com/login/device/code"
        const val ACCESS_TOKEN_URL = "https://github.com/login/oauth/access_token"
        const val GRANT_TYPE = "urn:ietf:params:oauth:grant-type:device_code"
        private const val DEFAULT_EXPIRES_IN = 900
        private const val DEFAULT_INTERVAL = 5
        private const val EXPIRED = "승인 시간이 지났어요. 다시 시도해 주세요."
        private const val INVALID = "GitHub 응답을 읽지 못했어요."
    }
}
