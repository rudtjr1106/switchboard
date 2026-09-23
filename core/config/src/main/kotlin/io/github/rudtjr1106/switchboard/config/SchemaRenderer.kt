package io.github.rudtjr1106.switchboard.config

import io.github.rudtjr1106.switchboard.config.json.JsonPretty
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

/** schema.json 을 만들거나 화면 목록만 바꾼다. 서식은 저장소의 기존 파일과 같다 */
object SchemaRenderer {

    const val DIALECT = "https://json-schema.org/draft/2020-12/schema"

    fun render(
        screens: List<ScreenInfo>,
        includeMinimumVersion: Boolean = true,
        titleLimit: Int = ConfigSchema.DEFAULT_TITLE_LIMIT,
        bodyLimit: Int = ConfigSchema.DEFAULT_BODY_LIMIT,
        templates: List<String> = ConfigSchema.DEFAULT_TEMPLATES,
    ): String {
        val required = buildList {
            add("version")
            if (includeMinimumVersion) add("minimumVersion")
            add("notices")
        }
        val root = buildJsonObject {
            put("\$schema", DIALECT)
            put("type", "object")
            put("required", JsonArray(required.map(::JsonPrimitive)))
            put("additionalProperties", false)
            putJsonObject("properties") {
                putJsonObject("\$schema") { put("type", "string") }
                putJsonObject("version") { put("const", 1) }
                if (includeMinimumVersion) {
                    putJsonObject("minimumVersion") {
                        put("type", "string")
                        put("pattern", ConfigSchema.DEFAULT_MINIMUM_VERSION_PATTERN)
                    }
                }
                putJsonObject("notices") {
                    put("type", "array")
                    putJsonObject("items") {
                        put("type", "object")
                        put("required", JsonArray(listOf("screen", "enabled", "template", "title", "body").map(::JsonPrimitive)))
                        put("additionalProperties", false)
                        putJsonObject("properties") {
                            putJsonObject("screen") { put("enum", JsonArray(screens.map { JsonPrimitive(it.id) })) }
                            putJsonObject("enabled") { put("type", "boolean") }
                            putJsonObject("template") { put("enum", JsonArray(templates.map(::JsonPrimitive))) }
                            putJsonObject("title") {
                                put("type", "string")
                                put("minLength", 1)
                                put("maxLength", titleLimit)
                            }
                            putJsonObject("body") {
                                put("type", "string")
                                put("minLength", 1)
                                put("maxLength", bodyLimit)
                            }
                            putJsonObject("until") {
                                put("type", "string")
                                put("format", "date")
                            }
                        }
                    }
                }
            }
            metadata(screens)?.let { put(ConfigSchema.EXTENSION_KEY, it) }
        }
        return JsonPretty.print(root, compactLeaves = true) + "\n"
    }

    /** 기존 schema.json 에서 screen enum 과 x-switchboard 만 바꾸고 나머지는 그대로 둔다 */
    fun withScreens(schemaText: String, screens: List<ScreenInfo>): String {
        val root = Json.parseToJsonElement(schemaText).jsonObject
        val updated = root.mapValuesTo(LinkedHashMap()) { (key, value) ->
            if (key == "properties") replaceScreenEnum(value.jsonObject, screens) else value
        }
        val metadata = metadata(screens)
        if (metadata != null) updated[ConfigSchema.EXTENSION_KEY] = metadata else updated.remove(ConfigSchema.EXTENSION_KEY)
        return JsonPretty.print(JsonObject(updated), compactLeaves = true) + "\n"
    }

    /**
     * 자유 값 정의를 통째로 바꾼 schema.json
     *
     * `properties.values` 가 없으면 만들고, 목록이 비면 지운다. 나머지 부분은 건드리지 않는다.
     */
    fun withValues(schemaText: String, specs: List<ValueSpec>): String {
        val root = Json.parseToJsonElement(schemaText).jsonObject
        val properties = root["properties"]?.jsonObject ?: JsonObject(emptyMap())
        val updatedProperties = LinkedHashMap(properties)
        if (specs.isEmpty()) {
            updatedProperties.remove("values")
        } else {
            val definitions = LinkedHashMap<String, kotlinx.serialization.json.JsonElement>()
            specs.forEach { definitions[it.key] = ValueSpec.render(it) }
            // 앱이 모르는 키가 와도 무시하도록 additionalProperties 는 열어 둔다
            updatedProperties["values"] = JsonObject(
                linkedMapOf(
                    "type" to JsonPrimitive("object"),
                    "description" to JsonPrimitive("앱이 읽어가는 자유 값"),
                    "properties" to JsonObject(definitions),
                ),
            )
        }
        val updated = LinkedHashMap(root)
        updated["properties"] = JsonObject(updatedProperties)
        return JsonPretty.print(JsonObject(updated), compactLeaves = true) + "\n"
    }

    private fun replaceScreenEnum(properties: JsonObject, screens: List<ScreenInfo>): JsonObject =
        properties.replace("notices") { notices ->
            notices.jsonObject.replace("items") { items ->
                items.jsonObject.replace("properties") { noticeProperties ->
                    noticeProperties.jsonObject.replace("screen") { screen ->
                        screen.jsonObject.replace("enum") { JsonArray(screens.map { JsonPrimitive(it.id) }) }
                    }
                }
            }
        }

    private fun JsonObject.replace(key: String, transform: (kotlinx.serialization.json.JsonElement) -> kotlinx.serialization.json.JsonElement): JsonObject =
        JsonObject(mapValues { (k, v) -> if (k == key) transform(v) else v })

    private fun metadata(screens: List<ScreenInfo>): JsonObject? {
        if (screens.none { it.hasMetadata }) return null
        return buildJsonObject {
            putJsonObject("screens") {
                for (screen in screens.filter { it.hasMetadata }) {
                    putJsonObject(screen.id) {
                        put("label", screen.label)
                        screen.group?.let { put("group", it) }
                    }
                }
            }
        }
    }
}
