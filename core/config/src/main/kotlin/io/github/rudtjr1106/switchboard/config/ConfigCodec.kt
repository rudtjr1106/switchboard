package io.github.rudtjr1106.switchboard.config

import io.github.rudtjr1106.switchboard.config.json.JsonPretty
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * app-config.json 읽기·쓰기
 *
 * 쓸 때는 항상 같은 서식(키 순서, 2칸 들여쓰기, 마지막 줄바꿈)으로 다시 쓴다. 그래서 PR diff 에는 바꾼 값만 보인다.
 * 모르는 필드는 [Notice.extras]·[AppConfig.extras] 에 담아 두었다가 알려진 필드 뒤에 그대로 다시 쓴다.
 */
object ConfigCodec {

    private val knownRootKeys = setOf("\$schema", "version", "minimumVersion", "notices", "values")
    private val knownNoticeKeys = setOf("screen", "enabled", "template", "title", "body", "until")

    fun decode(text: String, schema: ConfigSchema): AppConfig {
        val root = try {
            Json.parseToJsonElement(text) as? JsonObject
                ?: throw ConfigFormatException("app-config.json 의 최상위가 객체가 아니에요")
        } catch (e: SerializationException) {
            throw ConfigFormatException("app-config.json 이 올바른 JSON 이 아니에요: ${e.message}", e)
        }
        val version = (root["version"] as? JsonPrimitive)?.intOrNull ?: schema.version
        val minimumVersion = when (val value = root["minimumVersion"]) {
            null -> if (schema.supportsMinimumVersion) "" else null
            is JsonPrimitive -> value.contentOrNull ?: ""
            else -> throw ConfigFormatException("minimumVersion 이 문자열이 아니에요")
        }
        val notices = (root["notices"] as? JsonArray).orEmpty().mapIndexed { index, element ->
            val obj = element as? JsonObject ?: throw ConfigFormatException("notices[$index] 가 객체가 아니에요")
            Notice(
                screen = obj.string("screen") ?: throw ConfigFormatException("notices[$index] 에 screen 이 없어요"),
                enabled = (obj["enabled"] as? JsonPrimitive)?.booleanOrNull ?: false,
                template = obj.string("template") ?: NoticeTemplate.INFO.id,
                title = obj.string("title") ?: "",
                body = obj.string("body") ?: "",
                until = obj.string("until"),
                extras = obj.filterKeys { it !in knownNoticeKeys },
            )
        }
        return AppConfig(
            version = version,
            minimumVersion = minimumVersion,
            notices = notices,
            values = (root["values"] as? JsonObject)?.toMap().orEmpty(),
            schemaRef = root.string("\$schema"),
            extras = root.filterKeys { it !in knownRootKeys },
        )
    }

    fun encode(config: AppConfig): String = buildString {
        append("{\n")
        val lines = mutableListOf<String>()
        config.schemaRef?.let { lines += "  \"\$schema\": ${JsonPretty.quote(it)}" }
        lines += "  \"version\": ${config.version}"
        config.minimumVersion?.let { lines += "  \"minimumVersion\": ${JsonPretty.quote(it)}" }
        lines += "  \"notices\": " + encodeNotices(config.notices)
        // 값이 없던 파일은 키를 새로 만들지 않는다 (기존 저장소 파일이 그대로 유지되게)
        if (config.values.isNotEmpty()) lines += "  \"values\": " + encodeValues(config.values)
        for ((key, value) in config.extras) {
            lines += "  ${JsonPretty.quote(key)}: ${JsonPretty.print(value, compactLeaves = false, indentLevel = 1)}"
        }
        append(lines.joinToString(",\n"))
        append("\n}\n")
    }

    private fun encodeValues(values: Map<String, JsonElement>): String =
        values.entries.joinToString(separator = ",\n", prefix = "{\n", postfix = "\n  }") { (key, value) ->
            "    ${JsonPretty.quote(key)}: ${JsonPretty.print(value, compactLeaves = false, indentLevel = 2)}"
        }

    private fun encodeNotices(notices: List<Notice>): String {
        if (notices.isEmpty()) return "[]"
        return notices.joinToString(separator = ",\n", prefix = "[\n", postfix = "\n  ]") { notice ->
            val fields = mutableListOf(
                "screen" to JsonPretty.quote(notice.screen),
                "enabled" to notice.enabled.toString(),
                "template" to JsonPretty.quote(notice.template),
                "title" to JsonPretty.quote(notice.title),
                "body" to JsonPretty.quote(notice.body),
            )
            notice.until?.let { fields += "until" to JsonPretty.quote(it) }
            for ((key, value) in notice.extras) {
                fields += key to JsonPretty.print(value, compactLeaves = false, indentLevel = 3)
            }
            fields.joinToString(separator = ",\n", prefix = "    {\n", postfix = "\n    }") { (key, value) ->
                "      ${JsonPretty.quote(key)}: $value"
            }
        }
    }

    private fun JsonObject.string(key: String): String? = when (val value = this[key]) {
        null -> null
        is JsonPrimitive -> value.contentOrNull
        else -> throw ConfigFormatException("$key 가 문자열이 아니에요")
    }

    private fun JsonElement?.orEmptyArray(): JsonArray = this as? JsonArray ?: JsonArray(emptyList())
}

private fun JsonArray?.orEmpty(): JsonArray = this ?: JsonArray(emptyList())
