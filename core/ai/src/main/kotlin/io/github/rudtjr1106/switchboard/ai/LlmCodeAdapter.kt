package io.github.rudtjr1106.switchboard.ai

/**
 * Kotlin 파일 템플릿을 프로젝트 관례(패키지·DI·HTTP 라이브러리)에 맞게 고쳐 쓴다
 *
 * 답은 파일 내용 전체여야 한다. 모델이 그래도 ``` 펜스나 설명을 붙이면 걷어낸다.
 */
class LlmCodeAdapter(private val engine: LlmEngine) : CodeAdapter {

    override suspend fun adapt(fileName: String, template: String, projectNotes: String): String {
        val model = engine.require()
        val request = buildString {
            appendLine("파일 이름: ${fileName.trim()}")
            appendLine()
            appendLine("프로젝트 정보:")
            appendLine(projectNotes.trim().ifBlank { "(없음)" })
            appendLine()
            appendLine("템플릿:")
            appendLine(template.trimEnd())
        }
        val text = model.complete(
            listOf(ChatMessage(ChatRole.SYSTEM, SYSTEM_PROMPT), ChatMessage(ChatRole.USER, request)),
            OPTIONS,
        )
        val code = CodeText.extract(text)
        if (code.isBlank()) throw AiUnavailableException("코드를 만들지 못했어요. 다시 시도해 주세요.")
        return code + "\n"
    }

    companion object {
        /** 템플릿이 길어 답도 길다. 온도는 낮춰 코드를 지어내지 않게 */
        private val OPTIONS = GenerationOptions(temperature = 0.1f, maxTokens = 2048)

        private val SYSTEM_PROMPT = """
            너는 안드로이드 프로젝트에 Kotlin 파일 템플릿을 맞춰 넣는 도우미야.
            주어진 템플릿을 프로젝트 정보(패키지 이름, DI·HTTP 라이브러리, 코드 관례)에 맞게 고쳐 써.
            규칙:
            - 템플릿의 동작과 공개 API 이름은 그대로 두고, 패키지·import·DI 등록·HTTP 호출 방식만 프로젝트에 맞춘다.
            - 프로젝트 정보에 없는 라이브러리를 새로 끌어오지 않는다.
            - 완성된 파일 내용 전체만 출력한다. 코드 펜스(```), 설명, 인사말을 붙이지 않는다.
        """.trimIndent()
    }
}

/** 모델이 돌려준 코드 손질 */
internal object CodeText {

    private val fileStart = Regex("""^(package |import |@file:)""")

    /** 펜스를 벗기고, 파일 시작(package/import) 앞에 붙은 설명 줄을 버린다 */
    fun extract(text: String): String {
        val body = ModelText.stripFences(text)
        val lines = body.lines()
        val start = lines.indexOfFirst { fileStart.containsMatchIn(it) }
        val kept = if (start > 0) lines.drop(start) else lines
        return kept.joinToString("\n").trimEnd()
    }
}
