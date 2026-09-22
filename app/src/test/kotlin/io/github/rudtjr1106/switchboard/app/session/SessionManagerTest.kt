package io.github.rudtjr1106.switchboard.app.session

import io.github.rudtjr1106.switchboard.app.settings.SettingsStore
import io.github.rudtjr1106.switchboard.app.testing.FakeClientFactory
import io.github.rudtjr1106.switchboard.app.testing.FakeDeviceFlow
import io.github.rudtjr1106.switchboard.app.testing.FakeGhCli
import io.github.rudtjr1106.switchboard.app.testing.FakeGitHubApi
import io.github.rudtjr1106.switchboard.app.testing.FakeTokenStore
import io.github.rudtjr1106.switchboard.github.DeviceCode
import io.github.rudtjr1106.switchboard.github.DeviceFlowEvent
import io.github.rudtjr1106.switchboard.github.GitHubToken
import io.github.rudtjr1106.switchboard.github.GitHubUser
import io.github.rudtjr1106.switchboard.github.TokenSource
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class SessionManagerTest {

    private val user = GitHubUser("octocat", null, null, "https://github.com/octocat")

    private fun TestScope.manager(
        tokenStore: FakeTokenStore = FakeTokenStore(),
        api: FakeGitHubApi = FakeGitHubApi(user),
        ghCli: FakeGhCli = FakeGhCli(installed = false),
        deviceFlow: FakeDeviceFlow = FakeDeviceFlow(emptyList()),
        clientId: String? = "client-123",
    ): Pair<SessionManager, FakeClientFactory> {
        val settings = SettingsStore(Files.createTempDirectory("switchboard-test").resolve("settings.json"))
        settings.update { it.copy(githubClientId = clientId) }
        val factory = FakeClientFactory(api) { error("unused") }
        val scope = TestScope(UnconfinedTestDispatcher(testScheduler))
        return SessionManager(tokenStore, ghCli, deviceFlow, factory, settings, scope, defaultClientId = "") to factory
    }

    @Test
    fun `restore with a valid stored token signs in`() = runTest {
        val store = FakeTokenStore(GitHubToken("t", TokenSource.DEVICE_FLOW))
        val (manager, _) = manager(tokenStore = store)
        manager.restore()
        val signedIn = assertIs<SessionState.SignedIn>(manager.state.value)
        assertEquals("octocat", signedIn.user.login)
        assertEquals(emptyList(), signedIn.missingScopes)
    }

    @Test
    fun `restore with an expired token clears it`() = runTest {
        val store = FakeTokenStore(GitHubToken("old", TokenSource.MANUAL))
        val (manager, _) = manager(tokenStore = store, api = FakeGitHubApi(user, unauthorized = true))
        manager.restore()
        assertIs<SessionState.SignedOut>(manager.state.value)
        assertNull(store.stored)
    }

    @Test
    fun `device flow shows the code then signs in and saves the token`() = runTest {
        val store = FakeTokenStore()
        val token = GitHubToken("new", TokenSource.DEVICE_FLOW)
        val flow = FakeDeviceFlow(
            listOf(
                DeviceFlowEvent.CodeIssued(DeviceCode("d", "ABCD-1234", "https://github.com/login/device", 900, 5)),
                DeviceFlowEvent.Pending,
                DeviceFlowEvent.Authorized(token, user),
            ),
        )
        val (manager, factory) = manager(tokenStore = store, deviceFlow = flow)
        manager.restore()
        manager.signInWithDeviceFlow()
        assertIs<SessionState.SignedIn>(manager.state.value)
        assertEquals("client-123", flow.requestedClientId)
        assertEquals(token, store.stored)
        assertEquals(listOf(token), factory.tokensSeen)
    }

    @Test
    fun `device flow without a client id explains what to do`() = runTest {
        val (manager, _) = manager(clientId = null)
        manager.restore()
        manager.signInWithDeviceFlow()
        val out = assertIs<SessionState.SignedOut>(manager.state.value)
        assertEquals("GitHub OAuth App 의 Client ID 가 없어요. 설정에서 넣어 주세요.", out.error)
    }

    @Test
    fun `disconnect clears token and settings and points to the revoke page`() = runTest {
        val store = FakeTokenStore(GitHubToken("t", TokenSource.DEVICE_FLOW))
        val (manager, _) = manager(tokenStore = store, clientId = "client-123")
        manager.restore()
        assertIs<SessionState.SignedIn>(manager.state.value)
        val url = manager.disconnect()
        assertEquals("https://github.com/settings/connections/applications/client-123", url)
        assertNull(store.stored)
        val out = assertIs<SessionState.SignedOut>(manager.state.value)
        assertTrue(out.notice!!.contains("Revoke"))
    }

    @Test
    fun `gh cli tokens are never sent to a revoke page`() = runTest {
        val (manager, _) = manager(ghCli = FakeGhCli(installed = true, token = GitHubToken("gh", TokenSource.GH_CLI)))
        manager.restore()
        manager.signInWithGhCli()
        assertNull(manager.disconnect())
        assertEquals("https://github.com/settings/tokens", manager.revokeUrl(TokenSource.MANUAL))
    }

    @Test
    fun `gh cli fallback and sign out`() = runTest {
        val store = FakeTokenStore()
        val (manager, _) = manager(tokenStore = store, ghCli = FakeGhCli(installed = true, token = GitHubToken("gh", TokenSource.GH_CLI)))
        manager.restore()
        manager.signInWithGhCli()
        assertIs<SessionState.SignedIn>(manager.state.value)
        assertEquals(TokenSource.GH_CLI, store.stored?.source)
        manager.signOut()
        assertEquals("로그아웃했어요.", assertIs<SessionState.SignedOut>(manager.state.value).notice)
        assertNull(store.stored)
    }
}
