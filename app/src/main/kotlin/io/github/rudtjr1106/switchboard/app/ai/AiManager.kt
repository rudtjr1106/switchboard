package io.github.rudtjr1106.switchboard.app.ai

import io.github.oshai.kotlinlogging.KotlinLogging
import io.github.rudtjr1106.switchboard.ai.AiUnavailableException
import io.github.rudtjr1106.switchboard.ai.CodeAdapter
import io.github.rudtjr1106.switchboard.ai.EngineState
import io.github.rudtjr1106.switchboard.ai.LlmEngine
import io.github.rudtjr1106.switchboard.ai.ModelCatalog
import io.github.rudtjr1106.switchboard.ai.ModelDownloadEvent
import io.github.rudtjr1106.switchboard.ai.ModelSpec
import io.github.rudtjr1106.switchboard.ai.ModelStore
import io.github.rudtjr1106.switchboard.ai.NoticeCopywriter
import io.github.rudtjr1106.switchboard.ai.ScreenLabeler
import io.github.rudtjr1106.switchboard.app.settings.SettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private val logger = KotlinLogging.logger {}

sealed interface ModelStatus {
    data object NotInstalled : ModelStatus
    data class Downloading(val fraction: Float, val downloadedBytes: Long, val totalBytes: Long) : ModelStatus
    data object Installed : ModelStatus
    data object Loading : ModelStatus
    data object Ready : ModelStatus
    data class Failed(val message: String) : ModelStatus
}

data class AiState(
    val supported: Boolean,
    val platform: String,
    val selected: ModelSpec,
    val statuses: Map<String, ModelStatus>,
) {
    val selectedStatus: ModelStatus get() = statuses[selected.id] ?: ModelStatus.NotInstalled
    val isReady: Boolean get() = selectedStatus == ModelStatus.Ready
    val isBusy: Boolean get() = selectedStatus is ModelStatus.Downloading || selectedStatus == ModelStatus.Loading
}

/**
 * 모델 내려받기·올리기와 AI 기능 서비스를 한 곳에서 관리한다
 *
 * 앱은 모델을 자동으로 올리지 않는다. 사용자가 설정에서 켜야 메모리(2~3GB)를 쓴다.
 */
class AiManager(
    private val store: ModelStore,
    private val engine: LlmEngine,
    private val settings: SettingsStore,
    private val scope: CoroutineScope,
    private val supported: Boolean,
    private val platformDescription: String,
    copywriterFactory: (LlmEngine) -> NoticeCopywriter,
    labelerFactory: (LlmEngine) -> ScreenLabeler,
    codeAdapterFactory: (LlmEngine) -> CodeAdapter,
) {
    val copywriter: NoticeCopywriter = copywriterFactory(engine)
    val labeler: ScreenLabeler = labelerFactory(engine)
    val codeAdapter: CodeAdapter = codeAdapterFactory(engine)

    private val _state = MutableStateFlow(
        AiState(
            supported = supported,
            platform = platformDescription,
            selected = settings.current.modelId?.let(ModelCatalog::find) ?: ModelCatalog.all.first { it.recommended },
            statuses = ModelCatalog.all.associate { it.id to if (store.isInstalled(it)) ModelStatus.Installed else ModelStatus.NotInstalled },
        ),
    )
    val state: StateFlow<AiState> = _state.asStateFlow()

    private var downloadJob: Job? = null

    init {
        scope.launch {
            engine.state.collect { engineState ->
                when (engineState) {
                    EngineState.Idle -> Unit
                    is EngineState.Loading -> setStatus(engineState.spec, ModelStatus.Loading)
                    is EngineState.Ready -> setStatus(engineState.spec, ModelStatus.Ready)
                    is EngineState.Failed -> setStatus(engineState.spec, ModelStatus.Failed(engineState.message))
                }
            }
        }
    }

    fun select(spec: ModelSpec) {
        settings.update { it.copy(modelId = spec.id) }
        _state.update { it.copy(selected = spec) }
    }

    fun download(spec: ModelSpec) {
        if (downloadJob?.isActive == true) return
        downloadJob = scope.launch {
            store.download(spec).collect { event ->
                when (event) {
                    is ModelDownloadEvent.Progress -> setStatus(spec, ModelStatus.Downloading(event.fraction, event.downloadedBytes, event.totalBytes))
                    is ModelDownloadEvent.Done -> setStatus(spec, ModelStatus.Installed)
                    is ModelDownloadEvent.Failed -> {
                        logger.warn(event.cause) { "모델 다운로드 실패: ${event.message}" }
                        setStatus(spec, ModelStatus.Failed(event.message))
                    }
                }
            }
        }
    }

    fun cancelDownload(spec: ModelSpec) {
        downloadJob?.cancel()
        downloadJob = null
        setStatus(spec, if (store.isInstalled(spec)) ModelStatus.Installed else ModelStatus.NotInstalled)
    }

    fun load(spec: ModelSpec = _state.value.selected) {
        val path = store.installedPath(spec) ?: return
        scope.launch {
            try {
                engine.load(spec, path)
            } catch (e: AiUnavailableException) {
                setStatus(spec, ModelStatus.Failed(e.message ?: "모델을 올리지 못했어요"))
            } catch (e: Exception) {
                logger.error(e) { "모델 로드 중 예상치 못한 오류" }
                setStatus(spec, ModelStatus.Failed(e.message ?: "모델을 올리지 못했어요"))
            }
        }
    }

    fun unload() {
        scope.launch {
            val current = (engine.state.value as? EngineState.Ready)?.spec
            engine.unload()
            if (current != null) setStatus(current, ModelStatus.Installed)
        }
    }

    /** 내려받은 모델 전체 크기. 연결 해제 확인 창에 보여준다 */
    fun installedSizeBytes(): Long = ModelCatalog.all.filter(store::isInstalled).sumOf { it.sizeBytes }

    /** 연결 해제할 때 모델을 모두 내리고 지운다 */
    fun deleteAll() {
        downloadJob?.cancel()
        scope.launch {
            engine.unload()
            ModelCatalog.all.forEach { spec ->
                store.delete(spec)
                setStatus(spec, ModelStatus.NotInstalled)
            }
        }
    }

    fun delete(spec: ModelSpec) {
        scope.launch {
            if ((engine.state.value as? EngineState.Ready)?.spec?.id == spec.id) engine.unload()
            store.delete(spec)
            setStatus(spec, ModelStatus.NotInstalled)
        }
    }

    /** 사용자에게 보여줄 짧은 상태 문구. 버튼 옆 안내에 쓴다 */
    fun unavailableReason(): String? = when {
        !supported -> "이 기기($platformDescription)에서는 온디바이스 AI 를 쓸 수 없어요."
        !state.value.isReady -> "설정 › AI 에서 모델을 내려받고 켜 주세요."
        else -> null
    }

    private fun setStatus(spec: ModelSpec, status: ModelStatus) {
        _state.update { it.copy(statuses = it.statuses + (spec.id to status)) }
    }
}
