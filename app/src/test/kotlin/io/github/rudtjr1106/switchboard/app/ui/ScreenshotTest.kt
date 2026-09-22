package io.github.rudtjr1106.switchboard.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import io.github.rudtjr1106.switchboard.app.editor.EditorModel
import io.github.rudtjr1106.switchboard.app.session.SessionManager
import io.github.rudtjr1106.switchboard.app.session.SessionState
import io.github.rudtjr1106.switchboard.app.testing.FakeConfigRepository
import io.github.rudtjr1106.switchboard.app.testing.FakeGitHubApi
import io.github.rudtjr1106.switchboard.app.testing.UiFakes
import io.github.rudtjr1106.switchboard.app.ui.editor.EditorScreen
import io.github.rudtjr1106.switchboard.app.ui.login.LoginScreen
import io.github.rudtjr1106.switchboard.app.ui.settings.AccountDialog
import io.github.rudtjr1106.switchboard.app.ui.settings.SettingsScreen
import io.github.rudtjr1106.switchboard.app.ui.theme.SwitchboardTheme
import io.github.rudtjr1106.switchboard.app.ui.workspace.RepoPickerScreen
import io.github.rudtjr1106.switchboard.app.workspace.WorkspaceState
import io.github.rudtjr1106.switchboard.config.Fixtures
import io.github.rudtjr1106.switchboard.github.GitHubOwner
import io.github.rudtjr1106.switchboard.github.GitHubRepo
import io.github.rudtjr1106.switchboard.github.GitHubToken
import io.github.rudtjr1106.switchboard.github.GitHubUser
import io.github.rudtjr1106.switchboard.github.OwnerType
import io.github.rudtjr1106.switchboard.github.RepoPermissions
import io.github.rudtjr1106.switchboard.github.RepoRef
import io.github.rudtjr1106.switchboard.github.TokenSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.jetbrains.skia.EncodedImageFormat
import java.io.File
import java.time.Instant
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * 주요 화면을 헤드리스로 그려 PNG 로 남긴다 (build/screenshots, 또는 SWITCHBOARD_SCREENSHOTS_DIR)
 *
 * 화면 회귀를 눈으로 확인하고 README 이미지를 만드는 데 쓴다. 픽셀 비교는 하지 않고 렌더링이 예외 없이 끝나는지만 검사한다.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ScreenshotTest {

    private val outputDir: File = (System.getenv("SWITCHBOARD_SCREENSHOTS_DIR")?.let(::File) ?: File("build/screenshots")).apply { mkdirs() }

    private val user = GitHubUser("rudtjr1106", "조경석", null, "https://github.com/rudtjr1106")
    private val umcRef = RepoRef("UMC-PRODUCT", "umc-product-android-config")

    private fun render(name: String, width: Int = 1280, height: Int = 800, content: @Composable () -> Unit) {
        // 2배 밀도로 그리므로 픽셀 크기는 dp 의 두 배다 (1280×800dp → 2560×1600px)
        val scene = ImageComposeScene(width * 2, height * 2, density = Density(2f), coroutineContext = Dispatchers.Unconfined) {
            SwitchboardTheme(darkTheme = false) { content() }
        }
        try {
            // LaunchedEffect 와 다이얼로그 등장 애니메이션이 끝나도록 1초 분량의 프레임을 돌린다
            var image = scene.render(0L)
            repeat(60) { frame -> image = scene.render((frame + 1) * 16_000_000L) }
            val png = image.encodeToData(EncodedImageFormat.PNG) ?: error("PNG 인코딩 실패")
            File(outputDir, "$name.png").writeBytes(png.bytes)
        } finally {
            scene.close()
        }
        assertTrue(File(outputDir, "$name.png").length() > 0)
    }

    private fun sessionFor(scope: TestScope, api: FakeGitHubApi): SessionState.SignedIn =
        SessionState.SignedIn(user, GitHubToken("t", TokenSource.DEVICE_FLOW), api, listOf("repo", "workflow", "read:org"))

    @Test
    fun `login screens`() = runTest {
        val scope = TestScope(UnconfinedTestDispatcher(testScheduler))
        val container = UiFakes.container(scope, FakeGitHubApi(user))
        container.settings.update { it.copy(githubClientId = "Ov23liExample") }
        val manager: SessionManager = container.session
        render("login") { LoginScreen(manager, SessionState.SignedOut(ghCliAvailable = true), onOpenSettings = {}) }
        render("login-device-code") {
            LoginScreen(manager, SessionState.SigningIn("7B4K-QM2X", "https://github.com/login/device", Instant.now().plusSeconds(900)), onOpenSettings = {})
        }
    }

    @Test
    fun `repo picker`() = runTest {
        val scope = TestScope(UnconfinedTestDispatcher(testScheduler))
        val api = FakeGitHubApi(user)
        val container = UiFakes.container(scope, api)
        val session = sessionFor(scope, api)
        val repos = listOf(
            GitHubRepo(umcRef, umcRef.htmlUrl, description = "umc-product-android 앱이 원격으로 읽어가는 설정 저장소", topics = listOf("switchboard-config"), permissions = RepoPermissions(push = true)),
            GitHubRepo(RepoRef("rudtjr1106", "damoim-android-config"), "https://github.com/rudtjr1106/damoim-android-config", description = "앱이 원격으로 읽어가는 설정 저장소", permissions = RepoPermissions(push = true)),
        )
        val state = WorkspaceState.Browsing(repos = repos, owners = listOf(GitHubOwner(user.login, OwnerType.USER), GitHubOwner("UMC-PRODUCT", OwnerType.ORGANIZATION)), loading = false)
        render("repo-picker") { RepoPickerScreen(container, session, state, onOpenSettings = {}) }
    }

    @Test
    fun `editor with the umc android config`() = runTest {
        val scope = TestScope(UnconfinedTestDispatcher(testScheduler))
        val api = FakeGitHubApi(user)
        val repo = FakeConfigRepository(umcRef, configText = Fixtures.androidConfigText, schemaText = Fixtures.androidSchemaText)
        val container = UiFakes.container(scope, api) { repo }
        val session = sessionFor(scope, api)
        val editor = EditorModel(repo, scope) { LocalDate.of(2026, 9, 22) }
        editor.load()
        // 사이드바에 상태가 다양하게 보이도록 안내를 몇 개 더 만든다
        editor.addNotice()
        val kill = editor.current.draft.notices.last().id
        editor.updateNotice(kill) { it.copy(template = "BLOCKING", enabled = true, title = "서비스 점검 중이에요", body = "더 나은 서비스를 위해 점검하고 있어요. 잠시 후 다시 이용해주세요.") }
        editor.addNotice()
        val mail = editor.current.draft.notices.last().id
        editor.updateNotice(mail) { it.copy(screen = "EmailSignUp", title = "인증 메일이 안 올 수 있어요", body = "지금 이메일 발송량이 많아 인증 메일이 늦거나 도착하지 않을 수 있어요.", until = "2026-12-31") }
        val first = editor.current.draft.notices.first().id
        editor.updateNotice(first) { it.copy(enabled = true, until = "2026-09-30") }
        editor.select(io.github.rudtjr1106.switchboard.app.editor.Selection.NoticeItem(first))
        val gitHubRepo = GitHubRepo(umcRef, umcRef.htmlUrl, permissions = RepoPermissions(push = true))

        render("editor") { EditorScreen(container, session, editor, gitHubRepo, onOpenSettings = {}, onOpenSetup = {}) }

        editor.select(io.github.rudtjr1106.switchboard.app.editor.Selection.NoticeItem(kill))
        render("editor-blocking") { EditorScreen(container, session, editor, gitHubRepo, onOpenSettings = {}, onOpenSetup = {}) }

        editor.beginApply()
        render("apply-dialog") { EditorScreen(container, session, editor, gitHubRepo, onOpenSettings = {}, onOpenSetup = {}) }
        editor.closeApply()
    }

    @Test
    fun `settings page and account dialogs`() = runTest {
        val scope = TestScope(UnconfinedTestDispatcher(testScheduler))
        val api = FakeGitHubApi(user)
        val container = UiFakes.container(scope, api)
        container.session.signInWithToken("t")
        render("settings") { SettingsScreen(container, onBack = {}) }
        render("settings-logout") { SettingsScreen(container, onBack = {}, initialDialog = AccountDialog.LOGOUT) }
        render("settings-disconnect") { SettingsScreen(container, onBack = {}, initialDialog = AccountDialog.DISCONNECT) }
    }

    @Test
    fun `repo picker opened from the editor has a way back`() = runTest {
        val scope = TestScope(UnconfinedTestDispatcher(testScheduler))
        val api = FakeGitHubApi(user)
        val repo = FakeConfigRepository(umcRef, configText = Fixtures.androidConfigText, schemaText = Fixtures.androidSchemaText)
        val container = UiFakes.container(scope, api) { repo }
        val session = sessionFor(scope, api)
        val editor = EditorModel(repo, scope) { LocalDate.of(2026, 9, 22) }
        editor.load()
        editor.addNotice()
        val open = WorkspaceState.Open(editor, GitHubRepo(umcRef, umcRef.htmlUrl, permissions = RepoPermissions(push = true)))
        val state = WorkspaceState.Browsing(owners = listOf(GitHubOwner(user.login, OwnerType.USER)), loading = false, returnTo = open)
        render("repo-picker-back") { RepoPickerScreen(container, session, state, onOpenSettings = {}) }
    }
}
