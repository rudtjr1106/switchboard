package io.github.rudtjr1106.switchboard.config.json

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * 저장소 파일과 같은 서식(2칸 들여쓰기, 키 순서 유지)으로 JSON 을 쓴다
 *
 * kotlinx 의 prettyPrint 는 `["a", "b"]` 같은 짧은 배열까지 여러 줄로 펼쳐서 PR diff 가 지저분해진다.
 * [compactLeaves] 가 true 면 원시 값만 담은 객체·배열을 한 줄로 쓴다 (schema.json 서식).
 */
object JsonPretty {

    private const val INDENT = "  "

    fun print(element: JsonElement, compactLeaves: Boolean, indentLevel: Int = 0): String =
        buildString { write(element, compactLeaves, indentLevel) }

    fun quote(text: String): String = JsonPrimitive(text).toString()

    private fun StringBuilder.write(element: JsonElement, compactLeaves: Boolean, level: Int) {
        when (element) {
            is JsonNull -> append("null")
            is JsonPrimitive -> append(element.toString())
            is JsonArray -> writeArray(element, compactLeaves, level)
            is JsonObject -> writeObject(element, compactLeaves, level)
        }
    }

    private fun StringBuilder.writeArray(array: JsonArray, compactLeaves: Boolean, level: Int) {
        if (array.isEmpty()) {
            append("[]")
            return
        }
        if (compactLeaves && array.all { it is JsonPrimitive }) {
            append(array.joinToString(prefix = "[", postfix = "]", separator = ", ") { it.toString() })
            return
        }
        append("[\n")
        array.forEachIndexed { index, item ->
            append(INDENT.repeat(level + 1))
            write(item, compactLeaves, level + 1)
            if (index < array.size - 1) append(',')
            append('\n')
        }
        append(INDENT.repeat(level)).append(']')
    }

    private fun StringBuilder.writeObject(obj: JsonObject, compactLeaves: Boolean, level: Int) {
        if (obj.isEmpty()) {
            append("{}")
            return
        }
        if (compactLeaves && obj.values.all { it is JsonPrimitive || (it is JsonArray && it.all { item -> item is JsonPrimitive }) }) {
            append("{ ")
            append(obj.entries.joinToString(", ") { (key, value) -> quote(key) + ": " + print(value, compactLeaves = true) })
            append(" }")
            return
        }
        append("{\n")
        obj.entries.forEachIndexed { index, (key, value) ->
            append(INDENT.repeat(level + 1)).append(quote(key)).append(": ")
            write(value, compactLeaves, level + 1)
            if (index < obj.size - 1) append(',')
            append('\n')
        }
        append(INDENT.repeat(level)).append('}')
    }
}
