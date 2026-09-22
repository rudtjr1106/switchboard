package io.github.rudtjr1106.switchboard.ai

import de.kherud.llama.InferenceParameters
import de.kherud.llama.LlamaModel
import de.kherud.llama.LogLevel
import de.kherud.llama.ModelParameters
import de.kherud.llama.args.LogFormat
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.nio.file.Path
import kotlin.io.path.isRegularFile
import de.kherud.llama.Pair as LlamaPair

private val logger = KotlinLogging.logger {}

/**
 * java-llama.cpp 로 GGUF 모델을 돌리는 엔진
 *
 * 채팅 템플릿은 `applyTemplate` 로 먼저 프롬프트 문자열을 만들고, 그 문자열로 `complete`/`generate` 를 부른다.
 * 4.2.0 의 네이티브 `requestCompletion` 은 `prompt` 만 읽고 `messages` 는 무시해서, 완성 요청에
 * `setMessages` + `setUseChatTemplate` 를 직접 쓰면 템플릿 없이 빈 프롬프트가 들어간다.
 *
 * 알아둘 것
 * - 네이티브 라이브러리는 jar 에 묶인 것을 임시 폴더로 풀어 쓴다. Apple Silicon 빌드는 Metal 이 내장돼 [gpuLayers] 만큼 GPU 에 올린다.
 * - `LlamaModel.close()` 는 작업 루프만 멈추고 모델 메모리는 풀지 않는다(4.2.0 의 `delete ctx_server` 가 주석 처리돼 있다).
 *   모델을 바꿔 올리면 이전 모델만큼의 메모리가 프로세스에 남는다.
 * - 서버 스레드가 JVM 에 non-daemon 으로 붙어 있어 close 뒤에도 JVM 이 저절로 끝나지 않는다.
 *   앱은 `exitProcess` 로 끝내야 한다(Compose `application` 의 기본 동작).
 */
class LlamaCppEngine(
    private val threads: Int = (Runtime.getRuntime().availableProcessors() - 1).coerceAtLeast(2),
    private val gpuLayers: Int = 99,
    /** Metal 에서 KV 캐시 메모리를 아낀다. M1 + Gemma 3 1B 에선 켜도 속도 차이가 없었다 */
    private val flashAttention: Boolean = true,
) : LlmEngine {

    private val _state = MutableStateFlow<EngineState>(EngineState.Idle)
    override val state: StateFlow<EngineState> = _state.asStateFlow()

    @Volatile
    private var current: LlamaLanguageModel? = null
    override val model: LanguageModel? get() = current

    /** load 와 unload 가 겹치지 않게 */
    private val lifecycle = Mutex()

    override suspend fun load(spec: ModelSpec, path: Path): LanguageModel = lifecycle.withLock {
        current?.takeIf { it.spec.id == spec.id }?.let { return@withLock it }
        closeCurrent()
        _state.value = EngineState.Loading(spec)
        try {
            val loaded = withContext(Dispatchers.IO) { LlamaLanguageModel.open(spec, path, parameters(spec, path)) }
            current = loaded
            _state.value = EngineState.Ready(spec)
            loaded
        } catch (e: AiUnavailableException) {
            _state.value = EngineState.Failed(spec, e.message ?: "AI 모델을 불러오지 못했어요.")
            throw e
        }
    }

    override suspend fun unload() = lifecycle.withLock { closeCurrent() }

    private suspend fun closeCurrent() {
        val old = current ?: return
        current = null
        withContext(Dispatchers.IO) { old.shutdown() }
        _state.value = EngineState.Idle
    }

    private fun parameters(spec: ModelSpec, path: Path): ModelParameters = ModelParameters()
        .setModel(path.toAbsolutePath().toString())
        .setCtxSize(spec.contextLength)
        .setGpuLayers(gpuLayers)
        .setThreads(threads)
        // Gemma 3 템플릿은 jinja 경로가 맞다. 템플릿이 넣는 <bos> 도 이 경로가 지워 준다
        .enableJinja()
        // 서버 로그(slot/timing)가 stderr 로 쏟아지는 걸 막는다. llama/ggml 메시지는 setLogger 콜백으로 받는다
        .disableLog()
        .also { if (flashAttention) it.enableFlashAttn() }
}

/**
 * 올라온 모델 하나. [turns] 로 한 번에 하나의 생성만 돌린다
 *
 * 네이티브 호출은 [dispatcher] 에서만 한다. 같은 스레드를 보장하진 않지만 동시 호출은 막는다.
 */
class LlamaLanguageModel internal constructor(
    override val spec: ModelSpec,
    private val llama: LlamaModel,
) : LanguageModel {

    private val turns = Mutex()
    private val dispatcher = Dispatchers.IO.limitedParallelism(1)

    @Volatile
    private var closed = false

    override suspend fun complete(messages: List<ChatMessage>, options: GenerationOptions): String = turns.withLock {
        withContext(dispatcher) {
            val params = inferenceParameters(messages, options)
            native { complete(params) }.trim()
        }
    }

    override fun stream(messages: List<ChatMessage>, options: GenerationOptions): Flow<String> = flow {
        turns.withLock {
            val iterator = native { generate(inferenceParameters(messages, options)).iterator() }
            try {
                while (iterator.hasNext()) emit(native { iterator.next() }.text)
            } finally {
                // 수집이 취소되면 생성도 멈춘다. 끝까지 읽었으면 hasNext 가 false 라 부르지 않는다
                if (iterator.hasNext()) {
                    runCatching { iterator.cancel() }.onFailure { logger.debug(it) { "생성 취소 실패" } }
                }
            }
        }
    }.flowOn(dispatcher)

    /** 진행 중인 생성이 끝나길 기다렸다가 닫는다 */
    internal suspend fun shutdown() = turns.withLock { close() }

    override fun close() {
        if (closed) return
        closed = true
        runCatching { llama.close() }.onFailure { logger.warn(it) { "${spec.id} 닫기 실패" } }
        logger.info { "모델 내림: ${spec.id}" }
    }

    private fun inferenceParameters(messages: List<ChatMessage>, options: GenerationOptions): InferenceParameters {
        val params = InferenceParameters(renderPrompt(messages))
            .setTemperature(options.temperature)
            .setNPredict(options.maxTokens)
        if (options.stopStrings.isNotEmpty()) params.setStopStrings(*options.stopStrings.toTypedArray())
        options.jsonSchema?.let { schema -> params.setGrammar(native { LlamaModel.jsonSchemaToGrammar(schema) }) }
        return params
    }

    /** SYSTEM 은 하나로 합쳐 system 자리에, 나머지는 user/assistant 로 모델의 채팅 템플릿에 태운다 */
    private fun renderPrompt(messages: List<ChatMessage>): String {
        val system = messages.filter { it.role == ChatRole.SYSTEM }
            .joinToString("\n\n") { it.content.trim() }
            .ifBlank { null }
        val turns = messages.filter { it.role != ChatRole.SYSTEM }
            .map { LlamaPair(if (it.role == ChatRole.USER) "user" else "assistant", it.content) }
        require(turns.isNotEmpty()) { "user 메시지가 하나는 있어야 해요" }
        val prompt = native { applyTemplate(InferenceParameters("").setMessages(system, turns)) }
        // 템플릿이 <bos> 를 글자로 넣어 두면 토크나이저가 또 붙인다. jinja 경로는 이미 지우지만 legacy 템플릿은 모델마다 다르다
        return prompt.removePrefix("<bos>")
    }

    private inline fun <T> native(block: LlamaModel.() -> T): T {
        if (closed) throw AiUnavailableException("AI 모델이 내려간 상태예요. 다시 켜 주세요.")
        return try {
            llama.block()
        } catch (e: RuntimeException) {
            // LlamaException 은 패키지 전용 클래스라 이름으로만 가릴 수 있다
            if (e.javaClass.name == LLAMA_EXCEPTION) throw AiUnavailableException("AI 응답을 만들지 못했어요: ${e.message}", e)
            throw e
        }
    }

    companion object {
        private const val LLAMA_EXCEPTION = "de.kherud.llama.LlamaException"

        @Volatile
        private var loggerInstalled = false

        /** 네이티브 라이브러리를 올리고 모델을 읽는다. 실패는 전부 [AiUnavailableException] 으로 */
        internal fun open(spec: ModelSpec, path: Path, parameters: ModelParameters): LlamaLanguageModel {
            if (!AiSupport.platformSupported()) {
                throw AiUnavailableException(
                    "이 컴퓨터(${AiSupport.describePlatform()})에서는 AI 기능을 쓸 수 없어요. " +
                        "macOS(Apple Silicon·Intel), Windows x64, Linux x64·arm64 에서만 돼요.",
                )
            }
            if (!path.isRegularFile()) throw AiUnavailableException("모델 파일이 없어요: $path")
            val started = System.nanoTime()
            val llama = try {
                installLogger()
                LlamaModel(parameters)
            } catch (e: LinkageError) {
                // UnsatisfiedLinkError, 또는 초기화가 한 번 실패한 뒤의 NoClassDefFoundError
                logger.error(e) { "llama.cpp 네이티브 라이브러리 로드 실패 (${AiSupport.describePlatform()})" }
                throw AiUnavailableException(
                    "이 컴퓨터(${AiSupport.describePlatform()})에서 llama.cpp 라이브러리를 불러오지 못했어요.", e,
                )
            } catch (e: RuntimeException) {
                logger.error(e) { "${spec.id} 로드 실패" }
                throw AiUnavailableException("AI 모델을 불러오지 못했어요: ${e.message ?: e.javaClass.simpleName}", e)
            }
            // loadModel 안의 common_init() 이 로그 콜백을 자기 것으로 덮어쓴다. 다시 걸어야 이후 메시지가 우리 로그로 온다
            installLogger(force = true)
            logger.info { "모델 로드: ${spec.id} (${(System.nanoTime() - started) / 1_000_000}ms)" }
            return LlamaLanguageModel(spec, llama)
        }

        /** llama/ggml 로그를 kotlin-logging 으로. 정적이라 한 번이면 되지만 [force] 로 다시 걸 수 있다 */
        private fun installLogger(force: Boolean = false) {
            if (loggerInstalled && !force) return
            LlamaModel.setLogger(LogFormat.TEXT) { level, message ->
                val text = message.trimEnd()
                if (text.isEmpty()) return@setLogger
                when (level) {
                    LogLevel.ERROR -> logger.warn { "llama: $text" }
                    LogLevel.WARN -> logger.debug { "llama: $text" }
                    else -> logger.trace { "llama: $text" }
                }
            }
            loggerInstalled = true
        }
    }
}
