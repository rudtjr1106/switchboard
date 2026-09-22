package io.github.rudtjr1106.switchboard.ai

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

/**
 * 모델 답변에서 JSON 을 꺼낸다
 *
 * grammar 로 형태를 강제해도 모델에 따라 ``` 펜스나 "결과는 다음과 같아요:" 같은 서두가 붙을 수 있어
 * 첫 객체·배열만 괄호 짝을 세어 잘라낸다.
 */
object JsonOutput {

    val json: Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    /** 첫 JSON 객체 또는 배열 텍스트. 괄호가 끝까지 닫히지 않으면(출력이 잘림) null */
    fun extract(text: String): String? {
        val body = ModelText.stripFences(text)
        val start = body.indexOfFirst { it == '{' || it == '[' }.takeIf { it >= 0 } ?: return null
        var depth = 0
        var inString = false
        var escaped = false
        for (i in start until body.length) {
            val c = body[i]
            when {
                escaped -> escaped = false
                inString -> when (c) {
                    '\\' -> escaped = true
                    '"' -> inString = false
                }
                c == '"' -> inString = true
                c == '{' || c == '[' -> depth++
                c == '}' || c == ']' -> {
                    depth--
                    if (depth == 0) return body.substring(start, i + 1)
                }
            }
        }
        return null
    }

    fun parseElement(text: String): JsonElement? =
        extract(text)?.let { runCatching { json.parseToJsonElement(it) }.getOrNull() }

    /** 꺼낸 JSON 을 [T] 로 읽는다. 없거나 형태가 맞지 않으면 null */
    inline fun <reified T> parse(text: String): T? =
        extract(text)?.let { runCatching { json.decodeFromString<T>(it) }.getOrNull() }
}

/** 모델 답변 텍스트 손질 */
object ModelText {

    /** ``` 또는 ```kotlin 같은 여는 펜스 한 줄 */
    private val openingFence = Regex("```[A-Za-z0-9_+.#-]*[ \\t]*\\r?\\n?")

    /**
     * 첫 ``` 펜스 안의 내용만 남긴다. 펜스가 없으면 앞뒤 공백만 지운다
     *
     * 펜스 앞의 설명("아래는 결과예요:")은 함께 버린다. 닫는 펜스가 없으면(출력이 잘림) 끝까지 쓴다.
     */
    fun stripFences(text: String): String {
        val open = openingFence.find(text) ?: return text.trim()
        val contentStart = open.range.last + 1
        val close = text.indexOf("```", contentStart)
        val inner = if (close < 0) text.substring(contentStart) else text.substring(contentStart, close)
        return inner.trim()
    }
}
