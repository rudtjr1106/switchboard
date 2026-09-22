package io.github.rudtjr1106.switchboard.github

import io.ktor.http.HttpMethod
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class KtorDeviceFlowAuthenticatorTest {

    private val server = ScriptedServer()
    private val delays = mutableListOf<Long>()
    private val authenticator = KtorDeviceFlowAuthenticator(
        client = server.client,
        apiFactory = { token -> KtorGitHubApi(token, server.client) },
        delay = { delays += it },
    )

    private val issuedCode = DeviceCode(
        deviceCode = "dev123",
        userCode = "ABCD-1234",
        verificationUri = "https://github.com/login/device",
        expiresInSeconds = 900,
        intervalSeconds = 5,
    )

    private fun scriptDeviceCode(expiresIn: Int = 900) {
        server.on(
            HttpMethod.Post, "/login/device/code",
            body = """{"device_code":"dev123","user_code":"ABCD-1234","verification_uri":"https://github.com/login/device","expires_in":$expiresIn,"interval":5}""",
        )
    }

    private fun scriptTokenResponses(vararg bodies: String) {
        val queue = ArrayDeque(bodies.toList())
        server.on(HttpMethod.Post, "/login/oauth/access_token") { jsonResponse(queue.removeFirstOrNull() ?: bodies.last()) }
    }

    @Test
    fun `issues a code, polls until authorized and honours slow_down`() = runTest {
        scriptDeviceCode()
        scriptTokenResponses(
            """{"error":"authorization_pending","error_description":"The authorization request is still pending."}""",
            """{"error":"slow_down","interval":10}""",
            """{"access_token":"gho_abc","token_type":"bearer","scope":"repo,workflow"}""",
        )
        server.on(HttpMethod.Get, "/user", body = Fixtures.USER_JSON)

        val events = authenticator.authenticate("client-id", listOf("repo", "workflow")).toList()

        assertEquals(
            listOf(
                DeviceFlowEvent.CodeIssued(issuedCode),
                DeviceFlowEvent.Pending,
                DeviceFlowEvent.Pending,
                DeviceFlowEvent.Authorized(GitHubToken("gho_abc", TokenSource.DEVICE_FLOW), Fixtures.user),
            ),
            events,
        )
        assertEquals(listOf(5_000L, 5_000L, 10_000L), delays)

        val codeRequest = server.requests(HttpMethod.Post, "/login/device/code").single()
        assertEquals("application/json", codeRequest.headers["Accept"])
        assertEquals("client-id", codeRequest.form()["client_id"])
        assertEquals("repo workflow", codeRequest.form()["scope"])

        val tokenRequest = server.requests(HttpMethod.Post, "/login/oauth/access_token").first()
        assertEquals("application/json", tokenRequest.headers["Accept"])
        assertEquals("client-id", tokenRequest.form()["client_id"])
        assertEquals("dev123", tokenRequest.form()["device_code"])
        assertEquals("urn:ietf:params:oauth:grant-type:device_code", tokenRequest.form()["grant_type"])

        assertEquals("Bearer gho_abc", server.requests(HttpMethod.Get, "/user").single().headers["Authorization"])
    }

    @Test
    fun `expired_token ends the flow with Failed`() = runTest {
        scriptDeviceCode()
        scriptTokenResponses("""{"error":"expired_token"}""")

        val events = authenticator.authenticate("client-id").toList()

        assertEquals(2, events.size)
        assertEquals(DeviceFlowEvent.CodeIssued(issuedCode), events[0])
        val failed = assertIs<DeviceFlowEvent.Failed>(events[1])
        assertTrue(failed.reason.contains("승인 시간이 지났어요"), failed.reason)
        assertEquals(listOf(5_000L), delays)
    }

    @Test
    fun `access_denied ends the flow with Failed`() = runTest {
        scriptDeviceCode()
        scriptTokenResponses("""{"error":"access_denied"}""")

        val events = authenticator.authenticate("client-id").toList()

        val failed = assertIs<DeviceFlowEvent.Failed>(events.last())
        assertTrue(failed.reason.contains("거절"), failed.reason)
    }

    @Test
    fun `gives up when expires_in elapses while still pending`() = runTest {
        scriptDeviceCode(expiresIn = 12)
        scriptTokenResponses("""{"error":"authorization_pending"}""")

        val events = authenticator.authenticate("client-id").toList()

        assertEquals(
            listOf(DeviceFlowEvent.CodeIssued(issuedCode.copy(expiresInSeconds = 12)), DeviceFlowEvent.Pending, DeviceFlowEvent.Pending),
            events.dropLast(1),
        )
        val failed = assertIs<DeviceFlowEvent.Failed>(events.last())
        assertTrue(failed.reason.contains("승인 시간이 지났어요"), failed.reason)
        assertEquals(listOf(5_000L, 5_000L, 5_000L), delays)
        assertEquals(2, server.requests(HttpMethod.Post, "/login/oauth/access_token").size)
    }

    @Test
    fun `device code error is reported as Failed without polling`() = runTest {
        server.on(HttpMethod.Post, "/login/device/code", body = """{"error":"incorrect_client_credentials"}""")

        val events = authenticator.authenticate("bad-client").toList()

        val failed = assertIs<DeviceFlowEvent.Failed>(events.single())
        assertTrue(failed.reason.contains("Client ID"), failed.reason)
        assertTrue(delays.isEmpty())
    }
}
