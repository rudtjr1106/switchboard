package io.github.rudtjr1106.switchboard.app.workspace

import io.github.rudtjr1106.switchboard.app.session.SessionState
import io.github.rudtjr1106.switchboard.app.settings.SettingsStore
import io.github.rudtjr1106.switchboard.app.testing.FakeClientFactory
import io.github.rudtjr1106.switchboard.app.testing.FakeConfigRepository
import io.github.rudtjr1106.switchboard.app.testing.FakeGitHubApi
import io.github.rudtjr1106.switchboard.config.Fixtures
import io.github.rudtjr1106.switchboard.github.GitHubRepo
import io.github.rudtjr1106.switchboard.github.GitHubToken
import io.github.rudtjr1106.switchboard.github.GitHubUser
import io.github.rudtjr1106.switchboard.github.RepoRef
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
class WorkspaceManagerTest {

    private val user = GitHubUser("octocat", null, null, "https://github.com/octocat")
    private val configRef = RepoRef("octocat", "app-config")
    private val otherRef = RepoRef("octocat", "not-a-config")

    private fun TestScope.setUp(lastRepository: RepoRef? = null): Triple<WorkspaceManager, SessionState.SignedIn, SettingsStore> {
        val repos = mapOf(
            configRef to GitHubRepo(configRef, configRef.htmlUrl, topics = listOf("switchboard-config")),
            otherRef to GitHubRepo(otherRef, otherRef.htmlUrl),
        )
        val api = FakeGitHubApi(user, repos = repos)
        val factory = FakeClientFactory(api) { ref ->
            if (ref == configRef) FakeConfigRepository(ref, configText = Fixtures.iosConfigText, schemaText = Fixtures.iosSchemaText)
            else object : FakeConfigRepository(ref, configText = "", schemaText = "") {
                override suspend fun load() = throw io.github.rudtjr1106.switchboard.github.GitHubException.NotFound("파일 schema.json")
            }
        }
        val settings = SettingsStore(Files.createTempDirectory("switchboard-test").resolve("settings.json"))
        settings.update { it.copy(lastRepository = lastRepository) }
        val scope = TestScope(UnconfinedTestDispatcher(testScheduler))
        val session = SessionState.SignedIn(user, GitHubToken("t", TokenSource.MANUAL), api, listOf("repo"))
        return Triple(WorkspaceManager(settings, factory, scope), session, settings)
    }

    @Test
    fun `start without a remembered repo lists repos with the topic`() = runTest {
        val (workspace, session, _) = setUp()
        workspace.start(session)
        val browsing = assertIs<WorkspaceState.Browsing>(workspace.state.value)
        assertEquals(listOf(configRef), browsing.repos.map { it.ref })
        assertEquals(listOf("octocat"), browsing.owners.map { it.login })
    }

    @Test
    fun `start with a remembered repo opens it directly`() = runTest {
        val (workspace, session, _) = setUp(lastRepository = configRef)
        workspace.start(session)
        val open = assertIs<WorkspaceState.Open>(workspace.state.value)
        assertEquals(configRef, open.editor.ref)
    }

    @Test
    fun `opening a repo without config files falls back to browsing with an error`() = runTest {
        val (workspace, session, settings) = setUp(lastRepository = otherRef)
        workspace.start(session)
        val browsing = assertIs<WorkspaceState.Browsing>(workspace.state.value)
        assertEquals("octocat/not-a-config 은 설정 저장소가 아니에요. app-config.json 과 schema.json 이 있어야 해요.", browsing.openError)
        assertNull(settings.current.lastRepository)
    }

    @Test
    fun `switching repositories keeps the editor so back restores unapplied edits`() = runTest {
        val (workspace, session, _) = setUp(lastRepository = configRef)
        workspace.start(session)
        val open = assertIs<WorkspaceState.Open>(workspace.state.value)
        open.editor.addNotice()
        assertTrue(open.editor.current.hasChanges)

        workspace.switchRepository(session)
        val browsing = assertIs<WorkspaceState.Browsing>(workspace.state.value)
        assertEquals(open, browsing.returnTo)
        assertEquals(open.editor, workspace.editorInUse)

        workspace.back()
        val back = assertIs<WorkspaceState.Open>(workspace.state.value)
        assertTrue(back.editor === open.editor)
        assertTrue(back.editor.current.hasChanges)
    }

    @Test
    fun `picking the repo that is already open goes straight back to it`() = runTest {
        val (workspace, session, _) = setUp(lastRepository = configRef)
        workspace.start(session)
        val open = assertIs<WorkspaceState.Open>(workspace.state.value)
        workspace.switchRepository(session)
        workspace.open(session, configRef)
        assertTrue(assertIs<WorkspaceState.Open>(workspace.state.value).editor === open.editor)
    }
}
