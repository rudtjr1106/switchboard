package io.github.rudtjr1106.switchboard.ai

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import java.nio.file.Path

/** 미리 적어 둔 답을 순서대로 돌려주는 모델. 받은 프롬프트와 옵션을 [calls] 에 남긴다 */
class FakeLanguageModel(
    vararg responses: String,
    override val spec: ModelSpec = ModelCatalog.GEMMA_3_1B,
) : LanguageModel {

    data class Call(val messages: List<ChatMessage>, val options: GenerationOptions) {
        val system: String get() = messages.filter { it.role == ChatRole.SYSTEM }.joinToString("\n") { it.content }
        val user: String get() = messages.filter { it.role == ChatRole.USER }.joinToString("\n") { it.content }
    }

    private val queue = ArrayDeque(responses.toList())
    val calls = mutableListOf<Call>()

    override suspend fun complete(messages: List<ChatMessage>, options: GenerationOptions): String {
        calls += Call(messages, options)
        return queue.removeFirstOrNull() ?: error("준비된 답이 없어요 (${calls.size}번째 호출)")
    }

    override fun stream(messages: List<ChatMessage>, options: GenerationOptions): Flow<String> =
        flow { emit(complete(messages, options)) }

    override fun close() {}
}

class FakeLlmEngine(initial: LanguageModel? = null) : LlmEngine {

    private val _state = MutableStateFlow<EngineState>(initial?.let { EngineState.Ready(it.spec) } ?: EngineState.Idle)
    override val state: StateFlow<EngineState> = _state.asStateFlow()

    override var model: LanguageModel? = initial
        private set

    override suspend fun load(spec: ModelSpec, path: Path): LanguageModel =
        model ?: throw AiUnavailableException("가짜 엔진에는 모델이 없어요")

    override suspend fun unload() {
        model = null
        _state.value = EngineState.Idle
    }
}
