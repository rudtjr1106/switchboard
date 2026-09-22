package io.github.rudtjr1106.switchboard.ai

import io.github.oshai.kotlinlogging.KotlinLogging
import io.github.rudtjr1106.switchboard.config.NoticeTemplate
import kotlinx.serialization.Serializable

private val logger = KotlinLogging.logger {}

/**
 * 안내 문구를 "~해요" 말투로 다듬거나 상황 한 줄에서 초안을 만든다
 *
 * 출력은 JSON 스키마(grammar)로 `{"title","body"}` 를 강제한다. 글자 수는 grammar 로 막지 않고
 * 프롬프트로 부탁한 뒤, 넘치면 더 짧게 한 번 더 시키고, 그래도 넘치면 코드 포인트 기준으로 자른다.
 */
class LlmNoticeCopywriter(private val engine: LlmEngine) : NoticeCopywriter {

    override suspend fun polish(title: String, body: String, template: NoticeTemplate, titleLimit: Int, bodyLimit: Int): NoticeDraft {
        val request = buildString {
            appendLine("아래 안내 문구를 규칙에 맞게 다듬어 줘. 날짜·시간 같은 정보와 뜻은 그대로 두고 말투와 표현만 고쳐.")
            appendLine(describe(template))
            appendLine(limits(titleLimit, bodyLimit))
            appendLine()
            appendLine("제목: ${title.trim()}")
            appendLine("본문: ${body.trim()}")
        }
        return generate(request, titleLimit, bodyLimit)
    }

    override suspend fun draft(situation: String, template: NoticeTemplate, titleLimit: Int, bodyLimit: Int): NoticeDraft {
        val request = buildString {
            appendLine("아래 상황을 사용자에게 알리는 안내 문구를 새로 써 줘.")
            appendLine(describe(template))
            appendLine(limits(titleLimit, bodyLimit))
            appendLine()
            appendLine("상황: ${situation.trim()}")
        }
        return generate(request, titleLimit, bodyLimit)
    }

    private suspend fun generate(request: String, titleLimit: Int, bodyLimit: Int): NoticeDraft {
        val model = engine.require()
        val first = ask(model, request) ?: throw AiUnavailableException(EMPTY_MESSAGE)
        if (first.fits(titleLimit, bodyLimit)) return first
        logger.debug { "글자 수 초과(제목 ${first.title.codePointLength()}/$titleLimit, 본문 ${first.body.codePointLength()}/$bodyLimit), 더 짧게 한 번 더" }
        val stricter = buildString {
            append(request)
            appendLine()
            appendLine("방금 쓴 문구가 너무 길어. 제목은 ${titleLimit}자, 본문은 ${bodyLimit}자를 절대 넘지 않게 훨씬 짧게 다시 써.")
            appendLine("방금 쓴 문구:")
            appendLine("제목: ${first.title}")
            appendLine("본문: ${first.body}")
        }
        val second = ask(model, stricter) ?: first
        return second.truncated(titleLimit, bodyLimit)
    }

    /** 모델을 한 번 부르고 JSON 을 읽는다. 읽지 못했거나 둘 다 비었으면 null */
    private suspend fun ask(model: LanguageModel, request: String): NoticeDraft? {
        val text = model.complete(
            listOf(ChatMessage(ChatRole.SYSTEM, SYSTEM_PROMPT), ChatMessage(ChatRole.USER, request)),
            OPTIONS,
        )
        val parsed = JsonOutput.parse<NoticeJson>(text) ?: run {
            logger.warn { "안내 문구 JSON 을 읽지 못함: ${text.take(200)}" }
            return null
        }
        val draft = NoticeDraft(NoticeText.clean(parsed.title), NoticeText.clean(parsed.body))
        return draft.takeIf { it.title.isNotBlank() || it.body.isNotBlank() }
    }

    private fun describe(template: NoticeTemplate): String = when (template) {
        NoticeTemplate.BLOCKING -> "안내 종류: 차단 안내. 지금은 앱을 쓸 수 없다는 것과 언제부터 다시 쓸 수 있는지 알려 줘."
        NoticeTemplate.INFO -> "안내 종류: 일반 안내. 무슨 일이 있는지와 사용자가 하면 좋은 일을 알려 줘."
    }

    private fun limits(titleLimit: Int, bodyLimit: Int): String =
        "글자 수: 제목은 ${titleLimit}자 이내, 본문은 ${bodyLimit}자 이내로 써."

    private fun NoticeDraft.fits(titleLimit: Int, bodyLimit: Int): Boolean =
        title.codePointLength() <= titleLimit && body.codePointLength() <= bodyLimit

    private fun NoticeDraft.truncated(titleLimit: Int, bodyLimit: Int): NoticeDraft =
        NoticeDraft(NoticeText.truncate(title, titleLimit), NoticeText.truncate(body, bodyLimit))

    private fun String.codePointLength(): Int = codePointCount(0, length)

    @Serializable
    private data class NoticeJson(val title: String = "", val body: String = "")

    companion object {
        const val SCHEMA = """{"type":"object","properties":{"title":{"type":"string"},"body":{"type":"string"}},"required":["title","body"]}"""
        const val EMPTY_MESSAGE = "문구를 만들지 못했어요. 다시 시도해 주세요."

        private val OPTIONS = GenerationOptions(temperature = 0.3f, maxTokens = 256, jsonSchema = SCHEMA)

        private val SYSTEM_PROMPT = """
            너는 한국 안드로이드 앱에 띄우는 안내 문구를 쓰는 카피라이터야.
            규칙:
            - 사용자에게 말하듯 친근한 "~해요" 말투로 쓴다. "~합니다", "~하십시오", "~바랍니다" 는 쓰지 않는다.
            - 이모지, 마크다운, 특수 기호를 쓰지 않는다.
            - 제목은 한 문장, 본문은 한두 문장으로 짧게 쓴다.
            - 반드시 JSON 객체 하나만 출력한다: {"title": "제목", "body": "본문"}
            예시:
            {"title": "서비스 점검 중이에요", "body": "더 나은 서비스를 위해 점검하고 있어요. 잠시 후 다시 이용해주세요."}
        """.trimIndent()
    }
}

/** 안내 문구 손질. 모델이 규칙을 어기고 넣은 마크다운·이모지를 걷어낸다 */
internal object NoticeText {

    private val markdown = Regex("""\*\*|__|`|^\s*(#{1,6}\s+|[-*]\s+)""", RegexOption.MULTILINE)
    private val emoji = Regex("[\\x{1F000}-\\x{1FAFF}\\x{2600}-\\x{27BF}\\x{FE0F}\\x{200D}\\x{20E3}]")
    private val spaces = Regex("[ \\t]+")

    fun clean(text: String): String = text
        .replace(markdown, "")
        .replace(emoji, "")
        .replace(spaces, " ")
        .lines().joinToString("\n") { it.trim() }
        .trim()

    /** 코드 포인트 기준으로 자른다. 한글은 1자 = 1코드 포인트라 사용자가 보는 글자 수와 같다 */
    fun truncate(text: String, limit: Int): String {
        if (limit <= 0) return ""
        if (text.codePointCount(0, text.length) <= limit) return text
        return text.substring(0, text.offsetByCodePoints(0, limit)).trimEnd()
    }
}
