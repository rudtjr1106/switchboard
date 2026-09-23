package io.github.rudtjr1106.switchboard.app.testing

import io.github.rudtjr1106.switchboard.ai.ChatMessage
import io.github.rudtjr1106.switchboard.ai.EngineState
import io.github.rudtjr1106.switchboard.ai.GenerationOptions
import io.github.rudtjr1106.switchboard.ai.LanguageModel
import io.github.rudtjr1106.switchboard.ai.LlmEngine
import io.github.rudtjr1106.switchboard.ai.ModelDownloadEvent
import io.github.rudtjr1106.switchboard.ai.ModelSpec
import io.github.rudtjr1106.switchboard.ai.ModelStore
import io.github.rudtjr1106.switchboard.ai.NoticeCopywriter
import io.github.rudtjr1106.switchboard.ai.NoticeDraft
import io.github.rudtjr1106.switchboard.ai.ScreenLabeler
import io.github.rudtjr1106.switchboard.ai.CodeAdapter
import io.github.rudtjr1106.switchboard.app.ai.AiManager
import io.github.rudtjr1106.switchboard.app.di.AppContainer
import io.github.rudtjr1106.switchboard.app.session.SessionManager
import io.github.rudtjr1106.switchboard.app.settings.SettingsStore
import io.github.rudtjr1106.switchboard.app.update.UpdateChecker
import io.github.rudtjr1106.switchboard.app.workspace.WorkspaceManager
import io.github.rudtjr1106.switchboard.config.NoticeTemplate
import io.github.rudtjr1106.switchboard.config.ScreenInfo
import io.github.rudtjr1106.switchboard.github.GitHubApi
import io.github.rudtjr1106.switchboard.github.RepoRef
import io.github.rudtjr1106.switchboard.scanner.AndroidProject
import io.github.rudtjr1106.switchboard.scanner.AndroidProjectScanner
import io.github.rudtjr1106.switchboard.scanner.IntegrationGenerator
import io.github.rudtjr1106.switchboard.scanner.IntegrationPlan
import io.github.rudtjr1106.switchboard.scanner.IntegrationTarget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
import java.nio.file.Files
import java.nio.file.Path

/** 화면 렌더링 테스트용 AppContainer. 네트워크·모델 없이 인터페이스만 가짜로 채운다 */
object UiFakes {

    class FakeModelStore(override val directory: Path = Files.createTempDirectory("models")) : ModelStore {
        override fun installedPath(spec: ModelSpec): Path? = null
        override fun download(spec: ModelSpec): Flow<ModelDownloadEvent> = emptyFlow()
        override fun delete(spec: ModelSpec) = Unit
    }

    class FakeEngine : LlmEngine {
        override val state: StateFlow<EngineState> = MutableStateFlow(EngineState.Idle)
        override val model: LanguageModel? = null
        override suspend fun load(spec: ModelSpec, path: Path): LanguageModel = error("unused")
        override suspend fun unload() = Unit
    }

    fun container(
        scope: CoroutineScope,
        api: GitHubApi,
        settings: SettingsStore = SettingsStore(Files.createTempDirectory("switchboard-ui").resolve("settings.json")),
        scanner: AndroidProjectScanner = object : AndroidProjectScanner {
            override suspend fun scan(root: Path): AndroidProject = error("unused")
        },
        generator: IntegrationGenerator = object : IntegrationGenerator {
            override fun plan(project: AndroidProject, target: IntegrationTarget): IntegrationPlan = error("unused")
        },
        // 마지막 자리라 `container(scope, api) { repo }` 처럼 뒤에 붙여 줄 수 있다
        repositories: (RepoRef) -> io.github.rudtjr1106.switchboard.github.ConfigRepository = { error("unused") },
    ): AppContainer {
        val factory = FakeClientFactory(api, repositories)
        val session = SessionManager(FakeTokenStore(), FakeGhCli(installed = true), FakeDeviceFlow(emptyList()), factory, settings, scope)
        val ai = AiManager(
            store = FakeModelStore(),
            engine = FakeEngine(),
            settings = settings,
            scope = scope,
            supported = true,
            platformDescription = "macOS aarch64",
            copywriterFactory = {
                object : NoticeCopywriter {
                    override suspend fun polish(title: String, body: String, template: NoticeTemplate, titleLimit: Int, bodyLimit: Int) = NoticeDraft(title, body)
                    override suspend fun draft(situation: String, template: NoticeTemplate, titleLimit: Int, bodyLimit: Int) = NoticeDraft("", "")
                }
            },
            labelerFactory = { object : ScreenLabeler { override suspend fun label(screenIds: List<String>, appDescription: String?) = screenIds.map { ScreenInfo(it) } } },
            codeAdapterFactory = { object : CodeAdapter { override suspend fun adapt(fileName: String, template: String, projectNotes: String) = template } },
        )
        return AppContainer(
            scope = scope,
            settings = settings,
            session = session,
            workspace = WorkspaceManager(settings, factory, scope),
            clientFactory = factory,
            ai = ai,
            scanner = scanner,
            generator = generator,
            updater = UpdateChecker(settings, scope),
        )
    }
}
