package io.github.rudtjr1106.switchboard.app.ai

import io.github.rudtjr1106.switchboard.ai.ChatMessage
import io.github.rudtjr1106.switchboard.ai.CodeAdapter
import io.github.rudtjr1106.switchboard.ai.EngineState
import io.github.rudtjr1106.switchboard.ai.GenerationOptions
import io.github.rudtjr1106.switchboard.ai.LanguageModel
import io.github.rudtjr1106.switchboard.ai.LlmEngine
import io.github.rudtjr1106.switchboard.ai.ModelCatalog
import io.github.rudtjr1106.switchboard.ai.ModelDownloadEvent
import io.github.rudtjr1106.switchboard.ai.ModelSpec
import io.github.rudtjr1106.switchboard.ai.ModelStore
import io.github.rudtjr1106.switchboard.ai.NoticeCopywriter
import io.github.rudtjr1106.switchboard.ai.ScreenLabeler
import io.github.rudtjr1106.switchboard.app.settings.SettingsStore
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class AiManagerTest {

    private class Store(val installed: MutableSet<String>, val downloadEvents: List<ModelDownloadEvent> = emptyList()) : ModelStore {
        override val directory: Path = Files.createTempDirectory("models")
        override fun installedPath(spec: ModelSpec): Path? = if (spec.id in installed) directory.resolve(spec.fileName) else null
        override fun download(spec: ModelSpec): Flow<ModelDownloadEvent> = if (downloadEvents.isEmpty()) emptyFlow() else flowOf(*downloadEvents.toTypedArray()).also { installed += spec.id }
        override fun delete(spec: ModelSpec) { installed -= spec.id }
    }

    private class Engine : LlmEngine {
        val loads = mutableListOf<String>()
        override val state = MutableStateFlow<EngineState>(EngineState.Idle)
        override var model: LanguageModel? = null
        override suspend fun load(spec: ModelSpec, path: Path): LanguageModel {
            loads += spec.id
            state.value = EngineState.Ready(spec)
            return object : LanguageModel {
                override val spec = spec
                override suspend fun complete(messages: List<ChatMessage>, options: GenerationOptions) = ""
                override fun stream(messages: List<ChatMessage>, options: GenerationOptions) = emptyFlow<String>()
                override fun close() = Unit
            }.also { model = it }
        }
        override suspend fun unload() { state.value = EngineState.Idle; model = null }
    }

    private fun TestScope.manager(store: Store, engine: Engine, aiEnabled: Boolean = true): Pair<AiManager, SettingsStore> {
        val settings = SettingsStore(Files.createTempDirectory("settings").resolve("settings.json"))
        settings.update { it.copy(aiEnabled = aiEnabled) }
        val manager = AiManager(
            store = store, engine = engine, settings = settings, scope = TestScope(UnconfinedTestDispatcher(testScheduler)),
            supported = true, platformDescription = "test",
            copywriterFactory = { object : NoticeCopywriter {
                override suspend fun polish(title: String, body: String, template: io.github.rudtjr1106.switchboard.config.NoticeTemplate, titleLimit: Int, bodyLimit: Int) = error("unused")
                override suspend fun draft(situation: String, template: io.github.rudtjr1106.switchboard.config.NoticeTemplate, titleLimit: Int, bodyLimit: Int) = error("unused")
            } },
            labelerFactory = { object : ScreenLabeler { override suspend fun label(screenIds: List<String>, appDescription: String?) = error("unused") } },
            codeAdapterFactory = { object : CodeAdapter { override suspend fun adapt(fileName: String, template: String, projectNotes: String) = error("unused") } },
        )
        return manager to settings
    }

    @Test
    fun `a downloaded model is turned on at startup`() = runTest {
        val engine = Engine()
        val (manager, _) = manager(Store(mutableSetOf(ModelCatalog.GEMMA_3_4B.id)), engine)
        assertEquals(listOf(ModelCatalog.GEMMA_3_4B.id), engine.loads)
        assertTrue(manager.state.value.isReady)
    }

    @Test
    fun `the installed model is used when the selected one is missing`() = runTest {
        val engine = Engine()
        val (manager, settings) = manager(Store(mutableSetOf(ModelCatalog.GEMMA_3_1B.id)), engine)
        assertEquals(listOf(ModelCatalog.GEMMA_3_1B.id), engine.loads)
        assertEquals(ModelCatalog.GEMMA_3_1B.id, settings.current.modelId)
        assertEquals(ModelCatalog.GEMMA_3_1B, manager.state.value.selected)
    }

    @Test
    fun `turning ai off is remembered for the next launch`() = runTest {
        val store = Store(mutableSetOf(ModelCatalog.GEMMA_3_4B.id))
        val (manager, settings) = manager(store, Engine())
        manager.unload()
        assertFalse(settings.current.aiEnabled)

        val next = Engine()
        manager(store, next, aiEnabled = settings.current.aiEnabled)
        assertTrue(next.loads.isEmpty(), "끈 채로 닫았으면 자동으로 켜지 않는다")
    }

    @Test
    fun `nothing is loaded without a downloaded model, and a finished download turns ai on`() = runTest {
        val engine = Engine()
        val store = Store(mutableSetOf(), downloadEvents = listOf(ModelDownloadEvent.Done(Path.of("x"))))
        val (manager, settings) = manager(store, engine, aiEnabled = false)
        assertTrue(engine.loads.isEmpty())
        manager.download(ModelCatalog.GEMMA_3_1B)
        assertEquals(listOf(ModelCatalog.GEMMA_3_1B.id), engine.loads)
        assertTrue(settings.current.aiEnabled)
    }
}
