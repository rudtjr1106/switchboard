package io.github.rudtjr1106.switchboard.config

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull

/**
 * 앱이 읽어가는 자유 값의 타입
 *
 * 원격 설정 값은 결국 사람이 편집기에서 고치는 값이라, 스위치·숫자 칸·드롭다운으로 그릴 수 있는 것만 받는다.
 * 목록이나 중첩 객체는 폼으로 다룰 수 없고 스키마로 실수를 거르기도 어려워서 넣지 않았다.
 */
enum class ValueType(val jsonType: String, val label: String) {
    BOOLEAN("boolean", "켜고 끄기"),
    INTEGER("integer", "정수"),
    NUMBER("number", "소수"),
    STRING("string", "글"),
    ;

    companion object {
        fun fromJsonType(type: String?): ValueType? = entries.firstOrNull { it.jsonType == type }
    }
}

/**
 * `schema.json` 의 `properties.values.properties.<키>` 하나
 *
 * @property options 비어 있지 않으면 드롭다운으로 고른다 (JSON Schema 의 `enum`)
 * @property default 값이 아직 없거나 앱이 오프라인일 때 쓰는 값. 생성 코드에도 이 값이 박힌다
 */
data class ValueSpec(
    val key: String,
    val type: ValueType,
    val label: String,
    val description: String? = null,
    val default: JsonPrimitive,
    val options: List<String> = emptyList(),
    val minimum: Double? = null,
    val maximum: Double? = null,
    val maxLength: Int? = null,
) {
    /** 편집기 목록에 보일 이름 */
    val displayLabel: String get() = label.ifBlank { key }

    companion object {
        /** 키는 그대로 코틀린 프로퍼티 이름이 되므로 코드에 넣을 수 있는 모양만 받는다 */
        val KEY_PATTERN = Regex("^[a-z][A-Za-z0-9]*$")

        fun defaultFor(type: ValueType): JsonPrimitive = when (type) {
            ValueType.BOOLEAN -> JsonPrimitive(false)
            ValueType.INTEGER -> JsonPrimitive(0)
            ValueType.NUMBER -> JsonPrimitive(0.0)
            ValueType.STRING -> JsonPrimitive("")
        }

        /** 스키마 조각 하나를 읽는다. 타입을 모르면 null (편집기가 다루지 못하는 값이라 그대로 둔다) */
        fun parse(key: String, definition: JsonObject): ValueSpec? {
            val type = ValueType.fromJsonType((definition["type"] as? JsonPrimitive)?.content) ?: return null
            val options = (definition["enum"] as? kotlinx.serialization.json.JsonArray)
                ?.mapNotNull { (it as? JsonPrimitive)?.content }.orEmpty()
            val extension = (definition[ConfigSchema.EXTENSION_KEY] as? JsonObject)
            return ValueSpec(
                key = key,
                type = type,
                label = (extension?.get("label") as? JsonPrimitive)?.content ?: key,
                description = (definition["description"] as? JsonPrimitive)?.content,
                default = (definition["default"] as? JsonPrimitive) ?: defaultFor(type),
                options = if (type == ValueType.STRING) options else emptyList(),
                minimum = (definition["minimum"] as? JsonPrimitive)?.doubleOrNull,
                maximum = (definition["maximum"] as? JsonPrimitive)?.doubleOrNull,
                maxLength = (definition["maxLength"] as? JsonPrimitive)?.doubleOrNull?.toInt(),
            )
        }

        /** [ValueSpec] 을 다시 스키마 조각으로 */
        fun render(spec: ValueSpec): JsonObject = buildJsonObjectOrdered {
            put("type", JsonPrimitive(spec.type.jsonType))
            spec.description?.takeIf { it.isNotBlank() }?.let { put("description", JsonPrimitive(it)) }
            if (spec.options.isNotEmpty()) put("enum", kotlinx.serialization.json.JsonArray(spec.options.map(::JsonPrimitive)))
            spec.minimum?.let { put("minimum", numberOf(it, spec.type)) }
            spec.maximum?.let { put("maximum", numberOf(it, spec.type)) }
            spec.maxLength?.let { put("maxLength", JsonPrimitive(it)) }
            put("default", spec.default)
            if (spec.label.isNotBlank() && spec.label != spec.key) {
                put(ConfigSchema.EXTENSION_KEY, JsonObject(mapOf("label" to JsonPrimitive(spec.label))))
            }
        }

        private fun numberOf(value: Double, type: ValueType): JsonPrimitive =
            if (type == ValueType.INTEGER) JsonPrimitive(value.toLong()) else JsonPrimitive(value)

        private fun buildJsonObjectOrdered(build: MutableMap<String, kotlinx.serialization.json.JsonElement>.() -> Unit): JsonObject =
            JsonObject(LinkedHashMap<String, kotlinx.serialization.json.JsonElement>().apply(build))
    }
}

/** 값 하나가 스키마에 맞는지 */
object ValueChecks {

    /** @return 문제가 있으면 사람이 읽는 이유, 없으면 null */
    fun problem(spec: ValueSpec, value: JsonPrimitive): String? = when (spec.type) {
        // "true" 같은 문자열도 booleanOrNull 이 파싱해 주므로 따옴표 여부까지 본다
        ValueType.BOOLEAN -> if (value.isString || value.booleanOrNull == null) "켜고 끄기 값이어야 해요" else null
        ValueType.INTEGER -> {
            val number = value.longOrNull
            when {
                value.isString || number == null -> "정수여야 해요"
                else -> range(number.toDouble(), spec)
            }
        }
        ValueType.NUMBER -> {
            val number = value.doubleOrNull
            when {
                value.isString || number == null -> "숫자여야 해요"
                else -> range(number, spec)
            }
        }
        ValueType.STRING -> {
            val text = if (value.isString) value.content else null
            when {
                text == null -> "글이어야 해요"
                spec.options.isNotEmpty() && text !in spec.options -> "고를 수 있는 값은 ${spec.options.joinToString(", ")} 예요"
                spec.maxLength != null && text.codePointCount(0, text.length) > spec.maxLength -> "${spec.maxLength}자를 넘었어요"
                else -> null
            }
        }
    }

    private fun range(number: Double, spec: ValueSpec): String? = when {
        spec.minimum != null && number < spec.minimum -> "${format(spec.minimum, spec.type)} 이상이어야 해요"
        spec.maximum != null && number > spec.maximum -> "${format(spec.maximum, spec.type)} 이하여야 해요"
        else -> null
    }

    private fun format(value: Double, type: ValueType): String =
        if (type == ValueType.INTEGER) value.toLong().toString() else value.toString()
}

/** 스키마의 `properties.values` 를 읽어 [ValueSpec] 목록으로 (없으면 빈 목록) */
internal fun parseValueSpecs(properties: JsonObject?): List<ValueSpec> {
    val values = (properties?.get("values") as? JsonObject) ?: return emptyList()
    val definitions = (values["properties"] as? JsonObject) ?: return emptyList()
    return definitions.mapNotNull { (key, element) ->
        (element as? JsonObject)?.let { ValueSpec.parse(key, it) }
    }
}
