package io.github.rudtjr1106.switchboard.ai

import io.github.oshai.kotlinlogging.KotlinLogging
import io.github.rudtjr1106.switchboard.config.NoticeTemplate
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assumptions.assumeTrue
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.exists
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

private val logger = KotlinLogging.logger {}

/**
 * 진짜 Gemma 3 1B 로 네이티브 로드·채팅 템플릿·JSON grammar·스트리밍을 확인한다
 *
 * 모델 파일이 `~/Library/Application Support/Switchboard/models` 에 있거나 `SWITCHBOARD_AI_TESTS=1` 일 때만 돈다.
 * 로드가 10초 넘게 걸리고 close 해도 메모리가 돌아오지 않아(java-llama.cpp 4.2.0) 한 메서드에서 전부 본다.
 */
class LlamaCppEngineIntegrationTest {

    private val modelPath: Path =
        Path(System.getProperty("user.home"), "Library", "Application Support", "Switchboard", "models", ModelCatalog.GEMMA_3_1B.fileName)

    private val enabled: Boolean get() = modelPath.exists() || System.getenv("SWITCHBOARD_AI_TESTS") == "1"

    @Test
    fun `gemma 3 1b loads, chats, labels screens with a grammar and streams`() = runBlocking {
        assumeTrue(enabled, "Gemma 3 1B 모델 파일이 없어요: $modelPath (SWITCHBOARD_AI_TESTS=1 로 강제)")

        val engine = LlamaCppEngine()
        val loadStart = System.nanoTime()
        val model = engine.load(ModelCatalog.GEMMA_3_1B, modelPath)
        val loadMs = (System.nanoTime() - loadStart) / 1_000_000
        assertEquals(EngineState.Ready(ModelCatalog.GEMMA_3_1B), engine.state.value)
        assertSame(model, engine.load(ModelCatalog.GEMMA_3_1B, modelPath), "같은 모델은 다시 올리지 않는다")

        // 1. 채팅 템플릿을 탄 일반 완성
        val hello = model.complete(listOf(ChatMessage(ChatRole.USER, "안녕이라고만 답해")), GenerationOptions(maxTokens = 32))
        logger.info { "인사: $hello" }
        assertTrue(hello.isNotBlank())

        // 2. JSON grammar: 라벨러를 통째로, 그리고 날것의 배열도
        val ids = listOf("Login", "EmailSignUp", "Home")
        val labels = LlmScreenLabeler(engine).label(ids)
        logger.info { "라벨: $labels" }
        assertEquals(listOf("ALL") + ids, labels.map { it.id })
        assertTrue(labels.drop(1).all { it.label.isNotBlank() })

        val raw = model.complete(
            listOf(
                ChatMessage(ChatRole.SYSTEM, "화면 id 에 한국어 label 과 group 을 붙여 JSON 배열로만 답해."),
                ChatMessage(ChatRole.USER, "화면 id: ${ids.joinToString(", ")}"),
            ),
            GenerationOptions(
                maxTokens = 256,
                jsonSchema = """{"type":"array","minItems":3,"maxItems":3,"items":{"type":"object","properties":{"id":{"type":"string","enum":["Login","EmailSignUp","Home"]},"label":{"type":"string"},"group":{"type":"string"}},"required":["id","label","group"]}}""",
            ),
        )
        logger.info { "JSON: $raw" }
        val array = assertIs<JsonArray>(JsonOutput.parseElement(raw), "JSON 배열이어야 해요: $raw")
        assertEquals(ids.toSet(), array.map { it.jsonObject.getValue("id").jsonPrimitive.content }.toSet())

        // 3. 스트리밍과 중간 취소
        val chunks = model.stream(listOf(ChatMessage(ChatRole.USER, "1부터 100까지 세어 봐")), GenerationOptions(maxTokens = 200)).take(3).toList()
        assertEquals(3, chunks.size)

        // 4. 속도: 취소 뒤에도 생성이 되는지 겸해서 본다
        val streamStart = System.nanoTime()
        var tokens = 0
        model.stream(listOf(ChatMessage(ChatRole.USER, "한국의 사계절을 계절마다 두 문장씩 설명해 줘")), GenerationOptions(maxTokens = 96))
            .collect { tokens++ }
        val seconds = (System.nanoTime() - streamStart) / 1e9
        val tokensPerSecond = tokens / seconds
        logger.info { "Gemma 3 1B: 로드 ${loadMs}ms, 생성 ${"%.1f".format(tokensPerSecond)} tok/s ($tokens 토큰, ${"%.2f".format(seconds)}s)" }
        assertTrue(tokens > 10)
        assertTrue(tokensPerSecond > 1)

        // 5. 카피라이터 초안: 모양만 본다
        val draft = LlmNoticeCopywriter(engine).draft("9월 20일 새벽 2시부터 4시까지 서버 점검", NoticeTemplate.BLOCKING, 20, 80)
        logger.info { "초안: ${draft.title} / ${draft.body}" }
        assertTrue(draft.title.isNotBlank())
        assertTrue(draft.title.codePointCount(0, draft.title.length) <= 20)
        assertTrue(draft.body.codePointCount(0, draft.body.length) <= 80)

        engine.unload()
        assertEquals(EngineState.Idle, engine.state.value)
        assertNull(engine.model)
    }
}
