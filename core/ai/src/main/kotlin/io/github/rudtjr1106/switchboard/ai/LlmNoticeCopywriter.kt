package io.github.rudtjr1106.switchboard.ai

import io.github.oshai.kotlinlogging.KotlinLogging
import io.github.rudtjr1106.switchboard.config.NoticeTemplate
import kotlinx.serialization.Serializable

private val logger = KotlinLogging.logger {}

/**
 * 안내 문구를 "~해요" 말투로 다듬거나 상황 한 줄에서 초안을 만든다
 *
 * 출력은 JSON 스키마(grammar)로 `{"title","body"}` 를 강제한다. 첫 답을 [NoticeChecks] 로 검사해서
 * 글자 수 초과, 입력의 날짜·숫자 누락, "~합니다" 말투 같은 문제가 있으면 그 문제를 짚어 한 번 더 시킨다.
 * 두 답 중 문제가 적은 쪽을 쓰고, 그래도 넘치는 글자 수는 코드 포인트 기준으로 자른다.
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
        return generate(request, source = "$title\n$body", titleLimit, bodyLimit)
    }

    override suspend fun draft(situation: String, template: NoticeTemplate, titleLimit: Int, bodyLimit: Int): NoticeDraft {
        val request = buildString {
            appendLine("아래 상황을 사용자에게 알리는 안내 문구를 새로 써 줘.")
            appendLine(describe(template))
            appendLine(limits(titleLimit, bodyLimit))
            appendLine()
            appendLine("상황: ${situation.trim()}")
        }
        return generate(request, source = situation, titleLimit, bodyLimit)
    }

    private suspend fun generate(request: String, source: String, titleLimit: Int, bodyLimit: Int): NoticeDraft {
        val model = engine.require()
        val first = ask(model, request) ?: throw AiUnavailableException(EMPTY_MESSAGE)
        val firstProblems = NoticeChecks.problems(first, source, titleLimit, bodyLimit)
        if (firstProblems.isEmpty()) return first
        logger.debug { "안내 문구 다시 쓰기: ${firstProblems.joinToString()}" }
        val repair = buildString {
            append(request)
            appendLine()
            appendLine("방금 쓴 문구에 문제가 있어. 아래를 모두 고쳐서 다시 써.")
            firstProblems.forEach { appendLine("- $it") }
            appendLine("방금 쓴 문구:")
            appendLine("제목: ${first.title}")
            appendLine("본문: ${first.body}")
        }
        val second = ask(model, repair)
        val best = if (second != null && NoticeChecks.problems(second, source, titleLimit, bodyLimit).size <= firstProblems.size) second else first
        return best.truncated(titleLimit, bodyLimit)
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
        // 말투와 인사말은 모델에게 다시 묻지 않고 코드로 고친다 (KoreanTone). 나머지 문제만 다시 쓰기로 넘긴다
        val draft = NoticeDraft(
            KoreanTone.toHaeyo(NoticeText.clean(parsed.title)),
            KoreanTone.toHaeyo(KoreanTone.stripGreetings(NoticeText.clean(parsed.body))),
        )
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

        /**
         * 말투 규칙과 예시. 예시는 UMC-PRODUCT 원격 설정 저장소에 실제로 올라간 안내에서 가져왔다.
         * 시스템 프롬프트를 고정해 두면 llama.cpp 가 같은 앞부분의 KV 캐시를 다시 쓴다.
         */
        private val SYSTEM_PROMPT = """
            너는 한국 안드로이드 앱에 띄우는 안내 문구를 쓰는 카피라이터야.
            규칙:
            - 사용자에게 말하듯 친근한 "~해요" 말투로 쓴다. "~합니다", "~하십시오", "~바랍니다" 는 쓰지 않는다.
            - 원문이나 상황에 있는 날짜·시간·숫자·기능 이름은 하나도 빠뜨리거나 바꾸지 않는다. 없는 정보를 지어내지 않는다.
            - 제목은 무슨 일인지 한 문장으로, 본문은 이유와 사용자가 할 일을 한두 문장으로 쓴다.
            - 차단 안내는 지금 앱을 쓸 수 없다는 것과 언제 다시 쓸 수 있는지를 말한다. 버튼 안내는 쓰지 않는다.
            - 이모지, 마크다운, 대괄호 같은 자리표시자를 쓰지 않는다. 설명이나 인사말 없이 JSON 만 출력한다.
            - 반드시 JSON 객체 하나만 출력한다: {"title": "제목", "body": "본문"}
            예시:
            {"title": "서비스 점검 중이에요", "body": "더 나은 서비스를 위해 점검하고 있어요. 잠시 후 다시 이용해주세요."}
            {"title": "인증 메일이 안 올 수 있어요", "body": "지금 이메일 발송량이 많아 인증 메일이 늦거나 도착하지 않을 수 있어요. 메일이 오지 않으면 다음 날 다시 시도해주세요."}
            {"title": "가입 승인이 늦어지고 있어요", "body": "지금 가입 신청이 많아 승인까지 시간이 더 걸릴 수 있어요. 승인되면 바로 이용할 수 있으니 조금만 기다려주세요."}
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

/**
 * 안내 문구 검사 (하네스의 결정적 검증). 문제마다 모델에게 그대로 전할 한국어 한 줄을 돌려준다
 *
 * 숫자 검사는 UMC 앱의 온디바이스 AI 프롬프트 규칙("원문의 정보(날짜, 장소, 인원 등)를 빠뜨리거나 지어내지 말 것")을
 * 코드로 옮긴 것이다. 작은 모델은 "9월 20일 2시~4시" 에서 숫자 하나를 흘리는 일이 잦다.
 */
object NoticeChecks {

    private val number = Regex("\\d+")
    private val stiff = Regex("(합니다|습니다|십시오|바랍니다|드립니다|됩니다|였습니다)")
    private val placeholder = Regex("\\[[^\\]]*]|\\{[^}]*}|OOO|○○")

    fun problems(draft: NoticeDraft, source: String, titleLimit: Int, bodyLimit: Int): List<String> {
        val problems = mutableListOf<String>()
        val text = draft.title + "\n" + draft.body
        if (draft.title.isBlank()) problems += "제목이 비었어"
        else if (KoreanTone.isGreeting(draft.title)) problems += "제목이 인사말이야. 무슨 일인지 한 문장으로 써"
        if (draft.body.isBlank()) problems += "본문이 비었어"
        if (draft.title.codePointCount(0, draft.title.length) > titleLimit) problems += "제목이 ${titleLimit}자를 넘어. 더 짧게"
        if (draft.body.codePointCount(0, draft.body.length) > bodyLimit) problems += "본문이 ${bodyLimit}자를 넘어. 더 짧게"
        missingNumbers(source, text).takeIf { it.isNotEmpty() }?.let { problems += "원문의 숫자 ${it.joinToString(", ")} 가 빠졌어. 그대로 넣어" }
        stiff.find(text)?.let { problems += "'${it.value}' 대신 '~해요' 말투로 써" }
        placeholder.find(text)?.let { problems += "'${it.value}' 같은 자리표시자를 쓰지 마" }
        return problems
    }

    /** 원문에 있던 숫자 중 결과에 없는 것. `20` 이 `2026` 안에 있다고 있는 것으로 치지 않는다 */
    fun missingNumbers(source: String, result: String): List<String> {
        val present = number.findAll(result).map { it.value.trimStart('0').ifEmpty { "0" } }.toSet()
        return number.findAll(source).map { it.value.trimStart('0').ifEmpty { "0" } }.distinct().filter { it !in present }.toList()
    }

    /** 말투 검사만. 평가 하네스가 쓴다 */
    fun isFriendlyTone(text: String): Boolean = !stiff.containsMatchIn(text)
}
