package io.github.rudtjr1106.switchboard.config

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class ConfigFormatException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * 저장소의 schema.json 에서 읽은 규칙
 *
 * iOS 편집 앱은 규칙을 코드에 따로 갖고 있어 스키마를 바꾸면 앱도 고쳐야 한다.
 * 이 앱은 스키마를 그대로 읽어서 화면 목록·글자 수 제한·최소 버전 지원 여부를 정하므로 저장소마다 규칙이 달라도 된다.
 */
class ConfigSchema internal constructor(
    val text: String,
    val root: JsonObject,
    /** `version` 의 const 값. 앱은 아는 버전일 때만 설정을 읽는다 */
    val version: Int,
    /** 스키마에 `minimumVersion` 이 있으면 강제 업데이트 편집을 켠다 */
    val supportsMinimumVersion: Boolean,
    val minimumVersionPattern: Regex,
    val templateIds: List<String>,
    val titleLimit: Int,
    val bodyLimit: Int,
    val catalog: ScreenCatalog,
    /** `properties.values` 에 정의된 자유 값들. 없으면 빈 목록 */
    val values: List<ValueSpec> = emptyList(),
) {
    val screenIds: List<String> get() = catalog.ids

    /** 편집기가 값 편집을 켤지 */
    val supportsValues: Boolean get() = values.isNotEmpty()

    fun value(key: String): ValueSpec? = values.firstOrNull { it.key == key }

    fun withCatalog(catalog: ScreenCatalog): ConfigSchema = parse(SchemaRenderer.withScreens(text, catalog.screens))

    companion object {
        const val EXTENSION_KEY = "x-switchboard"
        const val DEFAULT_TITLE_LIMIT = 40
        const val DEFAULT_BODY_LIMIT = 200
        const val DEFAULT_MINIMUM_VERSION_PATTERN = "^([0-9]+(\\.[0-9]+){0,2})?$"
        val DEFAULT_TEMPLATES: List<String> = NoticeTemplate.entries.map { it.id }

        fun parse(text: String): ConfigSchema {
            val root = try {
                Json.parseToJsonElement(text).jsonObject
            } catch (e: SerializationException) {
                throw ConfigFormatException("schema.json 이 올바른 JSON 이 아니에요: ${e.message}", e)
            } catch (e: IllegalArgumentException) {
                throw ConfigFormatException("schema.json 의 최상위가 객체가 아니에요", e)
            }
            val properties = root["properties"]?.asObjectOrNull()
                ?: throw ConfigFormatException("schema.json 에 properties 가 없어요")
            val version = properties["version"]?.asObjectOrNull()?.get("const")?.jsonPrimitive?.intOrNull ?: 1
            val minimumVersion = properties["minimumVersion"]?.asObjectOrNull()
            val noticeProperties = properties["notices"]?.asObjectOrNull()
                ?.get("items")?.asObjectOrNull()
                ?.get("properties")?.asObjectOrNull()
                ?: throw ConfigFormatException("schema.json 에 notices.items.properties 가 없어요")
            val screenIds = noticeProperties["screen"]?.asObjectOrNull()?.get("enum")?.jsonArray
                ?.map { it.jsonPrimitive.content }
                ?: throw ConfigFormatException("schema.json 의 screen 에 enum 이 없어요")
            val templateIds = noticeProperties["template"]?.asObjectOrNull()?.get("enum")?.jsonArray
                ?.map { it.jsonPrimitive.content }
                ?: DEFAULT_TEMPLATES
            val titleLimit = noticeProperties["title"]?.asObjectOrNull()?.get("maxLength")?.jsonPrimitive?.intOrNull
                ?: DEFAULT_TITLE_LIMIT
            val bodyLimit = noticeProperties["body"]?.asObjectOrNull()?.get("maxLength")?.jsonPrimitive?.intOrNull
                ?: DEFAULT_BODY_LIMIT

            val metadata = root[EXTENSION_KEY]?.asObjectOrNull()?.get("screens")?.asObjectOrNull()
            val screens = screenIds.map { id ->
                val meta = metadata?.get(id)?.asObjectOrNull()
                ScreenInfo(
                    id = id,
                    label = meta?.get("label")?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
                        ?: ScreenCatalog.defaultLabel(id),
                    group = meta?.get("group")?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() },
                )
            }

            return ConfigSchema(
                text = text,
                root = root,
                version = version,
                supportsMinimumVersion = minimumVersion != null,
                minimumVersionPattern = Regex(
                    minimumVersion?.get("pattern")?.jsonPrimitive?.contentOrNull ?: DEFAULT_MINIMUM_VERSION_PATTERN,
                ),
                templateIds = templateIds,
                titleLimit = titleLimit,
                bodyLimit = bodyLimit,
                catalog = ScreenCatalog(screens),
                values = parseValueSpecs(properties),
            )
        }

        private fun kotlinx.serialization.json.JsonElement.asObjectOrNull(): JsonObject? = this as? JsonObject
    }
}
