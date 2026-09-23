package io.github.rudtjr1106.switchboard.app.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Density
import io.github.rudtjr1106.switchboard.app.editor.EditorModel
import io.github.rudtjr1106.switchboard.app.editor.Selection
import io.github.rudtjr1106.switchboard.app.session.SessionManager
import io.github.rudtjr1106.switchboard.app.session.SessionState
import io.github.rudtjr1106.switchboard.app.setup.ProjectSetupModel
import io.github.rudtjr1106.switchboard.app.setup.SetupStep
import io.github.rudtjr1106.switchboard.app.testing.FakeConfigRepository
import io.github.rudtjr1106.switchboard.app.testing.FakeGitHubApi
import io.github.rudtjr1106.switchboard.app.testing.UiFakes
import io.github.rudtjr1106.switchboard.app.ui.editor.EditorScreen
import io.github.rudtjr1106.switchboard.app.ui.login.LoginScreen
import io.github.rudtjr1106.switchboard.app.ui.settings.AccountDialog
import io.github.rudtjr1106.switchboard.app.ui.settings.SettingsScreen
import io.github.rudtjr1106.switchboard.app.ui.setup.ProjectSetupScreen
import io.github.rudtjr1106.switchboard.app.ui.theme.SwitchboardTheme
import io.github.rudtjr1106.switchboard.app.ui.workspace.CreateRepoDialog
import io.github.rudtjr1106.switchboard.app.ui.workspace.RepoPickerScreen
import io.github.rudtjr1106.switchboard.app.workspace.WorkspaceState
import io.github.rudtjr1106.switchboard.config.Fixtures
import io.github.rudtjr1106.switchboard.config.SchemaRenderer
import io.github.rudtjr1106.switchboard.config.ValueSpec
import io.github.rudtjr1106.switchboard.config.ValueType
import io.github.rudtjr1106.switchboard.github.GitHubOwner
import io.github.rudtjr1106.switchboard.github.GitHubRepo
import io.github.rudtjr1106.switchboard.github.GitHubToken
import io.github.rudtjr1106.switchboard.github.GitHubUser
import io.github.rudtjr1106.switchboard.github.OwnerType
import io.github.rudtjr1106.switchboard.github.RepoPermissions
import io.github.rudtjr1106.switchboard.github.RepoRef
import io.github.rudtjr1106.switchboard.github.TokenSource
import io.github.rudtjr1106.switchboard.scanner.GradleAndroidProjectScanner
import io.github.rudtjr1106.switchboard.scanner.TemplateIntegrationGenerator
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import java.time.LocalDate
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonPrimitive
import org.jetbrains.skia.EncodedImageFormat

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

/**
 * 안드로이드 팀에게 보여 줄 화면들 (docs/images)
 *
 * 프로젝트 세팅은 실제 스캐너·생성기를 그대로 쓰고, 임시로 만든 작은 Android 프로젝트를 읽힌다.
 * 그래서 문서의 그림이 실제로 나오는 결과와 어긋나지 않는다.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class GuideScreenshotTest {

    private val outputDir: File =
        (System.getenv("SWITCHBOARD_SCREENSHOTS_DIR")?.let(::File) ?: File("build/screenshots")).apply { mkdirs() }

    private val user = GitHubUser("rudtjr1106", "조경석", null, "https://github.com/rudtjr1106")
    private val umcRef = RepoRef("UMC-PRODUCT", "umc-product-android-config")

    private fun render(name: String, width: Int = 1280, height: Int = 800, content: @Composable () -> Unit) {
        val scene = ImageComposeScene(width * 2, height * 2, density = Density(2f), coroutineContext = Dispatchers.Unconfined) {
            // 배경은 AppRoot 가 칠한다. 화면 하나만 그릴 때도 같은 배경을 깔아야 실제와 같아 보인다
            SwitchboardTheme(darkTheme = false) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) { content() }
            }
        }
        try {
            var image = scene.render(0L)
            repeat(60) { frame -> image = scene.render((frame + 1) * 16_000_000L) }
            val png = image.encodeToData(EncodedImageFormat.PNG) ?: error("PNG 인코딩 실패")
            File(outputDir, "$name.png").writeBytes(png.bytes)
        } finally {
            scene.close()
        }
        assertTrue(File(outputDir, "$name.png").length() > 0)
    }

    private fun sessionFor(api: FakeGitHubApi): SessionState.SignedIn =
        SessionState.SignedIn(user, GitHubToken("t", TokenSource.DEVICE_FLOW), api, listOf("repo", "workflow", "read:org"))

    /**
     * 스캔·계획은 진짜 파일을 읽어 오므로 실제 시간이 걸린다. runTest 의 가상 시간에서는 withTimeout 이 바로 터져서
     * 실시간으로 기다린다
     */
    private inline fun <reified T : SetupStep> awaitStep(model: ProjectSetupModel): T =
        runBlocking { withTimeout(60_000) { model.state.first { it is T } as T } }

    /** 팀원들이 실제로 쓰는 모양에 가까운 작은 Android 프로젝트 (Hilt + Retrofit + Compose Navigation) */
    private fun sampleAndroidProject(): Path {
        val root = Files.createTempDirectory("damoim-android")
        fun write(path: String, text: String) {
            val file = root.resolve(path)
            Files.createDirectories(file.parent)
            file.writeText(text.trimIndent())
        }
        write("settings.gradle.kts", """
            rootProject.name = "damoim-android"
            include(":app", ":core:data")
        """)
        write("app/build.gradle.kts", """
            plugins {
                id("com.android.application")
                id("com.google.dagger.hilt.android")
            }
            android { namespace = "com.damoim.app" }
            dependencies {
                implementation("androidx.navigation:navigation-compose:2.9.0")
                implementation("androidx.compose.material3:material3:1.3.0")
                implementation("com.google.dagger:hilt-android:2.57")
                implementation("com.squareup.retrofit2:retrofit:3.0.0")
                implementation("com.squareup.retrofit2:converter-gson:3.0.0")
            }
        """)
        write("core/data/build.gradle.kts", """
            plugins { id("com.android.library") }
            android { namespace = "com.damoim.core.data" }
        """)
        write("app/src/main/java/com/damoim/app/navigation/Route.kt", """
            package com.damoim.app.navigation

            import kotlinx.serialization.Serializable

            sealed interface Route {
                /** 홈 */
                @Serializable data object Home : Route

                /** 모임 찾기 */
                @Serializable data object MeetingSearch : Route

                /** 모임 상세 */
                @Serializable data class MeetingDetail(val meetingId: Long) : Route

                /** 채팅방 */
                @Serializable data class ChatRoom(val roomId: Long) : Route

                /* 마이 페이지 */
                /** 내 프로필 */
                @Serializable data object MyProfile : Route

                /** 알림 설정 */
                @Serializable data object NotificationSetting : Route
            }
        """)
        return root
    }

    @Test
    fun `android project setup steps`() = runTest {
        val scope = TestScope(UnconfinedTestDispatcher(testScheduler))
        val api = FakeGitHubApi(user)
        val repo = FakeConfigRepository(umcRef, configText = Fixtures.androidConfigText, schemaText = Fixtures.androidSchemaText)
        val container = UiFakes.container(
            scope,
            api,
            scanner = GradleAndroidProjectScanner(),
            generator = TemplateIntegrationGenerator(),
        ) { repo }
        val session = sessionFor(api)
        val editor = EditorModel(repo, scope) { LocalDate.of(2026, 9, 22) }
        editor.load()
        val model = ProjectSetupModel(session, editor, container.scanner, container.generator, container.ai, container.settings, scope)

        @Composable
        fun screen() = ProjectSetupScreen(container, session, editor, window = null, onBack = {}, model = model)

        render("setup-1-pick") { screen() }

        model.pick(sampleAndroidProject())
        awaitStep<SetupStep.Review>(model)
        render("setup-2-review") { screen() }

        model.toLabeling()
        render("setup-3-labeling") { screen() }

        model.buildPlan()
        awaitStep<SetupStep.Plan>(model)
        render("setup-4-plan") { screen() }

        // 이미 자기 안내 화면이 있는 팀은 그 파일만 체크를 풀면 된다
        val hostPath = (model.state.value as SetupStep.Plan).plan.files.first { it.path.fileName.toString() == "RemoteNoticeHost.kt" }.path
        model.toggleFile(hostPath)
        assertTrue((model.state.value as SetupStep.Plan).skipped == setOf(hostPath), "이 파일만 빠져야 한다")
        render("setup-4-plan-skip") { screen() }
        model.toggleFile(hostPath)

        // 저장소 PR 까지 가면 가짜 API 에 기대야 하므로, 파일 쓰기만 켜고 완료 화면(직접 할 일)을 남긴다
        model.updatePlan { it.copy(updateRepo = false, updateReadme = false) }
        model.run()
        awaitStep<SetupStep.Done>(model)
        render("setup-5-done") { screen() }
    }

    @Test
    fun `free key-value screen`() = runTest {
        val scope = TestScope(UnconfinedTestDispatcher(testScheduler))
        val api = FakeGitHubApi(user)
        val specs = listOf(
            ValueSpec("eventBannerOn", ValueType.BOOLEAN, "이벤트 배너", default = JsonPrimitive(true)),
            ValueSpec(
                "maxUploadCount", ValueType.INTEGER, "한 번에 올릴 수 있는 사진",
                description = "모임 후기에 올릴 수 있는 사진 개수", default = JsonPrimitive(5), minimum = 1.0, maximum = 10.0,
            ),
            ValueSpec(
                "homeTabOrder", ValueType.STRING, "홈 탭 정렬",
                default = JsonPrimitive("recent"), options = listOf("recent", "popular"),
            ),
            ValueSpec("noticeUrl", ValueType.STRING, "공지 링크", default = JsonPrimitive(""), maxLength = 120),
        )
        val schemaText = SchemaRenderer.withValues(Fixtures.androidSchemaText, specs)
        val configText = Fixtures.androidConfigText.trimEnd().removeSuffix("}").trimEnd().removeSuffix(",") +
            ",\n  \"values\": {\n    \"eventBannerOn\": true,\n    \"maxUploadCount\": 3,\n" +
            "    \"homeTabOrder\": \"popular\",\n    \"noticeUrl\": \"https://umc.app/notice\"\n  }\n}\n"
        val repo = FakeConfigRepository(umcRef, configText = configText, schemaText = schemaText)
        val container = UiFakes.container(scope, api) { repo }
        val session = sessionFor(api)
        val editor = EditorModel(repo, scope) { LocalDate.of(2026, 9, 22) }
        editor.load()
        editor.select(Selection.Values)
        val gitHubRepo = GitHubRepo(umcRef, umcRef.htmlUrl, permissions = RepoPermissions(push = true))
        render("editor-values") { EditorScreen(container, session, editor, gitHubRepo, onOpenSettings = {}, onOpenSetup = {}) }
    }

    @Test
    fun `minimum version and create repo`() = runTest {
        val scope = TestScope(UnconfinedTestDispatcher(testScheduler))
        val api = FakeGitHubApi(user)
        val repo = FakeConfigRepository(umcRef, configText = Fixtures.androidConfigText, schemaText = Fixtures.androidSchemaText)
        val container = UiFakes.container(scope, api) { repo }
        val session = sessionFor(api)
        val editor = EditorModel(repo, scope) { LocalDate.of(2026, 9, 22) }
        editor.load()
        editor.select(Selection.MinimumVersion)
        val gitHubRepo = GitHubRepo(umcRef, umcRef.htmlUrl, permissions = RepoPermissions(push = true))
        render("editor-minimum-version") { EditorScreen(container, session, editor, gitHubRepo, onOpenSettings = {}, onOpenSetup = {}) }

        val owners = listOf(GitHubOwner(user.login, OwnerType.USER), GitHubOwner("UMC-PRODUCT", OwnerType.ORGANIZATION))
        render("create-repo") { CreateRepoDialog(container, session, owners, onClose = {}) }
    }
}
