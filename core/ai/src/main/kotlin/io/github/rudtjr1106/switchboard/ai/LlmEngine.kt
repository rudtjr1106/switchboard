package io.github.rudtjr1106.switchboard.ai

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import java.nio.file.Path

enum class ChatRole { SYSTEM, USER, ASSISTANT }

data class ChatMessage(val role: ChatRole, val content: String)

data class GenerationOptions(
    val temperature: Float = 0.3f,
    val maxTokens: Int = 512,
    /** JSON Schema 문자열. 주면 llama.cpp 의 grammar 로 출력을 그 형태로 강제한다 */
    val jsonSchema: String? = null,
    val stopStrings: List<String> = emptyList(),
)

/** 로드된 모델 하나. 스레드 안전해야 하며 동시에 하나의 생성만 돌린다 */
interface LanguageModel : AutoCloseable {
    val spec: ModelSpec
    suspend fun complete(messages: List<ChatMessage>, options: GenerationOptions = GenerationOptions()): String
    fun stream(messages: List<ChatMessage>, options: GenerationOptions = GenerationOptions()): Flow<String>
}

sealed interface EngineState {
    data object Idle : EngineState
    data class Loading(val spec: ModelSpec) : EngineState
    data class Ready(val spec: ModelSpec) : EngineState
    data class Failed(val spec: ModelSpec, val message: String) : EngineState
}

class AiUnavailableException(message: String, cause: Throwable? = null) : Exception(message, cause)

/** llama.cpp 를 감싼 엔진. 한 번에 모델 하나만 메모리에 올린다 */
interface LlmEngine {
    val state: StateFlow<EngineState>
    val model: LanguageModel?

    /** 이미 같은 모델이 올라와 있으면 그대로 돌려준다 */
    suspend fun load(spec: ModelSpec, path: Path): LanguageModel

    suspend fun unload()

    /** 모델이 없으면 [AiUnavailableException] */
    fun require(): LanguageModel = model ?: throw AiUnavailableException("AI 모델을 먼저 내려받아 켜 주세요.")
}
